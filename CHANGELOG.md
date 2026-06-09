# 变更日志

记录项目的所有代码修改，方便追溯。

---

## 2026-05-29

### 新增 - 腾讯会议集成模块

#### 设计文档

- **docs/superpowers/specs/2026-05-29-tencent-meeting-integration-design.md**
  - 腾讯会议集成设计文档
  - 包含：后端架构、数据库设计、前端页面、实施计划

#### 后端模块结构 (meeting)

**异常类**

- **meeting/exception/TencentMeetingException.java**
  - 腾讯会议 API 调用异常类
  - 包含阶段、消息、错误码、原始响应

**配置类**

- **meeting/config/TencentMeetingConfig.java**
  - 读取腾讯会议配置：SecretId、SecretKey、WebhookSecret、API Base URL

**Token 服务**

- **meeting/service/TencentMeetingTokenService.java**
  - Token 获取与缓存
  - 自动刷新机制（过期前5分钟）
  - 异常重试

**实体类**

- **meeting/entity/MeetingRecord.java**
  - 会议记录表：meetingId、topic、startTime、duration、joinUrl、status、creatorId、projectId、recordingUrl

- **meeting/entity/MeetingParticipant.java**
  - 参会人表：meetingRecordId、userId、attendStatus、joinedAt、leftAt

**数据访问层**

- **meeting/repository/MeetingRecordRepository.java**
  - 按 creatorId、projectId、status、时间范围查询

- **meeting/repository/MeetingParticipantRepository.java**
  - 按 meetingRecordId、userId 查询

**DTO**

- **meeting/dto/CreateMeetingRequest.java**
  - 创建会议请求：topic、description、startTime、duration、password、projectId、participantIds

- **meeting/dto/MeetingResponse.java**
  - 会议响应：包含参会人信息

**核心服务**

- **meeting/service/TencentMeetingService.java**
  - createMeeting() - 创建会议
  - getMeetingList() - 获取会议列表
  - getMeetingDetail() - 获取会议详情
  - cancelMeeting() - 取消会议
  - endMeeting() - 结束会议
  - updateMeetingStatus() - 更新会议状态
  - updateRecordingUrl() - 更新录制链接

**Webhook 服务**

- **meeting/service/TencentMeetingWebhookService.java**
  - 接收腾讯会议事件推送
  - 签名验证（HMAC-SHA256）
  - 处理：会议开始、结束、录制完成等事件

**REST 控制器**

- **meeting/controller/MeetingController.java**
  - POST /api/meetings - 创建会议
  - GET /api/meetings - 获取会议列表
  - GET /api/meetings/{id} - 获取会议详情
  - PUT /api/meetings/{id}/cancel - 取消会议
  - PUT /api/meetings/{id}/end - 结束会议
  - POST /api/meeting/webhook - Webhook 回调

**数据库迁移**

- **db/migration/V20260529_001__create_meeting_tables.sql**
  - 创建 meeting_record 表（会议记录）
  - 创建 meeting_participant 表（参会人）
  - 创建相关索引
  - **已修复**: creator_id 和 user_id 字段类型改为 VARCHAR(64) 与 sys_user.user_id 匹配

### 执行记录

**2026-05-29 10:30**
- 修复了 SQL 迁移文件中的外键引用问题（sys_user.id → sys_user.user_id）
- 成功执行建表 SQL：meeting_record, meeting_participant
- 验证表结构已创建

**2026-05-29 10:45**
- 在 .env 文件添加腾讯会议配置（占位符）

**2026-05-29 11:35**
- 修复编译错误：User 实体用的是 `String userId` 和 `name`，不是 `Long` 和 `realName`
- 修改文件：MeetingRecord、MeetingParticipant、CreateMeetingRequest、MeetingResponse、TencentMeetingService、MeetingController、MeetingRecordRepository、MeetingParticipantRepository
- 修复 TencentMeetingWebhookService 的 JsonProcessingException 未捕获问题
- `mvn compile` 编译通过

**2026-05-29 11:40**
- 新增 `application.properties` 配置文件，本地启动时读取环境变量
- 添加腾讯会议配置项（tencent.meeting.*）

**2026-05-29 12:00**
- 新增前端 API 层：`src/api/meeting.js`
- 新增会议中心页面：`src/views/MeetingCenterView.vue`
  - 会议列表展示（支持状态筛选）
  - 创建会议表单（主题、时间、参会人选择）
  - 加入会议功能
  - 取消/结束会议
- 在路由中添加 `/meetings` 路由
- 在 WorkspaceView 侧边栏添加"会议中心"快捷链接

**2026-05-29 14:58**
- 修复 Vite 代理端口：从 8101 改为 8081（匹配本地后端端口）

**2026-05-29 15:10**
- 在 ManagerDashboard 顶部添加"会议中心"按钮入口
- 优化创建会议表单：日期和时间选择器分离，选择更便捷
- 修复顶部导航栏：点击"智能博弈实验室"返回首页
- 移除会议中心页面的返回按钮（已有顶部导航返回逻辑）

**2026-06-05 10:50**
- 修复 JWT 过期后 userId attribute 未设置的问题（JwtAuthenticationFilter）
- 重构腾讯会议鉴权：从 OAuth Token 改为企业自建应用 AK/SK 签名鉴权
  - 新增 `TencentMeetingSignatureService` 实现 TC3-HMAC-SHA256 签名
  - 删除 Token 服务（企业自建应用不需要 access_token）
  - 修改 `TencentMeetingService` 使用签名鉴权
  - 新增 `TENCENT_MEETING_APP_ID` 配置项
- 修复签名计算错误：移除十六进制转换，直接对 HmacSHA256 结果 Base64 编码
- 添加详细调试日志：签名计算、请求参数、响应结果

### 待办

- [ ] 申请腾讯会议企业版获取 API 密钥（替换 .env 中的占位符）
- [ ] 前端：会议详情页面
- [ ] 前端：日历视图

---
