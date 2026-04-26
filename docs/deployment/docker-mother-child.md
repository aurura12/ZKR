# Docker Runtime Deployment Guide

> Legacy filename: this file used to document an abandoned mother-container / child-container / workspace-container workflow. The active deployment is now direct Docker Compose on the host. If a `zhangqi` container still exists, treat it as historical residue unless the user explicitly says that workflow is back in use.

## Runtime truth

- `docker-compose.yml` is the runtime orchestration entry point.
- Active runtime services are `postgres`, `erp-backend`, `lab-erp-demo`, `finance-rag-api`, `finance-rag-qdrant`, and `finance-rag-redis`.
- Frontend runtime image comes from `127.0.0.1:5555/zhangqi_frontend:<tag>`.
- Backend runtime image comes from `127.0.0.1:5555/zhangqi_backend:<tag>`.
- PostgreSQL data, backend uploads, and RAG sidecar state persist through Docker volumes.
- Day-to-day operations run from the project directory in a normal shell on the host or any shell already pointed at the host Docker daemon.
- Do not assume `SSH -> docker exec -it zhangqi bash` is required.
- Do not edit code inside runtime containers; change the repo working tree and redeploy.

## 1. Work from the project root

Run deployment commands from the repository root on the machine that owns the Docker daemon.

Typical server path:

```bash
cd /srv/zhangqi/workspace/ZKR
```

Local clone example:

```bash
cd /home/a/zhangqi/workspace/ZKR
```

Expected outcome:

- you are in the same directory as `docker-compose.yml`
- `docker compose` commands act on the current ZKR stack

## 2. Prepare `.env`

If this is the first deployment on that machine:

```bash
cp .env.example .env
```

At minimum, review these values:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `FRONTEND_PUBLIC_PORT`
- `PUBLIC_DOWNLOADS_DIR`
- `APP_UPLOADS_DIR`
- `AUTH_PROVISION_ADMIN_USER_ID`
- `AUTH_PROVISION_ADMIN_USERNAME`
- `AUTH_PROVISION_ADMIN_TEMP_PASSWORD`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `MAIL_FROM_NAME`

Notes:

- `PUBLIC_DOWNLOADS_DIR` is mounted into nginx and exposed at `/downloads/`
- QQ mailbox SMTP needs the SMTP authorization code, not the QQ login password
- `finance-rag-api` is controlled by the `FINANCE_RAG_*` variables already defined in `.env.example`

## 3. Check the currently selected runtime images

The active frontend and backend tags come from `docker-compose.yml`.

Useful checks:

```bash
docker compose ps
docker inspect zkr-lab-erp-demo --format '{{.Config.Image}}'
docker inspect zkr-erp-backend --format '{{.Config.Image}}'
curl -fsSL http://127.0.0.1:5555/v2/zhangqi_frontend/tags/list
curl -fsSL http://127.0.0.1:5555/v2/zhangqi_backend/tags/list
```

Expected outcome:

- you know which tags are currently deployed
- you know which tags are available locally for update or rollback

## 4. Start or refresh the runtime stack

Start everything:

```bash
docker compose up -d
```

Refresh only the main app after changing image tags:

```bash
docker compose up -d erp-backend lab-erp-demo
```

Notes:

- `finance-rag-api` is built from `./rag-service` by Compose when needed
- frontend and backend are pulled from the local registry by tag; they are not built by Compose in the current runtime model

## 5. Check status and logs

```bash
docker compose ps
docker compose logs -f postgres
docker compose logs -f erp-backend
docker compose logs -f lab-erp-demo
docker compose logs -f finance-rag-api
```

Expected outcome:

- `postgres` is healthy
- frontend serves on `http://<host>:${FRONTEND_PUBLIC_PORT}`
- backend is reachable through nginx `/api`
- no startup exceptions remain in the logs

## 6. Release updated frontend or backend images

If code changed, build and push a new versioned image to the local registry first.

### Frontend

```bash
docker build --build-arg APP_VERSION=<frontend-version> -t 127.0.0.1:5555/zhangqi_frontend:<frontend-tag> ./lab-erp-demo
docker push 127.0.0.1:5555/zhangqi_frontend:<frontend-tag>
docker tag 127.0.0.1:5555/zhangqi_frontend:<frontend-tag> 127.0.0.1:5555/zhangqi_frontend:latest
docker push 127.0.0.1:5555/zhangqi_frontend:latest
```

### Backend

```bash
docker build --build-arg APP_VERSION=<backend-version> -t 127.0.0.1:5555/zhangqi_backend:<backend-tag> ./erp-backend
docker push 127.0.0.1:5555/zhangqi_backend:<backend-tag>
docker tag 127.0.0.1:5555/zhangqi_backend:<backend-tag> 127.0.0.1:5555/zhangqi_backend:latest
docker push 127.0.0.1:5555/zhangqi_backend:latest
```

Then update the image tags in `docker-compose.yml` and redeploy:

```bash
docker compose up -d erp-backend lab-erp-demo
```

## 7. Roll back quickly

Rollback means selecting an older frontend/backend tag in `docker-compose.yml` and redeploying.

Typical flow:

1. pick a known-good tag from the local registry
2. change the frontend and/or backend image tag in `docker-compose.yml`
3. redeploy the affected services
4. confirm the new running image with `docker inspect`

Commands:

```bash
docker compose up -d erp-backend lab-erp-demo
docker inspect zkr-lab-erp-demo --format '{{.Config.Image}}'
docker inspect zkr-erp-backend --format '{{.Config.Image}}'
```

## 8. Boundary rules

- Current production runtime is not a mother-child container model.
- A stopped `zhangqi` container can exist as a legacy leftover; it is not part of the required runtime path.
- Do not use runtime containers as source-editing environments.
- Inspect running containers, deployed tags, and logs before assuming code is wrong.
- Treat `workflow.md` as the maintenance/release procedure and this guide as the current runtime/deploy reference.
