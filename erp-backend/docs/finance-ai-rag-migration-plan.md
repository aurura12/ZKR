# T-005 Finance AI and RAG migration plan

## Decision

Implement both capabilities inside the in-repo `com.smartlab.erp.finance` domain defined by T-001, with two focused vertical slices:

- `com.smartlab.erp.finance.rag`: index rebuild and finance retrieval endpoints
- `com.smartlab.erp.finance.ai`: chat and conversation reset endpoints

This keeps JWT, JPA, transaction management, request validation, and deployment inside the current Spring Boot runtime and avoids any continued Flask runtime dependency.

## Runtime placement

- Controllers:
  - `POST /api/finance/rag/rebuild`
  - `POST /api/finance/rag/query`
  - `POST /api/finance/ai/chat`
  - `POST /api/finance/ai/reset`
- Shared ownership:
  - reuse `SecurityConfig` and JWT authentication for all four endpoints
  - reuse `UserPrincipal` as the actor identity for per-user rate limiting and chat session isolation
  - reuse research-side `MiddlewareAsset` metadata only as a finance context source, not as the RAG/AI aggregate root
- New finance-local support packages:
  - `finance.dto.rag`
  - `finance.dto.ai`
  - `finance.support.ai`
  - `finance.support.rag`

## RAG design

### Purpose

Replace Flask + FAISS retrieval with a Java-managed retrieval pipeline that can answer finance queries from two frontend modes without changing the existing global auth or router contracts.

### Recommended implementation

- Document source: finance tables and approved project/research metadata projected into finance-readable text blocks
- Embedding gateway: wrap the chosen embedding model behind a Spring service interface such as `FinanceEmbeddingClient`
- Vector store: prefer PostgreSQL `pgvector` in the same database for the first migration cut; fallback option is Redis Stack or an external managed vector store if DBA constraints block extension rollout
- Index orchestration: `rag/rebuild` runs an asynchronous rebuild job that snapshots finance source rows into chunk records plus embedding vectors
- Query flow: `rag/query` embeds the incoming question, filters by requested mode, retrieves top-k chunks, and returns both answer-oriented snippets and cited source blocks

### Frontend-facing modes

- `VENTURE`: venture or project financial health view, focused on revenue, net profit, clearing, dividend, and bank context
- `PERSONNEL`: personnel ROI and wallet view, focused on dividend income, royalty income, wallet changes, and labor cost context

### RAG request contract

```json
{
  "query": "本月哪个创投单元亏损最高？",
  "mode": "VENTURE",
  "topK": 5,
  "ventureId": 123,
  "ledgerMonth": "2026-02"
}
```

Rules:

- `query` required, trimmed, 1-500 chars
- `mode` required, enum `VENTURE | PERSONNEL`
- `topK` optional, default `5`, max `10`
- `ventureId` optional narrowing filter for venture mode
- `ledgerMonth` optional filter, format `YYYY-MM`

### RAG response contract

```json
{
  "status": "success",
  "message": "finance rag query completed",
  "data": {
    "queryId": "uuid",
    "mode": "VENTURE",
    "answer": "...",
    "citations": [
      {
        "sourceType": "VENTURE_CLEARING",
        "sourceId": "123",
        "title": "创投单元 123 清算结果",
        "snippet": "净利润 -120000.00，亏损转公司 120000.00",
        "score": 0.91,
        "highlights": ["净利润", "亏损转公司"],
        "ledgerMonth": "2026-02"
      }
    ],
    "contextBlocks": [
      {
        "blockType": "VENTURE",
        "label": "重点风险",
        "content": "连续两期净利润为负"
      }
    ]
  },
  "meta": {
    "topK": 5,
    "citationCount": 1
  },
  "timestamp": "2026-03-11T12:00:00Z",
  "traceId": "2f5d3a3b7b93453d"
}
```

Notes:

- `data.answer` may be empty when retrieval finds only citations; frontend should still render `data.citations` and `data.contextBlocks`
- `data.citations` stays ordered by score descending
- `data.contextBlocks` is shared with AI chat so the frontend can pin or preview retrieved facts
- `meta` carries retrieval diagnostics such as `topK`, citation count, or rebuild snapshot metadata without polluting the domain payload

## AI chat design

### Purpose

Replace Flask + SiliconFlow Qwen orchestration with a Spring-managed chat gateway that injects finance context and preserves a short per-user conversation window.

### Recommended implementation

- Model gateway: `FinanceChatClient` interface with one concrete adapter for the chosen external model provider and one local mock adapter for non-production verification
- Conversation memory: persist the latest 20 turns per user and optional finance work area in a finance-local chat session store; database persistence is preferred over in-memory state so restarts do not corrupt reset semantics
- Context injection: resolve `contextBlocks` from explicit frontend payload first, then optionally enrich with fresh RAG retrieval when `contextMode` requests it
- Markdown output: backend returns plain markdown text; frontend owns rendering

### AI chat request contract

```json
{
  "message": "解释一下为什么这个创投单元本月没有分红",
  "contextMode": "VENTURE",
  "sessionId": "finance-default",
  "contextBlocks": [
    {
      "blockType": "VENTURE",
      "label": "净利润",
      "content": "2026-02 净利润 -120000.00"
    }
  ]
}
```

Rules:

- `message` required, trimmed, 1-2000 chars
- `contextMode` required, enum `NONE | VENTURE | PERSONNEL | MIXED`
- `sessionId` optional, default `finance-default`, max `64` chars
- `contextBlocks` optional, max `8` blocks; each block must include `blockType`, `label`, and `content`

### AI chat response contract

```json
{
  "status": "success",
  "message": "finance ai chat completed",
  "data": {
    "sessionId": "finance-default",
    "reply": "...markdown...",
    "history": [
      {
        "role": "USER",
        "content": "解释一下为什么这个创投单元本月没有分红"
      },
      {
        "role": "ASSISTANT",
        "content": "...markdown..."
      }
    ],
    "usedContextBlocks": [
      {
        "blockType": "VENTURE",
        "label": "净利润",
        "content": "2026-02 净利润 -120000.00"
      }
    ]
  },
  "meta": {
    "historySize": 2,
    "contextBlockCount": 1
  },
  "timestamp": "2026-03-11T12:00:00Z",
  "traceId": "6a1e5078988c4b5b"
}
```

Notes:

- `data.history` returns the latest 20 turns after the new assistant reply is appended
- frontend loading state should last until this payload returns or fails
- `data.usedContextBlocks` lets the UI show what evidence was injected into the prompt

### Reset contract

`POST /api/finance/ai/reset`

```json
{
  "sessionId": "finance-default"
}
```

Response:

```json
{
  "status": "success",
  "message": "finance ai session reset",
  "data": {
    "sessionId": "finance-default",
    "clearedTurns": 8
  },
  "meta": {
    "reset": true
  },
  "timestamp": "2026-03-11T12:00:00Z",
  "traceId": "88ca0c14f6f1484f"
}
```

Rules:

- if `sessionId` is absent, clear `finance-default`
- reset is idempotent; clearing an empty session still returns `status: "success"`

## Error and rate-limit contract

### Error format

Finance AI and RAG endpoints must use T-002's finance-wide error envelope instead of finance-local `success/code` payloads:

```json
{
  "status": "error",
  "message": "请求过于频繁，请稍后再试",
  "error": {
    "code": "RATE_LIMITED",
    "details": [
      {
        "field": "userId",
        "reason": "ai/chat per-user quota exceeded"
      }
    ]
  },
  "timestamp": "2026-03-11T12:00:00Z",
  "traceId": "9f8cb3dfe95b4200"
}
```

Recommended codes:

- `INVALID_ARGUMENT`
- `RESOURCE_NOT_FOUND`
- `STATE_CONFLICT`
- `BUSINESS_RULE_VIOLATION`
- `RATE_LIMITED`
- `UPSTREAM_UNAVAILABLE`

### HTTP semantics

- `400`: request validation failure such as bad `mode`, bad `contextMode`, malformed `ledgerMonth`, or oversized `contextBlocks`
- `401`: unauthenticated
- `403`: authenticated but not authorized for finance work area
- `409`: rebuild already running or chat session conflict
- `422`: valid JSON but unsupported finance context combination
- `429`: per-user or per-IP rate limit exceeded
- `503`: model gateway or vector service unavailable

### Rate limiting

- Apply per-authenticated-user limits first because all calls already pass through JWT auth
- Suggested starting policy:
  - `rag/query`: 30 requests / minute / user
  - `ai/chat`: 10 requests / minute / user
  - `ai/reset`: 20 requests / minute / user
  - `rag/rebuild`: admin or finance-ops only, serialized to one active rebuild job
- Recommended Java options: Bucket4j servlet filter or Spring Cloud Gateway rate limiting if ingress already exists; avoid pushing this concern back to Flask

## Frontend behavior contract

- `lab-erp-demo/src/utils/request.js` already unwraps Axios to the finance envelope body, so finance pages must still read `status`, `message`, `data`, `meta`, `timestamp`, and `traceId` through a finance-scoped adapter instead of consuming raw `data.reply` or `data.citations` directly in page components
- RAG page keeps its current mode switch shape and should post to `/api/finance/rag/query`
- AI page should post to `/api/finance/ai/chat`, render `data.reply` as markdown, and replace the visible timeline with `data.history`
- Reset button should call `/api/finance/ai/reset`, clear local draft input, and replace local history with an empty list after `status === "success"`
- Frontend may reuse the same `data.contextBlocks` object from RAG results when handing off into chat
- Finance adapters should map error envelopes by `error.code` and preserve `traceId` for operator-visible diagnostics

## Dependency boundary and rollout risks

- External model provider remains a runtime dependency; a provider outage must degrade gracefully with `503` and a stable error code
- `pgvector` is the preferred first-step vector store, but rollout depends on database extension approval in the target PostgreSQL environment
- Research `MiddlewareAsset` data overlaps with finance royalty context but does not replace finance retrieval sources; ownership must stay explicit to avoid duplicate semantics
- The current backend has no shared API error envelope, so finance endpoints must introduce the T-002 finance envelope in a finance-scoped adapter layer without changing older controllers or the global request wrapper
- If persistent chat history lands in PostgreSQL, retention and PII rules must be confirmed before production rollout

## Recommended delivery order

1. Add finance-local DTOs, controller stubs, and validation rules.
2. Implement `rag/query` against a mock retriever plus fixed contract tests.
3. Implement `ai/chat` with a mock chat gateway and persistent session store.
4. Add rate limiting and rebuild job serialization.
5. Swap mock retriever and chat gateway to production adapters after infrastructure approval.
