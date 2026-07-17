package com.smartlab.erp.projectfile;

import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/project-files")
@RequiredArgsConstructor
public class ProjectFileManagerController {

    private final AuthService authService;
    private final ProjectFileManagerService fileManagerService;

    private void requireProvisionAdmin() {
        if (!authService.canProvisionAccounts(authService.getCurrentUser())) {
            throw new PermissionDeniedException("仅指定管理员可操作");
        }
    }

    @GetMapping("/projects")
    public ResponseEntity<List<Map<String, Object>>> listProjects() {
        requireProvisionAdmin();
        return ResponseEntity.ok(fileManagerService.listProjects());
    }

    @GetMapping("/{projectId}/tree")
    public ResponseEntity<Map<String, Object>> getProjectTree(@PathVariable String projectId) {
        requireProvisionAdmin();
        return ResponseEntity.ok(fileManagerService.getProjectTree(projectId));
    }

    @PostMapping("/{projectId}/folders")
    public ResponseEntity<Map<String, Object>> createFolder(
            @PathVariable String projectId,
            @Valid @RequestBody CreateFolderRequest request) {
        requireProvisionAdmin();
        ProjectFileFolder folder = fileManagerService.createFolder(projectId, request.getParentId(), request.getName());
        return ResponseEntity.ok(Map.of(
                "id", folder.getId(),
                "name", folder.getName(),
                "path", folder.getPath()));
    }

    @DeleteMapping("/{projectId}/folders/{folderId}")
    public ResponseEntity<Map<String, String>> deleteFolder(
            @PathVariable String projectId,
            @PathVariable Long folderId) {
        requireProvisionAdmin();
        fileManagerService.deleteFolder(projectId, folderId);
        return ResponseEntity.ok(Map.of("message", "目录删除成功"));
    }

    @PatchMapping("/files/{mappingId}/move")
    public ResponseEntity<Map<String, String>> moveFile(
            @PathVariable Long mappingId,
            @Valid @RequestBody MoveFileRequest request) {
        requireProvisionAdmin();
        fileManagerService.moveFile(mappingId, request.getFolderId());
        return ResponseEntity.ok(Map.of("message", "文件移动成功"));
    }

    @GetMapping("/files/{mappingId}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long mappingId) {
        requireProvisionAdmin();
        byte[] content = fileManagerService.downloadFile(mappingId);
        String filename = "download";
        try {
            ProjectFileMapping mapping = fileManagerService.getMapping(mappingId);
            if (mapping != null && mapping.getDisplayName() != null) {
                filename = mapping.getDisplayName();
            }
        } catch (Exception ignored) {
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @GetMapping("/files/{mappingId}/preview")
    public ResponseEntity<byte[]> previewFile(@PathVariable Long mappingId) {
        requireProvisionAdmin();
        byte[] content = fileManagerService.downloadFile(mappingId);
        String filename = "preview";
        try {
            ProjectFileMapping mapping = fileManagerService.getMapping(mappingId);
            if (mapping != null && mapping.getDisplayName() != null) {
                filename = mapping.getDisplayName();
            }
        } catch (Exception ignored) {
        }

        HttpHeaders headers = new HttpHeaders();
        String mimeType = fileManagerService.getMimeType(filename);
        headers.setContentType(MediaType.parseMediaType(mimeType));
        ContentDisposition inline = ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        headers.setContentDisposition(inline);
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @PostMapping("/download-batch")
    public ResponseEntity<byte[]> downloadBatch(@RequestBody Map<String, Object> request) {
        requireProvisionAdmin();
        @SuppressWarnings("unchecked")
        List<Long> fileIds = toLongList((List<Object>) request.getOrDefault("fileIds", List.of()));
        @SuppressWarnings("unchecked")
        List<Long> folderIds = toLongList((List<Object>) request.getOrDefault("folderIds", List.of()));
        String zipName = (String) request.getOrDefault("zipName", "files_export.zip");

        byte[] zipBytes = fileManagerService.downloadBatch(fileIds, folderIds);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(zipName, StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(zipBytes);
    }

    @PatchMapping("/move-batch")
    public ResponseEntity<Map<String, String>> moveBatch(@RequestBody Map<String, Object> request) {
        requireProvisionAdmin();
        Long targetFolderId = request.get("targetFolderId") != null
                ? ((Number) request.get("targetFolderId")).longValue() : null;
        String projectId = (String) request.get("projectId");
        @SuppressWarnings("unchecked")
        List<Long> fileIds = toLongList((List<Object>) request.getOrDefault("fileIds", List.of()));
        @SuppressWarnings("unchecked")
        List<Long> folderIds = toLongList((List<Object>) request.getOrDefault("folderIds", List.of()));
        if (!fileIds.isEmpty()) {
            fileManagerService.moveFiles(fileIds, targetFolderId);
        }
        if (!folderIds.isEmpty()) {
            fileManagerService.moveFolders(folderIds, targetFolderId, projectId);
        }
        return ResponseEntity.ok(Map.of("message", "移动成功"));
    }

    @PostMapping("/{projectId}/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @PathVariable String projectId,
            @RequestParam(required = false) Long folderId,
            @RequestParam("file") MultipartFile file) {
        requireProvisionAdmin();
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        try {
            Map<String, Object> result = fileManagerService.uploadFile(
                    projectId, folderId, file.getOriginalFilename(), file.getBytes());
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @DeleteMapping("/files/{mappingId}")
    public ResponseEntity<Map<String, String>> deleteFile(
            @PathVariable Long mappingId,
            @RequestParam(defaultValue = "false") boolean deletePhysical) {
        requireProvisionAdmin();
        fileManagerService.deleteFile(mappingId, deletePhysical);
        return ResponseEntity.ok(Map.of("message", "文件删除成功"));
    }

    @DeleteMapping("/files/batch-delete")
    public ResponseEntity<Map<String, String>> batchDeleteFiles(
            @RequestBody List<Object> ids,
            @RequestParam(defaultValue = "false") boolean deletePhysical) {
        requireProvisionAdmin();
        fileManagerService.deleteFiles(toLongList(ids), deletePhysical);
        return ResponseEntity.ok(Map.of("message", "批量删除成功"));
    }

    @PostMapping("/scan")
    public ResponseEntity<Map<String, String>> scan() {
        requireProvisionAdmin();
        fileManagerService.scanAndInitializeMappings();
        return ResponseEntity.ok(Map.of("message", "扫描完成"));
    }

    private List<Long> toLongList(List<Object> list) {
        if (list == null) return List.of();
        return list.stream()
                .map(v -> ((Number) v).longValue())
                .toList();
    }

    @Data
    public static class CreateFolderRequest {
        private Long parentId;
        @NotBlank
        private String name;
    }

    @Data
    public static class MoveFileRequest {
        private Long folderId;
    }
}
