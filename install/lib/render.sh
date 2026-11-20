# shellcheck shell=bash
# shellcheck disable=SC2016  # 다른 스크립트의 본문을 만드는 코드라 작은따옴표 안의 $를 일부러 펼치지 않는다
# 설정 파일, 서비스 파일, SQL 같은 산출물의 내용을 만든다. 모든 함수는 표준 출력으로 내용만 쓰고 파일을 만들지 않는다
# (쓰는 일은 설치 단계가 한다). 같은 입력이면 항상 같은 출력이라 `scenarios/`의 골든 파일로 시험한다.
# 입력은 `answers.sh`의 답과 설치 환경의 사실(`env.os`, `env.java`, `env.user`, `env.tz`, `env.containers`)이다.

# JVM 힙을 서버 메모리의 몇 퍼센트로 둘지이다(컨테이너 문서와 같은 방식. 값은 구현 때 시험으로 확정한다).
YI_HEAP_PERCENT=75

# --- 이스케이프와 비밀 값 ---

# YAML 작은따옴표 문자열로 감싼다(작은따옴표는 두 개로 쓴다). 사용법: yaml_sq <값>
yaml_sq() {
  local q="'" v="$1"
  printf "'%s'" "${v//$q/$q$q}"
}

# Java properties 값으로 쓸 수 있게 역슬래시와 앞쪽 공백을 이스케이프한다. 사용법: props_escape <값>
props_escape() {
  local v="$1"
  v=${v//\\/\\\\}
  case "$v" in
    ' '*) v="\\ ${v# }" ;;
  esac
  printf '%s' "$v"
}

# MariaDB/MySQL 문자열 리터럴이다(작은따옴표와 역슬래시를 이스케이프). 사용법: sql_str_mysql <값>
sql_str_mysql() {
  local q="'" v="$1"
  v=${v//\\/\\\\}
  printf "'%s'" "${v//$q/$q$q}"
}

# 표준 SQL 문자열 리터럴이다(PostgreSQL, SQL Server, Cubrid). 작은따옴표만 두 개로 쓴다. 사용법: sql_str_std <값>
sql_str_std() {
  local q="'" v="$1"
  printf "'%s'" "${v//$q/$q$q}"
}

# 비밀번호가 보이는 ASCII 문자(공백 포함)만으로 이루어졌으면 참이다. 개행과 한글 같은 문자는 설정 파일과 SQL에
# 안전하게 쓰기 어려워 받지 않는다. 사용법: password_ok <값>
password_ok() {
  [ -n "$1" ] || return 1
  [ "$(printf '%s' "$1" | LC_ALL=C tr -d ' -~' | wc -c | tr -d ' ')" -eq 0 ]
}

# 비밀 값 파일을 읽는다(끝의 개행은 지운다). 사용법: read_secret_file <경로>
read_secret_file() {
  cat "$1"
}

# --- DB ---

# DB 종류별 기본 포트이다.
db_default_port() {
  case "$1" in
    postgresql) printf '5432' ;;
    mariadb) printf '3306' ;;
    mssql) printf '1433' ;;
    cubrid) printf '33000' ;;
    *) printf '0' ;;
  esac
}

# JDBC 접속 주소를 만든다. 사용법: jdbc_url <종류> <호스트> <포트> <DB 이름> <데이터 폴더>
jdbc_url() {
  case "$1" in
    postgresql) printf 'jdbc:postgresql://%s:%s/%s' "$2" "$3" "$4" ;;
    mariadb) printf 'jdbc:mariadb://%s:%s/%s' "$2" "$3" "$4" ;;
    mssql) printf 'jdbc:sqlserver://%s:%s;databaseName=%s;lockTimeout=20000' "$2" "$3" "$4" ;;
    cubrid) printf 'jdbc:cubrid:%s:%s:%s:::?charset=utf-8' "$2" "$3" "$4" ;;
    h2) printf 'jdbc:h2:file:%s/db;DB_CLOSE_DELAY=-1' "$5" ;;
  esac
}

# 이 설치가 쓸 DB의 접속 주소를 만든다(함께 설치하면 127.0.0.1, 따로 설치되었으면 입력한 호스트).
_yi_active_jdbc_url() {
  local type host port
  type="$(ans_get db.type)"
  if [ "$(ans_get db.install)" = "separate" ]; then
    host="$(ans_get db.host)"
    port="$(ans_get db.port "$(db_default_port "$type")")"
  else
    host="127.0.0.1"
    port="$(ans_get db.port "$(db_default_port "$type")")"
  fi
  jdbc_url "$type" "$host" "$port" "$(ans_get db.name yona)" "$(ans_get data_dir)"
}

# 비밀 값 파일이 필요한 설치이면 참이다(H2가 아니거나 메일 비밀번호를 줄 때).
_yi_needs_secrets() {
  _yi_inplace_migration && ans_has mail.password_file && return 0
  _yi_inplace_migration && [ "$(ans_get ssh.mode)" = "system" ] && return 0
  _yi_inplace_migration && return 1
  [ "$(ans_get db.type)" != "h2" ] || ans_has mail.password_file || [ "$(ans_get ssh.mode)" = "system" ]
}

# v1.6이 MariaDB/MySQL이고 같은 DB를 이어받는 이관이면 참이다. 이때 DB 접속 정보와 비밀 값은 변환기가 만든 속성 파일(conf/application.properties)이 담는다.
_yi_inplace_migration() {
  [ "$(ans_get env.mode)" = "migrate" ] && [ "$(ans_get v16.db)" != "h2" ] && [ -n "$(ans_get v16.db)" ]
}

# --- application.yml ---

# Yona 설정 파일 `conf/application.yml`의 내용이다. 비밀 값은 넣지 않고 `secrets.properties`를 가져오게 한다.
render_application_yml() {
  local type user
  type="$(ans_get db.type)"
  if [ "$type" = "h2" ]; then user="sa"; else user="$(ans_get db.user yona)"; fi
  printf '# Yona 설정: 설치 스크립트가 만들었다. 비밀 값은 secrets.properties에 있다.\n'
  printf 'spring:\n'
  if _yi_needs_secrets; then
    printf '  config:\n    import: %s\n' "$(yaml_sq "optional:file:$(ans_get install_dir)/conf/secrets.properties")"
  fi
  if ! _yi_inplace_migration; then
    printf '  datasource:\n    url: %s\n    username: %s\n' "$(yaml_sq "$(_yi_active_jdbc_url)")" "$(yaml_sq "$user")"
  fi
  if [ "$(ans_get mail.enabled)" = "true" ]; then
    printf '  mail:\n    host: %s\n    port: %s\n' "$(yaml_sq "$(ans_get mail.host)")" "$(ans_get mail.port)"
    ans_has mail.user && printf '    username: %s\n' "$(yaml_sq "$(ans_get mail.user)")"
    printf '    properties:\n      mail.smtp.ssl.enable: %s\n' "$(ans_get mail.ssl false)"
  fi
  printf 'server:\n  port: %s\n' "$(ans_get web.port 8080)"
  [ "$(ans_get site.behind_https_proxy)" = "true" ] && printf '  forward-headers-strategy: framework\n'
  printf 'yona:\n  data-dir: %s\n' "$(yaml_sq "$(ans_get data_dir)")"
  ans_has site.hostname && printf '  hostname: %s\n' "$(yaml_sq "$(ans_get site.hostname)")"
  ans_has site.port && printf '  port: %s\n' "$(ans_get site.port)"
  ans_has site.scheme && printf '  scheme: %s\n' "$(yaml_sq "$(ans_get site.scheme)")"
  if [ "$(ans_get mail.enabled)" = "true" ]; then
    printf '  smtp:\n'
    ans_has mail.from_user && printf '    user: %s\n' "$(yaml_sq "$(ans_get mail.from_user)")"
    ans_has mail.from_domain && printf '    domain: %s\n' "$(yaml_sq "$(ans_get mail.from_domain)")"
    printf '    mock: %s\n' "$(ans_get mail.mock false)"
  fi
  case "$(ans_get ssh.mode)" in
    builtin)
      printf '  ssh:\n    mina:\n      enabled: true\n      port: %s\n' "$(ans_get ssh.port 2222)"
      ans_has ssh.public_port && printf '    public-port: %s\n' "$(ans_get ssh.public_port)"
      ;;
    system)
      printf '  ssh:\n    relay:\n      enabled: true\n'
      ans_has ssh.public_port && printf '    public-port: %s\n' "$(ans_get ssh.public_port)"
      ;;
  esac
}

# 비밀 값 파일 `conf/secrets.properties`의 내용이다. 소유자만 읽도록 만들어야 한다(0600).
render_secrets_properties() {
  local pw
  if [ "$(ans_get db.type)" != "h2" ] && ans_has db.password_file && ! _yi_inplace_migration; then
    pw="$(read_secret_file "$(ans_get db.password_file)")"
    printf 'spring.datasource.password=%s\n' "$(props_escape "$pw")"
  fi
  if [ "$(ans_get mail.enabled)" = "true" ] && ans_has mail.password_file; then
    pw="$(read_secret_file "$(ans_get mail.password_file)")"
    printf 'spring.mail.password=%s\n' "$(props_escape "$pw")"
  fi
  if [ "$(ans_get ssh.mode)" = "system" ] && ans_has ssh.relay_secret; then
    printf 'yona.ssh.relay.internal-secret=%s\n' "$(props_escape "$(ans_get ssh.relay_secret)")"
  fi
}

# --- 서비스 ---

# 사용하는 포트 중 1024 미만이 있으면 참이다.
_yi_uses_low_port() {
  local web ssh
  web="$(ans_get web.port 8080)"
  [ "$web" -lt 1024 ] && return 0
  if [ "$(ans_get ssh.mode)" = "builtin" ]; then
    ssh="$(ans_get ssh.port 2222)"
    [ "$ssh" -lt 1024 ] && return 0
  fi
  return 1
}

# 함께 설치한 DB의 systemd 유닛 이름이다(패키지 설치일 때만).
_yi_db_unit() {
  case "$(ans_get db.type)" in
    postgresql) printf 'postgresql.service' ;;
    mariadb) printf 'mariadb.service' ;;
  esac
}

# `/etc/systemd/system/yona.service`의 내용이다. 서비스는 전용 비관리자 계정(`yona`)으로 돈다.
render_systemd_unit() {
  local after="network-online.target" requires="" pre="" install_dir java port
  install_dir="$(ans_get install_dir)"
  java="$(ans_get env.java java)"
  if [ "$(ans_get db.install)" = "together" ]; then
    if [ "$(ans_get db.method)" = "package" ]; then
      after="$after $(_yi_db_unit)"
      requires="$(_yi_db_unit)"
    elif [ "$(ans_get db.method)" = "container" ]; then
      port="$(ans_get db.port "$(db_default_port "$(ans_get db.type)")")"
      case "$(ans_get env.containers)" in
        docker) after="$after docker.service"; requires="docker.service" ;;
      esac
      pre="$install_dir/bin/wait-for-port.sh 127.0.0.1 $port 120"
    fi
  fi
  printf '[Unit]\nDescription=Yona\nDocumentation=https://github.com/yona-projects/yona\n'
  printf 'After=%s\nWants=network-online.target\n' "$after"
  [ -n "$requires" ] && printf 'Requires=%s\n' "$requires"
  printf '\n[Service]\nType=simple\nUser=yona\nGroup=yona\nWorkingDirectory=%s\n' "$install_dir"
  ans_has env.tz && printf 'Environment=TZ=%s\n' "$(ans_get env.tz)"
  [ -n "$pre" ] && printf 'ExecStartPre=%s\n' "$pre"
  printf 'ExecStart=%s -XX:MaxRAMPercentage=%s -jar %s/yona.jar --spring.config.additional-location=file:%s/conf/\n' \
    "$java" "$YI_HEAP_PERCENT" "$install_dir" "$install_dir"
  printf 'Restart=on-failure\nRestartSec=5\nSuccessExitStatus=143\nTimeoutStartSec=300\nNoNewPrivileges=true\n'
  _yi_uses_low_port && printf 'AmbientCapabilities=CAP_NET_BIND_SERVICE\n'
  printf '\n[Install]\nWantedBy=multi-user.target\n'
}

# macOS의 `/Library/LaunchDaemons/dev.yona.server.plist` 내용이다. 부팅 때 로그인 없이 시작하고 지정한 로컬 사용자로 돈다.
render_launchd_plist() {
  local install_dir data_dir
  install_dir="$(ans_get install_dir)"
  data_dir="$(ans_get data_dir)"
  printf '<?xml version="1.0" encoding="UTF-8"?>\n'
  printf '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n'
  printf '<plist version="1.0">\n<dict>\n'
  printf '  <key>Label</key>\n  <string>dev.yona.server</string>\n'
  printf '  <key>UserName</key>\n  <string>%s</string>\n' "$(ans_get env.user)"
  printf '  <key>WorkingDirectory</key>\n  <string>%s</string>\n' "$install_dir"
  printf '  <key>ProgramArguments</key>\n  <array>\n'
  printf '    <string>%s</string>\n' "$(ans_get env.java java)"
  printf '    <string>-XX:MaxRAMPercentage=%s</string>\n' "$YI_HEAP_PERCENT"
  printf '    <string>-jar</string>\n    <string>%s/yona.jar</string>\n' "$install_dir"
  printf '    <string>--spring.config.additional-location=file:%s/conf/</string>\n  </array>\n' "$install_dir"
  if ans_has env.tz; then
    printf '  <key>EnvironmentVariables</key>\n  <dict>\n    <key>TZ</key>\n    <string>%s</string>\n  </dict>\n' "$(ans_get env.tz)"
  fi
  printf '  <key>RunAtLoad</key>\n  <true/>\n  <key>KeepAlive</key>\n  <true/>\n'
  printf '  <key>StandardOutPath</key>\n  <string>%s/logs/launchd.out.log</string>\n' "$data_dir"
  printf '  <key>StandardErrorPath</key>\n  <string>%s/logs/launchd.err.log</string>\n' "$data_dir"
  printf '</dict>\n</plist>\n'
}

# `/etc/ssh/sshd_config.d/50-yona.conf`의 내용이다. sshd 설정 본문을 고치지 않고 드롭인 파일 하나만 둔다.
render_sshd_dropin() {
  printf '# Yona의 시스템 sshd 연동: Git, Mercurial, Subversion 전용 계정의 공개키를 Yona에 묻는다.\n'
  printf 'Match User git,hg,svn\n'
  printf '    AuthorizedKeysCommand /usr/local/bin/yona-ssh auth %%t %%k\n'
  printf '    AuthorizedKeysCommandUser yona-ssh-hook\n'
}

# --- DB 생성 SQL ---

# 데이터베이스와 사용자를 만드는 SQL이다. 인자는 사용자의 접속 호스트(MariaDB만 쓴다: 패키지는 localhost,
# 컨테이너는 %)이다. 비밀번호는 비밀 값 파일에서 읽는다. 사용법: render_db_sql <호스트>
render_db_sql() {
  local name user pw host="$1"
  name="$(ans_get db.name yona)"
  user="$(ans_get db.user yona)"
  pw="$(read_secret_file "$(ans_get db.password_file)" 2>/dev/null)"
  case "$(ans_get db.type)" in
    mariadb)
      printf 'CREATE DATABASE `%s` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_bin;\n' "$name"
      printf 'CREATE USER %s@%s IDENTIFIED BY %s;\n' "$(sql_str_mysql "$user")" "$(sql_str_mysql "$host")" "$(sql_str_mysql "$pw")"
      printf 'GRANT ALL PRIVILEGES ON `%s`.* TO %s@%s;\n' "$name" "$(sql_str_mysql "$user")" "$(sql_str_mysql "$host")"
      printf 'FLUSH PRIVILEGES;\n'
      ;;
    postgresql)
      printf 'CREATE ROLE "%s" LOGIN PASSWORD %s;\n' "$user" "$(sql_str_std "$pw")"
      printf 'CREATE DATABASE "%s" OWNER "%s" ENCODING '"'"'UTF8'"'"' TEMPLATE template0;\n' "$name" "$user"
      ;;
    mssql)
      printf 'CREATE DATABASE [%s];\n' "$name"
      printf 'ALTER DATABASE [%s] SET READ_COMMITTED_SNAPSHOT ON;\n' "$name"
      printf 'CREATE LOGIN [%s] WITH PASSWORD = %s, CHECK_POLICY = OFF;\n' "$user" "$(sql_str_std "$pw")"
      printf 'USE [%s];\n' "$name"
      printf 'CREATE USER [%s] FOR LOGIN [%s];\n' "$user" "$user"
      printf 'ALTER ROLE db_owner ADD MEMBER [%s];\n' "$user"
      ;;
    cubrid)
      printf 'create user %s password %s groups dba;\n' "$user" "$(sql_str_std "$pw")"
      ;;
  esac
}

# 데이터베이스와 사용자를 지우는 SQL이다(설치를 되돌릴 때 쓴다). 인자는 `render_db_sql`과 같다. 사용법: render_db_drop_sql <호스트>
render_db_drop_sql() {
  local name user host="$1"
  name="$(ans_get db.name yona)"
  user="$(ans_get db.user yona)"
  case "$(ans_get db.type)" in
    mariadb)
      printf 'DROP DATABASE IF EXISTS `%s`;\n' "$name"
      printf 'DROP USER IF EXISTS %s@%s;\n' "$(sql_str_mysql "$user")" "$(sql_str_mysql "$host")"
      ;;
    postgresql)
      printf 'DROP DATABASE IF EXISTS "%s";\n' "$name"
      printf 'DROP ROLE IF EXISTS "%s";\n' "$user"
      ;;
  esac
}

# 컨테이너 DB를 시작할 때 `--env-file`로 넘기는 환경 파일이다(명령줄에 비밀번호가 보이지 않게 한다). 소유자만 읽도록 만들어야 한다.
# 관리자 비밀번호는 `db.admin_password_file`에서 읽는다. 사용법: render_db_env
render_db_env() {
  local admin_user admin_pw=""
  admin_user="$(ans_get db.admin_user)"
  ans_has db.admin_password_file && admin_pw="$(read_secret_file "$(ans_get db.admin_password_file)")"
  case "$(ans_get db.type)" in
    postgresql)
      printf 'POSTGRES_USER=%s\nPOSTGRES_PASSWORD=%s\n' "${admin_user:-postgres}" "$admin_pw"
      ;;
    mariadb)
      printf 'MARIADB_ROOT_PASSWORD=%s\n' "$admin_pw"
      ;;
    mssql)
      printf 'ACCEPT_EULA=Y\nMSSQL_SA_PASSWORD=%s\n' "$admin_pw"
      ;;
    cubrid)
      printf 'CUBRID_LOCALE=en_US.utf8\nCUBRID_DB=%s\n' "$(ans_get db.name yona)"
      ;;
  esac
}

# 서비스를 등록하지 않았을 때 쓰는 시작, 중지, 상태 스크립트의 내용이다. 사용법: render_run_script <start|stop|status>
render_run_script() {
  local install_dir data_dir java port
  install_dir="$(ans_get install_dir)"
  data_dir="$(ans_get data_dir)"
  java="$(ans_get env.java java)"
  port="$(ans_get web.port 8080)"
  case "$1" in
    start)
      cat <<EOS
#!/usr/bin/env bash
# Yona를 백그라운드로 시작한다. 부팅 때 자동으로 시작하지 않는다.
PID_FILE='$data_dir/yona.pid'
LOG_FILE='$data_dir/logs/yona.out'
if [ -f "\$PID_FILE" ] && kill -0 "\$(cat "\$PID_FILE")" 2>/dev/null; then
  echo "Yona is already running (pid \$(cat "\$PID_FILE"))"
  exit 0
fi
mkdir -p '$data_dir/logs'
cd '$install_dir' || exit 1
nohup '$java' -XX:MaxRAMPercentage=$YI_HEAP_PERCENT -jar '$install_dir/yona.jar' --spring.config.additional-location=file:$install_dir/conf/ >> "\$LOG_FILE" 2>&1 &
echo \$! > "\$PID_FILE"
echo "Yona started (pid \$!), log: \$LOG_FILE"
EOS
      ;;
    stop)
      cat <<EOS
#!/usr/bin/env bash
# Yona를 종료한다(종료 신호를 보내 정상 종료하게 한다).
PID_FILE='$data_dir/yona.pid'
if [ ! -f "\$PID_FILE" ] || ! kill -0 "\$(cat "\$PID_FILE")" 2>/dev/null; then
  echo "Yona is not running"
  rm -f "\$PID_FILE"
  exit 0
fi
pid="\$(cat "\$PID_FILE")"
kill "\$pid"
for i in \$(seq 1 60); do
  kill -0 "\$pid" 2>/dev/null || { rm -f "\$PID_FILE"; echo "Yona stopped"; exit 0; }
  sleep 1
done
echo "Yona did not stop within 60 seconds (pid \$pid)" >&2
exit 1
EOS
      ;;
    status)
      cat <<EOS
#!/usr/bin/env bash
# Yona의 상태를 확인한다.
curl -fsS http://127.0.0.1:$port/-_-actuator/health
echo
EOS
      ;;
  esac
}

# 설치 결과 문서(`INSTALL-REPORT.md`)의 내용이다. 비밀 값은 쓰지 않는다. 문구는 메시지 파일에서 읽는다.
render_report() {
  local scheme host url
  scheme="$(ans_get site.scheme http)"
  host="$(ans_get site.hostname localhost)"
  if ans_has site.port; then url="$scheme://$host:$(ans_get site.port)"; elif [ "$scheme" = "https" ]; then url="$scheme://$host"; else url="$scheme://$host:$(ans_get web.port 8080)"; fi
  printf '# %s\n\n' "$(msg report.title)"
  printf '%s\n\n' "$(msg report.version "$(ans_get version)")"
  printf '## %s\n\n' "$(msg report.access)"
  printf -- '- %s\n' "$(msg report.url "$url")"
  printf -- '- %s\n' "$(msg report.web_port "$(ans_get web.port 8080)")"
  case "$(ans_get ssh.mode)" in
    builtin) printf -- '- %s\n' "$(msg report.ssh_builtin "$(ans_get ssh.port 2222)")" ;;
    system) printf -- '- %s\n' "$(msg report.ssh_system)" ;;
  esac
  printf '\n## %s\n\n' "$(msg report.locations)"
  printf -- '- %s\n' "$(msg report.install_dir "$(ans_get install_dir)")"
  printf -- '- %s\n' "$(msg report.data_dir "$(ans_get data_dir)")"
  printf -- '- %s\n' "$(msg report.database "$(ans_get db.type) / $(ans_get db.name yona)")"
  printf '\n## %s\n\n' "$(msg report.commands)"
  if [ "$(ans_get service.register)" = "true" ]; then
    if [ "$(ans_get env.os linux)" = "mac" ]; then
      printf -- '- %s\n' "$(msg report.cmd_mac)"
    else
      printf -- '- %s\n' "$(msg report.cmd_systemd)"
    fi
  else
    printf -- '- %s\n' "$(msg report.cmd_scripts "$(ans_get install_dir)")"
    printf -- '- %s\n' "$(msg report.no_autostart)"
  fi
  printf -- '- %s\n' "$(msg report.health "$(ans_get web.port 8080)")"
  printf '\n## %s\n\n' "$(msg report.backup)"
  printf -- '- %s\n' "$(msg report.backup_secret "$(ans_get data_dir)")"
  printf -- '- %s\n' "$(msg report.backup_ssh "$(ans_get data_dir)")"
  printf -- '- %s\n' "$(msg report.backup_repo "$(ans_get data_dir)")"
  [ "$(ans_get db.type)" != "h2" ] && printf -- '- %s\n' "$(msg report.backup_db)"
  printf '\n## %s\n\n' "$(msg report.next)"
  printf -- '- %s\n' "$(msg report.next_admin)"
  printf -- '- %s\n' "$(msg report.firewall "$(ans_get web.port 8080)")"
}

# 패키지로 설치한 DB의 시스템 관리자 비밀번호를 설정하는 SQL이다(컨테이너는 시작할 때 환경 파일로 준다).
# MariaDB의 `root`는 기본으로 운영체제 계정 인증(소켓)이라, 비밀번호 인증도 되도록 하되 소켓 인증은 남긴다.
# 사용법: render_db_admin_sql
render_db_admin_sql() {
  local pw
  ans_has db.admin_password_file || return 0
  pw="$(read_secret_file "$(ans_get db.admin_password_file)")"
  case "$(ans_get db.type)" in
    postgresql)
      printf 'ALTER ROLE "%s" PASSWORD %s;\n' "$(ans_get db.admin_user postgres)" "$(sql_str_std "$pw")"
      ;;
    mariadb)
      printf "ALTER USER 'root'@'localhost' IDENTIFIED VIA mysql_native_password USING PASSWORD(%s) OR unix_socket;\n" "$(sql_str_mysql "$pw")"
      ;;
  esac
}
