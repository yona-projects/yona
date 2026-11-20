# shellcheck shell=bash
# shellcheck disable=SC2016  # 다른 스크립트의 본문을 만드는 코드라 작은따옴표 안의 $를 일부러 펼치지 않는다
# 업그레이드와 제거 스크립트를 만든다. 적용 스크립트처럼 관리자 권한이 필요하면 한 번에 격상해서 실행한다.
# 입력은 답과 `env.stage`, `env.record_dir`, `env.old_version`이다.

# 서비스를 멈추고 시작하는 명령(Linux는 systemd, Mac은 launchd, 서비스가 없으면 시작과 중지 스크립트)을 돌려준다.
# 사용법: _yi_service_cmd <stop|start>
_yi_service_cmd() {
  if [ "$(ans_get service.register)" = "true" ]; then
    if [ "$(ans_get env.os linux)" = "mac" ]; then
      case "$1" in
        stop) printf 'launchctl bootout system/dev.yona.server' ;;
        start) printf 'launchctl bootstrap system /Library/LaunchDaemons/dev.yona.server.plist' ;;
      esac
    else
      printf 'systemctl %s yona' "$1"
    fi
  else
    printf '"$INSTALL_DIR/bin/yona-%s.sh"' "$1"
  fi
}

# 업그레이드 스크립트의 내용이다. 서비스를 멈추고, 옛 jar를 보관하고, 새 jar를 두고, 시작해 상태를 확인한다.
# 확인에 실패하면 옛 jar로 되돌려 다시 시작한다(서버가 이미 스키마를 바꿨다면 옛 jar가 못 뜰 수 있다).
render_upgrade_script() {
  cat <<EOS
#!/usr/bin/env bash
# Yona 업그레이드 스크립트: yona-install이 만들었다.
set -u
STAGE=$(printf '%q' "$(ans_get env.stage)")
RECORD_DIR=$(printf '%q' "$(ans_get env.record_dir)")
INSTALL_DIR=$(printf '%q' "$(ans_get install_dir)")
OLD=$(ans_get env.old_version)
NEW=$(ans_get version)
PORT=$(ans_get web.port 8080)

health() {
  local i
  for i in \$(seq 1 150); do
    curl -fsS "http://127.0.0.1:\$PORT/-_-actuator/health" 2>/dev/null | grep -q '"status":"UP"' && return 0
    sleep 2
  done
  return 1
}

restore() {
  echo "[yona-install] restore the previous jar" >&2
  $(_yi_service_cmd stop) || true
  mv -f "\$INSTALL_DIR/yona-\$OLD.jar.bak" "\$INSTALL_DIR/yona.jar"
  $(_yi_service_cmd start) || true
}

echo "[yona-install] stop"
$(_yi_service_cmd stop) || { echo "[yona-install] could not stop Yona" >&2; exit 1; }
echo "[yona-install] replace the jar"
cp -p "\$INSTALL_DIR/yona.jar" "\$INSTALL_DIR/yona-\$OLD.jar.bak" || { $(_yi_service_cmd start); exit 1; }
cp "\$STAGE/yona.jar" "\$INSTALL_DIR/yona.jar" || { restore; exit 1; }
echo "[yona-install] start"
$(_yi_service_cmd start) || { restore; exit 1; }
if health; then
  echo "\$NEW" > "\$INSTALL_DIR/VERSION"
  if [ -f "\$RECORD_DIR/install.rec" ]; then
    sed -i.bak "s/^version=.*/version=\$NEW/" "\$RECORD_DIR/install.rec" && rm -f "\$RECORD_DIR/install.rec.bak"
  fi
  echo "[yona-install] upgraded \$OLD -> \$NEW (version=$(ans_get version))"
  exit 0
fi
echo "[yona-install] health check failed after the upgrade" >&2
restore
exit 1
EOS
}

# 제거 스크립트의 내용이다. 서비스를 끄고 설치한 파일을 지운다. 데이터 폴더와 DB는 기본으로 남기고
# `--purge`일 때만 지운다(컨테이너 DB와 볼륨, 서비스 계정 포함).
render_uninstall_script() {
  local tool
  tool="$(ans_get env.containers docker)"
  cat <<EOS
#!/usr/bin/env bash
# Yona 제거 스크립트: yona-install이 만들었다.
set -u
RECORD_DIR=$(printf '%q' "$(ans_get env.record_dir)")
INSTALL_DIR=$(printf '%q' "$(ans_get install_dir)")
DATA_DIR=$(printf '%q' "$(ans_get data_dir)")
EOS
  if [ "$(ans_get service.register)" = "true" ]; then
    if [ "$(ans_get env.os linux)" = "mac" ]; then
      cat <<'EOS'
launchctl bootout system/dev.yona.server 2>/dev/null || true
rm -f /Library/LaunchDaemons/dev.yona.server.plist
EOS
    else
      cat <<'EOS'
systemctl disable --now yona 2>/dev/null || true
rm -f /etc/systemd/system/yona.service
systemctl daemon-reload 2>/dev/null || true
EOS
    fi
  else
    printf '"$INSTALL_DIR/bin/yona-stop.sh" 2>/dev/null || true\n'
  fi
  cat <<'EOS'
rm -f "$INSTALL_DIR/yona.jar" "$INSTALL_DIR"/yona-*.jar.bak "$INSTALL_DIR/VERSION" "$INSTALL_DIR/INSTALL-REPORT.md"
rm -rf "$INSTALL_DIR/conf" "$INSTALL_DIR/bin"
rmdir "$INSTALL_DIR" 2>/dev/null || true
EOS
  if [ "$(ans_get purge)" = "true" ]; then
    printf 'rm -rf "$DATA_DIR"\n'
    if [ "$(ans_get db.install)" = "together" ] && [ "$(ans_get db.method)" = "container" ]; then
      printf '%s rm -fv yona-db 2>/dev/null || true\n%s volume rm yona-db-data 2>/dev/null || true\n' "$tool" "$tool"
    fi
    if [ "$(ans_get service.register)" = "true" ] && [ "$(ans_get env.os linux)" != "mac" ]; then
      printf 'userdel yona 2>/dev/null || true\n'
    fi
  fi
  printf 'rm -f "$RECORD_DIR/install.rec" "$RECORD_DIR/journal.log"\necho "[yona-install] uninstalled"\n'
}
