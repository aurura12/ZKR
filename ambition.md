# 财务控制台开发需求文档

## 1. 产品目标
- 以当前 `lab-erp-demo` 作为新前端承载，以当前 `erp-backend` 作为新后端承载，重新开发一版 ERP。
- 

## 2. 系统范围
- 本系统定位为财务控制台。
- 登录不是本期核心范围，当前核心范围是财务业务模块重建。
- 系统采用后台控制台结构，包含侧边导航、顶部栏和主内容区。
- 所有需求以业务含义、数据口径和交互结果为准，

## 3. 一级模块清单
- 中央控制台
- 混合核算财务报表
- 手工调账对账
- 成本跑批引擎
- 自动化结算中心
- 分红中心
- 钱包余额与流水
- AI 智能数据平台

## 4. 页面结构与导航需求
- 页面采用后台控制台布局：左侧导航 + 顶部栏 + 主内容区。
- 左侧导航按模块切换页面内容。
- 顶部栏包含全局搜索入口和当前用户信息展示。
- 主内容区按模块切换不同 `section` 页面。
- 页面切换时按需加载模块数据，而不是一次性加载所有业务数据。

## 5. 模块需求明细

### 5.1 中央控制台
目标：提供财务总览、风险总览和现金流摘要。

页面内容：
- 4 张指标卡片：累计净利润、公司兜底亏损、全员钱包余额、中间件版权费。
- 风险创投单元表格。
- 现金流量摘要卡片。

核心指标：
- 净利润
- 亏损金额
- 钱包总余额
- 中间件版权费
- 利润率
- 亏损率
- 活跃钱包账户数
- 已清算创投单元数

交互需求：
- 页面初始加载时自动刷新。
- 风险表支持按“亏损风险”视角拉取高风险创投单元。

依赖接口：
- `GET /api/finance/statements`
- `POST /api/rag/query`

### 5.2 混合核算财务报表
目标：统一展示利润表、资产负债表、现金流量表。

页面内容：
- 利润表：总营业收入、总杠杆成本、1% 中间件抽成、净利润、公司兜底亏损。
- 资产负债表：银行存款快照、内部应付款、净资产。
- 现金流量表：流入、流出、净现金流。
- 银行余额快照录入表单。

交互需求：
- 支持手工录入银行余额快照。
- 展示账实差异与账实是否相符。

核心规则：
- 利润率 = `total_profit / total_revenue`。
- 亏损率 = `total_loss / total_revenue`。
- 理论余额 = 现金流净额 + 调账净额。
- 账实差异 = 银行实际快照 - 理论余额。

依赖接口：
- `GET /api/finance/statements`
- `POST /api/finance/bank_balance`

### 5.3 手工调账对账
目标：记录总账调账日志并支持审计回溯。

页面内容：
- 调账录入表单：科目、方向、金额、备注、操作人、参考单号。
- 调账日志表格。

核心规则：
- 方向仅允许 `DEBIT` / `CREDIT`。
- 科目不能为空。
- 金额必须大于 0。
- 调账记录写入后纳入账实差异计算。

依赖接口：
- `POST /api/adjustment/create`
- `GET /api/adjustment/list`

### 5.4 成本跑批引擎
目标：按账期将工时成本批量结转为创投单元结算成本。

页面内容：
- 跑批账期输入。
- 跑批执行结果展示。
- 按创投单元预览成本明细。

核心规则：
- 标准工时固定为 `160` 小时。
- 杠杆倍数固定为 `2.0`。
- 基础成本 = `base_monthly_cost * (hours_spent / 160)`。
- 最终结算成本 = `基础成本 * 2.0`。
- 跑批结果写入批量成本表。

依赖接口：
- `POST /api/batch/run_cost`
- `GET /api/batch/preview/{ventureId}`

### 5.5 自动化结算中心
目标：按创投单元完成收入确认、成本结转、利润计算和中间件版权分润。

页面内容：
- 创投单元选择。
- 最终营收录入。
- 清算结果卡片。
- 已清算创投单元列表。

核心规则：
- 总成本来自成本跑批表汇总。
- 每调用一个中间件，按创投单元最终营收抽取 `1%` 版权费。
- 总版权费 = `最终营收 * 1% * 中间件调用数量`。
- 净利润 = `最终营收 - 总成本 - 总版权费`。
- 若净利润 < 0，则：
  - 公司兜底亏损 = 绝对值
  - 净利润归零
- 清算完成后需要：
  - 写创投单元清算结果
  - 写中间件使用记录
  - 写钱包流水（ROYALTY）
  - 更新开发者钱包余额
  - 写现金流入/流出记录

依赖接口：
- `GET /api/clearing/ventures`
- `POST /api/clearing/execute`

### 5.6 分红中心
目标：基于创投单元净利润生成分红单，并在确认后入账到钱包。

页面内容：
- 创投单元选择。
- 分红准备结果。
- 分红确认结果。
- 分红单列表与状态筛选。

核心规则：
- 只有已清算且净利润大于 0 的创投单元才允许准备分红。
- 分红比例来自 `venture_cap_table`。
- 同一创投单元若已有 `PENDING` 分红单，不可重复生成。
- 分红准备时最后一位持有人吸收尾差，确保总额精确配平。
- 分红确认前要做银行余额断言：
  - 待支付总额 > 当前银行余额时，拒绝确认。
- 分红确认后需要：
  - 将分红单从 `PENDING` 改为 `CONFIRMED`
  - 写钱包流水（DIVIDEND）
  - 更新钱包余额
  - 写现金流出记录
  - 写新的银行余额快照

依赖接口：
- `POST /api/dividend/prepare`
- `GET /api/dividend/list`
- `POST /api/dividend/confirm`

### 5.7 钱包余额与流水
目标：查看人员钱包余额、累计收益和流水明细。

页面内容：
- 钱包余额表格。
- 钱包流水表格。

核心展示字段：
- 人员姓名
- 岗位角色
- 当前余额
- 累计分红
- 累计版权收益
- 更新时间
- 流水类型
- 业务来源表和来源 ID
- 备注

流水类型：
- `DIVIDEND`
- `ROYALTY`
- `WITHDRAWAL`
- `ADJUSTMENT`

依赖接口：
- `GET /api/finance/wallets`
- `GET /api/finance/transactions`

### 5.8 AI 智能数据平台
目标：支持基于 ERP 财务数据的智能问答。

页面内容：
- 问答输入框。
- 对话历史区。
- 快捷问题入口。

核心规则：
- 优先走真实大模型对话接口。
- 若 AI 接口失败，则降级到 RAG 查询接口。
- AI 上下文不是自由聊天，而是基于 ERP 财务快照构建。

上下文数据视角：
- 创投单元清算数据
- 全员钱包数据
- 成本跑批汇总
- 分红结算单
- 中间件版权费
- 现金流汇总
- 最近调账记录
- 最新银行余额快照

依赖接口：
- `POST /api/ai/chat`
- `POST /api/rag/query`
- `POST /api/rag/push`

## 6. 核心业务实体清单
- `business_venture`：创投单元主表，含类型、等级、状态。
- `fin_venture_ext`：创投单元扩展信息，主要承载显示名称。
- `cost_ledger`：原始工时记录。
- `fin_cost_ext`：工时账期扩展。
- `fin_cost_ledger_batch`：跑批后的成本结算表。
- `fin_venture_clearing`：创投单元清算结果表。
- `fin_middleware_registry`：中间件注册表。
- `fin_venture_middleware_call`：创投单元与中间件调用关系。
- `fin_middleware_usage`：中间件版权费落账明细。
- `venture_cap_table`：创投单元分红比例表。
- `fin_dividend_sheet`：分红结算单。
- `fin_user_wallet`：用户虚拟钱包。
- `fin_wallet_transaction`：钱包流水。
- `fin_cash_flow_entry`：现金流记录。
- `fin_bank_balance_entry`：银行余额快照。
- `fin_manual_adjustment`：手工调账日志。
- `sys_user`：用户主数据，至少含真实姓名、岗位、基础月成本。

## 7. 关键状态与枚举
- 调账方向：`DEBIT`、`CREDIT`
- 分红单状态：`PENDING`、`CONFIRMED`
- 钱包流水类型：`DIVIDEND`、`ROYALTY`、`WITHDRAWAL`、`ADJUSTMENT`
- 现金流方向：`IN`、`OUT`

## 8. 需要优先产品化沉淀的规则
- 创投单元清算公式
- 成本跑批公式
- 中间件版权费抽成规则
- 分红配平尾差规则
- 分红确认前的头寸断言规则
- 账实差异与对账规则
- 钱包余额增长路径
- AI 财务问答的数据上下文边界

## 9. 接口语义清单
- `GET /api/finance/statements`：返回三表合一财务总览与趋势指标。
- `GET /api/finance/wallets`：返回全员钱包余额概览。
- `GET /api/finance/transactions`：返回钱包流水审计明细。
- `POST /api/finance/bank_balance`：录入银行余额快照。
- `POST /api/batch/run_cost`：执行按账期成本跑批。
- `GET /api/batch/preview/{ventureId}`：预览创投单元成本明细。
- `GET /api/clearing/ventures`：获取创投单元列表及清算状态。
- `POST /api/clearing/execute`：执行创投单元清算。
- `POST /api/dividend/prepare`：生成待确认分红单。
- `GET /api/dividend/list`：查询分红单列表。
- `POST /api/dividend/confirm`：确认分红并入账。
- `POST /api/adjustment/create`：创建调账记录。
- `GET /api/adjustment/list`：查询调账记录。
- `POST /api/rag/query`：执行财务语义检索。
- `POST /api/rag/push`：重建向量索引。
- `POST /api/ai/chat`：执行 AI 财务问答。

## 10. 重开发建议优先级
- P0：财务报表、清算、分红、钱包、调账
- P1：成本跑批、创投单元列表与看板联动
- P2：AI/RAG 能力、搜索体验、交互优化

## 11. 删除旧代码前必须保留的文档化产物
- 模块清单
- 实体关系图
- 字段字典
- 核心公式说明
- 状态流转说明
- 接口需求文档
- 角色与权限矩阵
- 页面原型与交互说明

## 12. 当前结论
- 可以删旧代码，但不能先删规则说明。
- 在删除历史工程之前，必须先把本文件补全为可执行的产品需求和领域规则文档。

## 13. 字段字典（第一版）

### 13.1 创投单元与基础主数据

#### `business_venture`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 创投单元 ID | integer | 主键，用于清算、分红、成本、AI 查询关联 |
| `type` | 创投单元类型 | varchar | 前端用于区分展示图标和类型标签 |
| `tier_level` | 等级 | varchar | 前端展示等级信息 |
| `status_stage` | 当前阶段 | varchar | 前端展示状态标签、清算状态说明 |

#### `fin_venture_ext`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `venture_id` | 创投单元 ID | integer | 对应 `business_venture.id` |
| `fin_name` | 创投单元名称 | varchar | 前端展示名称 |

#### `sys_user`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 用户 ID | integer | 所有钱包、分红、工时、分润的核心关联键 |
| `real_name` | 真实姓名 | varchar | 前端展示人员名称 |
| `job_role` | 岗位角色 | varchar | 前端展示岗位、AI 上下文维度 |
| `base_monthly_cost` | 基础月成本 | numeric | 跑批基础计算口径 |

### 13.2 成本跑批相关

#### `cost_ledger`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 工时记录 ID | integer | 原始工时记录主键 |
| `venture_id` | 创投单元 ID | integer | 标识工时归属的创投单元 |
| `user_id` | 用户 ID | integer | 标识工时归属人员 |
| `hours_spent` | 工时数 | numeric | 跑批核心输入值 |

#### `fin_cost_ext`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `cost_ledger_id` | 工时记录 ID | integer | 对应 `cost_ledger.id` |
| `fin_ledger_month` | 账期 | varchar | 用于按月跑批 |

#### `fin_cost_ledger_batch`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 跑批记录 ID | integer | 跑批结果主键 |
| `venture_id` | 创投单元 ID | integer | 成本归属单元 |
| `user_id` | 用户 ID | integer | 成本归属人员 |
| `cost_type` | 成本类型 | varchar | 当前要求使用 `SALARY_LEVER` |
| `base_amount` | 基础成本 | numeric | 未乘杠杆前的成本 |
| `multiplier` | 杠杆倍数 | numeric | 当前要求固定为 `2.0` |
| `final_settlement_cost` | 最终结算成本 | numeric | 清算使用的最终成本 |
| `is_fixed_stipend` | 是否固定津贴 | boolean | 当前要求默认 `FALSE` |
| `batch_date` | 跑批日期 | date | 跑批执行日期 |
| `fin_ledger_month` | 账期 | varchar | 跑批所属账期 |

### 13.3 清算与中间件分润相关

#### `fin_venture_clearing`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 清算记录 ID | integer | 清算主键 |
| `venture_id` | 创投单元 ID | integer | 每个单元一条清算结果 |
| `final_revenue` | 最终营收 | numeric | 人工输入的清算收入 |
| `total_leveraged_cost` | 总杠杆成本 | numeric | 来自跑批成本汇总 |
| `net_profit` | 净利润 | numeric | 扣除成本和版权费后的净利润 |
| `loss_transferred_to_company` | 公司兜底亏损 | numeric | 净利润为负时转移到公司承担 |
| `fin_middleware_fee` | 中间件版权费总额 | numeric | 清算时计算的总版权费 |
| `fin_cleared_at` | 清算时间 | timestamp | 清算完成时间 |

#### `fin_middleware_registry`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 中间件 ID | integer | 中间件主键 |
| `fin_name` | 中间件名称 | varchar | 用于清算结果和收益文案 |
| `fin_developer_user_id` | 开发者用户 ID | integer | 中间件收益归属人 |
| `fin_tier_level` | 中间件等级 | varchar | 当前仅作元信息 |

#### `fin_venture_middleware_call`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 调用关系 ID | integer | 调用关系主键 |
| `venture_id` | 创投单元 ID | integer | 哪个单元调用了中间件 |
| `middleware_id` | 中间件 ID | integer | 被调用中间件 |

#### `fin_middleware_usage`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 使用明细 ID | integer | 分润落账明细 |
| `middleware_id` | 中间件 ID | integer | 被分润的中间件 |
| `developer_user_id` | 开发者用户 ID | integer | 收益归属用户 |
| `caller_venture_id` | 调用方创投单元 ID | integer | 发起调用的创投单元 |
| `caller_total_revenue` | 调用方总营收 | numeric | 用于还原版权费计算依据 |
| `royalty_fee` | 版权费 | numeric | 对应本次分润金额 |
| `fin_created_at` | 生成时间 | timestamp | 分润落账时间 |

### 13.4 分红相关

#### `venture_cap_table`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 股权记录 ID | integer | 主键 |
| `venture_id` | 创投单元 ID | integer | 分红所属创投单元 |
| `user_id` | 用户 ID | integer | 分红持有人 |
| `dividend_ratio` | 分红比例 | numeric | 分红准备使用的比例 |

#### `fin_dividend_sheet`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 分红单 ID | integer | 分红结算单主键 |
| `venture_id` | 创投单元 ID | integer | 分红所属单元 |
| `user_id` | 用户 ID | integer | 收款人 |
| `fin_dividend_ratio` | 分红比例快照 | numeric | 生成分红单时的比例快照 |
| `fin_net_profit_snapshot` | 净利润快照 | numeric | 生成分红单时的净利润快照 |
| `fin_amount` | 分红金额 | numeric | 应分金额 |
| `fin_status` | 分红单状态 | varchar | `PENDING` / `CONFIRMED` |
| `fin_confirmed_at` | 确认时间 | timestamp | 分红入账时间 |
| `fin_created_at` | 创建时间 | timestamp | 草单生成时间 |

### 13.5 钱包与流水相关

#### `fin_user_wallet`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `user_id` | 用户 ID | integer | 钱包主键/关联键 |
| `balance` | 当前余额 | numeric | 当前虚拟钱包余额 |
| `total_dividend_earned` | 累计分红 | numeric | 历史分红累计 |
| `fin_total_royalty_earned` | 累计版权收益 | numeric | 历史版权费累计 |
| `fin_updated_at` | 更新时间 | timestamp | 钱包最近更新时间 |

#### `fin_wallet_transaction`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 流水 ID | integer | 流水主键 |
| `wallet_id` | 钱包归属 ID | integer | 表示流水归属的钱包主体 |
| `trans_type` | 流水类型 | varchar | 如 `DIVIDEND`、`ROYALTY` |
| `amount` | 金额 | numeric | 本次流水金额 |
| `ref_biz_id` | 业务来源 ID | integer | 来源业务记录 ID |
| `fin_ref_biz_table` | 来源业务表 | varchar | 来源业务表名 |
| `fin_remark` | 备注 | varchar | 前端流水说明文案 |
| `created_at` | 创建时间 | timestamp | 流水发生时间 |

### 13.6 现金流、银行快照、调账相关

#### `fin_cash_flow_entry`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 现金流记录 ID | integer | 主键 |
| `fin_direction` | 方向 | varchar | `IN` / `OUT` |
| `fin_subject` | 科目 | varchar | 现金流科目名称 |
| `fin_amount` | 金额 | numeric | 现金流金额 |
| `fin_ref_biz_id` | 来源业务 ID | integer | 来源业务记录 |
| `fin_ref_biz_table` | 来源业务表 | varchar | 来源业务表名 |
| `fin_remark` | 备注 | varchar | 补充说明 |
| `fin_flow_date` | 业务日期 | date | 现金流业务日期 |
| `fin_created_at` | 创建时间 | timestamp | 创建时间 |

#### `fin_bank_balance_entry`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `fin_id` | 快照 ID | integer | 主键 |
| `fin_balance` | 银行余额 | numeric | 银行真实余额快照 |
| `fin_entry_date` | 录入日期 | date | 业务日期 |
| `fin_operator` | 录入人 | varchar | 手工录入人或系统 |
| `fin_remark` | 备注 | varchar | 快照说明 |
| `fin_created_at` | 创建时间 | timestamp | 快照生成时间 |

#### `fin_manual_adjustment`
| 字段 | 含义 | 类型 | 说明 |
|---|---|---|---|
| `id` | 调账记录 ID | integer | 主键 |
| `fin_subject` | 调账科目 | varchar | 调账目标科目 |
| `fin_direction` | 借贷方向 | varchar | `DEBIT` / `CREDIT` |
| `fin_amount` | 金额 | numeric | 调账金额 |
| `fin_remark` | 备注 | varchar | 调账原因 |
| `fin_operator` | 操作人 | varchar | 录入人 |
| `fin_ref_doc_no` | 参考单号 | varchar | 可选参考凭证 |
| `fin_created_at` | 创建时间 | timestamp | 调账时间 |

## 14. 核心公式说明（第一版）

### 14.1 成本跑批公式
- 标准工时：`160`
- 杠杆倍数：`2.0`
- 基础成本：`base_monthly_cost * (hours_spent / 160)`
- 最终结算成本：`基础成本 * 2.0`

### 14.2 清算公式
- 创投单元总成本：`SUM(fin_cost_ledger_batch.final_settlement_cost)`
- 单个中间件版权费：`final_revenue * 0.01`
- 总版权费：`单个中间件版权费 * 中间件调用数量`
- 净利润：`final_revenue - total_cost - total_middleware_fee`
- 若净利润 < 0：
  - `loss_transferred_to_company = abs(net_profit)`
  - `net_profit = 0`

### 14.3 分红准备公式
- 分红前提：`net_profit > 0`
- 单个持有人分红金额：`net_profit * dividend_ratio`
- 尾差处理：最后一位持有人吸收四舍五入尾差，确保总额等于净利润快照

### 14.4 分红确认规则
- 待支付总额：`SUM(fin_dividend_sheet.fin_amount where status=PENDING)`
- 若 `待支付总额 > 当前银行余额快照`，则拒绝分红确认
- 确认成功后：
  - 钱包余额增加
  - 累计分红增加
  - 现金流写 `OUT`
  - 银行余额快照自动扣减

### 14.5 账实对账公式
- 现金流净额：`cash_inflow - cash_outflow`
- 调账净额：`DEBIT 合计 - CREDIT 合计`
- 理论余额：`现金流净额 + 调账净额`
- 账实差异：`银行余额快照 - 理论余额`
- 若差异为 `0`，则视为账实相符

### 14.6 钱包累计口径
- 当前余额：钱包实时余额
- 累计分红：历史所有 `DIVIDEND` 入账累计
- 累计版权收益：历史所有 `ROYALTY` 入账累计

## 15. 页面清单（细化版）

### 15.1 页面总览
| 页面 | 目标 | 关键数据 | 关键动作 | 优先级 |
|---|---|---|---|---|
| 中央控制台 | 财务总览与风险总览 | 净利润、亏损、钱包、版权费 | 查看看板、查看风险表 | P0 |
| 财务报表页 | 三表合一展示 | 利润表、资产负债表、现金流 | 录入银行余额快照 | P0 |
| 调账页 | 手工调账与审计追踪 | 调账记录 | 新建调账、查看日志 | P0 |
| 跑批页 | 成本跑批执行与预览 | 账期、跑批记录、成本明细 | 执行跑批、预览成本 | P1 |
| 清算页 | 创投单元清算 | 创投单元、营收、成本、利润 | 执行清算 | P0 |
| 分红页 | 分红草单与确认 | 分红单、状态、金额 | 准备分红、确认分红、筛选列表 | P0 |
| 钱包页 | 钱包余额与流水审计 | 钱包余额、分红、版权收益、流水 | 查看余额、查看流水 | P0 |
| AI 页 | 智能财务问答 | 对话历史、财务上下文 | 发起问答、走 AI 或 RAG | P2 |

### 15.2 页面字段提炼

#### 中央控制台
- 指标字段：`total_profit`、`total_loss`、`total_middleware_fee`、`wallet_count`、`cleared_count`
- 表格字段：创投单元 ID、名称、净利润、兜底亏损

#### 财务报表页
- 银行余额录入：`balance`、`operator`
- 报表展示字段：收入、成本、版权费、净利润、亏损、资产、负债、净资产、流入、流出、净额、账实差异

#### 调账页
- 输入字段：`subject`、`direction`、`amount`、`remark`、`operator`、`ref_doc_no`
- 列表字段：编号、科目、方向、金额、参考单号、操作人、创建时间

#### 跑批页
- 输入字段：`ledger_month`
- 预览字段：人员、成本类型、基础成本、倍数、最终成本、账期/日期

#### 清算页
- 输入字段：`venture_id`、`final_revenue`
- 结果字段：收入、总成本、净利润、兜底亏损、中间件费、开发者入账明细

#### 分红页
- 输入字段：`venture_id`
- 列表字段：分红单 ID、创投单元、持有人、比例、金额、状态、确认时间

#### 钱包页
- 钱包字段：姓名、岗位、余额、累计分红、累计版权收益、更新时间
- 流水字段：流水 ID、姓名、类型、金额、来源表、来源 ID、备注、时间

#### AI 页
- 输入字段：`message`
- 输出字段：AI 回复内容、错误信息、流式状态

## 16. 接口清单（细化版）

| 接口 | 方法 | 输入 | 输出 | 业务说明 | 优先级 |
|---|---|---|---|---|---|
| `/api/finance/statements` | GET | 无 | 三表、趋势指标、对账结果 | 财务总览核心接口 | P0 |
| `/api/finance/wallets` | GET | 无 | 钱包列表 | 人员收益与余额总览 | P0 |
| `/api/finance/transactions` | GET | `limit` | 流水列表 | 审计流水查询 | P0 |
| `/api/finance/bank_balance` | POST | `balance`,`operator`,`remark` | 成功消息 | 手工录入银行快照 | P0 |
| `/api/batch/run_cost` | POST | `fin_ledger_month` | 账期、生成记录数、消息 | 执行成本跑批 | P1 |
| `/api/batch/preview/{ventureId}` | GET | `ventureId` | 成本明细、总成本 | 预览创投单元成本 | P1 |
| `/api/clearing/ventures` | GET | 无 | 创投单元列表 | 清算页下拉和已清算看板 | P0 |
| `/api/clearing/execute` | POST | `venture_id`,`final_revenue` | 收入、成本、净利润、版权费、开发者入账明细 | 执行清算闭环 | P0 |
| `/api/dividend/prepare` | POST | `venture_id` | 分红草单列表 | 生成待确认分红单 | P0 |
| `/api/dividend/list` | GET | `status` | 分红单列表 | 分红单筛选与查看 | P0 |
| `/api/dividend/confirm` | POST | `venture_id` | 支付总额、确认人数、详情 | 确认分红并入账 | P0 |
| `/api/adjustment/create` | POST | `subject`,`direction`,`amount`,`remark`,`operator`,`ref_doc_no` | 成功消息 | 创建调账记录 | P0 |
| `/api/adjustment/list` | GET | 无 | 调账记录列表 | 审计查看 | P0 |
| `/api/rag/query` | POST | `prompt` | `answer`,`data_rows` | 财务语义检索 | P2 |
| `/api/rag/push` | POST | 无 | 重建结果 | 向量索引重建 | P2 |
| `/api/ai/chat` | POST / SSE | `message`,`clear_history` | 流式回复 | 财务智能问答 | P2 |

## 17. 直接可交给产品和开发的下一步
- 基于本文件补一版页面原型。
- 基于字段字典补一版领域模型草图。
- 基于接口清单补一版前后端联调契约。
- 基于核心公式补一版财务口径确认文档。

## 18. 角色权限矩阵

### 18.1 角色定义
- 创始人/超级管理员
- 财务管理员
- 业务负责人
- 中间件开发者
- 普通查看者

### 18.2 权限矩阵
| 模块/动作 | 创始人/超级管理员 | 财务管理员 | 业务负责人 | 中间件开发者 | 普通查看者 |
|---|---|---|---|---|---|
| 查看中央控制台 | 是 | 是 | 是 | 是 | 是 |
| 查看财务报表 | 是 | 是 | 是 | 否 | 只读受限 |
| 录入银行余额快照 | 是 | 是 | 否 | 否 | 否 |
| 执行成本跑批 | 是 | 是 | 否 | 否 | 否 |
| 预览跑批结果 | 是 | 是 | 是 | 否 | 否 |
| 查看创投单元清算列表 | 是 | 是 | 是 | 否 | 只读受限 |
| 执行清算 | 是 | 是 | 否 | 否 | 否 |
| 准备分红 | 是 | 是 | 否 | 否 | 否 |
| 确认分红 | 是 | 是 | 否 | 否 | 否 |
| 查看分红单 | 是 | 是 | 是 | 查看本人相关 | 只读受限 |
| 创建调账 | 是 | 是 | 否 | 否 | 否 |
| 查看调账日志 | 是 | 是 | 是 | 否 | 否 |
| 查看钱包余额总览 | 是 | 是 | 是 | 查看本人相关 | 否 |
| 查看钱包流水 | 是 | 是 | 是 | 查看本人相关 | 否 |
| 使用 AI 财务问答 | 是 | 是 | 是 | 只读摘要 | 否 |

### 18.3 权限补充说明
- 分红确认、银行余额录入、调账录入属于高风险财务动作，必须保留二次确认。
- 中间件开发者默认只应看到与自己收益相关的数据，不应默认可见全员收益和全量财务报表。
- 普通查看者应仅能查看经过脱敏和汇总后的有限数据，不应直接看到可执行操作入口。

## 19. 状态流转说明（第一版）

### 19.1 创投单元状态
说明：`business_venture.status_stage` 需要在重开发中形成明确状态机定义。

建议流转：
- 立项中
- 研发中
- 运营中
- 待清算
- 已清算
- 已分红完成

建议规则：
- 当创投单元已有有效收入并准备执行清算时，应进入“待清算”。
- 清算成功后进入“已清算”。
- 若该单元分红流程全部确认完成，可进入“已分红完成”。

### 19.2 分红单状态流转
- 初始：`PENDING`
- 确认后：`CONFIRMED`

流转规则：
- 仅当创投单元已经完成清算且净利润大于 0 时，才能生成 `PENDING` 分红单。
- `PENDING` 分红单确认时必须校验银行余额是否充足。
- 确认后不可再次确认；若未来需要撤销，应设计独立“冲正/作废”机制，不直接复用 `CONFIRMED`。

### 19.3 钱包流水状态理解
说明：钱包流水按业务类型区分，不单独设计状态字段。

流水类型：
- `DIVIDEND`：分红入账
- `ROYALTY`：版权分润入账
- `WITHDRAWAL`：提款
- `ADJUSTMENT`：调账

建议规则：
- 每笔流水必须保留来源业务表、来源业务 ID、备注、创建时间。
- 钱包余额变化必须始终能追溯到一条或多条流水记录。

### 19.4 财务对账状态
建议产品化状态：
- 未录入银行快照
- 已录入待核对
- 账实相符
- 账实不符

判定规则：
- 未录入银行快照：无快照数据
- 已录入待核对：已有快照但未完成对账计算
- 账实相符：差异为 0
- 账实不符：差异不为 0

## 20. 前端开发任务单（第一版）

### 20.1 P0 任务
- 搭建财务控制台基础布局：侧边导航、顶部栏、主内容区。
- 重建中央控制台页面。
- 重建财务报表页面。
- 重建清算页面。
- 重建分红页面。
- 重建钱包余额与流水页面。
- 重建调账页面。
- 完成页面之间的基础路由和数据加载时机控制。

### 20.2 P1 任务
- 重建成本跑批页面与预览弹层/结果区。
- 对接创投单元下拉、状态标签、表格结果展示。
- 优化报表页的对账提示、空状态、错误状态。

### 20.3 P2 任务
- 重建 AI 智能数据平台页面。
- 增加快捷问题、流式渲染、降级提示。
- 优化移动端兼容和视觉层次。

### 20.4 前端统一规范任务
- 梳理金额格式化、日期格式化、状态标签组件。
- 统一结果卡、表格、表单、确认弹窗组件。
- 统一高风险操作的二次确认交互。

## 21. 后端开发任务单（第一版）

### 21.1 P0 任务
- 设计财务报表聚合查询接口。
- 设计清算执行接口。
- 设计分红准备/确认/查询接口。
- 设计钱包余额与流水查询接口。
- 设计调账创建与查询接口。
- 明确银行快照录入接口。

### 21.2 P1 任务
- 设计成本跑批执行与预览接口。
- 设计创投单元列表接口。
- 梳理领域对象：创投单元、清算、分红单、钱包、流水、调账、银行快照。

### 21.3 P2 任务
- 设计 AI 财务问答接口。
- 设计 RAG 重建与语义检索接口。
- 设计财务上下文构建服务。

### 21.4 后端统一规范任务
- 定义统一响应结构。
- 定义统一错误码和错误提示。
- 对清算、分红确认、调账、银行快照等高风险动作加审计日志。
- 对高风险动作补充事务边界与幂等约束。

## 22. 继续补全文档的建议顺序
- 先补页面原型草图说明。
- 再补角色权限最终确认版。
- 再补领域模型和接口 DTO 设计。
- 最后再进入实际开发。

## 23. 本轮开发归档记录（2026-03-12）

### 23.1 本轮已完成范围
- 本轮已按 `erp-backend` + `lab-erp-demo` 双目录完成财务控制台重构主线开发。
- 已完成从 P0 到 P2 的核心实现，不再停留在需求和设计阶段。
- 已补充设计文档：`docs/superpowers/specs/2026-03-12-finance-console-rebuild-design.md`。
- 已补充实施计划：`docs/superpowers/plans/2026-03-12-finance-console-rebuild-plan.md`。

### 23.2 后端已完成事项（`erp-backend`）
- 完成统一财务报表聚合与对账结果输出：指标卡、利润表、资产负债表、现金流、风险表、账实差异。
- 完成银行余额快照录入接口与快照持久化服务。
- 完成统一财务响应结构与鉴权收口，核心响应字段统一为 `success`、`message`、`data`、`traceId`。
- 完成调账创建/查询链路，支持 `DEBIT` / `CREDIT` 校验与审计列表输出。
- 完成清算链路：
  - 创投单元列表与状态输出
  - 清算执行
  - 亏损兜底归零
  - 后端主导版权费计算
  - 清算幂等保护
- 完成分红链路：
  - 分红准备
  - 分红列表查询
  - 银行余额断言
  - 分红确认
  - 重复确认幂等保护
- 完成钱包余额和流水审计查询，补齐角色、来源表、来源业务 ID、备注、时间等展示字段。
- 完成成本跑批与预览：
  - 固化 `160` 小时标准工时
  - 固化 `2.0` 杠杆倍数
  - 同账期幂等与运行中批次保护
  - 账期感知预览
- 完成 AI / RAG 财务智能链路：
  - AI first
  - RAG fallback
  - 只读上下文约束
  - fallback 元数据输出
  - 现金流纳入上下文

### 23.3 前端已完成事项（`lab-erp-demo`）
- 完成 finance adapter / store / API wrapper 对齐，前端已适配后端 snake_case 与 finance envelope。
- 完成财务总览页：
  - KPI 卡片
  - 风险表
  - 现金流摘要
  - 对账结果
  - 银行快照录入
- 完成钱包页：
  - 钱包汇总
  - 钱包流水审计表
  - 严格使用后端 summary，不再前端自行汇总
- 完成概览到清算页、跑批页的导航联动。
- 完成调账页：创建、列表、后端错误展示、二次确认弹窗。
- 完成清算页：创投单元选择、执行清算、展示后端返回结果卡片。
- 完成分红页：准备、筛选、确认、二次确认、状态/结果展示。
- 完成跑批页：账期输入、预览、执行、rerun 开关。
- 完成 AI 页：对话输入、渐进式回答展示、fallback 提示。
- 完成 RAG 页：快捷问题、只读检索、结果卡片展示。
- 完成一组复用型财务组件：结果卡、状态标签、空状态、错误状态、确认弹窗。

### 23.4 本轮验证结果
- 后端定向财务测试已通过：`65` 个测试，`0` 失败，`0` 错误。
- 前端 contract tests 已通过：
  - `finance-adapters.test.mjs`
  - `finance-workbench-store.test.mjs`
  - `finance-wallet-ai-store.test.mjs`
  - `finance-overview-links.test.mjs`
- 前端 `npm run build` 已通过。
- 当前仍存在一个非阻塞项：Vite 主包体积较大，会出现 chunk size warning，但不影响当前构建成功。

### 23.5 当前结论更新
- 本文件已经不再只是需求草案，当前已对应到实际代码实现。
- P0、P1、P2 的财务控制台主功能已完成一轮可运行交付。
- 后续工作重心应从“继续补需求”切换为“部署封装、联调验收、文档补完、上线准备”。

## 24. 下一步工作规划

### 24.1 第一优先级：Docker 化部署封装
- 为 `erp-backend` 编写生产可用 `Dockerfile`。
- 为 `lab-erp-demo` 编写前端构建与静态托管 `Dockerfile`。
- 增加根级或部署目录下的 `docker-compose.yml`，统一编排前后端容器。
- 明确容器启动端口、API 代理、环境变量、时区、日志目录等部署参数。
- 验证容器内前后端联通性，确保财务页面可正常访问后端接口。

### 24.2 第二优先级：联调验收
- 针对 P0 场景逐项验收：
  - 银行快照录入
  - 调账
  - 清算
  - 分红准备/确认
  - 钱包余额与流水展示
- 针对 P1 场景验收：
  - 成本跑批
  - 成本预览
  - 看板跳转链路
- 针对 P2 场景验收：
  - AI 对话
  - RAG 快捷问答
  - fallback 提示

### 24.3 第三优先级：上线前补完项
- 补部署说明文档：启动方式、环境变量、容器编排说明、接口联调说明。
- 评估是否需要继续拆分前端共享样式与页面脚手架，降低后续维护成本。
- 评估是否需要补充浏览器级回归测试或容器内集成测试。
- 评估是否需要进一步优化前端包体积与 chunk size warning。

### 24.4 建议执行顺序
- 第一步：完成 Docker 封装。
- 第二步：完成容器内联调。
- 第三步：补部署与验收文档。
- 第四步：准备测试环境或正式环境部署。

## 25. 财务系统账号隔离归档记录（2026-03-13）

### 25.1 本轮目标与结论
- 本轮已完成“财务系统账号域隔离”开发。
- 当前财务系统不再是与 ERP 共用入口语义的混合登录页，而是独立的财务系统登录/注册入口。
- 财务账号与 ERP 账号已形成前后端双重隔离，不再只是前端文案级别的区分。

### 25.2 后端已完成事项（`erp-backend`）
- 新增账号域概念：`AccountDomain`，当前包含 `FINANCE`、`ERP`。
- `sys_user` 对应用户实体已支持持久化 `accountDomain`。
- 增加历史账号域回填逻辑：
  - 默认缺失域账号回填为 `ERP`
  - 可通过配置将已知财务测试账号回填为 `FINANCE`
- 登录接口已按账号域强校验：
  - 财务账号登录财务成功
  - 财务账号登录 ERP 失败
  - ERP 账号登录财务失败
- 注册接口已收紧为当前财务注册路径强制落 `FINANCE` 域。
- JWT、认证主体、`/api/auth/me` 已全链路携带 `accountDomain`。
- 后端接口面已完成账号域隔离：
  - 财务账号不能访问 ERP API
  - ERP 账号不能访问财务 API

### 25.3 前端已完成事项（`lab-erp-demo`）
- 登录页已改为财务系统专用中文界面。
- 已去除 ERP / 旧系统 / 新系统入口切换逻辑。
- 登录成功后固定进入 `/finance/overview`。
- 登录页支持直接展示后端返回的账号域错误提示，不再仅显示泛化失败消息。
- `userStore` 现在固定发送 `domain=FINANCE`：
  - 登录请求固定为财务域
  - 注册请求固定为财务域
- 前端登录态已改为财务专用本地存储键：
  - `finance_token`
  - `finance_userInfo`
- 前端路由守卫已按账号域隔离：
  - 财务账号不能访问 `/manager/*`、`/workspace/*`、`/profile`
  - ERP 账号不能访问 `/finance/*`
- 财务注册角色值已对齐到当前后端允许角色，避免“注册成功但无法进入财务系统”的问题。

### 25.4 本轮验证结果
- 后端认证与域隔离验证已通过：
  - `AuthServiceTest`
  - `AuthControllerTest`
  - `DomainAccessIntegrationTest`
- 前端财务登录隔离验证已通过：
  - `finance-login-view.test.mjs`
  - `finance-auth-store.test.mjs`
  - `finance-route-domain-guard.test.mjs`
- 前端 `npm run build` 已通过。
- 当前仍有一个非阻塞项：Vite chunk size warning 依旧存在，但不影响本轮交付。

### 25.5 当前状态更新
- 财务系统现在已具备独立账号域、独立登录入口、独立前端路由边界、独立后端接口边界。
- 新注册的财务账号不应再进入 ERP。
- 财务系统与 ERP 的隔离已经从“页面入口切换”升级为“账号域与接口权限隔离”。

## 26. 下一步工作规划更新

### 26.1 当前第一优先级：Docker 化部署封装
- 为 `erp-backend` 增加生产可运行 `Dockerfile`。
- 为 `lab-erp-demo` 增加前端构建与静态托管 `Dockerfile`。
- 增加 `docker-compose.yml`，统一编排财务前端、财务后端及必要依赖。
- 将财务系统环境变量显式化，包括：
  - JWT 配置
  - 数据库连接
  - 账号域回填 allowlist 配置
  - 前端 API 地址

### 26.2 Docker 前的联调前置检查
- 确认数据库中历史账号的 `accountDomain` 回填策略是否与真实测试账号名单一致。
- 确认财务注册允许角色与业务预期是否一致。
- 确认财务系统首页 `/finance/overview` 在部署环境中可作为默认落点。

### 26.3 部署后优先验收项
- 财务账号注册 -> 登录 -> 进入 `/finance/overview`
- 财务账号访问 ERP 页面被拦截
- ERP 账号访问财务页面被拦截
- 财务账号调用 ERP API 被拒绝
- ERP 账号调用财务 API 被拒绝

### 26.4 建议新的执行顺序
- 第一步：完成 Docker 封装。
- 第二步：容器内验证账号域隔离与财务登录链路。
- 第三步：验证财务业务主链路（报表、调账、清算、分红、钱包、AI）。
- 第四步：补部署说明和上线说明。

## 27. Docker 母子容器部署归档记录（2026-03-13）

### 27.1 本轮目标与结论
- 本轮已完成财务系统 Docker 化部署资料与资产补齐，部署形态明确为“母容器运维壳 + 子容器业务栈”。
- 当前部署方案已从“需要补 Docker 封装”推进到“已有可交付部署资产 + 新手引导文档 + 待真实运行环境复核”。
- 本轮范围聚焦部署归档与交付准备，不改变既有财务业务范围定义。

### 27.2 母容器 / 子容器模型归档
- 母容器（mother container）定位为个人运维工作壳：挂载个人工作区与运行目录，并通过宿主机 Docker daemon 管理业务栈。
- 子容器（child containers）定位为实际业务部署栈：当前包含 `postgres`、`erp-backend`、`lab-erp-demo`。
- 前端作为默认对外入口，后端保持在 Compose 内部网络中与数据库联通，符合“前端入站、后端内联、数据库持久化”的部署思路。
- 数据持久化当前通过 Docker volumes 承载，母容器仅负责操作与编排，不直接承载业务服务。

### 27.3 本轮新增部署资产
- 根目录已补齐 `Dockerfile.mother`，用于构建个人母容器运维环境。
- 根目录已补齐 `docker-compose.yml`，用于编排 `postgres`、`erp-backend`、`lab-erp-demo` 子栈。
- 根目录已补齐 `.env.example`，用于首次部署时生成实际 `.env` 配置。
- 根目录已补齐 `.dockerignore`，并为前后端分别补齐构建忽略文件，降低镜像构建噪音。
- 已补齐 `mother-shell/run-mother.sh`，用于一致化启动母容器。
- 已补齐新手部署文档：`docs/deployment/docker-mother-child.md`。

### 27.4 首次部署引导状态
- 面向第一次接触 Docker 的使用者，当前已提供从概念说明、母容器构建、母容器进入、子栈 `.env` 准备，到 `docker compose build` / `up -d` / `ps` / `logs` 的完整引导。
- 新手引导文档已明确解释 image、container、volume、compose 这四个基础概念。
- 新手引导文档已覆盖首次部署、常见重启/停止、日志查看、代码更新后的重建流程。
- 当前引导文档已可作为首轮部署操作手册，但本会话内仍未完成一次基于真实可用 Docker daemon 的全流程运行验证。

### 27.5 本轮验证状态与阻塞说明
- 部署资产与归档文档已落地，文件级结果可审阅。
- 已在单独的本机 Docker 验证会话中完成一次本地运行态验证：母容器镜像、前端镜像、后端镜像均已完成构建，`docker-compose` 子栈已成功启动。
- 上述本机验证中已确认前端入口可访问：
  - `/login`
  - `/erp-login`
- 上述本机验证中已确认财务注册/登录链路与 ERP 注册/登录链路均可在容器环境中跑通。
- 上述本机验证中已确认母容器镜像可通过 Docker socket 读取子容器列表，说明“母容器管理子容器”模型可工作。
- 当前仍有一个剩余优化项：匿名访问 `/api/auth/me` 返回的是 `500` 而不是更友好的未登录响应，但这不阻塞当前部署主链路。
- 这些结果属于本机 Docker 环境的验证结论，不等同于远程服务器环境已经复验完成；服务器侧仍需独立执行一次实机部署和验收。

## 28. 部署就绪下一步更新

### 28.1 当前部署-ready 下一步
- 第一优先级：将当前本机已验证的 Docker 方案迁移到目标服务器个人空间，按母容器 + 子容器方式执行第一次真实部署。
- 第二优先级：在服务器环境中复做一次完整冒烟验收，确认网络、端口、镜像拉取、卷挂载与数据持久化均正常。
- 第三优先级：补齐匿名 `/api/auth/me` 的未登录响应行为，避免部署后调试时出现误导性的 `500`。
- 第四优先级：沉淀部署后的业务验收清单，覆盖财务登录、ERP 登录、报表、调账、清算、分红、钱包、AI 主链路。

### 28.2 建议执行顺序更新
- 第一步：在目标服务器创建个人工作区并准备真实 `.env`。
- 第二步：启动母容器并在母容器内执行 `docker compose build / up / ps / logs`。
- 第三步：完成财务与 ERP 双入口登录/注册冒烟验收。
- 第四步：补做业务主链路验收与文档回写。

## 29. 工作区容器生命周期与恢复归档（2026-03-14）

### 29.1 本轮归档目标
- 补齐母容器 `zhangqi` 的生命周期说明，避免服务器日常运维时把“工作区容器状态”误判为“源码或业务数据丢失”。
- 将“停止可恢复、重建可恢复、源码不随容器消失”的操作含义补录进部署文档与归档文档。

### 29.2 工作区容器运维意义更新
- `zhangqi` 是运维入口，不是源码唯一载体；它停掉只会中断当前操作壳，不代表 `/srv/zhangqi/workspace/ZKR` 被删除。
- 当前部署模型的关键点是 `/workspace/ZKR` 绑定到宿主机 `/srv/zhangqi/workspace/ZKR`，所以源码随宿主机目录持久化，而不是随母容器生命周期持久化。
- 因此，`zhangqi` 被停止时，优先执行 `docker start zhangqi` 恢复工作区；不需要先处理子容器源码或重新复制项目。
- 因此，`zhangqi` 被删除后重新创建时，重点是恢复运维壳本身；只要宿主机工作区目录还在，重新运行 `mother-shell/run-mother.sh` 后即可重新挂回原来的源码。

### 29.3 对部署资料的实际影响
- `docs/deployment/docker-mother-child.md` 现已补充 workspace restart and recovery checklist，作为日常停止、重启、重建时的直接操作手册。
- 这次补录使母子容器部署文档从“首次部署指南”扩展为“首次部署 + 生命周期恢复指南”，降低误删容器后的操作焦虑和恢复成本。
- 本轮仅做文档与归档更新，不改变 `Dockerfile.mother`、`mother-shell/run-mother.sh`、`docker-compose.yml` 或业务代码行为。
