# 项目文件总览管理页设计

> 设计日期：2026-06-23  
> 目标：为 Provision Admin 提供一个全局项目文件管理视图，支持按项目虚拟目录归类、移动、新建层级、查看与下载，且整理结果在各项目详情页同步可见。

## 1. 核心决策

| 项目 | 决策 |
|---|---|
| 可见权限 | Provision Admin（`Zhangqi`、`guojianwen`、`jiaomiao`） |
| 入口位置 | `App.vue` 顶部 `nav-left`，role-badge 右侧 |
| 展示形式 | 独立页面 `/admin/project-files` |
| 文件范围 | 全部项目相关文件 |
| 整理方案 | 虚拟目录 + 元数据映射，不改动物理文件位置 |
| 同步要求 | 项目详情页文件卡片读取同一虚拟目录元数据 |

## 2. 数据模型

### 2.1 `project_file_folder`

项目内虚拟目录。

```java
@Entity
@Table(name = "project_file_folder")
@Data
public class ProjectFileFolder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(name = "created_at")
    private Instant createdAt;
}
```

### 2.2 `project_file_mapping`

文件到虚拟目录的映射。

```java
@Entity
@Table(name = "project_file_mapping")
@Data
public class ProjectFileMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "source_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ProjectFileSourceType sourceType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(name = "display_name", nullable = false, length = 300)
    private String displayName;

    @Column(name = "created_at")
    private Instant createdAt;
}
```

### 2.3 `ProjectFileSourceType` 枚举

```java
public enum ProjectFileSourceType {
    PROJECT_ASSET,
    EXECUTION_FILE,
    PROJECT_EXPENSE_FILE,
    FINANCE_EXPENSE_SUBMISSION,
    PROJECT_COST_ADJUSTMENT
}
```

## 3. 后端 API

Base: `/api/admin/project-files`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/projects` | 列出所有项目（id、name、flowType） |
| GET | `/{projectId}/tree` | 返回该项目的虚拟目录树 + 文件列表 |
| POST | `/{projectId}/folders` | 新建目录：`{ parentId, name }` |
| DELETE | `/{projectId}/folders/{folderId}` | 删除空目录 |
| PATCH | `/files/{mappingId}/move` | 移动文件：`{ folderId }` |
| GET | `/files/{mappingId}/download` | 根据 source_type/source_id 下载实际文件 |
| POST | `/scan` | 扫描并初始化现有文件的 mapping |

## 4. 前端视图 `/admin/project-files`

布局：
- 左侧：项目列表（可搜索）。
- 右侧：文件管理器。
  - 面包屑显示当前路径。
  - 工具栏：新建文件夹、移动、下载、刷新。
  - 文件树表格展示目录和文件。
  - 点击文件可下载。

## 5. 与项目详情页同步

- `ProjectDetail.vue` 文件相关区域统一读取 `/api/admin/project-files/{projectId}/tree`。
- 普通成员只读；Provision admin 可编辑目录结构。

## 6. 初始化

- `ProjectFileMappingInitializer` 在启动时扫描：
  - `project_asset`
  - `execution_file`
  - `project_expense_file`
  - `finance_expense_submission`
  - `project_cost_adjustment`
- 为每条记录生成默认 mapping，按来源类型放入系统默认目录：
  - `PROJECT_ASSET` → `/项目资料/`
  - `EXECUTION_FILE` → `/执行文件/`
  - `PROJECT_EXPENSE_FILE` → `/费用报销/`
  - `FINANCE_EXPENSE_SUBMISSION` → `/财务报销/`
  - `PROJECT_COST_ADJUSTMENT` → `/成本调整/`

## 7. 安全

- 所有写操作校验 Provision Admin。
- 删除目录只删空目录，不删文件。
- 移动只改 `folder_id`，不改物理路径。
