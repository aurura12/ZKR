#!/usr/bin/env sh
set -eu

WORKSPACE_DIR="/srv/zhangqi/workspace"
DOCKER_SOCKET="/var/run/docker.sock"
CONTAINER_NAME="zhangqi"
IMAGE_NAME="zhangqi-workspace"

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

if docker container inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
  if [ "$(docker container inspect -f '{{.State.Running}}' "${CONTAINER_NAME}")" != "true" ]; then
    docker start "${CONTAINER_NAME}" >/dev/null
  fi
else
  docker run -d \
    -v "${WORKSPACE_DIR}:/workspace" \
    -v "${DOCKER_SOCKET}:${DOCKER_SOCKET}" \
    -w /workspace \
    --name "${CONTAINER_NAME}" \
    "${IMAGE_NAME}" \
    tail -f /dev/null >/dev/null
fi

docker exec -it "${CONTAINER_NAME}" bash
