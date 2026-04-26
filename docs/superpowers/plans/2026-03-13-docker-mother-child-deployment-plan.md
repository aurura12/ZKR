# Docker Mother-Child Deployment Implementation Plan

> Historical note (2026-03-15): this plan has been implemented and evolved into the current maintenance/update workflow. Use `workflow.md`, `docs/deployment/docker-mother-child.md`, and `docs/superpowers/current-project-memory.md` for live operations.

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a beginner-friendly Docker deployment setup with a personal mother-container CLI workspace and a child-container application stack for `lab-erp-demo`, `erp-backend`, and PostgreSQL.

**Architecture:** The host provides a personal workspace root. A mother-container mounts only that workspace and uses the host Docker socket to manage the child stack. Child containers run the actual application services through Docker Compose, with frontend as the only exposed ingress, backend internal by default, and PostgreSQL plus uploads persisted to runtime directories.

**Tech Stack:** Docker Engine, Docker Compose, nginx, Node 20, Vite, Java 17, Spring Boot 3.2, PostgreSQL 16

---

## File Structure Map

### Root-level deployment assets in `ZKR`

- Create: `Dockerfile.mother` - mother-container image for personal CLI workspace
- Create: `.dockerignore` - prevent repo-root temp/build data from bloating the mother image
- Create: `docker-compose.yml` - child-container stack orchestration
- Create: `.env.example` - server-side environment template without real secrets
- Create: `docs/deployment/docker-mother-child.md` - command-by-command deployment guide for a first-time Docker user
- Create: `mother-shell/run-mother.sh` - reproducible mother-container startup command

### Backend deployment files in `erp-backend`

- Create: `erp-backend/Dockerfile`
- Create: `erp-backend/.dockerignore`
- Modify: `erp-backend/src/main/resources/application.yml` - move hardcoded local DB/JWT/uploads values to environment-friendly defaults

### Frontend deployment files in `lab-erp-demo`

- Create: `lab-erp-demo/Dockerfile`
- Create: `lab-erp-demo/.dockerignore`
- Create: `lab-erp-demo/nginx.conf` - same-origin `/api` proxy to backend service name

### Verification helpers and docs

- Modify: `ambition.md` - archive Docker deployment work after completion
- Modify: `docs/superpowers/plans/2026-03-13-docker-mother-child-deployment-plan.md` - record execution evidence during implementation

## Chunk 1: Core container assets

### Task 1: Create the mother-container image and workspace model

**Files:**
- Create: `Dockerfile.mother`
- Create: `.dockerignore`
- Create: `mother-shell/run-mother.sh`

- [ ] **Step 1: Write a failing operational check by documenting the exact required tools and workspace mount assumptions for the mother-container**

```text
Mother container must provide: bash, git, docker, docker compose, curl
Mother container must default to /workspace
Mother container must be designed to mount only the personal server workspace
Mother container must mount /var/run/docker.sock
Mother container startup must be reproducible from a checked-in script
```

- [ ] **Step 2: Run a local build attempt to verify the mother-container image does not exist yet**

Run: `docker build -f Dockerfile.mother -t zkr-mother-shell .`
Expected: FAIL because `Dockerfile.mother` does not exist yet.

- [ ] **Step 3: Write the minimal mother-container Dockerfile and root `.dockerignore`**

```dockerfile
FROM docker:27-cli
RUN apk add --no-cache bash git curl docker-cli-compose
WORKDIR /workspace
CMD ["bash"]
```

- [ ] **Step 3A: Add `mother-shell/run-mother.sh` with the exact startup contract**

```bash
docker run --rm -it \
  -v /srv/<user-space>/workspace:/workspace \
  -v /srv/<user-space>/runtime:/runtime \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -w /workspace \
  --name zkr-mother-shell \
  zkr-mother-shell
```

- [ ] **Step 4: Re-run the mother-container build to verify it passes**

Run: `docker build -f Dockerfile.mother -t zkr-mother-shell .`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add Dockerfile.mother .dockerignore mother-shell/run-mother.sh
git commit -m "feat: add mother container shell image"
```

### Task 2: Containerize the Spring Boot backend

**Files:**
- Create: `erp-backend/Dockerfile`
- Create: `erp-backend/.dockerignore`
- Modify: `erp-backend/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing backend packaging verification by attempting a backend Docker build before the Dockerfile exists**

Run: `docker build -t zkr-erp-backend ./erp-backend`
Expected: FAIL because `erp-backend/Dockerfile` does not exist yet.

- [ ] **Step 2: Externalize backend runtime config in `application.yml` for datasource, JWT secret, and uploads directory**

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/postgres}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:postgres}
  web:
    resources:
      static-locations: ${SPRING_WEB_RESOURCES_STATIC_LOCATIONS:classpath:/static/,file:./uploads/}
jwt:
  secret: ${JWT_SECRET:change-me}
file:
  upload-dir: ${FILE_UPLOAD_DIR:./uploads}
auth:
  account-domain:
    finance-usernames: ${AUTH_ACCOUNT_DOMAIN_FINANCE_USERNAMES:}
```

- [ ] **Step 3: Add a multi-stage or straightforward Java 17 backend Dockerfile**

```dockerfile
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8101
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 3A: Add `erp-backend/.dockerignore` so backend image builds do not send `target/`, IDE files, or uploads data into the context**

- [ ] **Step 4: Build the backend image to verify it passes**

Run: `docker build -t zkr-erp-backend ./erp-backend`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add erp-backend/Dockerfile erp-backend/.dockerignore erp-backend/src/main/resources/application.yml
git commit -m "feat: add backend container runtime support"
```

### Task 3: Containerize the Vite frontend with same-origin API proxying

**Files:**
- Create: `lab-erp-demo/Dockerfile`
- Create: `lab-erp-demo/.dockerignore`
- Create: `lab-erp-demo/nginx.conf`

- [ ] **Step 1: Write the failing frontend packaging verification by attempting a frontend Docker build before the Dockerfile exists**

Run: `docker build -t zkr-lab-erp-demo ./lab-erp-demo`
Expected: FAIL because `lab-erp-demo/Dockerfile` does not exist yet.

- [ ] **Step 2: Add nginx config that serves the built frontend and proxies `/api` to the backend service name**

```nginx
location / {
  try_files $uri $uri/ /index.html;
}

location /api/ {
  proxy_pass http://erp-backend:8101/api/;
}
```

- [ ] **Step 3: Add a multi-stage frontend Dockerfile using Node build + nginx runtime**

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

- [ ] **Step 3A: Add `lab-erp-demo/.dockerignore` so frontend image builds do not send `node_modules/`, `dist/`, or temp artifacts into the context**

- [ ] **Step 4: Build the frontend image to verify it passes**

Run: `docker build -t zkr-lab-erp-demo ./lab-erp-demo`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lab-erp-demo/Dockerfile lab-erp-demo/.dockerignore lab-erp-demo/nginx.conf
git commit -m "feat: add frontend container runtime support"
```

## Chunk 2: Compose orchestration and teaching docs

### Task 4: Add child-container Compose orchestration with persistence

**Files:**
- Create: `docker-compose.yml`
- Create: `.env.example`

- [ ] **Step 1: Write the failing Compose validation check before the file exists**

Run: `docker compose config`
Expected: FAIL because `docker-compose.yml` does not exist yet.

- [ ] **Step 2: Create `.env.example` with all required variables and no real secrets**

```env
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=change-me
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=change-me
JWT_SECRET=change-me-too
AUTH_ACCOUNT_DOMAIN_FINANCE_USERNAMES=
FILE_UPLOAD_DIR=/app/uploads
SPRING_WEB_RESOURCES_STATIC_LOCATIONS=classpath:/static/,file:/app/uploads/
FRONTEND_PUBLIC_PORT=8080
```

- [ ] **Step 3: Define the shared internal network and service-name connectivity in `docker-compose.yml`**

- [ ] **Step 3A: Define the `postgres` service with persistent storage and no host port by default**

- [ ] **Step 3B: Define the `erp-backend` service with environment variables, uploads mount, and internal-only network exposure**

- [ ] **Step 3C: Define the `lab-erp-demo` service as the only public ingress on host port `${FRONTEND_PUBLIC_PORT}` and keep same-origin `/api` proxying through nginx to the backend service**

Key requirements:
- frontend published on host `8080`
- backend internal only by default
- PostgreSQL persistence under `/srv/<user-space>/runtime/postgres-data/` or equivalent bind mount placeholder
- backend uploads persistence under `/srv/<user-space>/runtime/uploads/` or equivalent bind mount placeholder
- explicit internal network shared by all child containers
- frontend `/api` same-origin proxy retained

- [ ] **Step 4: Validate the Compose file**

Run: `docker compose config`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml .env.example
git commit -m "feat: add compose stack for child containers"
```

### Task 5: Write the beginner-oriented deployment guide

**Files:**
- Create: `docs/deployment/docker-mother-child.md`

- [ ] **Step 1: Write the failing documentation completeness checklist**

```text
Guide must explain image, container, volume, compose
Guide must include personal workspace creation
Guide must include mother-container startup
Guide must include child stack startup, logs, stop, restart, update
```

- [ ] **Step 2: Draft the deployment guide as a command-by-command checklist with expected outcomes**

Required sections:
- what Docker image/container/volume/compose mean in simple language
- create `/srv/<user-space>/...` workspace
- build and run the mother-container
- enter the mother-container shell
- prepare `.env`
- run `docker compose build`
- run `docker compose up -d`
- run `docker compose ps`
- run `docker compose logs -f`
- restart and stop commands
- update flow for new code/images

- [ ] **Step 3: Self-check the guide against the completeness checklist**

- [ ] **Step 4: Commit**

```bash
git add docs/deployment/docker-mother-child.md
git commit -m "docs: add beginner docker deployment guide"
```

### Task 6: Archive the deployment work in `ambition.md`

**Files:**
- Modify: `ambition.md`

- [ ] **Step 1: Add a new archive section summarizing Docker deployment work and deployment-ready next steps**

- [ ] **Step 2: Record the mother-container/child-container model, new deployment assets, and first-run guidance status**

- [ ] **Step 3: Commit**

```bash
git add ambition.md
git commit -m "docs: archive docker deployment progress"
```

### Task 7: Final verification of Docker deployment assets

**Files:**
- Modify: `docs/superpowers/plans/2026-03-13-docker-mother-child-deployment-plan.md`

- [ ] **Step 1: Rebuild backend and frontend images**

Run: `docker build -t zkr-erp-backend ./erp-backend && docker build -t zkr-lab-erp-demo ./lab-erp-demo`
Expected: PASS

- [ ] **Step 2: Validate the Compose stack**

Run: `docker compose config`
Expected: PASS

- [ ] **Step 3: Start the stack and verify runtime connectivity**

Run: `docker compose up -d`
Expected: PASS with running `postgres`, `erp-backend`, and `lab-erp-demo` containers.

- [ ] **Step 3A: Verify backend-to-database and frontend-to-backend connectivity**

Run from inside the mother-container workspace: `docker compose ps && docker compose logs --no-color erp-backend && docker compose logs --no-color lab-erp-demo`
Expected: backend starts without datasource errors; frontend container starts cleanly and serves the app.

- [ ] **Step 3B: Verify persistence survives restart**

Run from inside the mother-container workspace: `docker compose restart postgres erp-backend`
Expected: services restart cleanly without losing mounted database/uploads paths.

- [ ] **Step 3C: Verify finance and ERP authentication flows against the deployed frontend entrypoints**

Manual verification from a browser against the deployed frontend host port:

```text
Open /login and verify finance login/register page appears
Open /erp-login and verify ERP login/register page appears
Use a FINANCE account to log in at /login and confirm /finance/overview access
Use an ERP account to log in at /erp-login and confirm /manager/dashboard or /workspace access by role
Confirm wrong-domain login attempts show the expected backend error message
```

Expected: PASS

- [ ] **Step 4: Verify the documentation checklist**

```text
Mother container image exists
Backend image exists
Frontend image exists
Compose stack defines postgres/backend/frontend
Frontend is the only exposed ingress by default
Uploads path is persisted
Guide explains Docker basics in beginner language
Guide includes workspace creation, startup, logs, stop, restart
Guide includes update flow
Mother shell startup script exists and explains docker socket usage
```

- [ ] **Step 5: Record execution evidence in a dedicated `## Execution Evidence` section inside `docs/superpowers/plans/2026-03-13-docker-mother-child-deployment-plan.md` during implementation**

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/plans/2026-03-13-docker-mother-child-deployment-plan.md
git commit -m "docs: track docker deployment verification"
```

## Execution Evidence

- Completed file generation:
  - `Dockerfile.mother`
  - `.dockerignore`
  - `mother-shell/run-mother.sh`
  - `erp-backend/Dockerfile`
  - `erp-backend/.dockerignore`
  - `lab-erp-demo/Dockerfile`
  - `lab-erp-demo/.dockerignore`
  - `lab-erp-demo/nginx.conf`
  - `docker-compose.yml`
  - `.env.example`
  - `docs/deployment/docker-mother-child.md`

- Static/backend verification passed:
  - `mvn -q -DskipTests package`
  - `docker compose --env-file .env.example config`

- Runtime Docker verification passed after Docker engine became available:
  - `docker build -f Dockerfile.mother -t zkr-mother-shell .`
  - `docker build -t zkr-erp-backend ./erp-backend`
  - `docker build -t zkr-lab-erp-demo ./lab-erp-demo`
  - `docker compose --env-file .env.example up -d`
  - `docker compose --env-file .env.example ps`
  - `curl -I http://localhost:8080/login`
  - `curl -I http://localhost:8080/erp-login`
  - finance register/login and ERP register/login were exercised against the deployed stack
  - `docker run --rm -v //var/run/docker.sock:/var/run/docker.sock zkr-mother-shell docker ps --format "{{.Names}}"`

## Execution Notes

- During runtime verification, two deployment/runtime issues were found and fixed:
  - `AccountDomainDataInitializer` queried `sys_user` too early on a fresh database and was hardened so first boot no longer crashes startup
  - deployment env lacked `SPRING_JPA_HIBERNATE_DDL_AUTO=update`, which prevented first-run schema creation inside containers
- The backend now starts successfully in the compose stack, PostgreSQL becomes healthy, and the frontend is reachable on port `8080`.
- The backend `/api/auth/me` endpoint still returns a non-ideal `500` for anonymous access, but authenticated `me` calls work correctly with issued tokens.
