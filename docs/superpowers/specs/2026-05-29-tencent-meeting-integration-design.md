# 腾讯会议集成设计文档

## 1. 项目背景

### 1.1 现状
- 目前会议从手机腾讯会议发起，手动通知参会人
- 会议纪要手动整理分发
- 会议信息与 ERP 系统割裂

### 1.2 目标
- ERP 内直接发起会议
- 从 ERP 员工中选择参会人
- 会后录音/会议纪要自动分发
- 实现会议与 ERP 项目的联动

### 1.3 技术选型
- **腾讯会议企业版 API**（必须企业版，个人版 API 有限制）
- 后端：Spring Boot Java 17
- 前端：Vue 3
- 参考现有钉钉集成模式（DingTalkTokenService + DingTalkAttendanceService）

---

## 2. 功能清单

| 功能 | 优先级 | 描述 |
|------|--------|------|
| 创建会议 | P0 | ERP 审批通过后自动/手动创建会议 |
| 选择参会人 | P0 | 从 ERP 员工列表中选择 |
| 获取会议列表 | P0 | 日历/列表展示所有会议 |
| 加入会议 | P0 | 一键跳转腾讯会议客户端 |
| Webhook 通知 | P1 | 实时接收会议状态变化 |
| 录制获取 | P1 | 获取会议录制文件链接 |
| 会议纪要生成 | P2 | 基于录制生成文字纪要 |
| 会议纪要分发 | P2 | 自动发送给参会人 |

---

## 3. 前置准备

### 3.1 申请腾讯会议企业版

1. 访问 https://meeting.tencent.com/
2. 注册企业版账号
3. 获取 API 密钥：
   - `SecretId`（类似用户名）
   - `SecretKey`（类似密码）

### 3.2 配置回调地址

在腾讯会议管理后台 → 开发者 → 事件订阅：
```
回调URL: https://your-erp-domain.com/api/meeting/webhook
```

### 3.3 配置 IP 白名单

将 ERP 服务器 IP 加入腾讯会议 API 白名单

---

## 4. 后端架构设计

### 4.1 模块结构

```
erp-backend/src/main/java/com/smartlab/erp/meeting/
├── config/
│   └── TencentMeetingConfig.java
├── service/
│   ├── TencentMeetingTokenService.java
│   ├── TencentMeetingService.java
│   └── TencentMeetingWebhookService.java
├── controller/
│   └── MeetingController.java
├── entity/
│   ├── MeetingRecord.java
│   └── MeetingParticipant.java
├── repository/
│   ├── MeetingRecordRepository.java
│   └── MeetingParticipantRepository.java
├── dto/
│   ├── CreateMeetingRequest.java
│   ├── MeetingResponse.java
│   └── MeetingWebhookEvent.java
└── exception/
    └── TencentMeetingException.java
```

### 4.2 环境变量配置

在 `.env` 文件中添加：
```env
# 腾讯会议配置
TENCENT_MEETING_SECRET_ID=your_secret_id
TENCENT_MEETING_SECRET_KEY=your_secret_key
TENCENT_MEETING_WEBHOOK_SECRET=your_webhook_secret
```

在 `application.properties` 或 `application.yml` 中引用：
```yaml
tencent:
  meeting:
    secret-id: ${TENCENT_MEETING_SECRET_ID}
    secret-key: ${TENCENT_MEETING_SECRET_KEY}
    webhook-secret: ${TENCENT_MEETING_WEBHOOK_SECRET}
    api-base-url: https://api.meeting.qq.com
```

### 4.3 Token 服务

参考 `DingTalkTokenService.java` 实现，核心逻辑：
- 使用 SecretId + SecretKey 获取 access_token
- Token 缓存（减少重复请求）
- 自动刷新（过期前 5 分钟刷新）
- 异常重试机制

### 4.4 核心 API 服务

| 方法 | HTTP | 腾讯会议 API | 说明 |
|------|------|--------------|------|
| `createMeeting()` | POST | `/v1/meetings` | 创建会议 |
| `getMeetingList()` | GET | `/v1/meetings` | 获取会议列表 |
| `getMeetingDetail()` | GET | `/v1/meetings/{meeting_id}` | 获取会议详情 |
| `getMeetingJoinUrl()` | GET | `/v1/meetings/{meeting_id}/join_url` | 获取加入链接 |
| `cancelMeeting()` | DELETE | `/v1/meetings/{meeting_id}` | 取消会议 |
| `endMeeting()` | POST | `/v1/meetings/{meeting_id}/end` | 结束会议 |

### 4.5 Webhook 服务

接收的事件类型：
| 事件 | 说明 |
|------|------|
| `meeting.started` | 会议开始 |
| `meeting.ended` | 会议结束 |
| `meeting.participant_joined` | 有人加入 |
| `meeting.participant_left` | 有人离开 |
| `meeting.recording_completed` | 录制完成 |

### 4.6 REST API

```
POST   /api/meetings                       # 创建会议
GET    /api/meetings                       # 获取会议列表
GET    /api/meetings/{meetingId}           # 获取会议详情
PUT    /api/meetings/{meetingId}           # 更新会议
DELETE /api/meetings/{meetingId}           # 取消会议
POST   /api/meetings/{meetingId}/join      # 获取加入链接
GET    /api/meetings/calendar              # 日历视图数据
POST   /api/meeting/webhook                # Webhook 回调（腾讯会议调用）
```

---

## 5. 数据库设计

### 5.1 meeting_record（会议记录表）

```sql
CREATE TABLE meeting_record (
    id SERIAL PRIMARY KEY,
    meeting_id VARCHAR(64) UNIQUE NOT NULL,       -- 腾讯会议ID
    topic VARCHAR(200) NOT NULL,                  -- 会议主题
    description TEXT,                             -- 会议描述
    start_time TIMESTAMP NOT NULL,                -- 开始时间
    duration INTEGER,                             -- 时长（分钟）
    join_url TEXT,                                -- 加入链接
    password VARCHAR(32),                         -- 会议密码
    status VARCHAR(20) DEFAULT 'SCHEDULED',       -- SCHEDULED/STARTED/ENDED/CANCELLED
    creator_id INTEGER REFERENCES sys_user(id),   -- 创建人
    project_id INTEGER REFERENCES sys_project(project_id), -- 关联项目（可选）
    recording_url TEXT,                           -- 录制文件链接
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_meeting_record_creator ON meeting_record(creator_id);
CREATE INDEX idx_meeting_record_project ON meeting_record(project_id);
CREATE INDEX idx_meeting_record_status ON meeting_record(status);
```

### 5.2 meeting_participant（参会人表）

```sql
CREATE TABLE meeting_participant (
    id SERIAL PRIMARY KEY,
    meeting_record_id INTEGER REFERENCES meeting_record(id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES sys_user(id),
    attend_status VARCHAR(20) DEFAULT 'PENDING',   -- PENDING/ACCEPTED/ATTENDED/ABSENT
    joined_at TIMESTAMP,                           -- 实际加入时间
    left_at TIMESTAMP,                             -- 实际离开时间
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(meeting_record_id, user_id)
);

CREATE INDEX idx_meeting_participant_meeting ON meeting_participant(meeting_record_id);
CREATE INDEX idx_meeting_participant_user ON meeting_participant(user_id);
```

---

## 6. 前端实现

### 6.1 页面结构

```
src/views/
├── workspace/
│   ├── MeetingCenterView.vue       # 会议中心主页
│   ├── MeetingDetailView.vue       # 会议详情
│   └── MeetingCalendarView.vue     # 日历视图
```

### 6.2 会议中心页面

主要功能：
- 会议列表（支持按状态筛选：全部/待开始/进行中/已结束）
- 创建会议按钮
- 一键加入会议
- 关联项目筛选

### 6.3 创建会议流程

```
1. 点击"创建会议"按钮
2. 弹出表单：
   - 会议主题（必填）
   - 开始时间（必填）
   - 时长（默认 60 分钟）
   - 会议密码（可选）
   - 选择参会人（从 sys_user 列表多选）
   - 关联项目（可选，从 sys_project 列表选择）
3. 提交 → 后端调用腾讯会议 API 创建
4. 返回成功 → 显示会议链接，可复制或直接分享
```

### 6.4 加入会议

```
1. 在会议列表或项目详情页显示"加入会议"按钮
2. 点击 → 调用后端获取 MMPURL（腾讯会议跳转链接）
3. 浏览器打开链接 → 自动唤起腾讯会议客户端
```

### 6.5 日历视图

- 按月/周展示所有会议
- 颜色区分：待开始（蓝）、进行中（绿）、已结束（灰）
- 点击跳转会议详情

---

## 7. 会议纪要自动分发

### 7.1 流程

```
会议结束
    ↓
腾讯会议 Webhook 推送 recording_completed 事件
    ↓
后端保存录制链接到 meeting_record.recording_url
    ↓
（可选）调用 AI 服务生成文字纪要
    ↓
通过 ERP 消息系统通知所有参会人
    ↓
参会人可在 ERP 查看纪要和录制
```

### 7.2 消息通知模板

```
会议纪要通知

会议主题: {topic}
会议时间: {start_time} - {end_time}
参会人: {participant_list}

录制链接: {recording_url}
会议纪要: {summary_url}

请登录 ERP 查看详情。
```

### 7.3 AI 纪要生成（可选增强）

方案A：调用腾讯会议智能纪要 API（如有）
方案B：下载录制 → 调用语音转文字 API → 生成纪要
方案C：调用 ERP 的 RAG 服务处理

---

## 8. 实施计划

### Phase 1: 基础集成（3-5天）

| 任务 | 预计工时 | 产出 |
|------|----------|------|
| 后端模块结构搭建 | 0.5天 | 目录结构、配置类 |
| Token 服务 | 0.5天 | TencentMeetingTokenService |
| 创建会议 API | 1天 | createMeeting + 数据库 |
| 获取会议列表 | 0.5天 | getMeetingList |
| 前端会议中心页面 | 1天 | MeetingCenterView |
| 前端创建会议表单 | 0.5天 | 弹窗表单 |

### Phase 2: Webhook & 实时状态（2-3天）

| 任务 | 预计工时 | 产出 |
|------|----------|------|
| Webhook 接收服务 | 1天 | WebhookService |
| 签名验证 | 0.5天 | HMAC-SHA256 校验 |
| 会议状态实时更新 | 0.5天 | 前端轮询/WebSocket |
| 加入会议功能 | 0.5天 | MMPURL 获取 |

### Phase 3: 日历 & 项目联动（2天）

| 任务 | 预计工时 | 产出 |
|------|----------|------|
| 日历视图页面 | 1天 | MeetingCalendarView |
| 项目详情页集成 | 0.5天 | 显示项目关联会议 |
| 会议筛选/搜索 | 0.5天 | 列表筛选功能 |

### Phase 4: 会议纪要（3-5天）

| 任务 | 预计工时 | 产出 |
|------|----------|------|
| 录制获取 | 0.5天 | recording_url 存储 |
| AI 纪要生成 | 2天 | 集成 AI 服务 |
| 消息分发 | 0.5天 | ERP 消息通知 |
| 纪要页面展示 | 1天 | 纪要查看页 |

---

## 9. 关键代码示例

### 9.1 Token 服务骨架

```java
@Service
@Slf4j
public class TencentMeetingTokenService {

    @Value("${tencent.meeting.secret-id}")
    private String secretId;

    @Value("${tencent.meeting.secret-key}")
    private String secretKey;

    private String cachedToken;
    private Instant tokenExpiresAt = Instant.MIN;

    public synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minus(Duration.ofMinutes(5)))) {
            return cachedToken;
        }
        refreshAccessToken();
        return cachedToken;
    }

    private void refreshToken() {
        // 调用腾讯会议 Token 接口
        // POST https://api.meeting.qq.com/v1/oauth/token
        // Body: { "secret_id": "xxx", "secret_key": "xxx" }
        // 缓存 token 和过期时间
    }
}
```

### 9.2 创建会议服务骨架

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TencentMeetingService {

    private final TencentMeetingTokenService tokenService;
    private final MeetingRecordRepository meetingRecordRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;

    public MeetingRecord createMeeting(CreateMeetingRequest request, Integer creatorId) {
        // 1. 调用腾讯会议 API 创建会议
        String token = tokenService.getAccessToken();
        // POST /v1/meetings
        // Body: { "topic": "xxx", "start_time": "xxx", "duration": 60 }

        // 2. 保存到本地数据库
        MeetingRecord record = MeetingRecord.builder()
            .meetingId(response.getMeetingId())
            .topic(request.getTopic())
            .startTime(request.getStartTime())
            .duration(request.getDuration())
            .joinUrl(response.getJoinUrl())
            .creatorId(creatorId)
            .build();
        meetingRecordRepository.save(record);

        // 3. 保存参会人
        for (Integer userId : request.getParticipantIds()) {
            meetingParticipantRepository.save(MeetingParticipant.builder()
                .meetingRecordId(record.getId())
                .userId(userId)
                .build());
        }

        return record;
    }
}
```

---

## 10. 测试计划

### 10.1 单元测试

- Token 获取与刷新
- 创建会议请求构建
- Webhook 签名验证

### 10.2 集成测试

- 创建会议 → 获取列表 → 获取详情 → 取消会议
- Webhook 事件接收与处理
- 参会人关联查询

### 10.3 端到端测试

- 前端创建会议 → 后端调用 API → 数据库保存
- 会议列表展示 → 点击加入 → 跳转腾讯会议

---

## 11. 风险与注意事项

| 风险 | 应对措施 |
|------|----------|
| 个人会员无 API 权限 | 必须申请企业版 |
| Token 过期导致请求失败 | 缓存 + 提前刷新 + 重试机制 |
| Webhook 签名被伪造 | 实现 HMAC-SHA256 签名校验 |
| 录制文件存储成本 | 先存链接，按需下载 |
| 并发创建会议 | 加分布式锁或幂等控制 |
| 服务器 IP 变化 | 配置 IP 白名单更新机制 |

---

## 12. 参考资源

- 腾讯会议开放平台文档: https://meeting.qq.com/
- 腾讯会议 API 文档: https://meeting.qq.com/open/api
- 项目现有钉钉集成参考: `erp-backend/src/main/java/com/smartlab/erp/service/DingTalkTokenService.java`

---

## 文档信息

| 项目 | 内容 |
|------|------|
| 版本 | v1.0 |
| 创建日期 | 2026-05-29 |
| 作者 | 周佳琪 |
| 状态 | 待实施 |
