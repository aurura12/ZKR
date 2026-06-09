# Docker 常用命令（ZKR 项目）

适用环境：Windows + PowerShell，项目目录 `E:\AAA-intern\ZKR`

## 1) 基础启动与停止

```powershell
# 启动全部服务（后台）
docker compose up -d

# 查看服务状态
docker compose ps

# 停止全部服务（保留容器）
docker compose stop

# 停止并删除容器/网络（保留卷）
docker compose down

# 停止并删除容器/网络/卷（会清空数据库数据）
docker compose down -v
```

## 2) 按服务操作（最常用）

```powershell
# 只重启前端
docker compose up -d --force-recreate lab-erp-demo

# 只重启后端
docker compose up -d --force-recreate erp-backend

# 只停前端（本地 npm run dev 时常用）
docker compose stop lab-erp-demo

# 只启动前端
docker compose up -d lab-erp-demo

# 可选：停掉 RAG 三件套（省资源）
docker compose stop finance-rag-api finance-rag-redis finance-rag-qdrant
```

## 3) 日志查看

```powershell
# 查看某服务最近日志
docker compose logs --tail=200 erp-backend
docker compose logs --tail=200 lab-erp-demo

# 持续跟踪日志
docker compose logs -f erp-backend
docker compose logs -f lab-erp-demo

# 查看单个容器日志
docker logs zkr-erp-backend --tail 200
docker logs zkr-lab-erp-demo --tail 200
```

## 4) 镜像构建与重建

```powershell
# 构建后端镜像
docker build -t 127.0.0.1:5555/zhangqi_backend:v1.98 .\erp-backend

# 构建前端镜像
docker build -t 127.0.0.1:5555/zhangqi_frontend:v1.134 .\lab-erp-demo

# 不使用缓存重建（排查问题时）
docker build --no-cache -t 127.0.0.1:5555/zhangqi_backend:v1.98 .\erp-backend
docker build --no-cache -t 127.0.0.1:5555/zhangqi_frontend:v1.134 .\lab-erp-demo
```

## 5) 健康检查与排错

```powershell
# 看所有运行容器和端口映射
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 看容器退出码/错误信息
docker inspect zkr-lab-erp-demo --format "{{.State.ExitCode}} {{.State.Error}}"
docker inspect zkr-erp-backend --format "{{.State.ExitCode}} {{.State.Error}}"

# 查看占用 8080 的进程
netstat -ano | findstr :8080

# 查哪个容器占用了 8080
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

## 6) 数据库相关（Postgres）

```powershell
# 进入 postgres 容器执行 SQL
docker exec -it zkr-postgres-1 psql -U postgres -d postgres

# 直接执行一条 SQL（示例：查用户）
docker exec zkr-postgres-1 psql -U postgres -d postgres -c "select user_id, username, role from sys_user;"
```

## 7) 清理（磁盘空间不够时）

```powershell
# 清理停止的容器、悬空镜像、未使用网络
docker system prune -f

# 额外清理未使用卷（谨慎）
docker volume prune -f

# 查看 Docker 占用
docker system df
```

## 8) 当前项目常见问题速查

```powershell
# 前端容器反复重启
docker logs zkr-lab-erp-demo --tail 200

# 后端接口访问异常
docker logs zkr-erp-backend --tail 200

# 改了配置后快速生效
docker compose up -d --force-recreate lab-erp-demo erp-backend
```

## 9) 开发模式（前端热更新）专用

### 9.1 推荐模式：后端用 Docker，前端本地 `npm run dev`

```powershell
# 进入项目根目录
cd E:\AAA-intern\ZKR

# 保留后端/数据库，停掉前端容器（避免你误访问旧页面）
docker compose stop lab-erp-demo

# 确认后端还在运行
docker compose ps

# 启动前端本地开发服务（热更新）
cd .\lab-erp-demo
npm install
npm run dev
```

访问地址通常是：
- `http://localhost:5173`

说明：
- 这个模式下，前端改代码保存后会自动热更新。
- 如果你本地前端请求 API 失败，通常是 `vite` 代理目标没对上当前后端地址。

### 9.2 纯 Docker 模式（无热更新，最接近部署）

```powershell
cd E:\AAA-intern\ZKR
docker compose up -d
```

说明：
- 该模式适合联调/演示/部署验证。
- 改代码后需要重建镜像或重建服务才会生效，不会自动热更新。

### 9.3 两个模式之间快速切换

```powershell
# 切到开发模式（前端本地）
docker compose stop lab-erp-demo
cd E:\AAA-intern\ZKR\lab-erp-demo
npm run dev

# 切回纯 Docker 模式
cd E:\AAA-intern\ZKR
docker compose up -d lab-erp-demo
```

### 9.4 你当前常用模式（前后端都本地）

```powershell
# 1) 先保证依赖容器运行（数据库必需，RAG按需）
cd E:\AAA-intern\ZKR
docker compose up -d postgres finance-rag-redis finance-rag-qdrant finance-rag-api

# 2) 停掉应用容器，避免端口冲突
docker compose stop lab-erp-demo erp-backend

# 3) 启动后端（新开终端）
cd E:\AAA-intern\ZKR\erp-backend
$env:JWT_SECRET="uihsadfkjnvzlksdjfiohouiyturydfhghjhkkb"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/postgres"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO="update"
mvn -s settings-aliyun.xml spring-boot:run

# 4) 启动前端（再开一个新终端）
cd E:\AAA-intern\ZKR\lab-erp-demo
npm run dev
```

---

## 10) 电脑重启后开工（直接照抄）

### 10.1 开发模式（推荐：前后端本地）

```powershell
# 终端 A：依赖容器
cd E:\AAA-intern\ZKR
docker compose up -d postgres finance-rag-redis finance-rag-qdrant finance-rag-api
docker compose stop lab-erp-demo erp-backend

# 终端 B：后端本地（端口 8081，避免与 Docker 8080 冲突）
cd E:\AAA-intern\ZKR\erp-backend
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"

# 终端 C：前端本地
cd E:\AAA-intern\ZKR\lab-erp-demo
npm run dev
```

访问地址：
- 前端：`http://localhost:5173/erp-login`（ERP）
- 后端：`http://localhost:8101`

### 10.2 如果今天只想跑 Docker（不本地开发）

```powershell
cd E:\AAA-intern\ZKR
docker compose up -d
```

访问地址：
- `http://localhost:8080/erp-login`
- `http://localhost:8080/login`

---

备注：
- 你的前端入口默认是 `http://localhost:8080/erp-login` 和 `http://localhost:8080/login`。
- 如果 `8080` 被占用，改 `.env` 的 `FRONTEND_PUBLIC_PORT` 后重建前端服务。
