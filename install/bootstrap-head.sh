#!/usr/bin/env bash
# Yona 설치 부트스트랩(Linux, macOS): 고른 릴리스의 설치 묶음(yona-install.tar.gz)을 받아 sha256을 검증한 뒤 풀어서 실행한다.
# 이 파일은 install/build-release.sh가 lib/common.sh와 lib/releases.sh를 이어 붙여 만든 단독 파일이다(직접 고치지 않는다).
# 사용법: curl -fsSL <릴리스 자산 주소>/bootstrap.sh | bash -s -- [yona-install.sh 옵션...]
#        받아서 읽어 보고 실행하려면: bash bootstrap.sh [옵션...]
set -u
