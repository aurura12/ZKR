# Docker Mother-Child Deployment Design

> Historical note (2026-04-13): this design documents an abandoned mother-container / child-container deployment idea. Preserve it only as background. Current operations should follow `workflow.md`, `docker-compose.yml`, and the rewritten `docs/deployment/docker-mother-child.md` direct-Compose guide instead.

## Context

- The project currently has no `Dockerfile`, `.dockerignore`, or `docker-compose.yml`.
- The frontend is `lab-erp-demo` based on Vite + Vue 3.
- The backend is `erp-backend` based on Spring Boot + PostgreSQL.
- The user has not used Docker before and wants deployment guidance as part of the work.
- The user explicitly wants a personal enclosed environment on the server first, using a mother-container plus child-container structure.
- The user wants the CLI inside the mother space to have relatively high operational power, but to stay constrained to the personal workspace boundary instead of the full server.

## Goal

Create a deployment model where the user operates from a personal mother-container workspace, and that mother-container manages child containers for frontend, backend, and PostgreSQL using Docker Compose, with teaching-oriented documentation and a deployment flow suitable for a first-time Docker user.

## Chosen Approach

Use a host-level personal workspace directory, a dedicated mother-container as the operations shell, and a child-container application stack managed with Docker Compose.

- The mother-container is a personal CLI workspace, not a business-serving container.
- The child containers run the actual application services.
- Docker Compose orchestrates child containers.
- Shared host-mounted project/runtime directories allow the mother-container CLI to manage deployment assets without exposing the entire host filesystem.

## Why This Approach

- It gives the user a clear and contained working environment.
- It matches the user’s requested “mother-container + child-container” mental model.
- It is easier to teach and maintain than full Docker-in-Docker.
- It avoids running the business system directly inside the mother-container.

## Scope

### In Scope

- Mother-container design and startup method
- Backend Docker image design for `erp-backend`
- Frontend Docker image design for `lab-erp-demo`
- PostgreSQL child-container setup
- Compose-based child-container orchestration
- Runtime directory and volume strategy
- Beginner-friendly deployment instructions

### Out of Scope

- Kubernetes or container orchestration beyond Compose
- Full production hardening beyond the first usable deployment baseline
- CI/CD pipeline automation
- Public registry publishing as a requirement for first deployment

## Architecture Overview

### Host Machine

The host machine provides:

- Docker Engine
- one personal workspace root for the user
- persistent storage for application data and configuration

Recommended root:

- `/srv/<user-space>/`

### Mother-Container

The mother-container is the user’s operations shell.

Responsibilities:

- provide CLI tools such as shell, git, docker cli, docker compose, curl, and basic diagnostics
- mount only the user’s personal workspace root
- serve as the default place where the user runs deployment commands
- edit deployment files and inspect logs/data inside the allowed workspace

Host Docker access model:

- the mother-container will use the host Docker daemon through a mounted Docker socket
- this is preferred over Docker-in-Docker for the first deployment baseline
- implementation consequence: the mother-container can manage child containers running on the host Docker engine while still operating from the personal mounted workspace

Non-responsibilities:

- does not serve frontend traffic
- does not run the backend business process directly
- does not contain PostgreSQL data as its main runtime function

### Child Containers

The child-container stack contains:

- `postgres`
- `erp-backend`
- `lab-erp-demo`

These containers communicate through an internal Docker network.

## Workspace and Directory Layout

Recommended host layout:

- `/srv/<user-space>/workspace/` - project source and deployment files
- `/srv/<user-space>/runtime/` - runtime material
- `/srv/<user-space>/runtime/postgres-data/` - PostgreSQL persistent data
- `/srv/<user-space>/runtime/logs/` - logs if file-based logging is enabled
- `/srv/<user-space>/mother-shell/` - mother-container definition and bootstrap assets

The mother-container should mount at least:

- `/srv/<user-space>/workspace/`
- `/srv/<user-space>/runtime/`

It should not mount unrestricted host paths such as the full root filesystem.

## Permission Model

### Mother-Container Permissions

The mother-container should have high operational usefulness inside the personal workspace.

Desired capabilities:

- read and write project files in the user workspace
- invoke `docker` and `docker compose`
- inspect child-container logs and status
- rebuild and restart the child stack

Constraint model:

- do not mount the whole host filesystem
- do not make unrelated server paths part of the mother-container workspace
- keep the mother-container’s working directory and mounted scope inside the personal workspace root

Important limitation:

This is an engineering boundary, not a strong security sandbox. The goal is practical isolation of working scope, not military-grade containment.

### Child-Container Permissions

Child containers should use narrower mounts than the mother-container.

- `postgres` mounts only persistent database storage
- `erp-backend` mounts configuration, uploads storage, and optional log directories only when needed
- `lab-erp-demo` preferably serves built assets and should not need broad source-code mounts in the deployment target

Backend file persistence requirement:

- the current backend uses a local `uploads` directory for file storage and static exposure
- the deployment must therefore provide a persistent uploads path for `erp-backend`
- recommended runtime path:
  - `/srv/<user-space>/runtime/uploads/`

## Image Design

### Mother-Container Image

The mother-container image should include:

- shell utilities
- docker cli
- docker compose plugin or equivalent
- git
- curl
- optional editors or convenience tools if lightweight

It should default into the mounted personal workspace.

### Backend Image

The backend image should:

- build the Spring Boot jar
- run with Java 17
- accept external environment variables for database and JWT config
- expose the backend service port

### Frontend Image

The frontend image should:

- build the Vite app
- serve the built static assets with a lightweight web server such as nginx
- expose the frontend service port
- forward API requests to the backend through runtime-configured upstream rules if needed

## Compose Design

The first deployment Compose stack should include:

- `postgres`
- `erp-backend`
- `lab-erp-demo`

Recommended Compose behaviors:

- internal bridge network for service-to-service traffic
- named or bind-mounted persistent storage for PostgreSQL
- service-name-based connectivity:
  - backend -> `postgres:5432`
  - frontend proxy -> backend service name

Recommended first-baseline exposure model:

- expose only the frontend container to the host network by default
- keep `erp-backend` internal to the compose network unless debugging requires temporary host publishing
- browser traffic should enter through the frontend container only
- frontend should use same-origin `/api` proxying to the backend to avoid beginner CORS complexity

Recommended first-baseline published ports:

- host `8080` -> frontend container http port
- PostgreSQL host publishing optional and off by default unless external DB tools are needed

## Environment Configuration

The deployment should externalize at least:

- PostgreSQL database name
- PostgreSQL username/password
- backend datasource URL
- JWT secret or equivalent auth secret
- finance account-domain allowlist/backfill config if still relevant to runtime startup
- frontend API upstream target if needed

Clarification for account-domain config:

- the finance account-domain allowlist/backfill setting is optional runtime configuration for first deployment
- it is only required if you still need startup-time migration of known finance pilot/test accounts on that environment
- if target data is already clean, this variable may be omitted

Recommended approach:

- one `.env` file for Compose-level configuration
- one backend runtime environment section for Spring Boot variables

Secrets handling boundary:

- the real `.env` file should live only on the server workspace
- the repository should contain only an example template such as `.env.example` if needed
- secrets such as DB password and JWT secret must not be committed as real values

Schema strategy for first baseline:

- keep the current Spring Boot schema behavior unchanged for the first deployment baseline
- broader migration-tool adoption is out of scope for this round

## Data Flow in Deployment

### User Operations Flow

- SSH into server
- enter the mother-container
- run compose build/up/log commands from inside the mother-container

### Runtime Request Flow

- browser -> frontend container
- frontend container -> backend container
- backend container -> PostgreSQL container

## Teaching-Oriented Deployment Flow

The deployment documentation must explain these concepts simply:

- image = reusable container template
- container = running instance of an image
- volume = persistent data outside container lifecycle
- compose = one file that starts multiple cooperating containers

The first-run guide should walk through:

1. create personal workspace on the server
2. start the mother-container
3. enter the mother-container shell
4. prepare environment files
5. build child images
6. start the child stack
7. inspect status and logs
8. stop and restart safely

## Testing Strategy

### Build Verification

- backend image builds successfully
- frontend image builds successfully
- compose configuration validates successfully

### Runtime Verification

- PostgreSQL starts and persists data
- backend starts and can connect to PostgreSQL
- frontend starts and can reach backend APIs
- finance login still works in the deployed environment
- ERP login still works in the deployed environment

### Operational Verification

- mother-container can manage the child stack from the mounted workspace
- logs are inspectable from the mother-container
- restart flow works without data loss for PostgreSQL

## Acceptance Criteria

- A personal workspace exists on the server.
- A mother-container exists and acts as the primary CLI workspace.
- Child containers run frontend, backend, and PostgreSQL through Compose.
- The mother-container can manage the child stack from inside the personal workspace.
- Deployment documentation is understandable for a first-time Docker user.
- Finance and ERP auth flows still work after deployment.

## Next Step

Create an implementation plan that covers mother-container assets, backend/frontend Dockerfiles, compose orchestration, environment files, and beginner-oriented deployment documentation, then execute it incrementally with verification after each stage.
