#!/usr/bin/env bats
# 설정 파일, 서비스 파일, SQL 같은 산출물을 만드는 부분의 시험.
load test_helper

setup() {
  setup_tmp
  . "$YI_DIR/lib/common.sh"
  . "$YI_DIR/lib/answers.sh"
  . "$YI_DIR/lib/render.sh"
  ans_clear
}
teardown() { teardown_tmp; }

# --- 이스케이프 ---

@test "YAML 작은따옴표 문자열은 작은따옴표를 두 개로 쓴다" {
  [ "$(yaml_sq "it's")" = "'it''s'" ]
  [ "$(yaml_sq "plain")" = "'plain'" ]
}

@test "properties 값은 역슬래시와 앞쪽 공백을 이스케이프한다" {
  [ "$(props_escape 'a\b')" = 'a\\b' ]
  [ "$(props_escape ' lead')" = '\ lead' ]
  [ "$(props_escape 'a=b:c#d')" = 'a=b:c#d' ]
}

@test "MariaDB SQL 문자열은 작은따옴표와 역슬래시를 이스케이프한다" {
  [ "$(sql_str_mysql "a'b\\c")" = "'a''b\\\\c'" ]
}

@test "표준 SQL 문자열은 작은따옴표를 두 개로 쓴다" {
  [ "$(sql_str_std "a'b\\c")" = "'a''b\\c'" ]
}

@test "비밀번호는 보이는 ASCII 문자만 받고 개행과 한글은 거절한다" {
  run password_ok 'Abc!@#123 ~'
  [ "$status" -eq 0 ]
  run password_ok "$(printf 'a\nb')"
  [ "$status" -ne 0 ]
  run password_ok '비밀번호'
  [ "$status" -ne 0 ]
  run password_ok ''
  [ "$status" -ne 0 ]
}

@test "비밀번호 파일은 끝의 개행 하나를 지우고 읽는다" {
  printf 'secret\n' > "$TMP_DIR/pw"
  [ "$(read_secret_file "$TMP_DIR/pw")" = "secret" ]
  printf 'sec ret' > "$TMP_DIR/pw2"
  [ "$(read_secret_file "$TMP_DIR/pw2")" = "sec ret" ]
}

# --- 접속 주소와 기본값 ---

@test "DB 종류별 기본 포트" {
  [ "$(db_default_port postgresql)" = "5432" ]
  [ "$(db_default_port mariadb)" = "3306" ]
  [ "$(db_default_port mssql)" = "1433" ]
  [ "$(db_default_port cubrid)" = "33000" ]
}

@test "DB 종류별 JDBC 주소" {
  [ "$(jdbc_url postgresql db.example.com 5432 yona /d)" = "jdbc:postgresql://db.example.com:5432/yona" ]
  [ "$(jdbc_url mariadb 127.0.0.1 3306 yona /d)" = "jdbc:mariadb://127.0.0.1:3306/yona" ]
  [ "$(jdbc_url mssql db 1433 yona /d)" = "jdbc:sqlserver://db:1433;databaseName=yona;lockTimeout=20000" ]
  [ "$(jdbc_url cubrid db 33000 yona /d)" = "jdbc:cubrid:db:33000:yona:::?charset=utf-8" ]
  [ "$(jdbc_url h2 x 0 yona /data/yona)" = "jdbc:h2:file:/data/yona/db;DB_CLOSE_DELAY=-1" ]
}

# --- application.yml ---

@test "H2는 사이트 주소와 메일 없이 최소 설정만 쓴다" {
  ans_set install_dir /home/u/yona; ans_set data_dir /home/u/yona-data; ans_set db.type h2
  out="$(render_application_yml)"
  [[ "$out" == *"url: 'jdbc:h2:file:/home/u/yona-data/db;DB_CLOSE_DELAY=-1'"* ]]
  [[ "$out" == *"username: 'sa'"* ]]
  [[ "$out" == *"data-dir: '/home/u/yona-data'"* ]]
  [[ "$out" == *"port: 8080"* ]]
  [[ "$out" != *"mail"* ]]
  [[ "$out" != *"import"* ]]
}

@test "메일을 쓰지 않으면 spring.mail 설정을 쓰지 않는다(서버가 없으면 메일을 보내지 않는다)" {
  ans_set install_dir /o; ans_set data_dir /d; ans_set db.type h2; ans_set mail.enabled false
  [[ "$(render_application_yml)" != *"host:"* ]]
}

@test "메일을 쓰면 서버, 포트, 보내는 주소, 모의 발송 여부를 쓴다" {
  ans_set install_dir /o; ans_set data_dir /d; ans_set db.type h2
  ans_set mail.enabled true; ans_set mail.host smtp.example.com; ans_set mail.port 587; ans_set mail.ssl false
  ans_set mail.user bot; ans_set mail.from_user yona; ans_set mail.from_domain example.com; ans_set mail.mock true
  out="$(render_application_yml)"
  [[ "$out" == *"host: 'smtp.example.com'"* ]]
  [[ "$out" == *"port: 587"* ]]
  [[ "$out" == *"mail.smtp.ssl.enable: false"* ]]
  [[ "$out" == *"user: 'yona'"* ]]
  [[ "$out" == *"domain: 'example.com'"* ]]
  [[ "$out" == *"mock: true"* ]]
}

@test "HTTPS 프록시 뒤 운영이면 scheme과 forward-headers-strategy를 쓴다" {
  ans_set install_dir /o; ans_set data_dir /d; ans_set db.type h2
  ans_set site.hostname git.example.com; ans_set site.scheme https; ans_set site.behind_https_proxy true
  out="$(render_application_yml)"
  [[ "$out" == *"hostname: 'git.example.com'"* ]]
  [[ "$out" == *"scheme: 'https'"* ]]
  [[ "$out" == *"forward-headers-strategy: framework"* ]]
}

@test "내장 SSH는 mina 설정과 바깥 포트를 쓴다" {
  ans_set install_dir /o; ans_set data_dir /d; ans_set db.type h2
  ans_set ssh.mode builtin; ans_set ssh.port 2222; ans_set ssh.public_port 22
  out="$(render_application_yml)"
  [[ "$out" == *"mina:"* ]]
  [[ "$out" == *"enabled: true"* ]]
  [[ "$out" == *"port: 2222"* ]]
  [[ "$out" == *"public-port: 22"* ]]
}

@test "외부 DB를 쓰면 비밀 값 파일을 가져오는 import를 쓴다" {
  ans_set install_dir /opt/yona; ans_set data_dir /d; ans_set db.type postgresql
  ans_set db.host db.example.com; ans_set db.port 5432; ans_set db.name yona; ans_set db.user yona; ans_set db.install separate
  out="$(render_application_yml)"
  [[ "$out" == *"import: 'optional:file:/opt/yona/conf/secrets.properties'"* ]]
  [[ "$out" == *"url: 'jdbc:postgresql://db.example.com:5432/yona'"* ]]
  [[ "$out" == *"username: 'yona'"* ]]
}

@test "함께 설치한 DB는 127.0.0.1로 접속한다" {
  ans_set install_dir /o; ans_set data_dir /d; ans_set db.type mariadb; ans_set db.install together
  ans_set db.name app; ans_set db.user app
  [[ "$(render_application_yml)" == *"jdbc:mariadb://127.0.0.1:3306/app"* ]]
}

# --- secrets.properties ---

@test "비밀 값 파일에 DB와 메일 비밀번호를 이스케이프해서 쓴다" {
  printf 'p\\w\n' > "$TMP_DIR/db"; printf 'mail pw\n' > "$TMP_DIR/mail"
  ans_set db.type postgresql; ans_set db.password_file "$TMP_DIR/db"
  ans_set mail.enabled true; ans_set mail.password_file "$TMP_DIR/mail"
  out="$(render_secrets_properties)"
  [[ "$out" == *'spring.datasource.password=p\\w'* ]]
  [[ "$out" == *'spring.mail.password=mail pw'* ]]
}

@test "H2이고 메일 비밀번호가 없으면 비밀 값 파일 내용이 없다" {
  ans_set db.type h2
  [ -z "$(render_secrets_properties)" ]
}

# --- systemd 유닛 ---

@test "systemd 유닛은 전용 계정, 재시작 정책, 종료 코드 143, 절대 경로를 쓴다" {
  ans_set install_dir /opt/yona; ans_set env.java /usr/bin/java; ans_set db.type h2; ans_set web.port 8080
  out="$(render_systemd_unit)"
  [[ "$out" == *"User=yona"* ]]
  [[ "$out" == *"Restart=on-failure"* ]]
  [[ "$out" == *"SuccessExitStatus=143"* ]]
  [[ "$out" == *"WorkingDirectory=/opt/yona"* ]]
  [[ "$out" == *"ExecStart=/usr/bin/java -XX:MaxRAMPercentage=75 -jar /opt/yona/yona.jar --spring.config.additional-location=file:/opt/yona/conf/"* ]]
  [[ "$out" == *"WantedBy=multi-user.target"* ]]
}

@test "1024번 미만 포트를 쓰면 포트를 열 권한을 준다" {
  ans_set install_dir /o; ans_set env.java /j; ans_set db.type h2; ans_set web.port 80
  [[ "$(render_systemd_unit)" == *"AmbientCapabilities=CAP_NET_BIND_SERVICE"* ]]
  ans_set web.port 8080
  [[ "$(render_systemd_unit)" != *"AmbientCapabilities"* ]]
}

@test "패키지로 설치한 DB 유닛이 먼저 뜬 뒤에 시작한다" {
  ans_set install_dir /o; ans_set env.java /j; ans_set db.type postgresql; ans_set db.install together; ans_set db.method package
  out="$(render_systemd_unit)"
  [[ "$out" == *"After=network-online.target postgresql.service"* ]]
  [[ "$out" == *"Requires=postgresql.service"* ]]
  ans_set db.type mariadb
  [[ "$(render_systemd_unit)" == *"mariadb.service"* ]]
}

@test "컨테이너 DB는 컨테이너 도구 서비스 뒤에 시작하고 DB 포트가 열릴 때까지 기다린다" {
  ans_set install_dir /o; ans_set env.java /j; ans_set db.type postgresql; ans_set db.install together; ans_set db.method container
  ans_set env.containers docker
  out="$(render_systemd_unit)"
  [[ "$out" == *"docker.service"* ]]
  [[ "$out" == *"ExecStartPre=/o/bin/wait-for-port.sh 127.0.0.1 5432 120"* ]]
  [[ "$out" != *'$'* ]]        # systemd는 $를 변수 치환으로 읽는다
  [[ "$out" != *'\&'* ]]
}

@test "시간대를 알면 TZ 환경을 쓴다" {
  ans_set install_dir /o; ans_set env.java /j; ans_set db.type h2; ans_set env.tz Asia/Seoul
  [[ "$(render_systemd_unit)" == *"Environment=TZ=Asia/Seoul"* ]]
}

# --- launchd ---

@test "launchd 설정은 부팅 때 시작하고 계속 살려 두며 지정한 사용자로 돈다" {
  ans_set install_dir /usr/local/yona; ans_set env.java /j/bin/java; ans_set env.user tester; ans_set data_dir /d
  out="$(render_launchd_plist)"
  [[ "$out" == *"<key>RunAtLoad</key>"* ]]
  [[ "$out" == *"<key>KeepAlive</key>"* ]]
  [[ "$out" == *"<string>tester</string>"* ]]
  [[ "$out" == *"<string>dev.yona.server</string>"* ]]
  [[ "$out" == *"<string>/usr/local/yona/yona.jar</string>"* ]]
}

# --- sshd ---

@test "sshd 드롭인은 git hg svn 계정에만 AuthorizedKeysCommand를 건다" {
  out="$(render_sshd_dropin)"
  [[ "$out" == *"Match User git,hg,svn"* ]]
  [[ "$out" == *"AuthorizedKeysCommand /usr/local/bin/yona-ssh auth %t %k"* ]]
  [[ "$out" == *"AuthorizedKeysCommandUser yona-ssh-hook"* ]]
}

# --- DB 생성 SQL ---

@test "MariaDB는 utf8mb4_bin 데이터베이스와 localhost 전용 사용자를 만든다" {
  printf 'pw\n' > "$TMP_DIR/db"
  ans_set db.type mariadb; ans_set db.name yona; ans_set db.user yona; ans_set db.password_file "$TMP_DIR/db"
  out="$(render_db_sql localhost)"
  [[ "$out" == *"CREATE DATABASE \`yona\` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_bin;"* ]]
  [[ "$out" == *"CREATE USER 'yona'@'localhost' IDENTIFIED BY 'pw';"* ]]
  [[ "$out" == *"GRANT ALL PRIVILEGES ON \`yona\`.* TO 'yona'@'localhost';"* ]]
}

@test "컨테이너 DB의 사용자 호스트는 %로 만든다(포트를 127.0.0.1에만 공개)" {
  printf 'pw\n' > "$TMP_DIR/db"
  ans_set db.type mariadb; ans_set db.name yona; ans_set db.user yona; ans_set db.password_file "$TMP_DIR/db"
  [[ "$(render_db_sql '%')" == *"'yona'@'%'"* ]]
}

@test "PostgreSQL은 역할과 UTF8 데이터베이스를 만든다" {
  printf "p'w\n" > "$TMP_DIR/db"
  ans_set db.type postgresql; ans_set db.name yona; ans_set db.user yona; ans_set db.password_file "$TMP_DIR/db"
  out="$(render_db_sql localhost)"
  [[ "$out" == *"CREATE ROLE \"yona\" LOGIN PASSWORD 'p''w';"* ]]
  [[ "$out" == *"CREATE DATABASE \"yona\" OWNER \"yona\" ENCODING 'UTF8' TEMPLATE template0;"* ]]
}

@test "SQL Server는 READ_COMMITTED_SNAPSHOT을 켠 데이터베이스와 db_owner 사용자를 만든다" {
  printf 'Pw1!\n' > "$TMP_DIR/db"
  ans_set db.type mssql; ans_set db.name yona; ans_set db.user yona; ans_set db.password_file "$TMP_DIR/db"
  out="$(render_db_sql localhost)"
  [[ "$out" == *"CREATE DATABASE [yona];"* ]]
  [[ "$out" == *"ALTER DATABASE [yona] SET READ_COMMITTED_SNAPSHOT ON;"* ]]
  [[ "$out" == *"CREATE LOGIN [yona] WITH PASSWORD = 'Pw1!'"* ]]
  [[ "$out" == *"ALTER ROLE db_owner ADD MEMBER [yona];"* ]]
}

@test "Cubrid는 사용자를 만든다(데이터베이스는 컨테이너 시작 때 만든다)" {
  printf 'pw\n' > "$TMP_DIR/db"
  ans_set db.type cubrid; ans_set db.name yona; ans_set db.user yona; ans_set db.password_file "$TMP_DIR/db"
  [[ "$(render_db_sql localhost)" == *"create user yona password 'pw' groups dba;"* ]]
}

@test "H2는 SQL이 없다" {
  ans_set db.type h2
  [ -z "$(render_db_sql localhost)" ]
}

@test "되돌리기용 삭제 SQL은 데이터베이스와 사용자를 지운다" {
  ans_set db.type mariadb; ans_set db.name yona; ans_set db.user yona
  out="$(render_db_drop_sql localhost)"
  [[ "$out" == *"DROP DATABASE IF EXISTS \`yona\`;"* ]]
  [[ "$out" == *"DROP USER IF EXISTS 'yona'@'localhost';"* ]]
  ans_set db.type postgresql
  out="$(render_db_drop_sql localhost)"
  [[ "$out" == *'DROP DATABASE IF EXISTS "yona";'* ]]
  [[ "$out" == *'DROP ROLE IF EXISTS "yona";'* ]]
}

# --- 컨테이너 DB 환경 파일 ---

@test "PostgreSQL 컨테이너 환경 파일은 관리자 ID와 비밀번호를 담는다" {
  printf 'adminpw\n' > "$TMP_DIR/admin"
  ans_set db.type postgresql; ans_set db.admin_password_file "$TMP_DIR/admin"
  out="$(render_db_env)"
  [[ "$out" == *"POSTGRES_USER=postgres"* ]]
  [[ "$out" == *"POSTGRES_PASSWORD=adminpw"* ]]
  ans_set db.admin_user dbadmin
  [[ "$(render_db_env)" == *"POSTGRES_USER=dbadmin"* ]]
}

@test "MariaDB 컨테이너는 root 비밀번호를 담는다" {
  printf 'adminpw\n' > "$TMP_DIR/admin"
  ans_set db.type mariadb; ans_set db.admin_password_file "$TMP_DIR/admin"
  [[ "$(render_db_env)" == *"MARIADB_ROOT_PASSWORD=adminpw"* ]]
}

@test "SQL Server 컨테이너는 약관 동의와 sa 비밀번호를 담는다" {
  printf 'Adm1n!pw\n' > "$TMP_DIR/admin"
  ans_set db.type mssql; ans_set db.admin_password_file "$TMP_DIR/admin"
  out="$(render_db_env)"
  [[ "$out" == *"ACCEPT_EULA=Y"* ]]
  [[ "$out" == *"MSSQL_SA_PASSWORD=Adm1n!pw"* ]]
}

@test "Cubrid 컨테이너는 UTF-8 로케일과 데이터베이스 이름을 담는다" {
  ans_set db.type cubrid; ans_set db.name yona
  out="$(render_db_env)"
  [[ "$out" == *"CUBRID_LOCALE=en_US.utf8"* ]]
  [[ "$out" == *"CUBRID_DB=yona"* ]]
}

# --- 시작, 중지, 상태 스크립트(서비스 없이 쓰는 경우) ---

@test "시작 스크립트는 java, jar, 설정 위치, pid 파일, 로그 위치를 쓴다" {
  ans_set install_dir /home/u/yona; ans_set data_dir /home/u/yona-data; ans_set env.java /j/bin/java
  out="$(render_run_script start)"
  [[ "$out" == *"/j/bin/java"* ]]
  [[ "$out" == *"/home/u/yona/yona.jar"* ]]
  [[ "$out" == *"--spring.config.additional-location=file:/home/u/yona/conf/"* ]]
  [[ "$out" == *"/home/u/yona-data/yona.pid"* ]]
  [[ "$out" == *"/home/u/yona-data/logs/yona.out"* ]]
}

@test "중지 스크립트는 pid 파일의 프로세스에 종료 신호를 보낸다" {
  ans_set install_dir /i; ans_set data_dir /d
  out="$(render_run_script stop)"
  [[ "$out" == *"kill"* ]]
  [[ "$out" == *"/d/yona.pid"* ]]
}

@test "상태 스크립트는 상태 확인 주소를 부른다" {
  ans_set install_dir /i; ans_set data_dir /d; ans_set web.port 9090
  [[ "$(render_run_script status)" == *"http://127.0.0.1:9090/-_-actuator/health"* ]]
}

@test "생성한 실행 스크립트는 bash 문법이 올바르다" {
  ans_set install_dir /i; ans_set data_dir /d; ans_set env.java /j
  for n in start stop status; do
    render_run_script "$n" > "$TMP_DIR/$n.sh"
    run bash -n "$TMP_DIR/$n.sh"
    [ "$status" -eq 0 ]
  done
}

# --- 설치 결과 문서 ---

@test "설치 결과 문서에는 접속 주소, 경로, 백업 목록이 있고 비밀번호는 없다" {
  . "$YI_DIR/lib/messages.sh"; msg_load en
  printf 'topsecret\n' > "$TMP_DIR/pw"
  ans_set version v2.0.0; ans_set install_dir /opt/yona; ans_set data_dir /var/lib/yona; ans_set web.port 8080
  ans_set db.type postgresql; ans_set db.install separate; ans_set db.host db.example.com; ans_set db.port 5432
  ans_set db.name yona; ans_set db.user yona; ans_set db.password_file "$TMP_DIR/pw"
  ans_set service.register true; ans_set site.hostname git.example.com; ans_set site.scheme https
  out="$(render_report)"
  [[ "$out" == *"https://git.example.com"* ]]
  [[ "$out" == *"/opt/yona"* ]]
  [[ "$out" == *"/var/lib/yona"* ]]
  [[ "$out" == *"secret.properties"* ]]
  [[ "$out" == *"v2.0.0"* ]]
  [[ "$out" != *"topsecret"* ]]
  [[ "$out" == *"systemctl"* ]]
}

@test "서비스 없이 설치했으면 결과 문서가 시작 스크립트와 자동 시작 없음을 안내한다" {
  . "$YI_DIR/lib/messages.sh"; msg_load en
  ans_set version v2.0.0; ans_set install_dir /i; ans_set data_dir /d; ans_set web.port 8080; ans_set db.type h2; ans_set service.register false
  out="$(render_report)"
  [[ "$out" == *"yona-start.sh"* ]]
  [[ "$out" == *"does not start automatically"* ]]
}

@test "패키지 DB의 관리자 비밀번호를 설정하는 SQL" {
  printf "ad'min\n" > "$TMP_DIR/admin"
  ans_set db.type postgresql; ans_set db.admin_password_file "$TMP_DIR/admin"
  [ "$(render_db_admin_sql)" = "ALTER ROLE \"postgres\" PASSWORD 'ad''min';" ]
  ans_set db.type mariadb
  [[ "$(render_db_admin_sql)" == *"ALTER USER 'root'@'localhost' IDENTIFIED VIA mysql_native_password USING PASSWORD('ad''min') OR unix_socket;"* ]]
  ans_set db.type h2
  [ -z "$(render_db_admin_sql)" ]
}
