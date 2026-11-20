#!/usr/bin/env bash
# 호스트의 포트가 열릴 때까지 기다린다. 서비스가 데이터베이스보다 먼저 시작하지 않게 `ExecStartPre`에서 부른다.
# 사용법: wait-for-port.sh <호스트> <포트> [제한 시간(초), 기본 120]
host="${1:?host}"
port="${2:?port}"
limit="${3:-120}"
waited=0
while [ "$waited" -lt "$limit" ]; do
  if (exec 3<>"/dev/tcp/$host/$port") 2>/dev/null; then
    exit 0
  fi
  sleep 2
  waited=$((waited + 2))
done
echo "port $host:$port is not reachable after ${limit}s" >&2
exit 1
