# Workspace Container Implementation Plan

> Historical note (2026-03-15): this plan has been substantially implemented. The repo is now in maintenance/update mode; use `workflow.md`, `docs/deployment/docker-mother-child.md`, and `docs/superpowers/current-project-memory.md` for current operations. Keep this file as planning history.

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the current temporary mother-container workflow into a long-lived workspace container named `zhangqi` that serves as the main SSH-attached source-editing and deployment-control environment, while keeping runtime application containers separate.

**Architecture:** The server-side source tree under `/srv/zhangqi/workspace/ZKR` becomes the operational source of truth. A persistent workspace container mounts that tree and the host Docker socket, so users and future agents can edit code and manage the runtime stack from one stable environment. Runtime services continue to run separately in the compose-managed application stack.

**Tech Stack:** Docker Engine, Docker Compose, Alpine-based workspace image, bash, git, curl, docker cli, Node/npm, Java 17, existing ZKR runtime stack

---

## File Structure Map

### Root workspace assets in `ZKR`

- Modify: `Dockerfile.mother` - evolve from temporary mother shell into long-lived workspace image baseline
- Modify: `mother-shell/run-mother.sh` - convert temporary `--rm` launcher into persistent `zhangqi` workspace startup logic
- Create: `mother-shell/enter-workspace.sh` - simple attach helper for `docker exec -it zhangqi bash`
- Modify: `.env.example` only if workspace-specific env defaults are required

### Deployment docs and archive

- Modify: `docs/deployment/docker-mother-child.md` - reframe the guide around `zhangqi` workspace container instead of a throwaway mother shell
- Modify: `ambition.md` - archive the workspace-container upgrade
- Modify: `docs/superpowers/plans/2026-03-14-workspace-container-plan.md` - record execution evidence during implementation

### Runtime orchestration touchpoints

- Modify: `docker-compose.yml` only if needed to document/align runtime ownership boundaries, not to merge runtime into the workspace container

## Chunk 1: Persistent workspace container assets

### Task 1: Turn the mother image into a workspace image baseline

**Files:**
- Modify: `Dockerfile.mother`

- [ ] **Step 1: Write the failing workspace-image checklist in the plan comments or notes**

```text
Workspace image must support bash, git, curl, docker cli, docker compose, node, npm, java
Workspace image must default to /workspace/ZKR or /workspace with clear attach workflow
Workspace image must be suitable for long-lived use, not only one-shot shell access
```

- [ ] **Step 2: Attempt a workspace image build and verify the current image is missing required repo-specific tools**

Run: `docker build -f Dockerfile.mother -t zhangqi-workspace .`
Expected: PASS build or fail checklist review, but current image definition should be visibly insufficient because it lacks Node/npm and Java needed for this repo.

- [ ] **Step 3: Add the minimum repo-specific toolchain to `Dockerfile.mother`**

```dockerfile
FROM docker:27-cli
RUN apk add --no-cache bash git curl docker-cli-compose nodejs npm openjdk17-jdk
WORKDIR /workspace
CMD ["bash"]
```

- [ ] **Step 3A: Define the first-pass workspace user/editability strategy in the image or startup flow**

Required outcome:
- the container user can write to `/workspace/ZKR`
- if root is kept for the first pass, the implementation must explicitly justify it and verify bind-mount editability

- [ ] **Step 4: Rebuild the workspace image to verify it passes**

Run: `docker build -f Dockerfile.mother -t zhangqi-workspace .`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add Dockerfile.mother
git commit -m "feat: convert mother image into workspace baseline"
```

### Task 2: Replace temporary launch behavior with persistent `zhangqi` workspace startup

**Files:**
- Modify: `mother-shell/run-mother.sh`
- Create: `mother-shell/enter-workspace.sh`

- [ ] **Step 1: Write the failing shell-workflow checklist for persistence and attach flow**

```text
Workspace startup must not use --rm
Container name must be zhangqi
Workspace mount must include /srv/zhangqi/workspace -> /workspace
Workspace mount must include /var/run/docker.sock -> /var/run/docker.sock
Startup must be idempotent if container already exists
Attach helper must use docker exec -it zhangqi bash
```

- [ ] **Step 2: Run shell syntax checks on the current scripts and verify the behavioral checklist is not yet met**

Run: `sh -n mother-shell/run-mother.sh`
Expected: PASS syntax, but current logic still uses throwaway shell semantics and lacks attach helper.

- [ ] **Step 3: Rewrite `run-mother.sh` into a persistent `zhangqi` launcher**

Required behavior:
- build/start container named `zhangqi`
- remove `--rm`
- if container exists and is stopped, start it
- if container exists and is running, do not recreate it unnecessarily
- mount `/srv/zhangqi/workspace` to `/workspace`
- mount `/var/run/docker.sock` to `/var/run/docker.sock`

- [ ] **Step 4: Add `mother-shell/enter-workspace.sh`**

```bash
docker exec -it zhangqi bash
```

- [ ] **Step 5: Re-run shell syntax checks to verify both scripts pass**

Run: `sh -n mother-shell/run-mother.sh && sh -n mother-shell/enter-workspace.sh`
Expected: PASS

- [ ] **Step 5A: Add behavioral verification for the launcher contract**

Run: `docker inspect zhangqi`
Expected: container name is `zhangqi`, workspace bind mount exists, Docker socket bind mount exists, and rerunning the launcher reuses or starts the same container rather than creating drift.

- [ ] **Step 6: Commit**

```bash
git add mother-shell/run-mother.sh mother-shell/enter-workspace.sh
git commit -m "feat: add persistent workspace container workflow"
```

### Task 3: Make the workspace source-of-truth path explicit in docs and commands

**Files:**
- Modify: `docs/deployment/docker-mother-child.md`

- [ ] **Step 1: Write the failing documentation checklist for source-of-truth and SSH attach workflow**

```text
Guide must say /srv/zhangqi/workspace/ZKR is the operational source of truth
Guide must say SSH -> docker exec -it zhangqi bash is the normal entry path
Guide must distinguish workspace container from runtime containers
```

- [ ] **Step 2: Update the guide to replace throwaway mother-shell language with persistent workspace-container language**

- [ ] **Step 3: Explicitly document the standard daily entry commands**

```bash
ssh <server>
docker exec -it zhangqi bash
cd /workspace/ZKR
```

- [ ] **Step 4: Self-check the guide against the checklist**

- [ ] **Step 5: Commit**

```bash
git add docs/deployment/docker-mother-child.md
git commit -m "docs: document persistent workspace container workflow"
```

## Chunk 2: Boundary rules and validation

### Task 4: Document and align runtime separation rules

**Files:**
- Modify: `docker-compose.yml`
- Modify: `docs/deployment/docker-mother-child.md`

- [ ] **Step 1: Write the failing boundary checklist**

```text
Workspace container must not be merged into app compose stack
Runtime stack remains postgres/backend/frontend only
Docs must explicitly forbid editing code inside runtime containers
```

- [ ] **Step 2: Treat `docker-compose.yml` as runtime-owned and avoid behavior changes unless a minimal clarifying comment is strictly necessary**

- [ ] **Step 3: Update docs so operators understand: workspace container = edit and operate; runtime containers = run the app; runtime containers are not source-editing environments**

- [ ] **Step 4: Self-check against the boundary checklist**

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml docs/deployment/docker-mother-child.md
git commit -m "docs: clarify workspace and runtime boundaries"
```

### Task 5: Add workspace lifecycle and recovery guidance

**Files:**
- Modify: `docs/deployment/docker-mother-child.md`
- Modify: `ambition.md`

- [ ] **Step 1: Add a failing documentation checklist for restart/recovery behavior**

```text
Guide must explain what to do if zhangqi is stopped
Guide must explain what to do if zhangqi is recreated
Guide must explain that source survives because it lives in host bind mount
```

- [ ] **Step 2: Update deployment guide with restart/recovery commands**

Required examples:

```bash
docker start zhangqi
docker exec -it zhangqi bash
docker rm -f zhangqi
sh mother-shell/run-mother.sh zhangqi
```

- [ ] **Step 3: Update `ambition.md` to archive the workspace-container upgrade and its operational significance**

- [ ] **Step 4: Self-check the guide and archive update against the checklist**

- [ ] **Step 5: Commit**

```bash
git add docs/deployment/docker-mother-child.md ambition.md
git commit -m "docs: add workspace lifecycle and recovery guidance"
```

### Task 6: Verify workspace-container workflow locally

**Files:**
- Modify: `docs/superpowers/plans/2026-03-14-workspace-container-plan.md`

- [x] **Step 1: Build the workspace image**

Run: `docker build -f Dockerfile.mother -t zhangqi-workspace .`
Expected: PASS

Evidence (2026-03-14, Windows host): PASS. `docker build -f Dockerfile.mother -t zhangqi-workspace .` completed successfully with final image `zhangqi-workspace:latest`.

- [x] **Step 2: Start or recreate the persistent workspace container locally with a representative mount**

Run: `sh mother-shell/run-mother.sh zhangqi`
Expected: PASS with a running container named `zhangqi`.

Evidence: local closest-equivalent verification passed. On this Windows host, the exact server path `/srv/zhangqi/workspace` is not usable as a real host checkout, so runtime verification used an equivalent local bind mount `C:/Users/28378/workspace -> /workspace` with the same container name `zhangqi`, Docker socket bind, and working directory `/workspace/ZKR`. `docker inspect zhangqi` confirmed `Name=/zhangqi`, `State.Running=true`, `AutoRemove=false`, bind mounts for `/workspace` and `/var/run/docker.sock`, and `WorkingDir=/workspace/ZKR`.

- [x] **Step 3: Verify attach flow**

Run: `sh mother-shell/enter-workspace.sh`
Expected: PASS and a shell inside `zhangqi`.

Evidence: direct scripted attach cannot complete inside this automated non-TTY session because `docker exec -it` requires an interactive terminal. Closest provable equivalent passed: `docker exec zhangqi bash -lc "pwd && test -d /workspace/ZKR && printf ATTACH_EQUIVALENT_OK"` confirmed the expected shell landing point and workspace path when a TTY is available.

- [x] **Step 4: Verify key tools inside the workspace container**

Run inside `zhangqi`: `git --version && docker version && node --version && java -version`
Expected: PASS

Evidence: PASS. Verified inside container with `docker exec zhangqi bash -lc "git --version && docker version --format '{{.Server.Version}}' && node --version && npm --version && java -version"` -> `git version 2.47.3`, Docker server `28.5.1`, `node v22.15.1`, `npm 10.9.1`, OpenJDK `17.0.18`.

- [x] **Step 5: Verify mounted source-tree editing path and runtime control path**

Run inside `zhangqi`:

```bash
pwd
ls /workspace
docker compose ps
```

Expected: source tree visible under `/workspace`, compose visible from the mounted project, runtime stack controllable.

Evidence: PASS under the local equivalent mount. `docker exec zhangqi bash -lc "pwd && ls /workspace && docker compose ps"` confirmed the working directory `/workspace/ZKR`, visible mounted source tree entries including `ZKR`, and successful runtime-stack control through Docker Compose from inside the workspace container.

- [x] **Step 5A: Verify host/container edit persistence and permissions explicitly**

Run inside `zhangqi`:

```bash
touch /workspace/ZKR/.workspace-write-test && echo ok > /workspace/ZKR/.workspace-write-test
```

Then verify from the host:

```bash
test -f /srv/zhangqi/workspace/ZKR/.workspace-write-test && cat /srv/zhangqi/workspace/ZKR/.workspace-write-test
```

Expected: file exists on the host bind mount, contains `ok`, and can be removed cleanly after verification.

Cleanup:

```bash
rm -f /workspace/ZKR/.workspace-write-test
rm -f /srv/zhangqi/workspace/ZKR/.workspace-write-test
```

Evidence: PASS under the local equivalent mount. Inside `zhangqi`, `touch /workspace/ZKR/.workspace-write-test && echo ok > /workspace/ZKR/.workspace-write-test && cat /workspace/ZKR/.workspace-write-test` succeeded and returned `ok`. Reading the host file `C:\Users\28378\workspace\ZKR\.workspace-write-test` from the Windows host returned `ok`, proving bind-mounted persistence from container to host. Cleanup then removed the file.

- [x] **Step 6: Record execution evidence in `docs/superpowers/plans/2026-03-14-workspace-container-plan.md` during implementation**

- [ ] **Step 7: Commit**

```bash
git add docs/superpowers/plans/2026-03-14-workspace-container-plan.md
git commit -m "docs: track workspace container verification"
```
