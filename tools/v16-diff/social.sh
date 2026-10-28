#!/bin/bash
# 소셜 로그인 대조를 돌린다: 가짜 제공자(idp.py, 19700)를 띄우고 두 서버가 그것을 보게 한 뒤 social.py를 부른다.
#   social.sh [social.py 인자...]        서버 설정은 끝나면 되돌린다
#   EXTRA16, EXTRA2 환경 변수로 설정을 더할 수 있다(예: 이름 동기화)
cd "$(dirname "$0")" || exit 1
WORK=/home/jiho/yona-convert/yona-diff-work
nohup python3 idp.py 19700 "$WORK/idp.jsonl" > "$WORK/idp.log" 2>&1 &
IDPPID=$!
echo $IDPPID > "$WORK/idp.pid"
sleep 1
P=http://127.0.0.1:19700
printf '%s' "-Dplay-authenticate.github.authorizationUrl=$P/authorize -Dplay-authenticate.github.accessTokenUrl=$P/token -Dplay-authenticate.github.userInfoUrl=$P/user -Dplay-authenticate.google.authorizationUrl=$P/authorize -Dplay-authenticate.google.accessTokenUrl=$P/token -Dplay-authenticate.google.userInfoUrl=$P/userinfo -Dplay-authenticate.github.clientId=id -Dplay-authenticate.github.clientSecret=secret -Dplay-authenticate.google.clientId=id -Dplay-authenticate.google.clientSecret=secret ${EXTRA16:-}" > "$WORK/cfg.v16"
printf '%s' "--yona.social.github.authorization-url=$P/authorize --yona.social.github.access-token-url=$P/token --yona.social.github.user-info-url=$P/user --yona.social.google.authorization-url=$P/authorize --yona.social.google.access-token-url=$P/token --yona.social.google.user-info-url=$P/userinfo ${EXTRA2:-}" > "$WORK/cfg.v2"
./servers.sh restore data2 >/dev/null 2>&1
: > "$WORK/idp.jsonl"
python3 social.py "$@"
kill $IDPPID 2>/dev/null
: > "$WORK/cfg.v16"; : > "$WORK/cfg.v2"
