# Bugfix To Release Workflow

Last updated: 2026-03-15

## Purpose

This file is the current production maintenance workflow for this project.

Use it when a user reports an error and the job is to:

- hear the error
- analyze frontend/backend/container state
- modify code
- verify the fix
- update containers
- publish `latest`

All earlier development-stage migration instructions are obsolete and removed from this file.

## Runtime Scope

- Frontend: `lab-erp-demo`
- Backend: `erp-backend`
- Runtime orchestration: `docker-compose.yml`
- Private registry:
  - `127.0.0.1:5555/zhangqi_frontend`
  - `127.0.0.1:5555/zhangqi_backend`

## Standard Workflow

### 1. Receive And Reproduce

- Record the exact user-facing symptom.
- Identify route, button, role, request path, and response code.
- Reproduce with the smallest possible path.
- If the issue is browser-visible, inspect the actual request/response before guessing.

Minimum checklist:

- page path
- user role/domain
- failing API
- response code/body
- whether the bug is frontend-only, backend-only, auth, DB, or deploy/cache related

### 2. Inspect Running State First

- Check running containers with `docker compose ps`.
- Check deployed image versions with `docker inspect`.
- Read relevant container logs before changing code.
- Confirm whether the user is seeing stale frontend assets or current runtime behavior.

Typical commands:

```bash
docker compose ps
docker logs zkr-lab-erp-demo --tail 100
docker logs zkr-erp-backend --tail 200
```

### 3. Read Code Before Editing

- Read the exact frontend view/store/router/request files involved.
- Read the exact backend controller/service/security/entity/repository files involved.
- Prefer fixing the real source of truth instead of layering UI-only workarounds.

Typical frontend hotspots:

- `lab-erp-demo/src/views`
- `lab-erp-demo/src/stores/userStore.js`
- `lab-erp-demo/src/utils/request.js`
- `lab-erp-demo/src/router`

Typical backend hotspots:

- `erp-backend/src/main/java/com/smartlab/erp/controller`
- `erp-backend/src/main/java/com/smartlab/erp/service`
- `erp-backend/src/main/java/com/smartlab/erp/security`
- `erp-backend/src/main/java/com/smartlab/erp/entity`

### 4. Fix The Code

- Prefer the smallest correct fix.
- Do not preserve stale finance-only assumptions in ERP flows.
- For shared auth/runtime logic, prefer active-session/domain-aware behavior.
- When upload/download is involved, check both metadata persistence and actual file retrieval path.
- When DB-backed data is required, verify the data is truly stored in database fields, not only on disk or in frontend memory.

### 5. Verify Before Release

Frontend minimum verification:

```bash
cd /home/a/zhangqi/workspace/ZKR/lab-erp-demo
npm run build
```

Backend minimum verification:

- If local Maven is unavailable, use Docker build as the verification path.
- Confirm the backend image builds successfully.

Functional verification must be targeted to the bug:

- auth issue -> register/login/me verification
- permissions issue -> same role/path/button flow
- upload issue -> upload, detail refresh, download
- deploy issue -> route `200`, container version, logs clean

### 6. Version Bump Rules

- Frontend changes -> bump frontend image tag
- Backend changes -> bump backend image tag
- If both changed, bump both
- Use explicit version tags such as `v1.7`, `v1.8`, `v1.5`
- Do not overwrite old version tags with different content

Also publish `latest` after the versioned push succeeds.

## Release Procedure

### Frontend

1. Update `lab-erp-demo/Dockerfile` default `APP_VERSION`
2. Update `docker-compose.yml` frontend image tag
3. Build and push version tag
4. Tag the same image as `latest` and push `latest`

Template:

```bash
docker build --build-arg APP_VERSION=<frontend-version> -t 127.0.0.1:5555/zhangqi_frontend:<frontend-tag> ./lab-erp-demo
docker push 127.0.0.1:5555/zhangqi_frontend:<frontend-tag>
docker tag 127.0.0.1:5555/zhangqi_frontend:<frontend-tag> 127.0.0.1:5555/zhangqi_frontend:latest
docker push 127.0.0.1:5555/zhangqi_frontend:latest
```

### Backend

1. Update `erp-backend/Dockerfile` default `APP_VERSION`
2. Update `docker-compose.yml` backend image tag
3. Build and push version tag
4. Tag the same image as `latest` and push `latest`

Template:

```bash
docker build --build-arg APP_VERSION=<backend-version> -t 127.0.0.1:5555/zhangqi_backend:<backend-tag> ./erp-backend
docker push 127.0.0.1:5555/zhangqi_backend:<backend-tag>
docker tag 127.0.0.1:5555/zhangqi_backend:<backend-tag> 127.0.0.1:5555/zhangqi_backend:latest
docker push 127.0.0.1:5555/zhangqi_backend:latest
```

### Redeploy

```bash
docker compose up -d erp-backend lab-erp-demo
```

If container-name conflicts exist because of older manual runs, remove the conflicting container and rerun compose.

## Post-Release Verification

- `docker compose ps`
- confirm running image tags with `docker inspect`
- check frontend entry routes return `200`
- run the exact bug reproduction flow again
- read backend logs if any API still returns `4xx/5xx`

Template:

```bash
docker compose ps
docker inspect zkr-lab-erp-demo --format '{{.Config.Image}} {{ index .Config.Labels "org.opencontainers.image.version" }}'
docker inspect zkr-erp-backend --format '{{.Config.Image}} {{ index .Config.Labels "org.opencontainers.image.version" }}'
curl -I http://127.0.0.1:8080/login
curl -I http://127.0.0.1:8080/erp-login
```

## Documentation And Memory Update

After every meaningful fix/release:

- update `docs/superpowers/current-project-memory.md`
- update deployment docs only if the operational procedure changed
- mark stale design/plan docs as historical instead of letting them pretend to be current
- keep `workflow.md` aligned with the real release path

## Current Project-Specific Rules

- Finance entry is `/login`
- ERP entry is `/erp-login`
- Login/register requests must not inherit stale `Authorization` headers
- `/api/auth/me` must remain authenticated
- ERP UI permission checks should use active-session user info, not finance-only state
- ERP feasibility report uploads must persist through the database-backed asset flow
- Login-page and authenticated shell branding should use `国科九天`

## About Skill Support

There is currently no platform skill registered for this repository environment.

So this file acts as the repo-local operational skill/source of truth until real skill registration becomes available.
