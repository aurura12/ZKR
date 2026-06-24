package com.smartlab.erp.agreement;

import com.smartlab.erp.entity.User;
import com.smartlab.erp.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgreementGenerationService {

    private final AgreementTemplateRepository templateRepository;

    public byte[] generateAgreement(User user, AgreementType type) {
        AgreementTemplate template = templateRepository.findByCode(type)
                .orElseThrow(() -> new ResourceNotFoundException("协议模板不存在: " + type));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(template.getContent()));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            switch (type) {
                case INTERNET -> fillInternetAgreement(doc, user);
                case GENERAL -> fillGeneralAgreement(doc, user);
                case PROOF -> fillProof(doc, user);
            }

            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("协议生成失败 [" + type + "]: " + e.getMessage(), e);
        }
    }

    private void fillInternetAgreement(XWPFDocument doc, User user) {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(3);
        List<XWPFParagraph> paragraphs = doc.getParagraphs();

        setParagraphText(paragraphs.get(6),
                "姓 名： " + user.getName() + "                    身份证号码：" + user.getIdNumber());
        setParagraphText(paragraphs.get(7), "学校院系： " + user.getSchoolDepartment());
        setParagraphText(paragraphs.get(8), "联系电话： " + user.getPhone());
        setParagraphText(paragraphs.get(9), "住 址： " + user.getAddress());

        fillDateParagraph(paragraphs.get(11), start, end);
    }

    private void fillGeneralAgreement(XWPFDocument doc, User user) {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(3);
        List<XWPFParagraph> paragraphs = doc.getParagraphs();

        setParagraphText(paragraphs.get(6),
                "姓 名： " + user.getName() + "                   身份证号码：" + user.getIdNumber());
        setParagraphText(paragraphs.get(7), "学校院系： " + user.getSchoolDepartment());
        setParagraphText(paragraphs.get(8), "联系电话： " + user.getPhone());
        setParagraphText(paragraphs.get(9), "住 址: " + user.getAddress());

        String dateText = "  " + start.getYear() + "年 " + start.getMonthValue() + "月 " + start.getDayOfMonth() + "日 至 "
                + end.getYear() + "年 " + end.getMonthValue() + "月 " + end.getDayOfMonth() + "日）。"
                + "乙方保证其有资格签订本协议，且承诺其参加实习符合国家及所在学校的相关规定，如因此引起纠纷由乙方自行全部承担。 ";
        setParagraphText(paragraphs.get(12), dateText);
    }

    private void fillProof(XWPFDocument doc, User user) {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(3);
        String school = extractSchoolName(user.getSchoolDepartment());

        String text = "兹有  " + school + "     学校 " + user.getName() + "    同学（身份证号: " + user.getIdNumber() + " ）于 "
                + start.getYear() + "年 " + start.getMonthValue() + "月 " + start.getDayOfMonth() + "日至 "
                + end.getYear() + "年 " + end.getMonthValue() + "月 " + end.getDayOfMonth() + "日 "
                + "在我单位  互联网软件技术实验室（部门）实习。在职期间，工作积极，成绩突出。";
        setParagraphText(doc.getParagraphs().get(2), text);
    }

    private void setParagraphText(XWPFParagraph paragraph, String text) {
        if (paragraph == null) {
            return;
        }
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs != null && !runs.isEmpty()) {
            runs.get(0).setText(text, 0);
            for (int i = 1; i < runs.size(); i++) {
                runs.get(i).setText("", 0);
            }
        } else {
            XWPFRun run = paragraph.createRun();
            run.setText(text);
        }
    }

    private void fillDateParagraph(XWPFParagraph paragraph, LocalDate start, LocalDate end) {
        if (paragraph == null) {
            return;
        }
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.size() < 22) {
            return;
        }
        runs.get(5).setText(" " + start.getYear(), 0);
        runs.get(6).setText("", 0);
        runs.get(7).setText("", 0);
        runs.get(10).setText(" " + start.getMonthValue(), 0);
        runs.get(12).setText(" " + start.getDayOfMonth(), 0);
        runs.get(13).setText("", 0);
        runs.get(15).setText(" " + end.getYear(), 0);
        runs.get(16).setText("", 0);
        runs.get(17).setText("", 0);
        runs.get(19).setText(" " + end.getMonthValue(), 0);
        runs.get(21).setText(" " + end.getDayOfMonth(), 0);
    }

    private String extractSchoolName(String schoolDepartment) {
        if (schoolDepartment == null || schoolDepartment.isBlank()) {
            return "";
        }
        String text = schoolDepartment.trim();
        for (String sep : new String[]{"  ", "   ", "\t"}) {
            if (text.contains(sep)) {
                return text.split(sep)[0].trim();
            }
        }
        if (text.contains("学院") || text.contains("系")) {
            String[] parts = text.split("\\s+");
            if (parts.length > 0) {
                return parts[0].trim();
            }
        }
        return text;
    }
}
