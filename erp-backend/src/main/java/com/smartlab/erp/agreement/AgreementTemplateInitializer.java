package com.smartlab.erp.agreement;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgreementTemplateInitializer {

    private final AgreementTemplateRepository templateRepository;

    private static final Map<AgreementType, String> DEFAULT_TEMPLATES = Map.of(
            AgreementType.INTERNET, "default-agreement-templates/1_互联网实习生协议--模板.docx",
            AgreementType.GENERAL, "default-agreement-templates/实习生协议模板.docx",
            AgreementType.PROOF, "default-agreement-templates/模板-实习证明.docx"
    );

    @PostConstruct
    public void init() {
        for (Map.Entry<AgreementType, String> entry : DEFAULT_TEMPLATES.entrySet()) {
            AgreementType type = entry.getKey();
            String path = entry.getValue();

            if (templateRepository.findByCode(type).isPresent()) {
                log.info("协议模板已存在，跳过初始化: {}", type);
                continue;
            }

            try (InputStream is = new ClassPathResource(path).getInputStream()) {
                byte[] content = is.readAllBytes();

                AgreementTemplate template = new AgreementTemplate();
                template.setCode(type);
                template.setName(type.getDisplayName());
                template.setFileType("docx");
                template.setContent(content);
                templateRepository.save(template);
                log.info("默认协议模板初始化完成: {}", type);
            } catch (Exception e) {
                throw new RuntimeException("初始化协议模板失败 [" + type + "]: " + e.getMessage(), e);
            }
        }
    }
}
