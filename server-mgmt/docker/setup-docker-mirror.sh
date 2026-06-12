#!/bin/bash
# 在本机配置 Docker 镜像加速器（需 sudo）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="/etc/docker/daemon.json"

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 docker，请先安装 Docker。" >&2
  exit 1
fi

sudo mkdir -p /etc/docker

if [ -f "$TARGET" ]; then
  BACKUP="${TARGET}.bak.$(date +%Y%m%d%H%M%S)"
  echo "备份现有配置 → $BACKUP"
  sudo cp "$TARGET" "$BACKUP"
fi

sudo cp "$SCRIPT_DIR/daemon.json.example" "$TARGET"
echo "已写入 $TARGET"

sudo systemctl daemon-reload
sudo systemctl restart docker

echo ""
echo "当前 Registry Mirrors:"
docker info 2>/dev/null | grep -A6 "Registry Mirrors" || sudo docker info | grep -A6 "Registry Mirrors"
echo ""
echo "完成。可执行: sudo docker compose up -d --build"
