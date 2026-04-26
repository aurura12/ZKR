#!/usr/bin/env sh
set -eu

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

docker exec -it zhangqi bash
