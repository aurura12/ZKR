import io
import re
import os
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


@app.post("/ocr/contract")
async def ocr_contract(file: UploadFile = File(...)):
    ext = os.path.splitext(file.filename or "")[1].lower()
    image_exts = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".tif"}

    try:
        file_bytes = await file.read()
        all_text = ""

        if ext in image_exts:
            all_text = _ocr_image(file_bytes)

        elif ext == ".pdf":
            all_text = _ocr_pdf(file_bytes)

        elif ext in {".docx", ".doc"}:
            all_text = _extract_docx_text(file_bytes)

        elif ext in {".txt", ".csv", ".md"}:
            all_text = file_bytes.decode("utf-8", errors="ignore")

        else:
            all_text = _ocr_image(file_bytes)

        fields = _parse_contract_fields(all_text)

        return {
            "success": True,
            "text": all_text[:5000],
            "fields": fields,
            "confidence": 0.95,
        }

    except Exception as e:
        logger.error(f"Contract OCR failed: {e}")
        return JSONResponse({"success": False, "error": str(e)}, status_code=500)


def _ocr_image(image_bytes: bytes) -> str:
    results = ocr.ocr(image_bytes, cls=True)
    if not results or not results[0]:
        return ""
    return "\n".join(line[1][0] for line in results[0])


def _ocr_pdf(pdf_bytes: bytes) -> str:
    all_text = ""

    try:
        from PyPDF2 import PdfReader
        reader = PdfReader(io.BytesIO(pdf_bytes))
        for page in reader.pages[:10]:
            text = page.extract_text()
            if text and text.strip():
                all_text += text + "\n"
        if len(all_text.strip()) > 200:
            return all_text
    except Exception as e:
        logger.warning(f"PyPDF2 extraction failed: {e}")

    try:
        from pdf2image import convert_from_bytes
        images = convert_from_bytes(pdf_bytes, first_page=1, last_page=3, dpi=200)
        for img in images:
            img_bytes = io.BytesIO()
            img.save(img_bytes, format="PNG")
            all_text += _ocr_image(img_bytes.getvalue()) + "\n"
    except Exception as e:
        logger.warning(f"pdf2image OCR fallback failed: {e}")

    return all_text


def _extract_docx_text(docx_bytes: bytes) -> str:
    try:
        from docx import Document
        doc = Document(io.BytesIO(docx_bytes))
        paragraphs = [p.text for p in doc.paragraphs if p.text.strip()]
        return "\n".join(paragraphs)
    except Exception as e:
        logger.warning(f"python-docx extraction failed: {e}")
        return ""


def _parse_contract_fields(text: str) -> dict:
    fields = {
        "contract_no": None,
        "company": None,
        "signing_entity": None,
        "counterparty": None,
        "description": None,
        "sign_type": None,
        "sign_date": None,
        "contract_amount": None,
        "currency": None,
        "payment_method": None,
        "start_date": None,
        "end_date": None,
        "collection_date": None,
        "status": None,
        "collected_amount": None,
        "uncollected_amount": None,
        "invoice_status": None,
        "invoice_amount": None,
        "responsible_person": None,
        "archive_no": None,
        "remarks": None,
    }

    fields["contract_no"] = _re_first(text, [r"合同编号[:：]\s*(\S+)", r"合同号[:：]\s*(\S+)", r"编号[:：]\s*([A-Za-z0-9\-]+)"])
    fields["company"] = _re_first(text, [r"甲方[:：]\s*(\S{2,30}(?:有限公司|有限责任公司|（集团）有限公司|公司|企业|工厂|事务所|中心))"])
    fields["signing_entity"] = fields.get("company")
    fields["counterparty"] = _re_first(text, [r"乙方[:：]\s*(\S{2,30}(?:有限公司|有限责任公司|（集团）有限公司|公司|企业|工厂|事务所|中心))", r"对方[:：]\s*(\S{2,30}(?:有限公司|有限责任公司|公司))"])
    fields["description"] = _re_first(text, [r"合同名称[:：]\s*(.+)", r"项目名称[:：]\s*(.+)", r"标的[:：]\s*(.+)"])
    fields["sign_type"] = _re_first(text, [r"签署类型[:：]\s*(\S+)", r"签署方式[:：]\s*(\S+)"])
    fields["sign_date"] = _extract_contract_date(text)
    fields["contract_amount"] = _re_first_amount(text, [r"合同金额[:：]\s*[¥￥]?\s*(\d+\.?\d*)", r"总金额[:：]\s*[¥￥]?\s*(\d+\.?\d*)", r"金额[:：]\s*[¥￥]?\s*(\d+\.?\d*)"])
    fields["currency"] = _re_first(text, [r"币种[:：]\s*(\S+)", r"货币[:：]\s*(\S+)"])
    fields["payment_method"] = _re_first(text, [r"支付方式[:：]\s*(.+)", r"付款方式[:：]\s*(.+)"])
    fields["start_date"] = _extract_contract_date_second(text)
    fields["end_date"] = _re_first(text, [r"结束日期[:：]\s*(\d{4}-\d{1,2}-\d{1,2})", r"截止日期[:：]\s*(\d{4}-\d{1,2}-\d{1,2})", r"终止日期[:：]\s*(\d{4}-\d{1,2}-\d{1,2})"])
    fields["collection_date"] = _re_first(text, [r"回款时间[:：]\s*(\d{4}-\d{1,2}-\d{1,2})", r"回款日期[:：]\s*(\d{4}-\d{1,2}-\d{1,2})"])
    fields["status"] = _re_first(text, [r"合同状态[:：]\s*(\S+)", r"状态[:：]\s*(\S+)"])
    fields["collected_amount"] = _re_first_amount(text, [r"累计回款[:：]\s*[¥￥]?\s*(\d+\.?\d*)", r"已回款[:：]\s*[¥￥]?\s*(\d+\.?\d*)"])
    fields["uncollected_amount"] = _re_first_amount(text, [r"未回款[:：]\s*[¥￥]?\s*(\d+\.?\d*)", r"待回款[:：]\s*[¥￥]?\s*(\d+\.?\d*)"])
    fields["invoice_status"] = _re_first(text, [r"开票情况[:：]\s*(.+)", r"发票情况[:：]\s*(.+)"])
    fields["invoice_amount"] = _re_first_amount(text, [r"发票金额[:：]\s*[¥￥]?\s*(\d+\.?\d*)"])
    fields["responsible_person"] = _re_first(text, [r"负责人[:：]\s*(\S+)", r"业务负责人[:：]\s*(\S+)", r"经办人[:：]\s*(\S+)"])
    fields["archive_no"] = _re_first(text, [r"归档编号[:：]\s*(\S+)", r"档案号[:：]\s*(\S+)"])
    fields["remarks"] = _re_first(text, [r"备注[:：]\s*(.+)"])
    if not fields["currency"]:
        fields["currency"] = "CNY"

    return fields


def _re_first(text: str, patterns: list) -> Optional[str]:
    for pat in patterns:
        m = re.search(pat, text)
        if m:
            return m.group(1).strip().rstrip(";；,，")
    return None


def _re_first_amount(text: str, patterns: list) -> Optional[float]:
    for pat in patterns:
        m = re.search(pat, text)
        if m:
            try:
                return float(m.group(1))
            except ValueError:
                pass
    return None


def _extract_contract_date(text: str) -> Optional[str]:
    patterns = [
        r"签署日期[:：]\s*(\d{4}-\d{1,2}-\d{1,2})",
        r"签订日期[:：]\s*(\d{4}-\d{1,2}-\d{1,2})",
        r"签约日期[:：]\s*(\d{4}-\d{1,2}-\d{1,2})",
        r"(\d{4})年(\d{1,2})月(\d{1,2})日",
    ]
    for pat in patterns:
        m = re.search(pat, text)
        if m:
            if m.lastindex and m.lastindex >= 3:
                return f"{m.group(1)}-{m.group(2).zfill(2)}-{m.group(3).zfill(2)}"
            return m.group(1)
    return None


def _extract_contract_date_second(text: str) -> Optional[str]:
    pats = [
        r"开始日期[:：]\s*(\d{4}-\d{1,2}-\d{1,2})",
        r"起始日期[:：]\s*(\d{4}-\d{1,2}-\d{1,2})",
        r"生效日期[:：]\s*(\d{4}-\d{1,2}-\d{1,2})",
        r"开始时间[:：]\s*(\d{4}-\d{1,2}-\d{1,2})",
    ]
    return _re_first(text, pats)
