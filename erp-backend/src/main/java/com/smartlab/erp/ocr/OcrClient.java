package com.smartlab.erp.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrClient {

    private final OcrProperties properties;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public OcrInvoiceResult recognizeInvoice(byte[] imageBytes, String fileName) {
        String url = properties.getBaseUrl() + "/ocr/invoice";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(new org.springframework.http.converter.StringHttpMessageConverter());
        ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate.getRequestFactory())
                .setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate.getRequestFactory())
                .setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            if (!root.path("success").asBoolean()) {
                String error = root.path("error").asText("OCR service returned failure");
                log.warn("OCR failed for {}: {}", fileName, error);
                return OcrInvoiceResult.failed(error);
            }

            JsonNode fields = root.path("fields");
            double confidence = root.path("confidence").asDouble(0.0);

            return OcrInvoiceResult.builder()
                    .success(true)
                    .invoiceNumber(fields.path("invoice_number").asText(null))
                    .invoiceDate(parseDate(fields.path("invoice_date").asText(null)))
                    .amountInclTax(parseBigDecimal(fields.path("amount_incl_tax")))
                    .amountExTax(parseBigDecimal(fields.path("amount_ex_tax")))
                    .taxAmount(parseBigDecimal(fields.path("tax_amount")))
                    .taxRate(fields.path("tax_rate").asText(null))
                    .sellerName(fields.path("seller_name").asText(null))
                    .buyerName(fields.path("buyer_name").asText(null))
                    .items(fields.path("items").asText(null))
                    .rawText(root.path("text").asText(null))
                    .confidence(BigDecimal.valueOf(confidence))
                    .build();

        } catch (Exception e) {
            log.error("OCR request failed for {}: {}", fileName, e.getMessage(), e);
            return OcrInvoiceResult.failed(e.getMessage());
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse OCR date: {}", dateStr);
            return null;
        }
    }

    private BigDecimal parseBigDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) return null;
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
