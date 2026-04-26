# Workspace Container Design

> Historical note (2026-04-13): this document describes an abandoned intermediate workspace-container operating model. It is preserved as architecture history only. Current runtime operations should follow `workflow.md`, `docker-compose.yml`, and the rewritten `docs/deployment/docker-mother-child.md` direct-Compose guide. A leftover `zhangqi` container, if present, should be treated as legacy residue rather than an active requirement.

## Context

- The current deployment work has already produced a mother-container image and a child-container application stack.
- The user clarified that the true goal is not just deployment, but a long-lived isolated workspace for CLI/agent-driven code editing and maintenance.
- The user wants to SSH into the server and then directly work inside this isolated environment.
- The server-side source of truth should become the main workspace, not the local PC.

## Goal

Replace the temporary mother-container concept with a long-lived workspace container that acts as both the main development/maintenance workspace and the deployment control plane, while keeping runtime application containers separate.

## Chosen Approach

Use one persistent workspace container named `zhangqi` plus a separate runtime application stack.

- The workspace container mounts the server source tree and Docker socket.
- The runtime stack remains separate and serves users.
- The user reaches the workspace container by SSHing into the server and then attaching to the `zhangqi` container.

## Why This Approach

- It matches the user’s actual working model more closely than a temporary mother-container.
- It gives the user and future agents one durable place to edit source code and operate deployment commands.
- It keeps runtime services separate from the development/maintenance environment.
- It is simpler and more maintainable than adding a separate container-in-container development model.

## Scope

### In Scope

- Long-lived workspace container image and startup method
- Workspace directory mounts and working directory
- SSH -> attach workflow
- Using the workspace container as the main source-editing and deployment control environment
- Aligning current deployment docs and assets with the workspace-container model

### Out of Scope

- Replacing runtime application containers
- Full VM-based or sandbox-based strong isolation
- Multi-user container tenancy design

## Architecture Overview

### Host Server

The host server provides:

- Docker Engine
- one server-side source-of-truth workspace root
- persistent application runtime state

Recommended root:

- `/srv/zhangqi/`

### Workspace Container `zhangqi`

The `zhangqi` container is the primary working environment.

Responsibilities:

- mount and expose the real source tree
- host CLI tools for editing, building, and deployment orchestration
- allow SSH users to attach into a stable workspace environment
- run Docker commands against the host Docker daemon via mounted socket

Non-responsibilities:

- does not directly serve user traffic
- does not replace the frontend/backend/postgres runtime containers

### Runtime Stack

The runtime stack remains the same child-container application stack:

- `postgres`
- `erp-backend`
- `lab-erp-demo`

These continue to serve application traffic separately from the workspace container.

## Source-of-Truth Model

The main source of truth moves to the server workspace.

Recommended server path:

- `/srv/zhangqi/workspace/ZKR`

The workspace container mounts that path to:

- `/workspace/ZKR`

This means:

- code edits happen against the real server source tree
- rebuilds and deployment commands are run from inside the workspace container
- the local PC becomes an optional access point, not the canonical source workspace

### Git Workflow Decision

The server-side workspace becomes the authoritative working tree for day-to-day maintenance.

First-pass workflow:

- `git` operations are run from inside the `zhangqi` workspace container against `/workspace/ZKR`
- local PC clones are optional mirrors or backup working copies, not the operational source of truth
- commits, pulls, branch inspection, and deployment-triggering builds are performed from inside the workspace container
- if local work must be brought back in later, it should be synchronized intentionally through git, not by ad-hoc file copying over the server workspace

Recovery expectation:

- if the workspace container is replaced, the source tree remains safe because it lives in the host bind mount under `/srv/zhangqi/workspace/ZKR`
- if the host workspace directory is lost, git remains the recovery source, not the container filesystem

## SSH Workflow

Recommended operator workflow:

1. SSH into the host server
2. verify the `zhangqi` workspace container is running
3. attach to the workspace container:

```bash
docker exec -it zhangqi bash
```

4. work inside `/workspace/ZKR`

This gives the user the feeling of “remote control of the CLI workspace” without needing SSH directly inside the container.

## Mount and Permission Model

### Required Mounts for `zhangqi`

- `/srv/zhangqi/workspace` -> `/workspace`
- `/var/run/docker.sock` -> `/var/run/docker.sock`

### Optional Mounts

- limited runtime/log directories if needed for inspection

### Things Not To Mount

- the full host root filesystem
- unrelated user directories
- broad host paths not needed for this workspace

### Permission Model

The workspace container should have:

- high operational usefulness inside the mounted workspace
- Docker control-plane access through the host Docker socket

This is sufficient for:

- code editing
- git operations
- image building
- compose deployment updates
- log inspection

### File Ownership and User Mapping

The workspace container should run with a predictable non-root user when possible, but the most important requirement is that the bind-mounted host workspace remains editable from inside the container.

First-pass rule for planning:

- define one explicit workspace user inside the container
- ensure that user can read and write `/workspace/ZKR`
- if host/container UID-GID mismatches appear on the target server, resolve them by aligning the workspace container user or host directory ownership before using the environment for real maintenance

Acceptance implication:

- editing a file inside `zhangqi` must create/modify files in `/srv/zhangqi/workspace/ZKR` without permission errors

Important limitation:

This is a practical engineering isolation boundary, not a strong security sandbox. Docker socket access grants powerful host-level container control.

## Tooling Inside the Workspace Container

The workspace container should include at least:

- shell
- git
- curl
- docker cli
- docker compose
- node and npm
- java runtime/tooling for this Spring Boot backend
- any exact CLI/agent tooling required for this repo, including `opencode` if this environment is intended to host it in phase one

Repo-specific minimum baseline:

- enough Node tooling to work on `lab-erp-demo`
- enough Java tooling to work on `erp-backend`
- enough Docker tooling to build images and run Compose from inside the workspace container

## Operational Workflow

### Code Maintenance

- enter `zhangqi`
- edit `/workspace/ZKR`
- run project-specific commands

### Deployment Update

- build updated images from `/workspace/ZKR`
- push images if using the private registry workflow
- restart or recreate the runtime stack

### Workspace Lifecycle

The `zhangqi` container is expected to be long-lived, but its filesystem contents are not the source of truth.

Lifecycle rules:

- if `zhangqi` is stopped, the operator restarts it and re-attaches
- if `zhangqi` is rebuilt, the host-mounted source tree and runtime access pattern remain the same
- the startup/attach procedure must be documented explicitly, not left implicit

### Boundary Rules

- `zhangqi` may control the runtime stack through Docker/Compose commands because that is part of the intended maintenance workflow
- secrets and real `.env` files remain stored in the host workspace, not baked into the workspace image
- runtime application containers remain the only place where the business services actually run
- direct editing inside runtime containers is forbidden as a maintenance strategy
- compose ownership should stay with the workspace source tree under `/workspace/ZKR`, so all runtime updates are issued from the same canonical project directory

### Runtime Separation Rule

Do not treat runtime application containers as code-editing environments.

- workspace container = edit and operate
- runtime containers = run the app

## Teaching Notes

The user should understand this distinction clearly:

- source tree = real files
- workspace container = tool-rich place to work on those files
- image = packaged template built from those files
- runtime container = running service created from the image

## Acceptance Criteria

- A persistent workspace container named `zhangqi` exists.
- The server-side source tree is mounted into the workspace container.
- The user can SSH into the host and then attach into `zhangqi` to work.
- The workspace container can edit source and control runtime containers.
- Runtime containers remain separate from the workspace environment.
- Deployment docs are updated to reflect the workspace-container model.
- Editing inside `zhangqi` persists to the host bind-mounted workspace without permission errors.
- Restarting or recreating `zhangqi` does not destroy the source tree because the source tree is host-mounted.
- The runtime stack can still be rebuilt/restarted from inside `zhangqi` without switching back to the local PC.

## Next Step

Create an implementation plan that upgrades the current mother-container assets into a persistent workspace-container workflow, updates docs, and defines the exact operational commands for SSH attach, code maintenance, and runtime stack control.
