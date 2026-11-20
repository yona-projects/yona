#!/usr/bin/env bash
# Yona 설치 프로그램(Linux, macOS): 신규 설치, 업그레이드, v1.6 이관, 제거.
# 사용법: yona-install.sh [install|uninstall|help] [옵션...]  (옵션은 `help`를 보세요)
# 같은 폴더의 `lib/`, `messages/`, `templates/`와 jar를 쓰므로 폐쇄 환경에서는 이 폴더째 옮겨 실행한다.
set -u
YI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export YI_DIR
for f in common messages answers releases render plan preflight record env prompt apply upgrade flow; do
  # shellcheck disable=SC1090
  . "$YI_DIR/lib/$f.sh"
done
flow_main "$@"
