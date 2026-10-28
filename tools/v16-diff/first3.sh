#!/bin/bash
# `first` 상태에서 비밀 키를 쓰지 못하게 서버를 띄운다: v1.6는 외부 설정 파일, v2는 쓰기가 막힌 설정 폴더.
R=/home/jiho/yona-convert
HERE=$(cd "$(dirname "$0")" && pwd)
"$HERE/servers.sh" restore first >/dev/null 2>&1
"$HERE/servers.sh" stop >/dev/null 2>&1
mkdir -p "$R/yona-diff-v2/data/config" && chmod 555 "$R/yona-diff-v2/data/config"
# v1.6의 "외부 파일" 판정은 설치 폴더 밖의 파일이다
mkdir -p "$R/yona-diff-work/ext" && cp "$R/yona-v1.16-release/yona-1.16.0/conf/application.conf" "$R/yona-diff-work/ext/application.conf"
V16_EXTRA="-Dconfig.file=$R/yona-diff-work/ext/application.conf" "$HERE/servers.sh" start
