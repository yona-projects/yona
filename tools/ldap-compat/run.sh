#!/bin/bash
# 실제 LDAP 서버(OpenLDAP, Apache Directory Server)에 대한 호환성 시험을 돌린다.
#   tools/ldap-compat/run.sh          서버를 띄우고, 시험을 돌리고, 서버를 지운다
#   tools/ldap-compat/run.sh start    서버만 띄운다
#   tools/ldap-compat/run.sh stop     서버를 지운다
# 필요한 것: docker, 호스트의 ldap-utils(ldapadd), 이미지 osixia/openldap:1.5.0, itzg/apacheds:latest
# 서버(컨테이너 이름 yona-ldapcompat-*): OpenLDAP 익명 허용 33890, 익명 차단 33891, 본인 항목만 읽기 33892, ApacheDS 익명 허용 33893
# ApacheDS(2.0.0-M22 이미지)는 설정 파일과 ou=config 수정으로 익명 인증기를 끄지 못해 익명 차단 서버는 없다. 결과는 yona-v2-wiki/architecture/ldap-server-compat.md를 본다.
set -euo pipefail
cd "$(dirname "$0")/../.."
HERE=tools/ldap-compat
NAMES="openldap-allow openldap-deny openldap-selfonly apacheds-allow"

stop() { for n in $NAMES; do docker rm -fv "yona-ldapcompat-$n" >/dev/null 2>&1 || true; done; }

start_openldap() { # 이름 포트
  docker run -d --name "yona-ldapcompat-$1" -p "$2:389" -e LDAP_ORGANISATION=Example -e LDAP_DOMAIN=example.com -e LDAP_ADMIN_PASSWORD=admin-pw -e LDAP_TLS=false osixia/openldap:1.5.0 >/dev/null
}
wait_for() { # 포트 DN 비밀번호
  for _ in $(seq 1 90); do ldapsearch -x -H "ldap://127.0.0.1:$1" -D "$2" -w "$3" -b dc=example,dc=com -s base dn >/dev/null 2>&1 && return 0; sleep 1; done
  echo "서버가 뜨지 않았다: $1" >&2; return 1
}
start() {
  stop
  start_openldap openldap-allow 33890; start_openldap openldap-deny 33891; start_openldap openldap-selfonly 33892
  docker run -d --name yona-ldapcompat-apacheds-allow -p 33893:10389 itzg/apacheds:latest >/dev/null
  for p in 33890 33891 33892; do wait_for $p cn=admin,dc=example,dc=com admin-pw; done
  wait_for 33893 uid=admin,ou=system secret
  # 사용자를 넣고 접근 규칙을 맞춘다. 기본 규칙은 "본인 항목만 읽기, 익명 거부"이다
  for n in openldap-allow:33890 openldap-deny:33891 openldap-selfonly:33892; do
    name=${n%%:*}; port=${n##*:}
    ldapadd -x -H "ldap://127.0.0.1:$port" -D cn=admin,dc=example,dc=com -w admin-pw -f $HERE/users.ldif >/dev/null
  done
  docker cp $HERE/allow-access.ldif yona-ldapcompat-openldap-allow:/tmp/access.ldif
  docker exec yona-ldapcompat-openldap-allow ldapmodify -Y EXTERNAL -H ldapi:/// -f /tmp/access.ldif >/dev/null
  docker cp $HERE/deny-access.ldif yona-ldapcompat-openldap-deny:/tmp/access.ldif
  docker exec yona-ldapcompat-openldap-deny ldapmodify -Y EXTERNAL -H ldapi:/// -f /tmp/access.ldif >/dev/null
  docker cp $HERE/selfonly-access.ldif yona-ldapcompat-openldap-selfonly:/tmp/access.ldif
  docker exec yona-ldapcompat-openldap-selfonly ldapmodify -Y EXTERNAL -H ldapi:/// -f /tmp/access.ldif >/dev/null
  ldapadd -x -H ldap://127.0.0.1:33893 -D uid=admin,ou=system -w secret -f $HERE/users.ldif >/dev/null
}
run_tests() {
  ./gradlew test --console=plain -Dyona.it.db=h2 -Dyona.ldap.compat=true \
    -Dyona.ldap.compat.openldap-allow=33890 -Dyona.ldap.compat.openldap-deny=33891 -Dyona.ldap.compat.openldap-selfonly=33892 -Dyona.ldap.compat.apacheds-allow=33893 \
    --tests 'dev.yona.domain.ldap.LdapServerCompatibilityTest'
}

case "${1:-all}" in
  start) start ;;
  stop) stop ;;
  all) trap stop EXIT; start; run_tests ;;
  *) echo "사용법: $0 [start|stop|all]" >&2; exit 2 ;;
esac
