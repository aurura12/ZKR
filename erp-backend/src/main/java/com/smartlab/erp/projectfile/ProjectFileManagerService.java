package com.smartlab.erp.projectfile;

import com.smartlab.erp.entity.*;
import com.smartlab.erp.finance.entity.FinanceExpenseSubmission;
import com.smartlab.erp.finance.repository.FinanceExpenseSubmissionRepository;
import com.smartlab.erp.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectFileManagerService {

    private final SysProjectRepository projectRepository;
    private final ProjectAssetRepository projectAssetRepository;
    private final ExecutionFileRepository executionFileRepository;
    private final ProjectExpenseFileRepository projectExpenseFileRepository;
    private final FinanceExpenseSubmissionRepository financeExpenseSubmissionRepository;
    private final ProjectCostAdjustmentRepository projectCostAdjustmentRepository;

    private final ProjectFileFolderRepository folderRepository;
    private final ProjectFileMappingRepository mappingRepository;

    @Value("${app.uploads.dir:/app/uploads}")
    private String uploadsDir;

    public List<Map<String, Object>> listProjects() {
        return projectRepository.findAll().stream().map(p -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("projectId", p.getProjectId());
            map.put("name", p.getName());
            map.put("flowType", p.getFlowType());
            return map;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProjectTree(String projectId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("projectName", projectRepository.findById(projectId).map(SysProject::getName).orElse(""));

        List<ProjectFileFolder> folders = folderRepository.findByProjectId(projectId);
        List<ProjectFileMapping> mappings = mappingRepository.findByProjectId(projectId);

        List<Map<String, Object>> folderNodes = folders.stream().map(f -> {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", f.getId());
            node.put("parentId", f.getParentId());
            node.put("name", f.getName());
            node.put("path", f.getPath());
            node.put("type", "folder");
            return node;
        }).toList();

        List<Map<String, Object>> fileNodes = mappings.stream().map(m -> {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", m.getId());
            node.put("folderId", m.getFolderId());
            node.put("sourceType", m.getSourceType());
            node.put("sourceId", m.getSourceId());
            node.put("name", m.getDisplayName());
            node.put("type", "file");
            node.put("createdAt", m.getCreatedAt());
            node.put("size", resolveFileSize(m));
            return node;
        }).toList();

        result.put("folders", folderNodes);
        result.put("files", fileNodes);
        return result;
    }

    @Transactional
    public ProjectFileFolder createFolder(String projectId, Long parentId, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("目录名不能为空");
        }
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            throw new IllegalArgumentException("目录名不能包含斜杠");
        }

        List<ProjectFileFolder> folders = folderRepository.findByProjectId(projectId);
        Map<Long, ProjectFileFolder> folderMap = new HashMap<>();
        for (ProjectFileFolder f : folders) {
            folderMap.put(f.getId(), f);
        }

        String parentPath = "/";
        if (parentId != null) {
            ProjectFileFolder parent = folderMap.get(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父目录不存在");
            }
            parentPath = parent.getPath();
        }

        String newPath = parentPath.endsWith("/") ? parentPath + trimmed : parentPath + "/" + trimmed;
        if (!newPath.endsWith("/")) {
            newPath = newPath + "/";
        }

        if (folderRepository.findByProjectIdAndPath(projectId, newPath).isPresent()) {
            throw new IllegalArgumentException("目录已存在");
        }

        ProjectFileFolder folder = new ProjectFileFolder();
        folder.setProjectId(projectId);
        folder.setParentId(parentId);
        folder.setName(trimmed);
        folder.setPath(newPath);
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(String projectId, Long folderId) {
        ProjectFileFolder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("目录不存在"));
        if (!folder.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("目录不属于该项目");
        }
        // 删除目录下所有文件（含物理文件）
        List<ProjectFileMapping> files = mappingRepository.findByProjectIdAndFolderId(projectId, folderId);
        for (ProjectFileMapping mapping : files) {
            if (mapping.getSourceType() == ProjectFileSourceType.UPLOADED_FILE) {
                try {
                    Files.deleteIfExists(Path.of(uploadsDir, mapping.getSourceId()));
                } catch (IOException e) {
                    log.warn("删除物理文件失败: {}", mapping.getSourceId(), e);
                }
            }
            mappingRepository.delete(mapping);
        }
        // 递归删除子目录
        List<ProjectFileFolder> children = folderRepository.findByProjectIdAndParentId(projectId, folderId);
        for (ProjectFileFolder child : children) {
            deleteFolder(projectId, child.getId());
        }
        folderRepository.delete(folder);
    }

    @Transactional
    public void moveFile(Long mappingId, Long folderId) {
        ProjectFileMapping mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("文件不存在"));
        if (folderId != null) {
            ProjectFileFolder folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new IllegalArgumentException("目标目录不存在"));
            if (!folder.getProjectId().equals(mapping.getProjectId())) {
                throw new IllegalArgumentException("不能跨项目移动文件");
            }
        }
        mapping.setFolderId(folderId);
        mappingRepository.save(mapping);
    }

    @Transactional
    public void moveFiles(List<Long> mappingIds, Long targetFolderId) {
        for (Long id : mappingIds) {
            moveFile(id, targetFolderId);
        }
    }

    @Transactional
    public void moveFolders(List<Long> folderIds, Long targetFolderId, String projectId) {
        for (Long id : folderIds) {
            ProjectFileFolder folder = folderRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("目录不存在"));
            if (!folder.getProjectId().equals(projectId)) {
                throw new IllegalArgumentException("不能跨项目移动目录");
            }
            folder.setParentId(targetFolderId);
            folderRepository.save(folder);
        }
    }

    public byte[] downloadBatch(List<Long> fileIds, List<Long> folderIds) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (Long fileId : fileIds) {
                ProjectFileMapping mapping = getMapping(fileId);
                byte[] content = downloadFile(fileId);
                String fileName = sanitizeZipName(mapping.getDisplayName());
                zos.putNextEntry(new ZipEntry(fileName));
                zos.write(content);
                zos.closeEntry();
            }

            for (Long folderId : folderIds) {
                ProjectFileFolder folder = folderRepository.findById(folderId)
                        .orElseThrow(() -> new IllegalArgumentException("目录不存在"));
                addFolderToZip(zos, folder, folder.getName() + "/");
            }

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("打包下载失败: " + e.getMessage(), e);
        }
    }

    private void addFolderToZip(ZipOutputStream zos, ProjectFileFolder folder, String zipPath) throws IOException {
        // 添加目录本身
        zos.putNextEntry(new ZipEntry(zipPath));
        zos.closeEntry();

        // 添加目录下的文件
        List<ProjectFileMapping> files = mappingRepository.findByProjectIdAndFolderId(folder.getProjectId(), folder.getId());
        for (ProjectFileMapping mapping : files) {
            byte[] content = readBytes(Path.of(uploadsDir, mapping.getSourceId()));
            zos.putNextEntry(new ZipEntry(zipPath + sanitizeZipName(mapping.getDisplayName())));
            zos.write(content);
            zos.closeEntry();
        }

        // 递归添加子目录
        List<ProjectFileFolder> children = folderRepository.findByProjectIdAndParentId(folder.getProjectId(), folder.getId());
        for (ProjectFileFolder child : children) {
            addFolderToZip(zos, child, zipPath + child.getName() + "/");
        }
    }

    private String sanitizeZipName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public ProjectFileMapping getMapping(Long mappingId) {
        return mappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("文件不存在"));
    }

    public byte[] downloadFile(Long mappingId) {
        ProjectFileMapping mapping = getMapping(mappingId);

        return switch (mapping.getSourceType()) {
            case PROJECT_ASSET -> downloadProjectAsset(mapping.getSourceId());
            case EXECUTION_FILE -> downloadExecutionFile(mapping.getSourceId());
            case PROJECT_EXPENSE_FILE -> downloadProjectExpenseFile(mapping.getSourceId());
            case FINANCE_EXPENSE_SUBMISSION -> downloadFinanceExpenseSubmission(mapping.getSourceId());
            case PROJECT_COST_ADJUSTMENT -> downloadProjectCostAdjustment(mapping.getSourceId());
            case UPLOADED_FILE -> downloadUploadedFile(mapping.getSourceId());
        };
    }

    private byte[] downloadUploadedFile(String sourceId) {
        Path path = Path.of(uploadsDir, sourceId);
        return readBytes(path);
    }

    @Transactional
    public Map<String, Object> uploadFile(String projectId, Long folderId, String originalFilename, byte[] content) {
        String relativePath = "project-files/" + projectId + "/" + UUID.randomUUID() + "_" + originalFilename;
        Path targetPath = Path.of(uploadsDir, relativePath);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, content);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败: " + e.getMessage(), e);
        }

        ProjectFileMapping mapping = new ProjectFileMapping();
        mapping.setProjectId(projectId);
        mapping.setFolderId(folderId);
        mapping.setSourceType(ProjectFileSourceType.UPLOADED_FILE);
        mapping.setSourceId(relativePath);
        mapping.setDisplayName(originalFilename);
        mappingRepository.save(mapping);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", mapping.getId());
        result.put("name", mapping.getDisplayName());
        result.put("sourceType", mapping.getSourceType());
        return result;
    }

    @Transactional
    public void deleteFile(Long mappingId, boolean deletePhysical) {
        ProjectFileMapping mapping = getMapping(mappingId);
        if (deletePhysical && mapping.getSourceType() == ProjectFileSourceType.UPLOADED_FILE) {
            Path path = Path.of(uploadsDir, mapping.getSourceId());
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("删除物理文件失败: {}", path, e);
            }
        }
        mappingRepository.delete(mapping);
    }

    @Transactional
    public void deleteFiles(List<Long> ids, boolean deletePhysical) {
        for (Long id : ids) {
            deleteFile(id, deletePhysical);
        }
    }

    public byte[] downloadFileBytes(Long mappingId) {
        return downloadFile(mappingId);
    }

    private long resolveFileSize(ProjectFileMapping mapping) {
        if (mapping.getSourceType() == ProjectFileSourceType.UPLOADED_FILE) {
            try {
                return Files.size(Path.of(uploadsDir, mapping.getSourceId()));
            } catch (IOException e) {
                return 0;
            }
        }
        return 0;
    }

    public String getMimeType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    private byte[] downloadProjectAsset(String sourceId) {
        ProjectAsset asset = projectAssetRepository.findById(Long.parseLong(sourceId))
                .orElseThrow(() -> new IllegalArgumentException("文件不存在"));
        if (asset.getFileData() != null && asset.getFileData().length > 0) {
            return asset.getFileData();
        }
        Path path = Path.of(uploadsDir, asset.getFilePath());
        return readBytes(path);
    }

    private byte[] downloadExecutionFile(String sourceId) {
        ExecutionFile file = executionFileRepository.findById(Long.parseLong(sourceId))
                .orElseThrow(() -> new IllegalArgumentException("文件不存在"));
        Path path = Path.of(uploadsDir, file.getFilePath());
        return readBytes(path);
    }

    private byte[] downloadProjectExpenseFile(String sourceId) {
        ProjectExpenseFile file = projectExpenseFileRepository.findById(Long.parseLong(sourceId))
                .orElseThrow(() -> new IllegalArgumentException("文件不存在"));
        Path path = Path.of(uploadsDir, file.getFilePath());
        return readBytes(path);
    }

    private byte[] downloadFinanceExpenseSubmission(String sourceId) {
        FinanceExpenseSubmission submission = financeExpenseSubmissionRepository.findById(Long.parseLong(sourceId))
                .orElseThrow(() -> new IllegalArgumentException("文件不存在"));
        Path path = Path.of(uploadsDir, submission.getInvoiceFilePath());
        return readBytes(path);
    }

    private byte[] downloadProjectCostAdjustment(String sourceId) {
        ProjectCostAdjustment adjustment = projectCostAdjustmentRepository.findById(Long.parseLong(sourceId))
                .orElseThrow(() -> new IllegalArgumentException("文件不存在"));
        Path path = Path.of(uploadsDir, adjustment.getInvoiceFilePath());
        return readBytes(path);
    }

    private byte[] readBytes(Path path) {
        try {
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("物理文件不存在: " + path);
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void scanAndInitializeMappings() {
        log.info("开始扫描项目文件并初始化映射");
        scanProjectAssets();
        scanExecutionFiles();
        scanProjectExpenseFiles();
        scanFinanceExpenseSubmissions();
        scanProjectCostAdjustments();
        log.info("项目文件映射初始化完成");
    }

    private void scanProjectAssets() {
        for (ProjectAsset asset : projectAssetRepository.findAll()) {
            if (asset.getProject() == null) continue;
            String projectId = asset.getProject().getProjectId();
            if (mappingRepository.existsByProjectIdAndSourceTypeAndSourceId(
                    projectId, ProjectFileSourceType.PROJECT_ASSET, String.valueOf(asset.getId()))) {
                continue;
            }
            ensureDefaultFolder(projectId, ProjectFileSourceType.PROJECT_ASSET);
            ProjectFileMapping mapping = new ProjectFileMapping();
            mapping.setProjectId(projectId);
            mapping.setFolderId(getDefaultFolderId(projectId, ProjectFileSourceType.PROJECT_ASSET));
            mapping.setSourceType(ProjectFileSourceType.PROJECT_ASSET);
            mapping.setSourceId(String.valueOf(asset.getId()));
            mapping.setDisplayName(asset.getFileName());
            mappingRepository.save(mapping);
        }
    }

    private void scanExecutionFiles() {
        for (ExecutionFile file : executionFileRepository.findAll()) {
            if (file.getProjectId() == null) continue;
            if (mappingRepository.existsByProjectIdAndSourceTypeAndSourceId(
                    file.getProjectId(), ProjectFileSourceType.EXECUTION_FILE, String.valueOf(file.getId()))) {
                continue;
            }
            ensureDefaultFolder(file.getProjectId(), ProjectFileSourceType.EXECUTION_FILE);
            ProjectFileMapping mapping = new ProjectFileMapping();
            mapping.setProjectId(file.getProjectId());
            mapping.setFolderId(getDefaultFolderId(file.getProjectId(), ProjectFileSourceType.EXECUTION_FILE));
            mapping.setSourceType(ProjectFileSourceType.EXECUTION_FILE);
            mapping.setSourceId(String.valueOf(file.getId()));
            mapping.setDisplayName(file.getFileName());
            mappingRepository.save(mapping);
        }
    }

    private void scanProjectExpenseFiles() {
        for (ProjectExpenseFile file : projectExpenseFileRepository.findAll()) {
            ProjectExpense expense = file.getExpense();
            if (expense == null || expense.getProjectId() == null) continue;
            if (mappingRepository.existsByProjectIdAndSourceTypeAndSourceId(
                    expense.getProjectId(), ProjectFileSourceType.PROJECT_EXPENSE_FILE, String.valueOf(file.getId()))) {
                continue;
            }
            ensureDefaultFolder(expense.getProjectId(), ProjectFileSourceType.PROJECT_EXPENSE_FILE);
            ProjectFileMapping mapping = new ProjectFileMapping();
            mapping.setProjectId(expense.getProjectId());
            mapping.setFolderId(getDefaultFolderId(expense.getProjectId(), ProjectFileSourceType.PROJECT_EXPENSE_FILE));
            mapping.setSourceType(ProjectFileSourceType.PROJECT_EXPENSE_FILE);
            mapping.setSourceId(String.valueOf(file.getId()));
            mapping.setDisplayName(file.getFileName());
            mappingRepository.save(mapping);
        }
    }

    private void scanFinanceExpenseSubmissions() {
        for (FinanceExpenseSubmission submission : financeExpenseSubmissionRepository.findAll()) {
            if (submission.getProjectId() == null || submission.getInvoiceFilePath() == null) continue;
            if (mappingRepository.existsByProjectIdAndSourceTypeAndSourceId(
                    submission.getProjectId(), ProjectFileSourceType.FINANCE_EXPENSE_SUBMISSION, String.valueOf(submission.getId()))) {
                continue;
            }
            ensureDefaultFolder(submission.getProjectId(), ProjectFileSourceType.FINANCE_EXPENSE_SUBMISSION);
            ProjectFileMapping mapping = new ProjectFileMapping();
            mapping.setProjectId(submission.getProjectId());
            mapping.setFolderId(getDefaultFolderId(submission.getProjectId(), ProjectFileSourceType.FINANCE_EXPENSE_SUBMISSION));
            mapping.setSourceType(ProjectFileSourceType.FINANCE_EXPENSE_SUBMISSION);
            mapping.setSourceId(String.valueOf(submission.getId()));
            mapping.setDisplayName(submission.getInvoiceFileName());
            mappingRepository.save(mapping);
        }
    }

    private void scanProjectCostAdjustments() {
        for (ProjectCostAdjustment adjustment : projectCostAdjustmentRepository.findAll()) {
            if (adjustment.getProjectId() == null || adjustment.getInvoiceFilePath() == null) continue;
            if (mappingRepository.existsByProjectIdAndSourceTypeAndSourceId(
                    adjustment.getProjectId(), ProjectFileSourceType.PROJECT_COST_ADJUSTMENT, String.valueOf(adjustment.getId()))) {
                continue;
            }
            ensureDefaultFolder(adjustment.getProjectId(), ProjectFileSourceType.PROJECT_COST_ADJUSTMENT);
            ProjectFileMapping mapping = new ProjectFileMapping();
            mapping.setProjectId(adjustment.getProjectId());
            mapping.setFolderId(getDefaultFolderId(adjustment.getProjectId(), ProjectFileSourceType.PROJECT_COST_ADJUSTMENT));
            mapping.setSourceType(ProjectFileSourceType.PROJECT_COST_ADJUSTMENT);
            mapping.setSourceId(String.valueOf(adjustment.getId()));
            mapping.setDisplayName(adjustment.getInvoiceFileName());
            mappingRepository.save(mapping);
        }
    }

    private void ensureDefaultFolder(String projectId, ProjectFileSourceType sourceType) {
        String path = "/" + sourceType.getDefaultFolderName() + "/";
        if (folderRepository.findByProjectIdAndPath(projectId, path).isEmpty()) {
            ProjectFileFolder folder = new ProjectFileFolder();
            folder.setProjectId(projectId);
            folder.setParentId(null);
            folder.setName(sourceType.getDefaultFolderName());
            folder.setPath(path);
            folderRepository.save(folder);
        }
    }

    private Long getDefaultFolderId(String projectId, ProjectFileSourceType sourceType) {
        String path = "/" + sourceType.getDefaultFolderName() + "/";
        return folderRepository.findByProjectIdAndPath(projectId, path)
                .map(ProjectFileFolder::getId)
                .orElse(null);
    }
}
