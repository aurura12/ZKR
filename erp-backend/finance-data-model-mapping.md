# T-003 数据模型与数据库迁移映射草案

## 1. 结论
- 以 `SysProject` 作为财务域的统一业务锚点，不直接把 legacy `business_venture.id` 改造成 `sys_project.project_id`。
- 延续现有 sidecar 模式，为财务域增加最小必要的 finance profile / ledger / sheet 聚合，避免把钱包、清算、分红字段硬塞进 `SysProject` 或 `SysProjectMember`。
- 复用现有 `User`、`SysProject`、`MiddlewareAsset`、`MiddlewareRoyaltyRoster`，其中成员参与关系继续放在 `SysProjectMember`，股权/分红关系单独建模。

## 2. 已审阅的现有落点
- `erp-backend/src/main/java/com/smartlab/erp/entity/SysProject.java`: `projectId` 为字符串主键，已承载 `flowType`、状态、预算、成本、预计收入，不适合继续吸收钱包/清算流水类字段。
- `erp-backend/src/main/java/com/smartlab/erp/entity/SysProjectMember.java`: 适合表达参与关系，不适合复用为股权分红表。
- `erp-backend/src/main/java/com/smartlab/erp/entity/ProductIdeaDetail.java` 与 `erp-backend/src/main/java/com/smartlab/erp/entity/ResearchProjectProfile.java`: 已证明 sidecar 弱关联是当前代码库的既有模式。
- `erp-backend/src/main/java/com/smartlab/erp/entity/MiddlewareAsset.java` 与 `erp-backend/src/main/java/com/smartlab/erp/entity/MiddlewareRoyaltyRoster.java`: 可直接承接 legacy 中间件资产和分润权属的一部分能力。
- `erp-backend/src/main/java/com/smartlab/erp/entity/User.java`: 可直接作为钱包、流水、分红、调账的用户引用锚点。

## 3. 总体映射策略

### 3.1 venture 主锚点
| Legacy 表/字段 | Spring Boot 承接 | 说明 |
|---|---|---|
| `business_venture.id` | finance sidecar.`legacy_venture_id` | 保留 BIGINT 原值，避免与 `SysProject.projectId` 的字符串主键硬合并。 |
| `business_venture` 主记录 | `SysProject` + finance venture profile sidecar | `SysProject` 负责统一项目身份，finance sidecar 负责 legacy 财务专属元数据。 |
| `fin_venture_ext.fin_name` | finance venture profile.`display_name` | 不落回 `SysProject.name` 的唯一来源，避免覆盖协同系统已有项目命名。 |
| `status_stage` | `flowType` + 对应状态枚举 + finance profile.`legacy_stage` | 先保留原阶段文案，再做受控映射，避免一次性丢失 legacy 语义。 |

建议的最小承接对象：`finance_venture_profile`
- 关键字段: `project_id`, `legacy_venture_id`, `display_name`, `legacy_stage`, `ledger_enabled`, `source_system`
- 理由: 这是所有 `fin_*` 表进入 Spring Boot 的最小桥接层，比直接改造 `SysProject` 更符合当前 sidecar 习惯。

### 3.2 成员、股权与分红权属
| Legacy 表 | Spring Boot 承接 | 说明 |
|---|---|---|
| `venture_cap_table` | finance equity roster | 与 `SysProjectMember` 分离；前者表达收益分配，后者表达协作参与。 |
| `sys_user` 关联 | `User.userId` | 用户主数据继续复用现有 `sys_user`。 |
| legacy 角色/岗位 | `SysProjectMember.role` + finance equity role 字段 | 参与角色与分红角色允许并存，不强行复用一个字段。 |

建议的最小承接对象：`finance_venture_equity`
- 关键字段: `project_id`, `user_id`, `equity_ratio`, `role_code`, `effective_from`, `effective_to`
- 理由: 分红口径需要审计历史，不能压缩进 `SysProjectMember.weight`。

### 3.3 成本跑批
| Legacy 表 | Spring Boot 承接 | 说明 |
|---|---|---|
| `cost_ledger` | finance cost entry snapshot | 保留原始工时/成本输入，不直接覆盖 `SysProject.cost`。 |
| `fin_cost_ext` | finance cost summary by venture | 作为 venture 级月度汇总 sidecar。 |
| `fin_cost_ledger_batch` | finance cost batch | 记录跑批批次、月份、操作者、状态。 |

建议的最小承接对象：`finance_cost_batch`、`finance_cost_entry`, `finance_cost_summary`
- `ledger_month` 保持 `YYYY-MM`
- `SysProject.cost` 仅保留协同系统展示性预算/成本字段，不承担财务审计主账职责

### 3.4 清算、钱包、分红、调账
| Legacy 表 | Spring Boot 承接 | 说明 |
|---|---|---|
| `fin_venture_clearing` | finance clearing sheet | 与 venture/profile 一对多，记录净利润、亏损结转、清算状态。 |
| `fin_user_wallet` | finance wallet account | 以 `User.userId` 为锚点，不改 `User` 主表。 |
| `fin_dividend_sheet` | finance dividend sheet | 保留 prepare/confirm 双阶段，不并入 wallet 表。 |
| `fin_cash_flow_entry` | finance wallet transaction | 作为钱包流水与调账审计统一台账。 |
| 手工调账记录 | finance adjustment log | 可以和 wallet transaction 共享主表、分离业务明细，也可单独 sidecar。 |
| `fin_bank_balance_entry` | finance bank balance snapshot | 单独快照表，避免混入钱包流水。 |

建议的最小承接对象：`finance_wallet_account`、`finance_wallet_transaction`、`finance_dividend_sheet`、`finance_clearing_sheet`、`finance_bank_balance_snapshot`

### 3.5 中间件资产与分润
| Legacy 表 | Spring Boot 承接 | 说明 |
|---|---|---|
| `fin_middleware_registry` | `MiddlewareAsset` + finance registry sidecar | 现有 `MiddlewareAsset` 可承接资产主信息；若 legacy 还有财务登记字段，则补一个 finance sidecar。 |
| `fin_middleware_usage` | finance middleware usage ledger | 记录调用方 venture、开发方 venture、royalty fee、结算批次。 |
| 中间件分润比例 | `MiddlewareRoyaltyRoster` | 现有对象已适合保存长期权属比例。 |

建议的最小承接对象：`finance_middleware_usage`
- 关键字段: `middleware_id`, `caller_project_id`, `source_project_id`, `royalty_fee`, `ledger_month`, `clearing_sheet_id`
- 理由: `MiddlewareRoyaltyRoster` 只解决比例，不解决每次使用计费与结算归档。

## 4. 字段与类型建议
| 主题 | 建议 |
|---|---|
| venture 主关联 | finance 表统一持有 `project_id`，必要时附带 `legacy_venture_id` |
| 金额精度 | 统一 `DECIMAL(15,2)`；比例字段单独用 `DECIMAL(6,4)` |
| 账期 | `ledger_month` 固定 `VARCHAR(7)`，格式 `YYYY-MM` |
| 审计时间 | 统一落库时间戳，接口序列化使用 ISO-8601 UTC |
| 状态兼容 | 迁移初期同时保存 Spring 枚举值和 legacy 原始状态文本 |
| 删除策略 | 财务台账默认逻辑保留，不做物理删除设计 |

## 5. 与现有实体的边界
- `SysProject`: 继续承载统一项目身份、流程类型、展示性预算字段；不吸收钱包余额、分红状态、清算批次字段。
- `SysProjectMember`: 继续承载协作成员；不兼任股权、收益分配、钱包账户。
- `User`: 继续作为人员主数据；钱包余额与流水放 finance 聚合。
- `MiddlewareAsset`: 作为中间件资产主实体复用；财务计费与使用流水另建台账。

## 6. 迁移顺序建议
1. 先建立 `finance_venture_profile`，完成 `business_venture` 到 `SysProject` 的桥接。
2. 再迁移 `finance_venture_equity`、`finance_wallet_account`、`finance_wallet_transaction`，先打通人员收益链路。
3. 随后迁移 `finance_cost_batch`、`finance_clearing_sheet`、`finance_dividend_sheet`。
4. 最后补齐 `finance_middleware_usage` 与银行快照类表。

## 7. 风险清单
- `business_venture` 与 `SysProject` 当前主键类型不同，必须通过 bridge/sidecar 过渡，不能直接主键复用。
- `status_stage` 无法无损映射到现有三流状态，必须保留 legacy 原值作为迁移审计字段。
- `SysProject.cost`/`budget` 已存在，但粒度不足以替代月度成本批次和审计流水。
- 当前 MCP 只读查询 `information_schema` 未返回目标 legacy 表元数据，后续需要在正确库连接下再次验证字段细节后再落 DDL。

## 8. 本轮只读验证记录
- `information_schema.tables` 查询 `business_venture`、`venture_cap_table`、`cost_ledger`、`fin_*` 未返回结果。
- 该结果仅说明当前 MCP 连接未暴露这些表，不能据此否定 legacy 表存在性。
