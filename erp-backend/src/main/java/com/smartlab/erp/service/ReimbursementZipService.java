package com.smartlab.erp.service;

import com.smartlab.erp.entity.InvoiceLedger;
import com.smartlab.erp.entity.ProjectExpense;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.repository.InvoiceLedgerRepository;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.util.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReimbursementZipService {

    private final InvoiceLedgerRepository invoiceLedgerRepository;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".pdf", ".ofd"
    ));

    private static final Set<String> EXCEL_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".xlsx", ".xls"
    ));

    private static final Set<String> SKIP_ENTRIES = new HashSet<>(Arrays.asList(
            "__MACOSX", ".DS_Store", "Thumbs.db"
    ));

    private static final List<String> EXCEL_HEADERS = Arrays.asList(
            "序号", "发票文件名", "费用发生时间", "金额（含税）", "费用性质",
            "与项目有关的其他supporting", "关联合同号"
    );

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional
    public List<InvoiceLedger> process(MultipartFile zipFile, ProjectExpense expense) {
        String originalFilename = zipFile.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("ZIP 文件名为空");
        }

        ParsedZipName parsed = parseZipFilename(originalFilename);

        Path extractionDir;
        try {
            extractionDir = Paths.get(uploadDir, "reimbursement-zips", expense.getId().toString());
            Files.createDirectories(extractionDir);
        } catch (IOException e) {
            throw new BusinessException("无法创建解压目录: " + e.getMessage());
        }

        List<InvoiceLedger> ledgers = new ArrayList<>();
        Path excelFile = null;

        try (InputStream is = zipFile.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (shouldSkipEntry(entry)) continue;

                String entryName = getEntryName(entry);
                String ext = getExtension(entryName).toLowerCase();

                Path targetFile = extractionDir.resolve(sanitizePathElement(entryName));
                Path parent = targetFile.getParent();
                if (parent != null) Files.createDirectories(parent);

                Files.copy(zis, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                if (EXCEL_EXTENSIONS.contains(ext)) {
                    excelFile = targetFile;
                }
            }
        } catch (IOException e) {
            throw new BusinessException("ZIP 文件解压失败: " + e.getMessage());
        }

        if (excelFile == null) {
            throw new BusinessException("ZIP 压缩包中未找到 Excel 汇总表（.xlsx/.xls）");
        }

        List<ImageEntry> imageEntries = scanImageFiles(extractionDir);

        ledgers = parseExcelAndCreateLedgers(excelFile, expense, imageEntries, parsed);

        invoiceLedgerRepository.saveAll(ledgers);
        log.info("报销 ZIP 处理完成: expenseId={}, ledgerRows={}", expense.getId(), ledgers.size());

        return ledgers;
    }

    ParsedZipName parseZipFilename(String filename) {
        String name = filename;
        String ext = getExtension(filename);
        if (!ext.equalsIgnoreCase(".zip")) {
            throw new BusinessException("仅支持 .zip 格式的压缩包，当前文件: " + filename);
        }
        name = name.substring(0, name.length() - ext.length());

        String[] parts = name.split("\\+");
        if (parts.length < 3) {
            throw new BusinessException(
                    "ZIP 文件名格式错误，正确格式为: 姓名+项目名+金额.zip。当前文件名: " + filename);
        }

        String submitterName = parts[0].trim();
        String projectName = String.join("+", Arrays.copyOfRange(parts, 1, parts.length - 1)).trim();
        String amountStr = parts[parts.length - 1].trim();

        if (submitterName.isEmpty()) {
            throw new BusinessException("ZIP 文件名中\"姓名\"为空: " + filename);
        }
        if (projectName.isEmpty()) {
            throw new BusinessException("ZIP 文件名中\"项目名\"为空: " + filename);
        }

        BigDecimal totalAmount;
        try {
            totalAmount = new BigDecimal(amountStr);
        } catch (NumberFormatException e) {
            throw new BusinessException("ZIP 文件名中\"金额\"格式无效: " + amountStr + "（文件名: " + filename + "）");
        }

        return new ParsedZipName(submitterName, projectName, totalAmount);
    }

    private List<InvoiceLedger> parseExcelAndCreateLedgers(Path excelFile, ProjectExpense expense,
                                                            List<ImageEntry> imageEntries,
                                                            ParsedZipName parsed) {
        List<InvoiceLedger> ledgers = new ArrayList<>();

        try (InputStream is = Files.newInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) {
                throw new BusinessException("Excel 汇总表中无数据行（至少需要表头行 + 数据行）");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException("Excel 汇总表缺少表头行");
            }

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String seqStr = getCellString(row.getCell(0));
                if (seqStr.isBlank()) continue;

                String imageFileName = getCellString(row.getCell(1)).trim();
                String expenseDateStr = getCellString(row.getCell(2)).trim();
                String amountStr = getCellString(row.getCell(3)).trim();
                String expenseNature = getCellString(row.getCell(4)).trim();
                String projectSupport = getCellString(row.getCell(5)).trim();
                String contractNo = getCellString(row.getCell(6)).trim();

                if (imageFileName.isEmpty()) {
                    log.warn("Excel 第 {} 行 \"发票文件名\" 为空，跳过", rowIdx + 1);
                    continue;
                }

                LocalDate expenseDate = null;
                if (!expenseDateStr.isEmpty()) {
                    try {
                        expenseDate = LocalDate.parse(expenseDateStr, DATE_FORMATTER);
                    } catch (DateTimeParseException e) {
                        log.warn("Excel 第 {} 行费用发生时间格式无效: {}", rowIdx + 1, expenseDateStr);
                    }
                }

                BigDecimal amountInclTax = null;
                if (!amountStr.isEmpty()) {
                    try {
                        amountInclTax = new BigDecimal(amountStr);
                    } catch (NumberFormatException e) {
                        log.warn("Excel 第 {} 行金额格式无效: {}", rowIdx + 1, amountStr);
                    }
                }

                ImageEntry matchedImage = findImageEntry(imageEntries, imageFileName);

                InvoiceLedger ledger = InvoiceLedger.builder()
                        .expenseId(expense.getId())
                        .excelRow(rowIdx + 1)
                        .expenseDate(expenseDate)
                        .amountInclTax(amountInclTax)
                        .expenseNature(expenseNature.isEmpty() ? null : expenseNature)
                        .projectName(expense.getProjectName())
                        .projectSupport(projectSupport.isEmpty() ? null : projectSupport)
                        .contractNo(contractNo.isEmpty() ? null : contractNo)
                        .isProjectRel(true)
                        .dataSource("EXCEL")
                        .ocrStatus("PENDING")
                        .verifiedStatus("PENDING")
                        .build();

                if (matchedImage != null) {
                    ledger.setImageFile(matchedImage.path.toString());
                    ledger.setImageHash(matchedImage.sha256);
                } else if (!imageFileName.isEmpty()) {
                    log.warn("Excel 第 {} 行指定的发票文件\"{}\"在压缩包中未找到", rowIdx + 1, imageFileName);
                }

                ledgers.add(ledger);
            }

        } catch (IOException e) {
            throw new BusinessException("Excel 汇总表读取失败: " + e.getMessage());
        }

        if (ledgers.isEmpty()) {
            throw new BusinessException("Excel 汇总表中无有效数据行");
        }

        return ledgers;
    }

    private ImageEntry findImageEntry(List<ImageEntry> entries, String fileName) {
        return entries.stream()
                .filter(e -> e.fileName.equalsIgnoreCase(fileName))
                .findFirst()
                .orElse(null);
    }

    private List<ImageEntry> scanImageFiles(Path dir) {
        List<ImageEntry> result = new ArrayList<>();
        try {
            Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String ext = getExtension(p.getFileName().toString()).toLowerCase();
                        return IMAGE_EXTENSIONS.contains(ext);
                    })
                    .forEach(p -> {
                        try {
                            String hash = sha256(p);
                            result.add(new ImageEntry(p.getFileName().toString(), p, hash));
                        } catch (Exception e) {
                            log.warn("计算文件哈希失败: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("扫描图片文件失败: {}", e.getMessage());
        }
        return result;
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(path);
        byte[] hash = digest.digest(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private boolean shouldSkipEntry(ZipEntry entry) {
        if (entry.isDirectory()) return true;
        String name = entry.getName().replace("\\", "/");
        for (String skip : SKIP_ENTRIES) {
            if (name.startsWith(skip + "/") || name.equals(skip) || name.startsWith("._")) {
                return true;
            }
        }
        return false;
    }

    private String getEntryName(ZipEntry entry) {
        String name = entry.getName().replace("\\", "/");
        int lastSlash = name.lastIndexOf('/');
        return lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private String sanitizePathElement(String name) {
        return name.replace("\\", "/")
                .replace("..", "")
                .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-./]", "_");
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMATTER);
                }
                double val = cell.getNumericCellValue();
                yield val == Math.floor(val) && !Double.isInfinite(val)
                        ? String.valueOf((long) val)
                        : new BigDecimal(String.valueOf(val)).stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    @Transactional(readOnly = true)
    public byte[] generateTemplateExcel() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("报销汇总表");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < EXCEL_HEADERS.size(); i++) {
                headerRow.createCell(i).setCellValue(EXCEL_HEADERS.get(i));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("生成 Excel 模板失败: " + e.getMessage());
        }
    }

    record ParsedZipName(String submitterName, String projectName, BigDecimal totalAmount) {}

    record ImageEntry(String fileName, Path path, String sha256) {}
}
