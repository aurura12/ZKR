package com.smartlab.erp.finance.service;

import com.smartlab.erp.entity.User;
import com.smartlab.erp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinanceLaborRelationsService {

    private final UserService userService;

    @Value("${app.uploads.dir:/app/uploads}")
    private String uploadsDir;

    private Path documentsDir() {
        return Path.of(uploadsDir, "documents");
    }

    public List<Map<String, Object>> listUsersWithDocuments() {
        List<User> users = userService.findAllUsersIncludingInactive();
        List<Map<String, Object>> result = new ArrayList<>();

        for (User u : users) {
            Map<String, Object> row = new HashMap<>();
            row.put("userId", u.getUserId());
            row.put("username", u.getUsername());
            row.put("name", u.getName());
            row.put("role", u.getRole());
            row.put("position", u.getPosition());

            String userId = u.getUserId();
            boolean hasAgreement = false;
            boolean hasIdCard = false;
            boolean hasStudentCard = false;

            Path userDir = documentsDir().resolve(userId);
            if (Files.exists(userDir)) {
                try (var stream = Files.list(userDir)) {
                    for (Path p : stream.toList()) {
                        String name = p.getFileName().toString();
                        if (name.startsWith("agreement")) hasAgreement = true;
                        if (name.startsWith("id_card")) hasIdCard = true;
                        if (name.startsWith("student_card")) hasStudentCard = true;
                    }
                } catch (IOException ignored) {
                }
            }
            row.put("hasAgreement", hasAgreement);
            row.put("hasIdCard", hasIdCard);
            row.put("hasStudentCard", hasStudentCard);
            result.add(row);
        }
        return result;
    }

    public List<Map<String, Object>> listDocuments(String userId) {
        List<Map<String, Object>> docs = new ArrayList<>();
        Path userDir = documentsDir().resolve(userId);
        if (!Files.exists(userDir)) return docs;

        try (var stream = Files.list(userDir)) {
            for (Path p : stream.toList()) {
                Map<String, Object> map = new HashMap<>();
                String filename = p.getFileName().toString();
                map.put("filename", filename);
                map.put("size", p.toFile().length());
                try {
                    map.put("modifiedAt", Files.getLastModifiedTime(p).toString());
                } catch (IOException ignored) {
                }
                if (filename.startsWith("agreement")) map.put("type", "协议");
                else if (filename.startsWith("id_card")) map.put("type", "身份证");
                else if (filename.startsWith("student_card")) map.put("type", "学生证");
                else map.put("type", "其他");
                docs.add(map);
            }
        } catch (IOException ignored) {
        }
        return docs;
    }

    public Map<String, String> uploadDocument(String userId, MultipartFile file, String docType) throws IOException {
        Path userDir = documentsDir().resolve(userId);
        Files.createDirectories(userDir);
        String filename = docType + "_" + Instant.now().toEpochMilli() + "_" + file.getOriginalFilename();
        Path filePath = userDir.resolve(filename);
        file.transferTo(filePath.toFile());
        Map<String, String> result = new HashMap<>();
        result.put("message", "上传成功");
        result.put("filename", filename);
        return result;
    }

    public byte[] downloadDocument(String userId, String filename) throws IOException {
        Path filePath = documentsDir().resolve(userId).resolve(filename);
        if (!Files.exists(filePath)) return null;
        return Files.readAllBytes(filePath);
    }

    public Map<String, String> generateAgreement(String userId) {
        User user = userService.findAllUsersIncludingInactive().stream()
                .filter(u -> userId.equals(u.getUserId()))
                .findFirst()
                .orElse(null);

        if (user == null) {
            Map<String, String> err = new HashMap<>();
            err.put("message", "用户不存在");
            return err;
        }

        try {
            Path userDir = documentsDir().resolve(userId);
            Files.createDirectories(userDir);
            String filename = "agreement_" + Instant.now().toEpochMilli() + ".txt";
            String content = "员工入职协议\n\n"
                    + "姓名: " + (user.getName() != null ? user.getName() : "") + "\n"
                    + "账号: " + user.getUsername() + "\n"
                    + "角色: " + (user.getRole() != null ? user.getRole() : "") + "\n"
                    + "岗位: " + (user.getPosition() != null ? user.getPosition() : "") + "\n"
                    + "入职日期: " + (user.getEntryDate() != null ? user.getEntryDate().toString() : "") + "\n"
                    + "身份证号: " + (user.getIdNumber() != null ? user.getIdNumber() : "") + "\n\n"
                    + "本协议一式两份，公司存档一份，本人持有一份。";
            Files.writeString(userDir.resolve(filename), content);
            Map<String, String> result = new HashMap<>();
            result.put("message", "协议生成成功");
            result.put("filename", filename);
            return result;
        } catch (IOException e) {
            Map<String, String> err = new HashMap<>();
            err.put("message", "协议生成失败: " + e.getMessage());
            return err;
        }
    }
}
