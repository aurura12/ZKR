package com.smartlab.erp.finance.controller;

import com.smartlab.erp.finance.service.FinanceLaborRelationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/labor-relations")
@RequiredArgsConstructor
public class FinanceLaborRelationsController {

    private final FinanceLaborRelationsService laborRelationsService;

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsersWithDocuments() {
        return ResponseEntity.ok(laborRelationsService.listUsersWithDocuments());
    }

    @GetMapping("/users/{userId}/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments(@PathVariable String userId) {
        return ResponseEntity.ok(laborRelationsService.listDocuments(userId));
    }

    @PostMapping("/users/{userId}/documents")
    public ResponseEntity<Map<String, String>> uploadDocument(
            @PathVariable String userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "other") String docType) {
        try {
            Map<String, String> result = laborRelationsService.uploadDocument(userId, file, docType);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "上传失败: " + e.getMessage()));
        }
    }

    @GetMapping("/users/{userId}/documents/{filename}")
    public ResponseEntity<?> downloadDocument(@PathVariable String userId, @PathVariable String filename) {
        try {
            byte[] bytes = laborRelationsService.downloadDocument(userId, filename);
            if (bytes == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/users/{userId}/agreement")
    public ResponseEntity<Map<String, String>> generateAgreement(@PathVariable String userId) {
        Map<String, String> result = laborRelationsService.generateAgreement(userId);
        if (result.containsKey("message") && result.get("message").contains("失败")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
        return ResponseEntity.ok(result);
    }
}
