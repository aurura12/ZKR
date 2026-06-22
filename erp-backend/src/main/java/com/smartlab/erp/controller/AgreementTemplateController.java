package com.smartlab.erp.controller;

import com.smartlab.erp.agreement.AgreementTemplate;
import com.smartlab.erp.agreement.AgreementTemplateRepository;
import com.smartlab.erp.agreement.AgreementType;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/agreement-templates")
@RequiredArgsConstructor
public class AgreementTemplateController {

    private final AuthService authService;
    private final AgreementTemplateRepository templateRepository;

    private void requireProvisionAdmin() {
        if (!authService.canProvisionAccounts(authService.getCurrentUser())) {
            throw new PermissionDeniedException("仅指定管理员可操作");
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listTemplates() {
        requireProvisionAdmin();
        return ResponseEntity.ok(templateRepository.findAll().stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("code", t.getCode());
            map.put("name", t.getName());
            map.put("fileType", t.getFileType());
            map.put("createdAt", t.getCreatedAt());
            map.put("updatedAt", t.getUpdatedAt());
            return map;
        }).collect(Collectors.toList()));
    }

    @PutMapping("/{code}")
    public ResponseEntity<Map<String, String>> updateTemplate(
            @PathVariable AgreementType code,
            @RequestParam("file") MultipartFile file) {
        requireProvisionAdmin();
        try {
            AgreementTemplate template = templateRepository.findByCode(code)
                    .orElseGet(AgreementTemplate::new);
            template.setCode(code);
            template.setName(code.getDisplayName());
            template.setFileType("docx");
            template.setContent(file.getBytes());
            templateRepository.save(template);
            return ResponseEntity.ok(Map.of("message", "模板更新成功"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "模板更新失败: " + e.getMessage()));
        }
    }
}
