#!/usr/bin/env bash
# 생성한 적용 스크립트를 컨테이너(root)에서 실제로 실행해 계정, 파일, 소유권, 기록, 되돌리기를 확인한다.
# systemd가 없는 컨테이너라서 `systemctl`은 가짜 실행 파일로 대신한다(호출 기록만 남긴다).
# 사용법: apply-in-container.sh [이미지, 기본 ubuntu:24.04]
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
YI_DIR="$(cd "$HERE/../.." && pwd)"
IMAGE="${1:-ubuntu:24.04}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
fail=0
check() { if [ "$2" = "$3" ]; then echo "  ok   $1"; else echo "  FAIL $1: 기대 [$2] 실제 [$3]"; fail=1; fi; }

export YI_DIR
# shellcheck disable=SC1091
. "$YI_DIR/lib/common.sh"; . "$YI_DIR/lib/answers.sh"; . "$YI_DIR/lib/render.sh"; . "$YI_DIR/lib/apply.sh"

mkdir -p "$WORK/stage/conf" "$WORK/stage/bin"
printf 'fake-jar' > "$WORK/stage/yona.jar"
ans_set env.os linux; ans_set install_dir /opt/yona; ans_set data_dir /var/lib/yona; ans_set version v2.0.0
ans_set service.register true; ans_set db.type h2; ans_set web.port 8080
ans_set env.stage /stage; ans_set env.record_dir /var/log/yona-install; ans_set env.java /usr/bin/java
render_application_yml > "$WORK/stage/conf/application.yml"
printf 'spring.datasource.password=x\n' > "$WORK/stage/conf/secrets.properties"
render_systemd_unit > "$WORK/stage/yona.service"
printf '#!/bin/sh\necho start\n' > "$WORK/stage/bin/yona-start.sh"
printf 'report' > "$WORK/stage/INSTALL-REPORT.md"; printf 'v2.0.0' > "$WORK/stage/VERSION"
render_apply_script > "$WORK/apply.sh"
# 실패를 흉내 내는 변형: systemctl enable이 실패하면 되돌려져야 한다
ans_set service.register true
render_apply_script > "$WORK/apply-fail.sh"

# 가짜 systemctl: 호출을 기록하고, FAIL_ENABLE이 있으면 enable에서 실패한다
cat > "$WORK/systemctl" <<'EOT'
#!/bin/sh
echo "systemctl $*" >> /tmp/systemctl.log
if [ -n "${FAIL_ENABLE:-}" ] && [ "$1" = "enable" ]; then exit 1; fi
exit 0
EOT
chmod +x "$WORK/systemctl"

run() { docker run --rm -v "$WORK:/w" -v "$WORK/stage:/stage:ro" -e FAIL_ENABLE="${2:-}" "$IMAGE" bash -c "cp /w/systemctl /usr/local/bin/systemctl; $1"; }

echo "== 성공 경로"
out="$(run '
bash /w/apply.sh >/tmp/out.log 2>&1; echo "exit=$?"
echo "user=$(id -u yona >/dev/null 2>&1 && echo yes || echo no)"
echo "jar=$(ls /opt/yona/yona.jar 2>/dev/null)"
echo "owner_data=$(stat -c %U:%G /var/lib/yona)"
echo "conf_mode=$(stat -c %a /opt/yona/conf)"
echo "secrets_mode=$(stat -c %a:%U:%G /opt/yona/conf/secrets.properties)"
echo "unit=$(ls /etc/systemd/system/yona.service 2>/dev/null)"
echo "record=$(grep -c "version=v2.0.0" /var/log/yona-install/install.rec)"
echo "enable=$(grep -c "enable --now yona" /tmp/systemctl.log)"
')"
echo "$out" | sed 's/^/    /'
check "종료 코드" "exit=0" "$(echo "$out" | grep '^exit=')"
check "전용 계정" "user=yes" "$(echo "$out" | grep '^user=')"
check "jar 설치" "jar=/opt/yona/yona.jar" "$(echo "$out" | grep '^jar=')"
check "데이터 폴더 소유권" "owner_data=yona:yona" "$(echo "$out" | grep '^owner_data=')"
check "conf 폴더 권한" "conf_mode=750" "$(echo "$out" | grep '^conf_mode=')"
check "비밀 값 파일 권한" "secrets_mode=640:root:yona" "$(echo "$out" | grep '^secrets_mode=')"
check "유닛 파일" "unit=/etc/systemd/system/yona.service" "$(echo "$out" | grep '^unit=')"
check "설치 기록" "record=1" "$(echo "$out" | grep '^record=')"
check "서비스 켜기" "enable=1" "$(echo "$out" | grep '^enable=')"

echo "== 실패하면 이번 실행이 만든 것만 되돌린다"
out="$(run '
mkdir -p /preexisting && echo keep > /preexisting/file
bash /w/apply-fail.sh >/tmp/out.log 2>&1; echo "exit=$?"
echo "user=$(id -u yona >/dev/null 2>&1 && echo yes || echo no)"
echo "install_dir=$(ls -d /opt/yona 2>/dev/null)"
echo "data_dir=$(ls -d /var/lib/yona 2>/dev/null)"
echo "unit=$(ls /etc/systemd/system/yona.service 2>/dev/null)"
echo "preexisting=$(cat /preexisting/file)"
echo "rolled=$(ls /var/log/yona-install/ | grep -c rolled-back)"
' FAIL_ENABLE=1)"
echo "$out" | sed 's/^/    /'
check "실패 종료 코드" "exit=1" "$(echo "$out" | grep '^exit=')"
check "계정 되돌림" "user=no" "$(echo "$out" | grep '^user=')"
check "설치 폴더 되돌림" "install_dir=" "$(echo "$out" | grep '^install_dir=')"
check "데이터 폴더 되돌림" "data_dir=" "$(echo "$out" | grep '^data_dir=')"
check "유닛 되돌림" "unit=" "$(echo "$out" | grep '^unit=')"
check "이미 있던 파일 보존" "preexisting=keep" "$(echo "$out" | grep '^preexisting=')"
check "기록 보관" "rolled=1" "$(echo "$out" | grep '^rolled=')"

[ "$fail" -eq 0 ] && echo "통합 시험 통과" || echo "통합 시험 실패"
exit "$fail"
