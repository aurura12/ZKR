package com.smartlab.erp.finance.service;

import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.finance.dto.FinanceExpenseSubmissionCenterResponse;
import com.smartlab.erp.finance.dto.FinanceExpenseSubmissionCreateRequest;
import com.smartlab.erp.finance.entity.FinanceExpenseSubmission;
import com.smartlab.erp.finance.enums.FinanceExpenseSubmissionStatus;
import com.smartlab.erp.finance.enums.FinanceExpenseSubmissionType;
import com.smartlab.erp.finance.repository.FinanceExpenseSubmissionRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinanceExpenseSubmissionService {

    private final FinanceExpenseSubmissionRepository financeExpenseSubmissionRepository;
    private final SysProjectRepository sysProjectRepository;
    private final UserRepository userRepository;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${auth.admin-usernames:Zhangqi,guojianwen,jiaomiao}")
    private String adminUsernamesConfig;

    private boolean isAdminUser(User user) {
        if (user == null) return false;
        if ("ADMIN".equalsIgnoreCase(user.getRole())) return true;
        if (user.getUsername() == null || user.getUsername().isBlank()) return false;
        return java.util.Set.of(adminUsernamesConfig.split(",")).stream()
                .map(String::trim).filter(s -> !s.isEmpty())
                .anyMatch(name -> name.equalsIgnoreCase(user.getUsername().trim()));
    }

    @Transactional
    public FinanceExpenseSubmission submitPersonalProcurement(FinanceExpenseSubmissionCreateRequest request, String userId) {
        User submitter = loadErpSubmitter(userId);
        validateCommonRequest(request);
        return financeExpenseSubmissionRepository.save(buildSubmission(request, submitter, null, FinanceExpenseSubmissionType.PERSONAL_PROCUREMENT));
    }

    @Transactional
    public FinanceExpenseSubmission submitProjectTravelReimbursement(String projectId,
                                                                     FinanceExpenseSubmissionCreateRequest request,
                                                                     String userId) {
        User submitter = loadErpSubmitter(userId);
        SysProject project = sysProjectRepository.findProjectByIdAndUser(projectId, userId)
                .orElseThrow(() -> new PermissionDeniedException("项目不存在或无权限提交出差报销"));
        validateCommonRequest(request);
        validateTravelRequest(request);
        return financeExpenseSubmissionRepository.save(buildSubmission(request, submitter, project, FinanceExpenseSubmissionType.PROJECT_TRAVEL_REIMBURSEMENT));
    }

    @Transactional
    public FinanceExpenseSubmission submitProjectExpense(String projectId,
                                                         FinanceExpenseSubmissionCreateRequest request,
                                                         String userId,
                                                         FinanceExpenseSubmissionType expenseType) {
        User submitter = userRepository.findById(userId)
                .orElseThrow(() -> new PermissionDeniedException("当前用户不存在或会话已过期"));
        SysProject project = sysProjectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在"));
        boolean admin = isAdminUser(submitter);
        if (!admin && submitter.getAccountDomain() != AccountDomain.ERP) {
            throw new PermissionDeniedException("仅 ERP 账号或管理员可提交项目成本");
        }
        if (!admin) {
            sysProjectRepository.findProjectByIdAndUser(projectId, userId)
                    .orElseThrow(() -> new PermissionDeniedException("项目不存在或无权限提交项目成本"));
        }
        validateCommonRequest(request);
        return financeExpenseSubmissionRepository.save(buildSubmission(request, submitter, project, expenseType));
    }

    @Transactional(readOnly = true)
    public FinanceExpenseSubmissionCenterResponse getSubmissionCenter() {
        List<FinanceExpenseSubmission> submissions = financeExpenseSubmissionRepository.findAllByOrderByCreatedAtDesc();
        BigDecimal procurementAmount = sumByType(submissions, FinanceExpenseSubmissionType.PERSONAL_PROCUREMENT);
        BigDecimal travelAmount = sumByType(submissions, FinanceExpenseSubmissionType.PROJECT_TRAVEL_REIMBURSEMENT);

        return FinanceExpenseSubmissionCenterResponse.builder()
                .summary(FinanceExpenseSubmissionCenterResponse.Summary.builder()
                        .totalCount(submissions.size())
                        .procurementCount(submissions.stream().filter(item -> item.getSubmissionType() == FinanceExpenseSubmissionType.PERSONAL_PROCUREMENT).count())
                        .travelCount(submissions.stream().filter(item -> item.getSubmissionType() == FinanceExpenseSubmissionType.PROJECT_TRAVEL_REIMBURSEMENT).count())
                        .totalAmount(scale(procurementAmount.add(travelAmount)))
                        .procurementAmount(procurementAmount)
                        .travelAmount(travelAmount)
                        .build())
                .submissions(submissions.stream()
                        .sorted(Comparator.comparing(FinanceExpenseSubmission::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(this::toRow)
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public InvoiceDownloadPayload loadInvoice(Long submissionId) {
        FinanceExpenseSubmission submission = financeExpenseSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException("报销记录不存在"));
        try {
            byte[] bytes = Files.readAllBytes(Path.of(submission.getInvoiceFilePath()));
            return new InvoiceDownloadPayload(
                    submission.getInvoiceFileName(),
                    submission.getInvoiceContentType(),
                    submission.getInvoiceFileSize(),
                    bytes
            );
        } catch (IOException ex) {
            throw new BusinessException("发票文件不存在或无法读取");
        }
    }

    private FinanceExpenseSubmission buildSubmission(FinanceExpenseSubmissionCreateRequest request,
                                                     User submitter,
                                                     SysProject project,
                                                     FinanceExpenseSubmissionType submissionType) {
        StoredInvoice invoice = storeInvoice(request.getInvoiceFile());
        BigDecimal totalAmount = resolveTotalAmount(request);
        Integer quantity = resolveQuantity(request.getQuantity());

        return FinanceExpenseSubmission.builder()
                .submissionType(submissionType)
                .status(FinanceExpenseSubmissionStatus.SUBMITTED)
                .submitterUserId(submitter.getUserId())
                .submitterName(resolveSubmitterName(submitter))
                .projectId(project == null ? null : project.getProjectId())
                .projectName(project == null ? null : project.getName())
                .projectFlowType(project == null || project.getFlowType() == null ? null : project.getFlowType().name())
                .itemName(normalizeText(request.getItemName()))
                .itemCategory(normalizeOptional(request.getItemCategory()))
                .itemSpecification(normalizeOptional(request.getItemSpecification()))
                .quantity(quantity)
                .unitPrice(scaleNullable(request.getUnitPrice()))
                .totalAmount(totalAmount)
                .supplierName(normalizeOptional(request.getSupplierName()))
                .invoiceNumber(normalizeText(request.getInvoiceNumber()))
                .occurredAt(parseInstantOrDate(request.getOccurredAt(), "发生日期格式错误"))
                .purpose(normalizeText(request.getPurpose()))
                .remarks(normalizeOptional(request.getRemarks()))
                .departureLocation(normalizeOptional(request.getDepartureLocation()))
                .destinationLocation(normalizeOptional(request.getDestinationLocation()))
                .travelStartAt(parseOptionalInstantOrDate(request.getTravelStartAt(), "出差开始日期格式错误"))
                .travelEndAt(parseOptionalInstantOrDate(request.getTravelEndAt(), "出差结束日期格式错误"))
                .invoiceFileName(invoice.originalFileName())
                .invoiceFilePath(invoice.filePath())
                .invoiceContentType(invoice.contentType())
                .invoiceFileSize(invoice.fileSize())
                .build();
    }

    private User loadErpSubmitter(String userId) {
        User submitter = userRepository.findById(userId)
                .orElseThrow(() -> new PermissionDeniedException("当前用户不存在或会话已过期"));
        if (submitter.getAccountDomain() != AccountDomain.ERP) {
            throw new PermissionDeniedException("仅 ERP 账号可提交采购和差旅报销");
        }
        return submitter;
    }

    private void validateCommonRequest(FinanceExpenseSubmissionCreateRequest request) {
        if (request.getInvoiceFile() == null || request.getInvoiceFile().isEmpty()) {
            throw new BusinessException("请上传发票");
        }
        if (normalizeText(request.getItemName()).isBlank()) {
            throw new BusinessException("费用项目不能为空");
        }
        if (normalizeText(request.getInvoiceNumber()).isBlank()) {
            throw new BusinessException("发票号码不能为空");
        }
        if (normalizeText(request.getPurpose()).isBlank()) {
            throw new BusinessException("用途说明不能为空");
        }
        resolveTotalAmount(request);
        parseInstantOrDate(request.getOccurredAt(), "发生日期不能为空");
    }

    private void validateTravelRequest(FinanceExpenseSubmissionCreateRequest request) {
        if (normalizeOptional(request.getDepartureLocation()) == null || normalizeOptional(request.getDestinationLocation()) == null) {
            throw new BusinessException("请填写出发地和目的地");
        }
        if (parseOptionalInstantOrDate(request.getTravelStartAt(), "出差开始日期不能为空") == null
                || parseOptionalInstantOrDate(request.getTravelEndAt(), "出差结束日期不能为空") == null) {
            throw new BusinessException("请填写完整的出差日期");
        }
    }

    private BigDecimal resolveTotalAmount(FinanceExpenseSubmissionCreateRequest request) {
        BigDecimal provided = scaleNullable(request.getTotalAmount());
        if (provided != null && provided.compareTo(BigDecimal.ZERO) > 0) {
            return provided;
        }

        BigDecimal unitPrice = scaleNullable(request.getUnitPrice());
        Integer quantity = resolveQuantity(request.getQuantity());
        if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
            return scale(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }
        throw new BusinessException("请填写有效金额");
    }

    private Integer resolveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            return 1;
        }
        return quantity;
    }

    private StoredInvoice storeInvoice(MultipartFile invoiceFile) {
        try {
            Path directory = Paths.get(uploadDir, "finance-submissions");
            Files.createDirectories(directory);
            String originalFileName = sanitizeFileName(invoiceFile.getOriginalFilename());
            String extension = extractExtension(originalFileName);
            String storedFileName = UUID.randomUUID() + extension;
            Path target = directory.resolve(storedFileName);
            invoiceFile.transferTo(target);
            return new StoredInvoice(
                    originalFileName,
                    target.toString(),
                    normalizeOptional(invoiceFile.getContentType()) == null ? "application/octet-stream" : invoiceFile.getContentType(),
                    invoiceFile.getSize()
            );
        } catch (IOException ex) {
            throw new BusinessException("发票上传失败");
        }
    }

    private FinanceExpenseSubmissionCenterResponse.Row toRow(FinanceExpenseSubmission submission) {
        return FinanceExpenseSubmissionCenterResponse.Row.builder()
                .id(submission.getId())
                .submissionType(submission.getSubmissionType() == null ? "UNKNOWN" : submission.getSubmissionType().name())
                .status(submission.getStatus() == null ? "UNKNOWN" : submission.getStatus().name())
                .submitterUserId(submission.getSubmitterUserId())
                .submitterName(submission.getSubmitterName())
                .projectId(submission.getProjectId())
                .projectName(submission.getProjectName())
                .projectFlowType(submission.getProjectFlowType())
                .itemName(submission.getItemName())
                .itemCategory(submission.getItemCategory())
                .itemSpecification(submission.getItemSpecification())
                .quantity(submission.getQuantity())
                .unitPrice(submission.getUnitPrice())
                .totalAmount(submission.getTotalAmount())
                .supplierName(submission.getSupplierName())
                .invoiceNumber(submission.getInvoiceNumber())
                .occurredAt(submission.getOccurredAt())
                .purpose(submission.getPurpose())
                .remarks(submission.getRemarks())
                .departureLocation(submission.getDepartureLocation())
                .destinationLocation(submission.getDestinationLocation())
                .travelStartAt(submission.getTravelStartAt())
                .travelEndAt(submission.getTravelEndAt())
                .invoiceFileName(submission.getInvoiceFileName())
                .invoiceFileSize(submission.getInvoiceFileSize())
                .createdAt(submission.getCreatedAt())
                .build();
    }

    private BigDecimal sumByType(List<FinanceExpenseSubmission> submissions, FinanceExpenseSubmissionType type) {
        return submissions.stream()
                .filter(item -> item.getSubmissionType() == type)
                .map(FinanceExpenseSubmission::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveSubmitterName(User submitter) {
        if (submitter.getName() != null && !submitter.getName().isBlank()) {
            return submitter.getName().trim();
        }
        return submitter.getUsername();
    }

    private BigDecimal scaleNullable(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return scale(value);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private Instant parseOptionalInstantOrDate(String raw, String errorMessage) {
        String normalized = normalizeOptional(raw);
        if (normalized == null) {
            return null;
        }
        return parseInstantOrDate(normalized, errorMessage);
    }

    private Instant parseInstantOrDate(String raw, String errorMessage) {
        String normalized = normalizeOptional(raw);
        if (normalized == null) {
            throw new BusinessException(errorMessage);
        }
        try {
            if (normalized.contains("T")) {
                return Instant.parse(normalized);
            }
            LocalDate localDate = LocalDate.parse(normalized);
            return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(normalized).atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception ignoredAgain) {
                throw new BusinessException(errorMessage);
            }
        }
    }

    private String sanitizeFileName(String raw) {
        String fallback = "invoice";
        String base = normalizeOptional(raw);
        if (base == null) {
            return fallback;
        }
        return base.replace('\\', '_').replace('/', '_');
    }

    private String extractExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index).toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    private String normalizeOptional(String value) {
        String normalized = normalizeText(value);
        return normalized.isBlank() ? null : normalized;
    }

    private record StoredInvoice(String originalFileName, String filePath, String contentType, long fileSize) {
    }

    public record InvoiceDownloadPayload(String fileName, String contentType, Long fileSize, byte[] bytes) {
    }
}
