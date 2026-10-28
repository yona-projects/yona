#!/bin/bash
# 알림 메일 설정에 따라 메일이 어떻게 달라지는지 대조한다: 받는 사람 숨김 끔, 받는 사람 수 제한, 메일 끔. 각 설정마다 data3로 되돌려 mail1을 돌리고 mailcompare로 메일을 견준다.
cd "$(dirname "$0")" || exit 1
WORK=/home/jiho/yona-convert/yona-diff-work
OUT=/home/jiho/yona-convert/yona-diff-v2
run() { # 이름 v16옵션 v2옵션
  printf '%s' "$2" > "$WORK/cfg.v16"; printf '%s' "$3" > "$WORK/cfg.v2"
  ./servers.sh restore data3 >/dev/null 2>&1
  PYTHONPATH=scenarios python3 harness.py run mail1 --out "$OUT/report-cfgmail-$1.md" --top 30 >/dev/null 2>&1
  sleep 8
  echo "== $1: v1.6 메일 $(grep -c . $WORK/mails-v16.jsonl) 통, v2 메일 $(grep -c . $WORK/mails-v2.jsonl) 통"
  python3 mailcompare.py --out "$OUT/report-cfgmail-$1-mails.md" 2>&1 | tail -3
}
run base "" ""
run show_addr "-Dapplication.notification.bymail.hideAddress=false" "--yona.notification-mail-hide-address=false"
run limit2 "-Dapplication.notification.bymail.recipientLimit=2" "--yona.notification-mail-recipient-limit=2"
run limit1_show "-Dapplication.notification.bymail.recipientLimit=1 -Dapplication.notification.bymail.hideAddress=false" "--yona.notification-mail-recipient-limit=1 --yona.notification-mail-hide-address=false"
run mail_off "-Dnotification.bymail.enabled=false" "--yona.notification-mail-enabled=false"
run delay_long "-Dapplication.notification.bymail.delay=60s" "--yona.notification-mail-delay=60s"
run draft_long "-Dapplication.notification.draft-time=120s" "--yona.notification-draft-time=120s"
: > "$WORK/cfg.v16"; : > "$WORK/cfg.v2"
./servers.sh restore data2 >/dev/null 2>&1
