package com.smartlab.erp.controller;

import com.smartlab.erp.agreement.AgreementBatchRequest;
import com.smartlab.erp.agreement.AgreementGenerationService;
import com.smartlab.erp.agreement.AgreementType;
import com.smartlab.erp.agreement.AgreementZipService;
import com.smartlab.erp.dto.NaturalLanguageParseRequest;
import com.smartlab.erp.dto.ProvisionUserRequest;
import com.smartlab.erp.dto.UpdateDailyWageRequest;
import com.smartlab.erp.llm.NaturalLanguageParserService;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.entity.UserStatusLog;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.repository.UserStatusLogRepository;
import com.smartlab.erp.service.AuthService;
import com.smartlab.erp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.smartlab.erp.entity.ProjectExpense;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.entity.UserRole;
import com.smartlab.erp.finance.entity.FinanceDividendSheet;
import com.smartlab.erp.finance.enums.FinanceDividendStatus;
import com.smartlab.erp.finance.repository.FinanceDividendSheetRepository;
import com.smartlab.erp.repository.ProjectExpenseRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.UserRoleRepository;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AuthService authService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final UserStatusLogRepository userStatusLogRepository;
    private final AgreementGenerationService agreementGenerationService;
    private final AgreementZipService agreementZipService;
    private final NaturalLanguageParserService naturalLanguageParserService;
    private final ProjectExpenseRepository projectExpenseRepository;
    private final SysProjectMemberRepository sysProjectMemberRepository;
    private final UserRoleRepository userRoleRepository;
    private final FinanceDividendSheetRepository financeDividendSheetRepository;
    private final SysProjectRepository sysProjectRepository;

    @Value("${app.uploads.dir:/app/uploads}")
    private String uploadsDir;

    private Path documentsDir() {
        return Path.of(uploadsDir, "documents");
    }

    private void requireProvisionAdmin() {
        if (!authService.canProvisionAccounts(authService.getCurrentUser())) {
            throw new PermissionDeniedException("仅指定管理员可操作");
        }
    }

    @Transactional
    @PostMapping("/provision")
    public ResponseEntity<Map<String, String>> provisionUser(@Valid @RequestBody ProvisionUserRequest request) {
        String userId = authService.provisionUser(request);

        User operator = authService.getCurrentUser();
        userStatusLogRepository.save(UserStatusLog.builder()
                .userId(userId)
                .action("CREATE")
                .operatorId(operator != null ? operator.getUserId() : "SYSTEM")
                .build());

        return ResponseEntity.ok(Map.of(
                "message", "账号创建成功，初始密码为：账号+123",
                "userId", userId));
    }

    @PostMapping("/parse-natural-language")
    public ResponseEntity<ProvisionUserRequest> parseNaturalLanguage(
            @Valid @RequestBody NaturalLanguageParseRequest request) {
        requireProvisionAdmin();
        ProvisionUserRequest parsed = naturalLanguageParserService.parse(request.getText());
        return ResponseEntity.ok(parsed);
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        requireProvisionAdmin();
        return ResponseEntity.ok(userService.findAllUsersIncludingInactive());
    }

    @PutMapping("/{userId}/daily-wage")
    public ResponseEntity<Map<String, String>> updateDailyWage(
            @PathVariable String userId,
            @Valid @RequestBody UpdateDailyWageRequest request) {
        requireProvisionAdmin();
        userService.updateDailyWage(userId, request.getDailyWage());
        return ResponseEntity.ok(Map.of("message", "日工资更新成功"));
    }

    @Transactional
    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<Map<String, String>> deactivateUser(@PathVariable String userId) {
        requireProvisionAdmin();
        userService.deactivateUser(userId);

        User operator = authService.getCurrentUser();
        userStatusLogRepository.save(UserStatusLog.builder()
                .userId(userId)
                .action("DEACTIVATE")
                .operatorId(operator != null ? operator.getUserId() : "SYSTEM")
                .build());

        return ResponseEntity.ok(Map.of("message", "用户已离职"));
    }

    @Transactional
    @PostMapping("/{userId}/activate")
    public ResponseEntity<Map<String, String>> activateUser(@PathVariable String userId) {
        requireProvisionAdmin();
        userService.activateUser(userId);

        User operator = authService.getCurrentUser();
        userStatusLogRepository.save(UserStatusLog.builder()
                .userId(userId)
                .action("ACTIVATE")
                .operatorId(operator != null ? operator.getUserId() : "SYSTEM")
                .build());

        return ResponseEntity.ok(Map.of("message", "用户已还原为在职"));
    }

    @GetMapping("/users/export")
    public ResponseEntity<byte[]> exportRoster() {
        requireProvisionAdmin();
        byte[] xlsxBytes = userService.exportRosterXlsx();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "花名册.xlsx");
        return ResponseEntity.ok().headers(headers).body(xlsxBytes);
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<Map<String, Object>>> searchUsers(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        List<User> users = userRepository.findByUsernameContainingIgnoreCase(q.trim());
        return ResponseEntity.ok(users.stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", u.getUserId());
            map.put("username", u.getUsername());
            map.put("name", u.getName());
            map.put("role", u.getRole());
            map.put("dailyWage", u.getDailyWage());
            return map;
        }).toList());
    }

    @PostMapping("/users/{userId}/documents")
    public ResponseEntity<Map<String, String>> uploadDocument(
            @PathVariable String userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "other") String docType) {
        try {
            Path userDir = documentsDir().resolve(userId);
            Files.createDirectories(userDir);
            String filename = docType + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = userDir.resolve(filename);
            file.transferTo(filePath.toFile());
            return ResponseEntity.ok(Map.of("message", "上传成功", "filename", filename));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "上传失败: " + e.getMessage()));
        }
    }

    @GetMapping("/users/{userId}/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments(@PathVariable String userId) {
        try {
            Path userDir = documentsDir().resolve(userId);
            if (!Files.exists(userDir)) return ResponseEntity.ok(List.of());
            return ResponseEntity.ok(Files.list(userDir).map(p -> {
                Map<String, Object> map = new HashMap<>();
                map.put("filename", p.getFileName().toString());
                map.put("size", p.toFile().length());
                try { map.put("modifiedAt", Files.getLastModifiedTime(p).toString()); } catch (Exception ignored) {}
                map.put("type", p.getFileName().toString().startsWith("agreement") ? "协议" :
                        p.getFileName().toString().startsWith("id_card") ? "身份证" :
                        p.getFileName().toString().startsWith("student_card") ? "学生证" : "其他");
                return map;
            }).toList());
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/users/{userId}/documents/{filename}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String userId, @PathVariable String filename) {
        try {
            Path filePath = documentsDir().resolve(userId).resolve(filename);
            if (!Files.exists(filePath)) return ResponseEntity.notFound().build();
            byte[] bytes = Files.readAllBytes(filePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/users/{userId}/agreement")
    public ResponseEntity<Map<String, String>> generateAgreement(
            @PathVariable String userId,
            @RequestParam(defaultValue = "INTERNET") AgreementType type) {
        requireProvisionAdmin();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        validateAgreementFields(user);

        byte[] content = agreementGenerationService.generateAgreement(user, type);
        Path userDir = documentsDir().resolve(userId);
        try {
            Files.createDirectories(userDir);
            String filename = "agreement_" + type.name().toLowerCase() + "_" + System.currentTimeMillis() + ".docx";
            Files.write(userDir.resolve(filename), content);
            return ResponseEntity.ok(Map.of("message", "协议生成成功", "filename", filename));
        } catch (Exception e) {
            throw new RuntimeException("协议保存失败: " + e.getMessage(), e);
        }
    }

    @PostMapping("/users/{userId}/agreements/batch")
    public ResponseEntity<byte[]> generateAgreementBatch(
            @PathVariable String userId,
            @RequestBody AgreementBatchRequest request) {
        requireProvisionAdmin();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        validateAgreementFields(user);

        if (request.getTypes() == null || request.getTypes().isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        byte[] zipBytes = agreementZipService.generateZip(user, request.getTypes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String safeName = user.getName() != null ? user.getName() : "未命名";
        headers.setContentDispositionFormData("attachment", safeName + "_实习文件.zip");
        return ResponseEntity.ok().headers(headers).body(zipBytes);
    }

    private void validateAgreementFields(User user) {
        if (isBlank(user.getName()) || isBlank(user.getIdNumber())
                || isBlank(user.getSchoolDepartment()) || isBlank(user.getPhone())
                || isBlank(user.getAddress())) {
            throw new BusinessException("生成协议前请完善用户的姓名、身份证号、学校院系、手机号和住址");
        }
    }

    @GetMapping("/{userId}/status-history")
    public ResponseEntity<List<Map<String, Object>>> getUserStatusHistory(@PathVariable String userId) {
        requireProvisionAdmin();
        List<UserStatusLog> logs = userStatusLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(logs.stream().map(log -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", log.getId());
            map.put("userId", log.getUserId());
            map.put("action", log.getAction());
            map.put("operatorId", log.getOperatorId());
            map.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
            return map;
        }).toList());
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(@PathVariable String userId) {
        requireProvisionAdmin();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        // 个人信息
        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getUserId());
        profile.put("username", user.getUsername());
        profile.put("name", user.getName());
        profile.put("email", user.getEmail());
        profile.put("role", user.getRole());
        profile.put("accountDomain", user.getAccountDomain());
        profile.put("active", user.getActive());
        profile.put("dailyWage", user.getDailyWage());
        profile.put("phone", user.getPhone());
        profile.put("idNumber", user.getIdNumber());
        profile.put("ethnicity", user.getEthnicity());
        profile.put("position", user.getPosition());
        profile.put("partTime", user.getPartTime());
        profile.put("paymentEntity", user.getPaymentEntity());
        profile.put("schoolDepartment", user.getSchoolDepartment());
        profile.put("address", user.getAddress());
        profile.put("bankName", user.getBankName());
        profile.put("bankAccount", user.getBankAccount());
        profile.put("entryDate", user.getEntryDate());
        profile.put("departureDate", user.getDepartureDate());

        // 合同文档
        List<Map<String, Object>> documents = listUserDocuments(userId);
        profile.put("documents", documents);

        // 报销记录
        List<ProjectExpense> expenses = projectExpenseRepository.findBySubmitterUserIdOrderByCreatedAtDesc(userId);
        profile.put("expenses", expenses.stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.getId());
            m.put("projectName", e.getProjectName());
            m.put("itemName", e.getItemName());
            m.put("expenseType", e.getExpenseType());
            m.put("amount", e.getAmount());
            m.put("status", e.getStatus());
            m.put("createdAt", e.getCreatedAt());
            return m;
        }).collect(Collectors.toList()));

        // 参与项目
        List<SysProjectMember> members = sysProjectMemberRepository.findByUserUserIdWithUser(userId);
        profile.put("projects", members.stream().map(m -> {
            Map<String, Object> mp = new HashMap<>();
            String projectName = sysProjectRepository.findById(m.getProjectId())
                    .map(com.smartlab.erp.entity.SysProject::getName)
                    .orElse(m.getProjectId());
            mp.put("projectName", projectName);
            mp.put("role", m.getRole());
            mp.put("joinedAt", m.getJoinedAt());
            return mp;
        }).collect(Collectors.toList()));

        // 决策身份（队长）
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        profile.put("leaderRoles", userRoles.stream().filter(UserRole::getIsLeader).map(r -> {
            Map<String, Object> mr = new HashMap<>();
            mr.put("role", r.getRole());
            mr.put("isLeader", r.getIsLeader());
            return mr;
        }).collect(Collectors.toList()));

        // 已分成
        List<FinanceDividendSheet> dividends = financeDividendSheetRepository.findByUser_UserIdAndStatusOrderByCreatedAtDesc(userId, FinanceDividendStatus.CONFIRMED);
        profile.put("dividends", dividends.stream().map(d -> {
            Map<String, Object> md = new HashMap<>();
            md.put("id", d.getId());
            md.put("amount", d.getAmount());
            md.put("ledgerMonth", d.getLedgerMonth());
            md.put("status", d.getStatus());
            md.put("confirmedAt", d.getConfirmedAt());
            if (d.getProject() != null) {
                md.put("projectId", d.getProject().getProjectId());
                md.put("projectName", d.getProject().getName());
            }
            return md;
        }).collect(Collectors.toList()));

        // 待确认分成
        List<FinanceDividendSheet> pendingDividends = financeDividendSheetRepository.findByUser_UserIdAndStatusOrderByCreatedAtDesc(userId, FinanceDividendStatus.PENDING);
        profile.put("pendingDividends", pendingDividends.stream().map(d -> {
            Map<String, Object> md = new HashMap<>();
            md.put("id", d.getId());
            md.put("amount", d.getAmount());
            md.put("ledgerMonth", d.getLedgerMonth());
            md.put("status", d.getStatus());
            if (d.getProject() != null) {
                md.put("projectId", d.getProject().getProjectId());
                md.put("projectName", d.getProject().getName());
            }
            return md;
        }).collect(Collectors.toList()));

        return ResponseEntity.ok(profile);
    }

    private List<Map<String, Object>> listUserDocuments(String userId) {
        try {
            Path userDir = documentsDir().resolve(userId);
            if (!Files.exists(userDir)) return List.of();
            return Files.list(userDir).map(p -> {
                Map<String, Object> map = new HashMap<>();
                map.put("filename", p.getFileName().toString());
                map.put("size", p.toFile().length());
                try { map.put("modifiedAt", Files.getLastModifiedTime(p).toString()); } catch (Exception ignored) {}
                map.put("type", p.getFileName().toString().startsWith("agreement") ? "协议" :
                        p.getFileName().toString().startsWith("id_card") ? "身份证" :
                        p.getFileName().toString().startsWith("student_card") ? "学生证" : "其他");
                return map;
            }).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
