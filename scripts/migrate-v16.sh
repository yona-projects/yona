#!/usr/bin/env bash
# v1.6 데이터베이스를 이어받을 때 필요한 데이터 보정 4건을 한 번에 돌린다.
#   1) 풀 리퀘스트 번호 정리  2) 옛 사용자 토큰 이관  3) 리뷰어 설정 이관  4) 승인 리뷰 채우기
# 반드시 서버를 끈 상태에서 실행한다. 몇 번을 돌려도 결과가 같다. 서버와 같은 설정(DB 접속, yona.data-dir)을 써야 한다.
#
# 사용법: scripts/migrate-v16.sh <yona.jar 경로> [서버를 시작할 때 쓰던 --옵션 ...]
#   예)  scripts/migrate-v16.sh build/libs/yona-2.0.0.jar --spring.config.additional-location=file:/etc/yona/
# 종료 코드: 0 성공, 1 실패(실패하면 아무것도 바꾸지 않는다), 2 사용법 오류
set -euo pipefail

if [ "$#" -lt 1 ] || [ ! -f "$1" ]; then
  echo "사용법: $0 <yona.jar 경로> [서버 옵션 ...]" >&2
  exit 2
fi

JAR="$1"
shift

# 웹 서버는 임의 포트로 잠깐 떴다가 이관이 끝나면 종료된다(웹 계층을 끄면 MCP 연결 빈이 시작하지 못한다)
exec java -jar "$JAR" --yona.migrate=v16 --server.port=0 "$@"
