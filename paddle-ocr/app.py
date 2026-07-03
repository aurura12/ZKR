import io
import re
import logging
from datetime import datetime
from typing import Optional

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from paddleocr import PaddleOCR

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("erp-paddle-ocr")

app = FastAPI(title="ERP PaddleOCR", version="1.0.0")

ocr = PaddleOCR(lang="ch", use_angle_cls=True, show_log=False)


@app.get("/health")
async def health():
    return {"status": "ok", "service": "erp-paddle-ocr"}


@app.post("/ocr/invoice")
async def ocr_invoice(file: UploadFile = File(...)):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(400, "Only image files are supported")

    try:
        image_bytes = await file.read()
        results = ocr.ocr(image_bytes, cls=True)

        if not results or not results[0]:
            return JSONResponse({"success": True, "text": "", "fields": {}, "confidence": 0.0})

        lines = []
        for line_result in results[0]:
            text = line_result[1][0]
            confidence = line_result[1][1]
            lines.append({"text": text, "confidence": confidence})

        full_text = "\n".join(l["text"] for l in lines)
        avg_confidence = sum(l["confidence"] for l in lines) / len(lines) if lines else 0.0

        fields = parse_invoice_fields(lines)

        return {
            "success": True,
            "text": full_text,
            "fields": fields,
            "raw_lines": lines,
            "confidence": round(avg_confidence, 4),
        }

    except Exception as e:
        logger.error(f"OCR failed: {e}")
        return JSONResponse({"success": False, "error": str(e)}, status_code=500)


def parse_invoice_fields(lines: list) -> dict:
    fields = {
        "invoice_number": None,
        "invoice_date": None,
        "amount_incl_tax": None,
        "amount_ex_tax": None,
        "tax_amount": None,
        "tax_rate": None,
        "seller_name": None,
        "buyer_name": None,
        "items": None,
    }

    all_text = "\n".join(l["text"].strip() for l in lines)

    invoice_no = _extract_invoice_no(all_text)
    if invoice_no:
        fields["invoice_number"] = invoice_no

    date_str = _extract_date(all_text)
    if date_str:
        fields["invoice_date"] = date_str

    amounts = _extract_amounts(all_text)
    fields.update(amounts)

    tax_info = _extract_tax_rate(all_text)
    if tax_info:
        fields["tax_rate"] = tax_info

    seller = _extract_company_name(all_text, "销")
    if seller:
        fields["seller_name"] = seller

    buyer = _extract_company_name(all_text, "购")
    if buyer:
        fields["buyer_name"] = buyer

    items_text = _extract_items(lines)
    if items_text:
        fields["items"] = items_text

    return fields


def _extract_invoice_no(text: str) -> Optional[str]:
    patterns = [
        r"发票号码[:：]\s*(\d{8,20})",
        r"发票代码[:：]\s*(\d{10,12})",
        r"号码[:：]\s*(\d{8,20})",
        r"No[:：]\s*(\d{8,20})",
        r"(\d{10})[\n\s]*(\d{8})",
    ]
    for pat in patterns:
        m = re.search(pat, text)
        if m:
            if m.lastindex and m.lastindex >= 2:
                return m.group(1) + m.group(2)
            return m.group(1)
    return None


def _extract_date(text: str) -> Optional[str]:
    patterns = [
        r"开票日期[:：]\s*(\d{4})年(\d{1,2})月(\d{1,2})日",
        r"开票日期[:：]\s*(\d{4}-\d{1,2}-\d{1,2})",
        r"日期[:：]\s*(\d{4})年(\d{1,2})月(\d{1,2})日",
        r"(\d{4})年(\d{1,2})月(\d{1,2})日",
    ]
    for pat in patterns:
        m = re.search(pat, text)
        if m:
            if m.lastindex and m.lastindex >= 3:
                y, mo, d = m.group(1), m.group(2).zfill(2), m.group(3).zfill(2)
                return f"{y}-{mo}-{d}"
            if m.lastindex and m.lastindex == 1:
                return m.group(1)
    return None


def _extract_amounts(text: str) -> dict:
    result = {"amount_incl_tax": None, "amount_ex_tax": None, "tax_amount": None}

    patterns = {
        "amount_incl_tax": [
            r"价税合计[（(]大写[)）]?[:：]?\s*[¥￥]?\s*(\d+\.?\d*)",
            r"合\s*计[:：]?\s*[¥￥]?\s*(\d+\.?\d*)",
            r"价税合计[（(]小写[)）]?[:：]?\s*[¥￥]?\s*(\d+\.?\d*)",
        ],
        "amount_ex_tax": [
            r"不含税金额[:：]?\s*[¥￥]?\s*(\d+\.?\d*)",
            r"金\s*额[:：]?\s*[¥￥]?\s*(\d+\.?\d*)",
        ],
        "tax_amount": [
            r"税\s*额[:：]?\s*[¥￥]?\s*(\d+\.?\d*)",
        ],
    }

    for field, pats in patterns.items():
        for pat in pats:
            m = re.search(pat, text.replace(" ", "").replace("\u3000", ""))
            if m:
                try:
                    result[field] = float(m.group(1))
                except ValueError:
                    pass
                break

    if result["amount_incl_tax"] is None:
        m = re.search(r"[¥￥]\s*(\d+\.?\d*)", text)
        if m:
            lines_after = text.split(m.group(0))[1][:200] if m.group(0) in text else ""
            if "合计" in text[: m.start()] or "价税" in text[: m.start()]:
                try:
                    result["amount_incl_tax"] = float(m.group(1))
                except ValueError:
                    pass

    return result


def _extract_tax_rate(text: str) -> Optional[str]:
    m = re.search(r"税率[:：]?\s*(\d+%?)", text)
    if m:
        rate = m.group(1)
        return rate if rate.endswith("%") else rate + "%"
    return None


def _extract_company_name(text: str, prefix: str) -> Optional[str]:
    patterns = [
        rf"{prefix}方名称[:：]\s*(\S+有限公司|\S+有限责任公司|\S+（集团）有限公司|\S+公司|\S+企业|\S+工厂|\S+事务所|\S+中心)",
        rf"{prefix}方[:：]\s*(\S+有限公司|\S+有限责任公司|\S+（集团）有限公司|\S+公司|\S+企业|\S+工厂|\S+事务所|\S+中心)",
        rf"名称[:：]\s*(\S+有限公司|\S+有限责任公司|\S+（集团）有限公司|\S+公司)",
    ]
    cleaned = text.replace(" ", "").replace("\u3000", "")
    for pat in patterns:
        m = re.search(pat, cleaned)
        if m:
            return m.group(1)
    return None


def _extract_items(lines: list) -> Optional[str]:
    keywords = ["货物", "服务", "名称", "规格型号", "商品", "项目"]
    item_lines = []

    for line_info in lines:
        text = line_info["text"].strip()
        if any(kw in text for kw in keywords) and ":" in text:
            item_lines.append(text.split(":", 1)[-1].strip() if ":" in text else text)

    if item_lines:
        return "; ".join(item_lines[-3:])

    for line_info in reversed(lines):
        text = line_info["text"].strip()
        if len(text) > 3 and not re.match(r"^[\d.,¥￥%]+$", text) and "公司" not in text and "发票" not in text:
            return text

    return None
