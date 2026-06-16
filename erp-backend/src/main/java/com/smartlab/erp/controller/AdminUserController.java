package com.smartlab.erp.controller;

import com.smartlab.erp.dto.ProvisionUserRequest;
import com.smartlab.erp.dto.UpdateDailyWageRequest;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.service.AuthService;
import com.smartlab.erp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AuthService authService;
    private final UserService userService;
    private final UserRepository userRepository;

    private static final String DOCUMENTS_DIR = "./uploads/documents";

    private void requireProvisionAdmin() {
        if (!authService.canProvisionAccounts(authService.getCurrentUser())) {
            throw new PermissionDeniedException("仅指定管理员可操作");
        }
    }

    @PostMapping("/provision")
    public ResponseEntity<Map<String, String>> provisionUser(@Valid @RequestBody ProvisionUserRequest request) {
        authService.provisionUser(request);
        return ResponseEntity.ok(Map.of("message", "账号创建成功，初始密码为：账号+123"));
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

    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<Map<String, String>> deactivateUser(@PathVariable String userId) {
        requireProvisionAdmin();
        userService.deactivateUser(userId);
        return ResponseEntity.ok(Map.of("message", "用户已离职"));
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<Map<String, String>> activateUser(@PathVariable String userId) {
        requireProvisionAdmin();
        userService.activateUser(userId);
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
            Path userDir = Path.of(DOCUMENTS_DIR, userId);
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
            Path userDir = Path.of(DOCUMENTS_DIR, userId);
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
            Path filePath = Path.of(DOCUMENTS_DIR, userId, filename);
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
    public ResponseEntity<Map<String, String>> generateAgreement(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        try {
            Path userDir = Path.of(DOCUMENTS_DIR, userId);
            Files.createDirectories(userDir);
            String filename = "agreement_" + System.currentTimeMillis() + ".txt";
            String content = "员工入职协议\n\n"
                    + "姓名: " + (user.getName() != null ? user.getName() : "") + "\n"
                    + "账号: " + user.getUsername() + "\n"
                    + "角色: " + (user.getRole() != null ? user.getRole() : "") + "\n"
                    + "岗位: " + (user.getPosition() != null ? user.getPosition() : "") + "\n"
                    + "入职日期: " + (user.getEntryDate() != null ? user.getEntryDate().toString() : "") + "\n"
                    + "身份证号: " + (user.getIdNumber() != null ? user.getIdNumber() : "") + "\n\n"
                    + "本协议一式两份，公司存档一份，本人持有一份。";
            Files.writeString(userDir.resolve(filename), content);
            return ResponseEntity.ok(Map.of("message", "协议生成成功", "filename", filename));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "协议生成失败: " + e.getMessage()));
        }
    }
}
