# shellcheck shell=bash
# shellcheck disable=SC2016  # 다른 스크립트의 본문을 만드는 코드라 작은따옴표 안의 $를 일부러 펼치지 않는다
# 설치 적용 스크립트를 만든다. 관리자 권한이 필요한 단계를 이 스크립트 하나에 모아 한 번에 실행하면(sudo는 한 번만 묻는다)
# 권한이 필요 없는 구성은 같은 스크립트를 일반 사용자로 실행한다. 스크립트는 단계마다 "이번 실행이 만든 것"의 되돌리는 명령을
# 기록(journal)에 남기고, 실패하면 기록을 거꾸로 실행해 이번 실행이 만든 것만 지운다(이미 있던 것은 건드리지 않는다).
# `apply.sh rollback`으로 기록을 다시 되돌릴 수도 있다. 입력은 답과 `env.stage`(준비해 둔 파일 폴더), `env.record_dir`이다.

# 컨테이너 DB의 이미지이다(시험과 개발에서 쓰는 것과 같다. MariaDB는 결정된 기본 버전).
yi_db_image() {
  case "$1" in
    postgresql) printf 'postgres:16' ;;
    mariadb) printf 'mariadb:12.3.3' ;;
    mssql) printf 'mcr.microsoft.com/mssql/server:2022-latest' ;;
    cubrid) printf 'cubrid/cubrid:11.4' ;;
  esac
}

# 컨테이너 안에서 데이터가 쌓이는 경로이다.
_yi_db_data_path() {
  case "$1" in
    postgresql) printf '/var/lib/postgresql/data' ;;
    mariadb) printf '/var/lib/mysql' ;;
    mssql) printf '/var/opt/mssql' ;;
    cubrid) printf '/home/cubrid/CUBRID/databases' ;;
  esac
}

# 배포본의 패키지 관리자로 DB를 설치하는 명령이다.
_yi_db_package_install() {
  local type pkg
  type="$(ans_get db.type)"
  case "$(ans_get env.pkg apt)" in
    apt)
      case "$type" in postgresql) pkg="postgresql" ;; mariadb) pkg="mariadb-server" ;; esac
      printf 'DEBIAN_FRONTEND=noninteractive apt-get install -y %s' "$pkg"
      ;;
    dnf)
      case "$type" in postgresql) pkg="postgresql-server" ;; mariadb) pkg="mariadb-server" ;; esac
      printf 'dnf install -y %s' "$pkg"
      ;;
  esac
}

# DB 서비스 이름(systemd)이다.
_yi_db_service() {
  case "$(ans_get db.type)" in
    postgresql) printf 'postgresql' ;;
    mariadb) printf 'mariadb' ;;
  esac
}

# 스크립트 머리: 변수, 기록, 되돌리기 함수.
_yi_apply_header() {
  cat <<'EOS'
#!/usr/bin/env bash
# Yona 설치 적용 스크립트: yona-install이 만들었다. 관리자 권한이 필요한 단계를 모아 한 번에 실행한다.
# 사용법: apply.sh [apply|rollback]
set -u
EOS
  printf 'STAGE=%q\n' "$(ans_get env.stage)"
  printf 'RECORD_DIR=%q\n' "$(ans_get env.record_dir)"
  printf 'INSTALL_DIR=%q\n' "$(ans_get install_dir)"
  printf 'DATA_DIR=%q\n' "$(ans_get data_dir)"
  cat <<'EOS'
JOURNAL="$RECORD_DIR/journal.log"

# 되돌리는 명령을 기록에 더한다(실행하는 쪽이 거꾸로 읽는다).
j_add() { printf '%s\n' "$1" >> "$JOURNAL"; }

# 기록을 거꾸로 실행해 이번 실행이 만든 것만 되돌린다.
rollback() {
  [ -f "$JOURNAL" ] || return 0
  echo "[yona-install] rollback" >&2
  awk '{ a[NR] = $0 } END { for (i = NR; i > 0; i--) print a[i] }' "$JOURNAL" | while IFS= read -r cmd; do
    echo "  undo: $cmd" >&2
    eval "$cmd" || true
  done
  mv "$JOURNAL" "$JOURNAL.rolled-back" 2>/dev/null || true
}

die() {
  echo "[yona-install] FAILED: $1" >&2
  if [ "${YI_KEEP_ON_FAILURE:-0}" = "1" ]; then
    echo "[yona-install] --keep-on-failure: 되돌리지 않고 남깁니다. 기록: $JOURNAL" >&2
  else
    rollback
  fi
  exit 1
}

step() { echo "[yona-install] $1"; }

if [ "${1:-apply}" = "rollback" ]; then
  rollback
  exit 0
fi
mkdir -p "$RECORD_DIR" || die record-dir
: > "$JOURNAL"
EOS
}

# 서비스 전용 계정을 만든다(Linux). 없을 때만 만들고 그때만 기록한다.
_yi_apply_user() {
  cat <<'EOS'

step user
if ! id -u yona >/dev/null 2>&1; then
  useradd --system --no-create-home --home-dir "$DATA_DIR" --shell /usr/sbin/nologin yona || die user
  j_add 'userdel yona'
fi
EOS
}

# 설치 폴더와 데이터 폴더를 만들고 준비한 파일을 둔다.
_yi_apply_files() {
  cat <<'EOS'

step directories
if [ ! -d "$INSTALL_DIR" ]; then
  mkdir -p "$INSTALL_DIR" || die directories
  j_add "rm -rf $(printf '%q' "$INSTALL_DIR")"
else
  for f in yona.jar conf/application.yml conf/secrets.properties conf/application.properties INSTALL-REPORT.md VERSION; do
    j_add "rm -f $(printf '%q' "$INSTALL_DIR/$f")"
  done
fi
if [ ! -d "$DATA_DIR" ]; then
  mkdir -p "$DATA_DIR" || die directories
  j_add "rm -rf $(printf '%q' "$DATA_DIR")"
fi

step files
mkdir -p "$INSTALL_DIR/conf" "$INSTALL_DIR/bin" || die files
cp "$STAGE/yona.jar" "$INSTALL_DIR/yona.jar" || die files
# conf/ 아래 모든 파일을 둔다(application.yml, secrets.properties, 이관 변환기가 만든 application.properties). 비밀 값이 있어 소유자만 읽게 한다.
cp "$STAGE"/conf/* "$INSTALL_DIR/conf/" || die files
chmod 600 "$INSTALL_DIR"/conf/secrets.properties "$INSTALL_DIR"/conf/application.properties 2>/dev/null || true
cp "$STAGE"/bin/* "$INSTALL_DIR/bin/" && chmod 755 "$INSTALL_DIR"/bin/* || die files
cp "$STAGE/INSTALL-REPORT.md" "$INSTALL_DIR/INSTALL-REPORT.md" 2>/dev/null || true
cp "$STAGE/VERSION" "$INSTALL_DIR/VERSION" 2>/dev/null || true
EOS
}

# 서비스 계정이 읽고 쓸 수 있도록 소유권을 맞춘다.
_yi_apply_ownership() {
  if [ "$(ans_get env.os)" = "mac" ]; then
    printf '\nstep ownership\nchown -R %q "$DATA_DIR" "$INSTALL_DIR/conf" || die ownership\n' "$(ans_get env.user)"
  else
    cat <<'EOS'

step ownership
chown root:yona "$INSTALL_DIR/conf" && chmod 750 "$INSTALL_DIR/conf" || die ownership
chown root:yona "$INSTALL_DIR/conf"/* && chmod 640 "$INSTALL_DIR/conf"/* || die ownership
chown -R yona:yona "$DATA_DIR" && chmod 750 "$DATA_DIR" || die ownership
EOS
  fi
}

# JDK를 패키지로 설치한다(설치자가 동의했을 때).
_yi_apply_jdk() {
  case "$(ans_get env.pkg apt)" in
    apt) printf '\nstep jdk\nDEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-21-jre-headless || die jdk\n' ;;
    dnf) printf '\nstep jdk\ndnf install -y java-21-openjdk-headless || die jdk\n' ;;
  esac
}

# 컨테이너로 DB를 설치한다. 컨테이너의 초기화 중에는 소켓 전용 임시 서버가 먼저 떠서 ping이 성공하므로, 기다림과 SQL 실행은 TCP(127.0.0.1)로 한다.
# 포트는 127.0.0.1에만 공개하고, 비밀번호는 명령줄이 아니라 env 파일로 넘긴다.
_yi_apply_db_container() {
  local tool type image port inner data
  tool="$(ans_get env.containers docker)"
  type="$(ans_get db.type)"
  image="$(yi_db_image "$type")"
  port="$(ans_get db.port "$(db_default_port "$type")")"
  inner="$(db_default_port "$type")"
  data="$(_yi_db_data_path "$type")"
  printf '\nstep db-container\n'
  printf '%s run -d --name yona-db --restart unless-stopped -p 127.0.0.1:%s:%s --env-file "$STAGE/db.env" -v yona-db-data:%s %s >/dev/null || die db-container\n' \
    "$tool" "$port" "$inner" "$data" "$image"
  printf "j_add '%s rm -fv yona-db; %s volume rm yona-db-data'\n" "$tool" "$tool"
  printf 'step db-wait\n'
  case "$type" in
    postgresql) printf 'until %s exec yona-db sh -c '"'"'pg_isready -h 127.0.0.1 -U "$POSTGRES_USER"'"'"' >/dev/null 2>&1; do sleep 2; done\n' "$tool" ;;
    mariadb) printf 'until %s exec yona-db sh -c '"'"'mariadb-admin ping -h 127.0.0.1 -uroot -p"$MARIADB_ROOT_PASSWORD" --silent'"'"' >/dev/null 2>&1; do sleep 2; done\n' "$tool" ;;
    mssql) printf 'until %s exec yona-db sh -c '"'"'/opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -Q "select 1" -b'"'"' >/dev/null 2>&1; do sleep 2; done\n' "$tool" ;;
    cubrid) printf 'sleep 20\n' ;;
  esac
  printf 'step db-sql\n'
  case "$type" in
    postgresql) printf '%s exec -i yona-db sh -c '"'"'psql -h 127.0.0.1 -U "$POSTGRES_USER" -v ON_ERROR_STOP=1'"'"' < "$STAGE/db.sql" || die db-sql\n' "$tool" ;;
    mariadb) printf '%s exec -i yona-db sh -c '"'"'mariadb -h 127.0.0.1 -uroot -p"$MARIADB_ROOT_PASSWORD"'"'"' < "$STAGE/db.sql" || die db-sql\n' "$tool" ;;
    mssql) printf '%s exec -i yona-db sh -c '"'"'/opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -b'"'"' < "$STAGE/db.sql" || die db-sql\n' "$tool" ;;
    cubrid) printf '%s exec -i yona-db sh -c '"'"'csql -u dba %s'"'"' < "$STAGE/db.sql" || die db-sql\n' "$tool" "$(ans_get db.name yona)" ;;
  esac
}

# 패키지로 DB를 설치하고 서비스를 켠 뒤 데이터베이스와 사용자를 만든다. 패키지는 되돌릴 때 지우지 않는다(데이터 보호).
_yi_apply_db_package() {
  local svc
  svc="$(_yi_db_service)"
  printf '\nstep db-package\n%s || die db-package\n' "$(_yi_db_package_install)"
  if [ "$(ans_get db.type)" = "postgresql" ] && [ "$(ans_get env.pkg apt)" = "dnf" ]; then
    printf 'postgresql-setup --initdb || die db-package\n'
  fi
  printf 'systemctl enable --now %s || die db-package\n' "$svc"
  printf "j_add 'systemctl disable --now %s'\n" "$svc"
  printf 'step db-sql\n'
  # 되돌릴 때 쓸 삭제 SQL을 기록 폴더에 보관한다(준비 폴더는 그때 없을 수 있다)
  printf 'cp "$STAGE/db-drop.sql" "$RECORD_DIR/db-drop.sql" 2>/dev/null || true\n'
  case "$(ans_get db.type)" in
    postgresql)
      printf 'runuser -u postgres -- psql -v ON_ERROR_STOP=1 < "$STAGE/db.sql" || die db-sql\n'
      printf "j_add 'runuser -u postgres -- psql < \"\$RECORD_DIR/db-drop.sql\"'\n"
      ;;
    mariadb)
      printf 'mariadb < "$STAGE/db.sql" || die db-sql\n'
      printf "j_add 'mariadb < \"\$RECORD_DIR/db-drop.sql\"'\n"
      ;;
  esac
}

# 시스템 sshd 연동: 계정, 공유 비밀 값, 도우미, 설정 파일, 드롭인을 만들고 검사한 뒤 reload한다. 검사에 실패하면 드롭인을 지운다.
_yi_apply_sshd() {
  local unit
  unit="$(ans_get env.sshd_unit sshd)"
  cat <<'EOS'

step ssh-accounts
for u in git hg svn; do
  if ! id -u "$u" >/dev/null 2>&1; then
    useradd --system --shell /bin/bash --home-dir "/home/$u" --create-home "$u" || die ssh-accounts
    j_add "userdel -r $u"
  fi
  usermod -aG yona "$u" || die ssh-accounts
done
if ! getent group yona-ssh-hook >/dev/null 2>&1; then
  groupadd --system yona-ssh-hook || die ssh-accounts
  j_add 'groupdel yona-ssh-hook'
fi
if ! id -u yona-ssh-hook >/dev/null 2>&1; then
  useradd --system --no-create-home --shell /sbin/nologin --gid yona-ssh-hook yona-ssh-hook || die ssh-accounts
  j_add 'userdel yona-ssh-hook'
fi

step ssh-helper
mkdir -p /etc/yona || die ssh-helper
cp "$STAGE/ssh-internal-secret" /etc/yona/ssh-internal-secret && chown root:yona-ssh-hook /etc/yona/ssh-internal-secret && chmod 640 /etc/yona/ssh-internal-secret || die ssh-helper
j_add 'rm -f /etc/yona/ssh-internal-secret /etc/yona/ssh-auth.conf'
cp "$STAGE/ssh-auth.conf" /etc/yona/ssh-auth.conf || die ssh-helper
install -o root -g root -m 755 "$STAGE/yona-ssh" /usr/local/bin/yona-ssh || die ssh-helper
j_add 'rm -f /usr/local/bin/yona-ssh'

step sshd-dropin
cp "$STAGE/sshd-50-yona.conf" /etc/ssh/sshd_config.d/50-yona.conf || die sshd-dropin
j_add 'rm -f /etc/ssh/sshd_config.d/50-yona.conf'
if ! sshd -t; then
  rm -f /etc/ssh/sshd_config.d/50-yona.conf
  die sshd-config-check
fi
EOS
  printf 'systemctl reload %s || die sshd-reload\n' "$unit"
}

# 서비스를 등록하고 부팅 때 자동으로 시작하게 한다.
_yi_apply_service() {
  if [ "$(ans_get env.os)" = "mac" ]; then
    cat <<'EOS'

step service
cp "$STAGE/dev.yona.server.plist" /Library/LaunchDaemons/dev.yona.server.plist || die service
chown root:wheel /Library/LaunchDaemons/dev.yona.server.plist && chmod 644 /Library/LaunchDaemons/dev.yona.server.plist || die service
j_add 'rm -f /Library/LaunchDaemons/dev.yona.server.plist'
launchctl bootstrap system /Library/LaunchDaemons/dev.yona.server.plist || die service
j_add 'launchctl bootout system/dev.yona.server'
EOS
  else
    cat <<'EOS'

step service
cp "$STAGE/yona.service" /etc/systemd/system/yona.service || die service
j_add 'rm -f /etc/systemd/system/yona.service; systemctl daemon-reload'
systemctl daemon-reload || die service
systemctl enable --now yona || die service
j_add 'systemctl disable --now yona'
EOS
  fi
}

# 설치 기록(`install.rec`)을 쓴다. 제거와 "이미 설치됨" 판단, 업그레이드가 이 파일을 읽는다.
_yi_apply_record() {
  printf '\nstep record\n'
  printf '{\n'
  printf '  echo version=%s\n' "$(ans_get version)"
  printf '  echo install_dir=%s\n' "$(ans_get install_dir)"
  printf '  echo data_dir=%s\n' "$(ans_get data_dir)"
  printf '  echo service=%s\n' "$(ans_get service.register false)"
  printf '  echo os=%s\n' "$(ans_get env.os linux)"
  printf '  echo db_type=%s\n' "$(ans_get db.type)"
  printf '  echo db_install=%s\n' "$(ans_get db.install)"
  printf '  echo db_method=%s\n' "$(ans_get db.method)"
  printf '  echo ssh_mode=%s\n' "$(ans_get ssh.mode none)"
  printf '} > "$RECORD_DIR/install.rec" || die record\n'
}

# 적용 스크립트 전체를 표준 출력으로 만든다.
render_apply_script() {
  local service
  service="$(ans_get service.register false)"
  _yi_apply_header
  [ "$(ans_get install_jdk)" = "true" ] && [ "$(ans_get env.need_jdk)" = "true" ] && _yi_apply_jdk
  if [ "$service" = "true" ] && [ "$(ans_get env.os linux)" != "mac" ]; then
    _yi_apply_user
  fi
  if [ "$(ans_get db.install)" = "together" ]; then
    case "$(ans_get db.method)" in
      container) _yi_apply_db_container ;;
      package) _yi_apply_db_package ;;
    esac
  fi
  _yi_apply_files
  [ "$service" = "true" ] && _yi_apply_ownership
  [ "$service" = "true" ] && [ "$(ans_get ssh.mode)" = "system" ] && _yi_apply_sshd
  [ "$service" = "true" ] && _yi_apply_service
  _yi_apply_record
  printf '\necho "[yona-install] done"\n'
}
