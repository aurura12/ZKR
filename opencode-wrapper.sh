#!/bin/bash
BASE="$HOME/zhangqi"
case "$(pwd)" in
  $BASE/*|$BASE) ;;
  *)
    echo "opencode is only available inside ~/zhangqi"
    exit 1
  ;;
esac
"$BASE/node_modules/.bin/opencode" "$@"
