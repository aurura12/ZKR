package com.smartlab.erp.agreement;

import com.smartlab.erp.entity.User;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementGenerationServiceTest {

    @Mock
    private AgreementTemplateRepository templateRepository;

    @InjectMocks
    private AgreementGenerationService generationService;

    @Test
    void shouldGenerateInternetAgreement() throws Exception {
        byte[] templateBytes = new ClassPathResource("default-agreement-templates/1_互联网实习生协议--模板.docx").getInputStream().readAllBytes();
        AgreementTemplate template = new AgreementTemplate();
        template.setCode(AgreementType.INTERNET);
        template.setContent(templateBytes);
        when(templateRepository.findByCode(AgreementType.INTERNET)).thenReturn(Optional.of(template));

        User user = new User();
        user.setName("测试用户");
        user.setIdNumber("110101200001011234");
        user.setSchoolDepartment("测试大学 计算机学院");
        user.setPhone("13800138000");
        user.setAddress("测试地址");

        byte[] result = generationService.generateAgreement(user, AgreementType.INTERNET);

        assertNotNull(result);
        assertTrue(result.length > 0);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            String text = doc.getParagraphs().get(6).getText();
            assertTrue(text.contains("测试用户"));
            assertTrue(text.contains("110101200001011234"));
        }
    }
}
