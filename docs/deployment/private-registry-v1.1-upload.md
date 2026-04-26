# 私有镜像仓库发布说明（通用版）

## 1. 用途
- 将前后端镜像发布到服务器本机私有 Docker Registry
- 用规范 tag 切换运行版本
- 保持 runtime 容器与 workspace 容器流程一致

## 2. 当前约定
- 私有仓库地址：`127.0.0.1:5555`
- 前端镜像：`127.0.0.1:5555/zhangqi_frontend:<tag>`
- 后端镜像：`127.0.0.1:5555/zhangqi_backend:<tag>`
- 版本 tag 使用显式版本号，如 `v1.5`、`v1.6`
- 不要复用旧 tag 覆盖不同内容

## 3. 前端发布
```bash
docker build --build-arg APP_VERSION=<version> -t 127.0.0.1:5555/zhangqi_frontend:<tag> ./lab-erp-demo
docker push 127.0.0.1:5555/zhangqi_frontend:<tag>
docker compose up -d lab-erp-demo
```

## 4. 后端发布
```bash
docker build -t 127.0.0.1:5555/zhangqi_backend:<tag> ./erp-backend
docker push 127.0.0.1:5555/zhangqi_backend:<tag>
docker compose up -d erp-backend
```

## 5. 发布后检查
```bash
docker compose ps
docker inspect zkr-lab-erp-demo --format '{{.Config.Image}}'
docker inspect zkr-erp-backend --format '{{.Config.Image}}'
curl http://127.0.0.1:5555/v2/zhangqi_frontend/tags/list
curl http://127.0.0.1:5555/v2/zhangqi_backend/tags/list
curl -I http://127.0.0.1:8080/login
curl -I http://127.0.0.1:8080/erp-login
```

## 6. 回滚
- 将 `docker-compose.yml` 中对应服务的镜像 tag 改回上一个稳定版本
- 执行 `docker compose up -d <service>`

## 7. 说明
- 这份文档替代旧的固定 `v1.1` 发布说明
- 具体运行环境、compose 结构、回滚镜像标签与当前部署入口以 `workflow.md` 和 `docs/deployment/docker-mother-child.md` 为准；不要把该文件名理解为当前仍在使用母子容器
