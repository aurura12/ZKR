# ERP 账号创建协议生成模块实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 ERP 账号创建流程中为新用户生成三份实习协议 Word 文档，支持前端勾选、后端打包 zip 下载，并保存到用户文档目录。

**Architecture:** 在现有 ERP 后端新增 `AgreementTemplate` 表存储模板，新增 `AgreementGenerationService` 用 Apache POI 重写 outside 脚本逻辑；扩展 `AdminUserController` 提供单份/批量生成接口；前端 `AdminCreateUserView.vue` 增加字段并在创建成功后弹出协议生成对话框。

**Tech Stack:** Java 17, Spring Boot 3.2, Apache POI 5.2.5, PostgreSQL, Vue 3, Element Plus.

---

## Task 1: 准备默认协议模板文件

**Files:**
- Copy from: `outside/自动识别信息并填写协议的网页(1)/自动识别信息并填写协议的网页/word_templates/`
- Create: `erp-backend/src/main/resources/default-agreement-templates/1_互联网实习生协议--模板.docx`
- Create: `erp-backend/src/main/resources/default-agreement-templates/实习生协议模板.docx`
- Create: `erp-backend/src/main/resources/default-agreement-templates/模板-实习证明.docx`（由 `.doc` 转换而来）

- [ ] **Step 1: 把 outside 模板复制到 resources 目录**

Run:
```bash
mkdir -p "/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/resources/default-agreement-templates"
cp "/home/a/zhangqi/workspace/ZKR/outside/自动识别信息并填写协议的网页(1)/自动识别信息并填写协议的网页/word_templates/1_互联网实习生协议--模板.docx" "/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/resources/default-agreement-templates/"
cp "/home/a/zhangqi/workspace/ZKR/outside/自动识别信息并填写协议的网页(1)/自动识别信息并填写协议的网页/word_templates/实习生协议模板.docx" "/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/resources/default-agreement-templates/"
```

- [ ] **Step 2: 把 `.doc` 实习证明模板转换为 `.docx`**

Run:
```bash
libreoffice --headless --convert-to docx --outdir "/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/resources/default-agreement-templates/" "/home/a/zhangqi/workspace/ZKR/outside/自动识别信息并填写协议的网页(1)/自动识别信息并填写协议的网页/word_templates/模板-实习证明(1).doc"
mv "/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/resources/default-agreement-templates/模板-实习证明(1).docx" "/home/a/zhangqi/workspace/ZKR/erp-backend/src/main/resources/default-agreement-templates/模板-实习证明.docx"
```

Expected: three `.docx` files exist in `default-agreement-templates/`.

---

## Task 2: 新增 `AgreementType` 枚举

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/agreement/AgreementType.java`

- [ ] **Step 1: 创建枚举文件**

```java
package com.smartlab.erp.agreement;

public enum AgreementType {
    INTERNET("互联网实习生协议"),
    GENERAL("实习生协议"),
    PROOF("实习证明");

    private final String displayName;

    AgreementType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

---

## Task 3: 新增 `AgreementTemplate` 实体与 Repository

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/agreement/AgreementTemplate.java`
- Create: `erp-backend/src/main/java/com/smartlab/erp/agreement/AgreementTemplateRepository.java`

- [ ] **Step 1: 创建实体**

```java
package com.smartlab.erp.agreement;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "agreement_template")
@Data
public class AgreementTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    @Enumerated(EnumType.STRING)
    private AgreementType code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 10)
    private String fileType;

    @Lob
    @Column(nullable = false)
    private byte[] content;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

- [ ] **Step 2: 创建 Repository**

```java
package com.smartlab.erp.agreement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgreementTemplateRepository extends JpaRepository<AgreementTemplate, Long> {
    Optional<AgreementTemplate> findByCode(AgreementType code);
}
```

---

## Task 4: 扩展 `User` 实体和 `ProvisionUserRequest`

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/entity/User.java`
- Modify: `erp-backend/src/main/java/com/smartlab/erp/dto/ProvisionUserRequest.java`

- [ ] **Step 1: 在 `User` 实体中新增字段**

Add after `paymentEntity` field:

```java
@Column(name = "school_department", length = 200)
private String schoolDepartment;

@Column(name = "address", length = 300)
private String address;
```

- [ ] **Step 2: 在 `ProvisionUserRequest` 中新增字段**

Add before closing brace:

```java
private String schoolDepartment;
private String address;
```

---

## Task 5: 修改 `AuthService.provisionUser` 保存新字段

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/service/AuthService.java`

- [ ] **Step 1: 读取 `AuthService` 中 `provisionUser` 方法**

Find the `User` builder/creation code, for example:

```java
User user = User.builder()
    .username(request.getUsername().trim())
    .name(request.getName().trim())
    .role(request.getRole())
    .accountDomain(request.getDomain())
    .position(request.getPosition())
    .ethnicity(request.getEthnicity())
    .phone(request.getPhone())
    .idNumber(request.getIdNumber())
    .dailyWage(request.getDailyWage() != null ? request.getDailyWage() : new BigDecimal("300.00"))
    .partTime(request.getPartTime() != null ? request.getPartTime() : false)
    .paymentEntity(request.getPaymentEntity() != null ? request.getPaymentEntity() : "国科九天")
    .bankName(request.getBankName())
    .bankAccount(request.getBankAccount())
    .build();
```

- [ ] **Step 2: 增加新字段映射**

Add two lines:

```java
    .schoolDepartment(request.getSchoolDepartment())
    .address(request.getAddress())
```

---

## Task 6: 实现 `AgreementGenerationService`

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/agreement/AgreementGenerationService.java`

- [ ] **Step 1: 创建服务类，实现三份协议生成**

```java
package com.smartlab.erp.agreement;

import com.smartlab.erp.entity.User;
import com.smartlab.erp.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgreementGenerationService {

    private final AgreementTemplateRepository templateRepository;

    public byte[] generateAgreement(User user, AgreementType type) {
        AgreementTemplate template = templateRepository.findByCode(type)
                .orElseThrow(() -> new ResourceNotFoundException("协议模板不存在: " + type));

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(template.getContent()));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            switch (type) {
                case INTERNET -> fillInternetAgreement(doc, user);
                case GENERAL -> fillGeneralAgreement(doc, user);
                case PROOF -> fillProof(doc, user);
            }

            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("协议生成失败: " + e.getMessage(), e);
        }
    }

    private void fillInternetAgreement(XWPFDocument doc, User user) {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(3);

        setParagraphText(doc.getParagraphs().get(6),
                "姓 名： " + user.getName() + "                    身份证号码：" + user.getIdNumber());
        setParagraphText(doc.getParagraphs().get(7), "学校院系： " + user.getSchoolDepartment());
        setParagraphText(doc.getParagraphs().get(8), "联系电话： " + user.getPhone());
        setParagraphText(doc.getParagraphs().get(9), "住 址： " + user.getAddress());

        fillDateParagraph(doc.getParagraphs().get(11), start, end);
    }

    private void fillGeneralAgreement(XWPFDocument doc, User user) {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(3);

        setParagraphText(doc.getParagraphs().get(6),
                "姓 名： " + user.getName() + "                   身份证号码：" + user.getIdNumber());
        setParagraphText(doc.getParagraphs().get(7), "学校院系： " + user.getSchoolDepartment());
        setParagraphText(doc.getParagraphs().get(8), "联系电话： " + user.getPhone());
        setParagraphText(doc.getParagraphs().get(9), "住 址: " + user.getAddress());

        XWPFParagraph datePara = doc.getParagraphs().get(12);
        String text = "  " + start.getYear() + "年 " + start.getMonthValue() + "月 " + start.getDayOfMonth() + "日 至 "
                + end.getYear() + "年 " + end.getMonthValue() + "月 " + end.getDayOfMonth() + "日）。"
                + "乙方保证其有资格签订本协议，且承诺其参加实习符合国家及所在学校的相关规定，如因此引起纠纷由乙方自行全部承担。 ";
        setParagraphText(datePara, text);
    }

    private void fillProof(XWPFDocument doc, User user) {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(3);
        String school = extractSchoolName(user.getSchoolDepartment());

        String text = "兹有  " + school + "     学校 " + user.getName() + "    同学（身份证号: " + user.getIdNumber() + " ）于 "
                + start.getYear() + "年 " + start.getMonthValue() + "月 " + start.getDayOfMonth() + "日至 "
                + end.getYear() + "年 " + end.getMonthValue() + "月 " + end.getDayOfMonth() + "日 "
                + "在我单位  互联网软件技术实验室（部门）实习。在职期间，工作积极，成绩突出。";
        setParagraphText(doc.getParagraphs().get(2), text);
    }

    private void setParagraphText(XWPFParagraph paragraph, String text) {
        if (paragraph == null) return;
        if (!paragraph.getRuns().isEmpty()) {
            paragraph.getRuns().get(0).setText(text, 0);
            for (int i = 1; i < paragraph.getRuns().size(); i++) {
                paragraph.getRuns().get(i).setText("", 0);
            }
        } else {
            XWPFRun run = paragraph.createRun();
            run.setText(text);
        }
    }

    private void fillDateParagraph(XWPFParagraph paragraph, LocalDate start, LocalDate end) {
        if (paragraph == null || paragraph.getRuns().size() < 22) return;
        var runs = paragraph.getRuns();
        runs.get(5).setText(" " + start.getYear(), 0);
        runs.get(6).setText("", 0);
        runs.get(7).setText("", 0);
        runs.get(10).setText(" " + start.getMonthValue(), 0);
        runs.get(12).setText(" " + start.getDayOfMonth(), 0);
        runs.get(13).setText("", 0);
        runs.get(15).setText(" " + end.getYear(), 0);
        runs.get(16).setText("", 0);
        runs.get(17).setText("", 0);
        runs.get(19).setText(" " + end.getMonthValue(), 0);
        runs.get(21).setText(" " + end.getDayOfMonth(), 0);
    }

    private String extractSchoolName(String schoolDepartment) {
        if (schoolDepartment == null || schoolDepartment.isBlank()) return "";
        String text = schoolDepartment.trim();
        for (String sep : new String[]{"  ", "   ", "\t"}) {
            if (text.contains(sep)) {
                return text.split(sep)[0].trim();
            }
        }
        if (text.contains("学院") || text.contains("系")) {
            String[] parts = text.split("\\s+");
            if (parts.length > 0) return parts[0].trim();
        }
        return text;
    }
}
```

- [ ] **Step 2: 创建 `ResourceNotFoundException`（如不存在）**

If it doesn't exist, create in `erp-backend/src/main/java/com/smartlab/erp/exception/ResourceNotFoundException.java`:

```java
package com.smartlab.erp.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

---

## Task 7: 实现 `AgreementZipService`

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/agreement/AgreementZipService.java`

- [ ] **Step 1: 创建 zip 打包服务**

```java
package com.smartlab.erp.agreement;

import com.smartlab.erp.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
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
```

---

## Task 8: 实现默认模板初始化 `CommandLineRunner`

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/agreement/AgreementTemplateInitializer.java`

- [ ] **Step 1: 创建初始化器**

```java
package com.smartlab.erp.agreement;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

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
            } catch (Exception e) {
                throw new RuntimeException("初始化协议模板失败 [" + type + "]: " + e.getMessage(), e);
            }
        }
    }
}
```

---

## Task 9: 扩展 `AdminUserController` 协议生成接口

**Files:**
- Modify: `erp-backend/src/main/java/com/smartlab/erp/controller/AdminUserController.java`

- [ ] **Step 1: 注入服务并修改单份生成接口**

Add imports and fields:

```java
import com.smartlab.erp.agreement.AgreementGenerationService;
import com.smartlab.erp.agreement.AgreementType;
import com.smartlab.erp.agreement.AgreementZipService;
import java.util.Arrays;
import java.util.stream.Collectors;
```

Add constructor-injected fields (or use `@RequiredArgsConstructor` and add `final`):

```java
private final AgreementGenerationService agreementGenerationService;
private final AgreementZipService agreementZipService;
```

- [ ] **Step 2: 替换旧的 `/users/{userId}/agreement` 接口**

Replace the old method with:

```java
@PostMapping("/users/{userId}/agreement")
public ResponseEntity<?> generateAgreement(
        @PathVariable String userId,
        @RequestParam(defaultValue = "INTERNET") AgreementType type) {
    requireProvisionAdmin();
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    validateAgreementFields(user);

    try {
        byte[] content = agreementGenerationService.generateAgreement(user, type);
        Path userDir = documentsDir().resolve(userId);
        Files.createDirectories(userDir);
        String filename = "agreement_" + type.name().toLowerCase() + "_" + System.currentTimeMillis() + ".docx";
        Files.write(userDir.resolve(filename), content);
        return ResponseEntity.ok(Map.of("message", "协议生成成功", "filename", filename));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "协议生成失败: " + e.getMessage()));
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
        throw new RuntimeException("生成协议前请完善用户的姓名、身份证号、学校院系、手机号和住址");
    }
}

private boolean isBlank(String s) {
    return s == null || s.isBlank();
}

private Path documentsDir() {
    return Path.of(uploadsDir, "documents");
}
```

- [ ] **Step 3: 在类上增加 `@Value` 字段以替换硬编码上传目录**

Add:

```java
@Value("${app.uploads.dir:/app/uploads}")
private String uploadsDir;
```

And remove the old `private static final String DOCUMENTS_DIR = "./uploads/documents";` if present, updating other methods that use it to use `documentsDir()`.

---

## Task 10: 创建 `AgreementBatchRequest` DTO

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/agreement/AgreementBatchRequest.java`

- [ ] **Step 1: 创建 DTO**

```java
package com.smartlab.erp.agreement;

import lombok.Data;

import java.util.List;

@Data
public class AgreementBatchRequest {
    private List<AgreementType> types;
}
```

---

## Task 11: 创建模板管理接口

**Files:**
- Create: `erp-backend/src/main/java/com/smartlab/erp/controller/AgreementTemplateController.java`

- [ ] **Step 1: 创建控制器**

```java
package com.smartlab.erp.controller;

import com.smartlab.erp.agreement.AgreementTemplate;
import com.smartlab.erp.agreement.AgreementTemplateRepository;
import com.smartlab.erp.agreement.AgreementType;
import com.smartlab.erp.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/agreement-templates")
@RequiredArgsConstructor
public class AgreementTemplateController {

    private final AuthService authService;
    private final AgreementTemplateRepository templateRepository;

    private void requireProvisionAdmin() {
        if (!authService.canProvisionAccounts(authService.getCurrentUser())) {
            throw new RuntimeException("仅指定管理员可操作");
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listTemplates() {
        requireProvisionAdmin();
        return ResponseEntity.ok(templateRepository.findAll().stream().map(t -> {
            Map<String, Object> map = Map.of(
                    "code", t.getCode(),
                    "name", t.getName(),
                    "fileType", t.getFileType(),
                    "createdAt", t.getCreatedAt(),
                    "updatedAt", t.getUpdatedAt()
            );
            return map;
        }).collect(Collectors.toList()));
    }

    @PutMapping("/{code}")
    public ResponseEntity<Map<String, String>> updateTemplate(
            @PathVariable AgreementType code,
            @RequestParam("file") MultipartFile file) {
        requireProvisionAdmin();
        try {
            AgreementTemplate template = templateRepository.findByCode(code)
                    .orElseGet(AgreementTemplate::new);
            template.setCode(code);
            template.setName(code.getDisplayName());
            template.setFileType("docx");
            template.setContent(file.getBytes());
            templateRepository.save(template);
            return ResponseEntity.ok(Map.of("message", "模板更新成功"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "模板更新失败: " + e.getMessage()));
        }
    }
}
```

---

## Task 12: 前端表单增加字段并改造生成流程

**Files:**
- Modify: `lab-erp-demo/src/views/AdminCreateUserView.vue`

- [ ] **Step 1: 在 `form` 对象中新增字段**

```javascript
const form = reactive({
  // ... existing fields ...
  schoolDepartment: '',
  address: ''
})
```

- [ ] **Step 2: 在模板表单网格中新增两个输入框**

Add after idNumber field block:

```html
<div class="field-block full-width">
  <label>学校院系</label>
  <el-input v-model="form.schoolDepartment" placeholder="例如：中国科学院大学 计算机软件与理论" />
</div>

<div class="field-block full-width">
  <label>住址</label>
  <el-input v-model="form.address" placeholder="例如：中国科学院大学雁栖湖校区" />
</div>
```

- [ ] **Step 3: 改造 handleSubmit 保留数据并弹出协议生成对话框**

Add state:

```javascript
const createdUserId = ref('')
const createdUserName = ref('')
const showAgreementDialog = ref(false)
const selectedAgreements = ref([])
const agreementOptions = [
  { label: '互联网实习生协议', value: 'INTERNET' },
  { label: '实习生协议', value: 'GENERAL' },
  { label: '实习证明', value: 'PROOF' }
]
```

Modify `handleSubmit`:

```javascript
const handleSubmit = async () => {
  // ... existing validation ...

  submitting.value = true
  try {
    const payload = { /* ... existing fields plus ... */
      schoolDepartment: form.schoolDepartment,
      address: form.address
    }
    const response = await request.post('/api/admin/users/provision', payload)
    createdUserId.value = response.userId || ''
    createdUserName.value = form.name.trim()
    ElMessage.success(response?.message || `账号创建成功，初始密码为：${form.username.trim()}123`)
    showAgreementDialog.value = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '账号创建失败')
  } finally {
    submitting.value = false
  }
}
```

- [ ] **Step 4: 实现批量生成下载**

```javascript
const handleGenerateAgreements = async () => {
  if (selectedAgreements.value.length === 0) {
    ElMessage.warning('请至少选择一份协议')
    return
  }
  try {
    const res = await request.post(
      `/api/admin/users/${createdUserId.value}/agreements/batch`,
      { types: selectedAgreements.value },
      { responseType: 'blob' }
    )
    const blob = new Blob([res.data], { type: 'application/zip' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${createdUserName.value}_实习文件.zip`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success('协议生成成功')
  } catch (error) {
    ElMessage.error('协议生成失败')
  }
}
```

- [ ] **Step 5: 在模板底部添加协议生成对话框**

```html
<el-dialog v-model="showAgreementDialog" title="生成协议" width="420px">
  <div style="display: grid; gap: 12px;">
    <el-checkbox-group v-model="selectedAgreements">
      <el-checkbox v-for="opt in agreementOptions" :key="opt.value" :label="opt.value">
        {{ opt.label }}
      </el-checkbox>
    </el-checkbox-group>
  </div>
  <template #footer>
    <el-button @click="showAgreementDialog = false">跳过</el-button>
    <el-button type="primary" @click="handleGenerateAgreements">生成并下载</el-button>
  </template>
</el-dialog>
```

---

## Task 13: 编译与测试

**Files:**
- All modified files.

- [ ] **Step 1: 编译后端**

Run:
```bash
./mvnw -f /home/a/zhangqi/workspace/ZKR/erp-backend/pom.xml clean compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: 运行后端单元测试**

Run:
```bash
./mvnw -f /home/a/zhangqi/workspace/ZKR/erp-backend/pom.xml test
```

Expected: Tests pass.

- [ ] **Step 3: 启动后端并验证模板初始化**

Run:
```bash
./mvnw -f /home/a/zhangqi/workspace/ZKR/erp-backend/pom.xml spring-boot:run
```

Then:
```bash
curl -s http://localhost:8080/api/admin/agreement-templates
```

Expected: returns three template metadata records.

- [ ] **Step 4: 验证协议批量生成**

After creating a user via frontend or curl, call:
```bash
curl -X POST "http://localhost:8080/api/admin/users/{userId}/agreements/batch" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"types":["INTERNET","GENERAL","PROOF"]}' \
  --output test.zip
```

Expected: `test.zip` contains 3 `.docx` files.

---

## Task 14: 提交代码

- [ ] **Step 1: 检查变更并提交**

Run:
```bash
git status
git add -A
git commit -m "feat: integrate agreement generation into ERP account creation"
```
