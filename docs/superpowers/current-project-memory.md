# Current Project Memory

Last updated: 2026-04-13

## Runtime truth
- Frontend runtime is served by `lab-erp-demo`
- Backend runtime is served by `erp-backend`
- Repo-local email skill lives in `skills/email/README.md` and `skills/email/send.py`; it reuses `SPRING_MAIL_*`, defaults to `2310406891@qq.com`, sends as `我是5090agent`, and supports `--attach` file attachments
- Public ERP downloads are exposed from host directory `PUBLIC_DOWNLOADS_DIR` at `/downloads/`, and nginx is tuned for large static archive delivery with range-friendly settings
- Finance RAG sidecar runtime now includes `finance-rag-api`, `finance-rag-qdrant`, and `finance-rag-redis` on isolated bridge `zkr_rag-isolated`
- Finance RAG host inspection ports are pinned to localhost high ports `36817`, `36333`, and `36379`
- Active runtime is direct Docker/Compose services, not a required `zhangqi` workspace-container workflow
- If a `zhangqi` container still exists on a machine, treat it as legacy residue unless the user explicitly says that workflow is active again
- Deployment source of truth is `workflow.md`, `docker-compose.yml`, and the legacy-named `docs/deployment/docker-mother-child.md` guide after its direct-Compose rewrite
- Production bugfix -> verify -> container release flow is documented in `workflow.md`
- The project is in maintenance/update mode, not migration/buildout mode
- Current active work should start from running containers, deployed image tags, runtime logs, and targeted bug reproduction

## Auth and entry state
- Finance login entry: `/login`
- ERP login entry: `/erp-login`
- Shared auth API family remains under `/api/auth/*`
- Login and register requests must not carry stale `Authorization` headers from the frontend
- Backend JWT filtering skips anonymous auth endpoints but still protects `/api/auth/me`
- Frontend auth state must use the active session/domain, not hard-coded finance user state
- Public self-registration is closed; account creation is now an internal provisioning flow
- The provisioning entry should appear only for the designated admin after login, via the avatar dropdown
- Provisioned users receive email-delivered temporary passwords and must change password on first login
- User role `USER` has been retired and replaced by `RESEARCH` across the stack
- Department is no longer a valid user field and should not appear in ERP or finance forms/APIs
- Provisioning mail should use raw SMTP from-address only; QQ mailbox SMTP requires an authorization code rather than the QQ login password

## ERP UI state
- Finance and ERP login pages share the same visual shell and animated character panel
- Login-page brand text is `国科九天`
- Login pages must not render the authenticated top navbar
- ERP business users can access the create flow from the top navbar
- ERP project/product permissions should read `userStore.activeUserInfo`
- DATA users on the current project can upload project assets and build the team during the team-formation flow
- Frontend nginx must allow ERP feasibility report uploads larger than the default 1 MB proxy body limit
- ERP project detail now carries implementation-side execution management, including mandatory goal/difficulty/tech-stack planning, member schedules, visible responsibility ratios in implementation stage, and isolated execution file folders
- Manager archive files use folder `A_MANAGER_ARCHIVE`; engineer work files use `B_ENGINEER_WORK`, with engineers isolated from each other while Manager can govern archive categorization
- Manager dashboard `managementRadius` means: among the projects the current user participates in, the percentage where that user serves as Manager

## Wording conventions
- Keep finance copy finance-specific on finance routes
- In ERP initiation flow, the top CTA uses `发起产品`
- The create form currently uses product-oriented wording for the initiation surface
- Project ratings should render as plain `S/A/B/C/N` without explanatory suffixes

## Documentation hygiene
- Treat old design/plan docs under `docs/superpowers/` as historical background unless explicitly marked current
- Prefer generic deployment docs over release-specific one-off notes
- Treat `workflow.md` as the current repo-local maintenance workflow until real skill registration is available
- Treat the legacy-named `docs/deployment/docker-mother-child.md` as the current direct-Compose deployment guide, not as evidence that mother-child containers are still in use
- Treat old workspace-container / mother-child design docs under `docs/superpowers/specs/*.md` and `docs/superpowers/plans/*.md` as historical only, not as current operations guidance
- Do not treat old `docs/superpowers/specs/*.md` or `docs/superpowers/plans/*.md` files as evidence that the repo is still in a feature-development phase
