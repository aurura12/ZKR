# ERP 账号创建协议生成模块设计

> 设计日期：2026-06-22  
> 目标：在 ERP 账号创建流程中，为新用户生成 outside 协议生成模块所产出的三份实习协议 Word 文档（`.docx`），并保存到 ERP 文档系统。

## 1. 背景与现状

- outside 协议生成模块位于 `outside/自动识别信息并填写协议的网页(...)/`，使用 Python + `python-docx` + LibreOffice 生成三份协议 Word 文档。
- 当前 ERP 的账号创建模块（`AdminCreateUserView.vue` + `AdminUserController`）只能创建账号，生成协议的按钮是 stub，后端旧的 `/agreement` 接口只生成简单 `.txt`。
- ERP 用户模型缺少协议生成必需的「学校院系」和「住址」字段。

## 2. 核心决策

| 项目 | 决策 |
|---|---|
| 字段补充 | 在创建账号表单新增「学校院系」和「住址」，写入 `sys_user` |
| 生成时机 | 账号创建成功后，手动勾选生成 |
| 生成格式 | `.docx`，三份打包成 zip 下载 |
| 模板存储 | 数据库存储，支持管理接口替换 |
| 技术方案 | 纯 Java + Apache POI XWPF 重写 outside 脚本逻辑 |
| 旧 txt 协议 | 不保留，`POST .../agreement` 改为生成 docx |
| 日期策略 | 实习开始 = 今天，结束 = 今天 + 3 个月 |

## 3. 数据模型

### 3.1 `sys_user` 新增字段

```java
@Column(name = "school_department", length = 200)
private String schoolDepartment;

@Column(name = "address", length = 300)
private String address;
```

### 3.2 `ProvisionUserRequest` 新增字段

```java
private String schoolDepartment;
private String address;
```

### 3.3 新增 `AgreementTemplate` 实体

```java
@Entity
@Table(name = "agreement_template")
@Data
public class AgreementTemplate {
    @Id
    @GeneratedValue(...)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code; // INTERNET, GENERAL, PROOF

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 10)
    private String fileType; // docx

    @Lob
    @Column(nullable = false)
    private byte[] content;

    private Instant createdAt;
    private Instant updatedAt;
}
```

### 3.4 新增 `AgreementType` 枚举

```java
public enum AgreementType {
    INTERNET,   // 互联网实习生协议
    GENERAL,    // 实习生协议
    PROOF       // 实习证明
}
```

## 4. 模板管理

### 4.1 默认模板初始化

通过 Spring Boot `CommandLineRunner` 在启动时检查 `agreement_template` 表：
- 若 `INTERNET`、`GENERAL`、`PROOF` 任一缺失，从 `erp-backend/src/main/resources/default-agreement-templates/` 读取默认 `.docx` 模板并写入数据库。
- 原 outside 的 `.doc` 实习证明模板需提前转换成 `.docx`。

### 4.2 模板管理接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/agreement-templates` | 列出模板元数据（不含 content） |
| PUT | `/api/admin/agreement-templates/{code}` | 上传新模板替换数据库内容 |

## 5. 后端服务

### 5.1 `AgreementGenerationService`

职责：
- 根据 `AgreementType` 从数据库读取模板；
- 按 outside 脚本逻辑填充姓名、身份证号、学校院系、联系电话、住址；
- 填充日期（今天 / 今天+3个月）；
- 返回生成的 docx 字节数组。

实现要点：
- 使用 Apache POI `XWPFDocument`；
- 按模板段落索引定位并填充（参考 outside `test1.py`、`test2.py`、`test3_实习证明.py`）；
- 金额大写转换逻辑用 Java 重写。

### 5.2 `AgreementZipService`

- 接收一组 `AgreementType`；
- 调用 `AgreementGenerationService` 生成多份 docx；
- 打包成 zip 返回字节数组。

## 6. 控制器端点

### 6.1 单份生成

```
POST /api/admin/users/{userId}/agreement?type=INTERNET
```

- 校验 provision admin 权限；
- 校验用户存在；
- 校验用户 `name`、`idNumber`、`schoolDepartment`、`phone`、`address` 非空；
- 生成 docx，保存到 `${app.uploads.dir}/documents/{userId}/agreement_{type}_{timestamp}.docx`；
- 返回 `{ message, filename }`。

### 6.2 批量生成

```
POST /api/admin/users/{userId}/agreements/batch
Body: { "types": ["INTERNET", "GENERAL", "PROOF"] }
```

- 校验、生成逻辑同上；
- 打包 zip；
- 返回 `Content-Disposition: attachment; filename="{name}_实习文件.zip"` 的流。

## 7. 前端改动

### 7.1 `AdminCreateUserView.vue`

- 表单增加：
  - 「学校院系」输入框
  - 「住址」输入框
- 底部「生成协议」按钮在 provision 成功前禁用或提示先创建账号。

### 7.2 创建成功后的协议生成弹窗

Provision 成功后弹出 `ElDialog`：
- 显示三份协议的复选框；
- 「生成并下载」按钮调用 `/agreements/batch`；
- 下载完成后可关闭弹窗返回个人中心。

### 7.3 临时数据保留

Provision 成功后保留 `userId`、`name`、`schoolDepartment` 等用于生成协议，直到弹窗关闭或跳转。

## 8. 文件存储

- 继续使用 `${app.uploads.dir}/documents/{userId}/`；
- 协议文件命名：`agreement_{type}_{timestamp}.docx`；
- 与劳动关系资料模块的 `hasAgreement` 标记兼容。

## 9. 测试计划

- 单元测试：`AgreementGenerationService` 使用内存中的 `XWPFDocument` 验证填充逻辑；
- 集成测试：调用 `/agreements/batch` 生成 zip 并断言包含 3 个 docx；
- 前端测试：手动验证创建账号 → 弹窗勾选 → 下载 zip 流程。
