package com.smartlab.erp.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.dto.ProvisionUserRequest;
import com.smartlab.erp.enums.AccountDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaturalLanguageParserService {

    private static final Set<String> VALID_ROLES = Set.of(
            "RESEARCH", "BUSINESS", "PROMOTION", "DATA", "DEV", "ALGORITHM", "CI"
    );

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            你是一位企业 ERP 账号信息提取助手。用户会用自然语言描述一个需要创建的账号信息。
            请从文本中提取以下字段，并返回 STRICT JSON 对象（不要 Markdown 代码块，不要解释，只输出 JSON）：
            {
              "username": "登录账号，建议用姓名的拼音或英文名小写，没有则留空",
              "name": "姓名",
              "role": "角色，必须是 RESEARCH/BUSINESS/PROMOTION/DATA/DEV/ALGORITHM/CI 之一，没有则推断最接近的或留空",
              "domain": "账号域，ERP 或 FINANCE，默认 ERP",
              "position": "岗位，例如 实习生、工程师、研究员，没有则留空",
              "ethnicity": "民族，例如 汉族，没有则留空",
              "phone": "手机号",
              "idNumber": "18位身份证号",
              "schoolDepartment": "学校院系，例如 中国科学院大学 计算机软件与理论",
              "address": "住址",
              "dailyWage": "日工资数字（元/天），没有则 300",
              "partTime": "是否兼职，true/false，默认 false",
              "paymentEntity": "支付主体，默认 国科九天",
              "bankName": "开户行",
              "bankAccount": "银行卡号"
            }
            规则：
            1. 只输出纯 JSON，不要任何其他文字。
            2. 字段不存在时用 null 或空字符串，不要编造。
            3. role 必须严格匹配给定枚举值之一。
            4. 从"学校院系"中同时提取学校和院系；从"住址"中提取地址。
            """;

    public ProvisionUserRequest parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("输入文本不能为空");
        }

        String content = llmClient.chatCompletion(SYSTEM_PROMPT, text);
        content = stripThinkTags(content);

        try {
            JsonNode node = objectMapper.readTree(content);
            return mapToRequest(node);
        } catch (Exception e) {
            log.error("Failed to parse LLM response as JSON: {}", content, e);
            throw new RuntimeException("无法解析大模型返回结果，请检查输入文本或重试", e);
        }
    }

    private String stripThinkTags(String content) {
        if (content == null) return "";
        String stripped = content.replaceAll("(?s)<think>.*?</think>", "").trim();
        if (stripped.isEmpty()) {
            return content.trim();
        }
        return stripped;
    }

    private ProvisionUserRequest mapToRequest(JsonNode node) {
        ProvisionUserRequest request = new ProvisionUserRequest();
        request.setUsername(getString(node, "username"));
        request.setName(getString(node, "name"));
        request.setRole(normalizeRole(getString(node, "role")));
        request.setDomain(normalizeDomain(getString(node, "domain")));
        request.setPosition(getString(node, "position"));
        request.setEthnicity(getString(node, "ethnicity"));
        request.setPhone(getString(node, "phone"));
        request.setIdNumber(getString(node, "idNumber"));
        request.setSchoolDepartment(getString(node, "schoolDepartment"));
        request.setAddress(getString(node, "address"));
        request.setDailyWage(parseDailyWage(getString(node, "dailyWage")));
        request.setPartTime(parseBoolean(getString(node, "partTime")));
        request.setPaymentEntity(getString(node, "paymentEntity", "国科九天"));
        request.setBankName(getString(node, "bankName"));
        request.setBankAccount(getString(node, "bankAccount"));
        return request;
    }

    private String getString(JsonNode node, String field) {
        return getString(node, field, null);
    }

    private String getString(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        String text = value.asText(defaultValue);
        if (text == null) return defaultValue;
        text = text.trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "";
        String upper = role.toUpperCase();
        if (VALID_ROLES.contains(upper)) return upper;
        // Fuzzy matching
        return switch (upper) {
            case "研究", "研究员", "RESEARCHER" -> "RESEARCH";
            case "商务", "BUSINESSMAN" -> "BUSINESS";
            case "推广", "运营", "OPERATION" -> "PROMOTION";
            case "数据", "数据工程师", "DATA ENGINEER" -> "DATA";
            case "开发", "DEVELOPER" -> "DEV";
            case "算法", "ALGORITHM ENGINEER" -> "ALGORITHM";
            case "群体智能", "COLLECTIVE INTELLIGENCE", "CI" -> "CI";
            default -> "";
        };
    }

    private AccountDomain normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) return AccountDomain.ERP;
        try {
            return AccountDomain.valueOf(domain.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AccountDomain.ERP;
        }
    }

    private BigDecimal parseDailyWage(String value) {
        if (value == null || value.isBlank()) return new BigDecimal("300.00");
        try {
            String cleaned = value.replaceAll("[^0-9.]", "");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return new BigDecimal("300.00");
        }
    }

    private Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) return false;
        return Boolean.parseBoolean(value.toLowerCase())
                || value.equalsIgnoreCase("是")
                || value.equalsIgnoreCase("yes")
                || value.equalsIgnoreCase("true");
    }
}
