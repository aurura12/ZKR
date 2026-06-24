package com.smartlab.erp.agreement;

import com.smartlab.erp.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class AgreementZipService {

    private final AgreementGenerationService generationService;

    public byte[] generateZip(User user, List<AgreementType> types) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (AgreementType type : types) {
                byte[] content = generationService.generateAgreement(user, type);
                String filename = user.getName() + "_" + type.getDisplayName() + ".docx";
                ZipEntry entry = new ZipEntry(filename);
                zos.putNextEntry(entry);
                zos.write(content);
                zos.closeEntry();
            }

            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("打包协议失败: " + e.getMessage(), e);
        }
    }
}
