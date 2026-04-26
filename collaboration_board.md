# Collaboration Board

## Contract Ledger - Product Flow

### Status Mapping
- `STATUS_IDEA` -> `IDEA`
- `STATUS_PROMOTION` -> `PROMOTION`
- `STATUS_DEMO` -> `DEMO_EXECUTION`
- `STATUS_MEETING` -> `MEETING_DECISION`
- `STATUS_TESTING` -> `TESTING`
- `STATUS_IMPLEMENTATION` -> `LAUNCHED` + spawn `FlowType.PROJECT`
- `STATUS_SHELVED` -> `SHELVED`

### DB Schema Deltas
- `product_idea_detail`
  - add `use_case` text
  - add `problem_statement` text
  - add `idea_owner_user_id` varchar(64)
  - add `promotion_ic_user_id` varchar(64)
  - add `meeting_participant_user_ids` text
- create `project_chat_message`
  - `id` bigserial pk
  - `project_id` varchar(64) not null
  - `sender_user_id` varchar(64) not null
  - `sender_name` varchar(120) not null
  - `content` text not null
  - `created_at` timestamptz not null default now()

### API Contracts
- `POST /api/products/idea`
  - required: `name`, `expectedBudget`, `targetUsers`, `coreFeatures`, `useCase`, `problemStatement`, `techStackDesc`
- `POST /api/products/{projectId}/meeting-decision`
  - multipart required: `meetingMinutes`, `decision`, `participantUserIds[]`
- `POST /api/products/{projectId}/testing-feedback`
  - pass gate: distinct chat participants in `project_chat_message` >= 5 when `isPassed=true`
- `GET /api/projects/{projectId}/chat/messages`
- `POST /api/projects/{projectId}/chat/messages`
  - required: `content`
- `GET /api/projects/{projectId}/chat/participants`

### Permission Rules
- product idea create: any authenticated ERP user
- project chat read/write: project members only
- meeting participants: must be valid project member user IDs
- testing decision: only `PROMOTION_IC`
