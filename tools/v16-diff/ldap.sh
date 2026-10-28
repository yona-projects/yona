#!/bin/bash
# LDAP 로그인 대조: 인메모리 LDAP 서버(10389)를 띄우고 두 서버가 그것을 보게 해 설정 변형마다 ldap.py를 돌린다.
#   ldap.sh [변형 ...]   변형: base, email, guest, confirm, down (기본 전부)
cd "$(dirname "$0")" || exit 1
WORK=/home/jiho/yona-convert/yona-diff-work
J21=/usr/lib/jvm/java-21-openjdk-amd64
JAR=$(find ~/.gradle/caches/modules-2 -name "unboundid-ldapsdk-7.0.3.jar" | head -1)
nohup $J21/bin/java -cp "$JAR" ldap/LdapServer.java 10389 "$WORK/ldap.log" > "$WORK/ldapd.out" 2>&1 &
LPID=$!
sleep 6
COMMON16="-Dapplication.use.ldap.login.supoort=true -Dldap.host=127.0.0.1 -Dldap.port=10389 -Dldap.protocol=ldap -Dldap.baseDN=ou=people,dc=example,dc=com -Dldap.distinguishedNamePostfix=OU=people,DC=example,DC=com -Dldap.loginProperty=uid -Dldap.displayNameProperty=displayName -Dldap.userNameProperty=CN -Dldap.emailProperty=mail -Dldap.options.englishNameAttributeName=givenName"
COMMON2="--yona.ldap.enabled=true --yona.ldap.host=127.0.0.1 --yona.ldap.port=10389 --yona.ldap.base-dn=ou=people,dc=example,dc=com --yona.ldap.distinguished-name-postfix=OU=people,DC=example,DC=com --yona.ldap.login-property=uid --yona.ldap.display-name-property=displayName --yona.ldap.user-name-property=CN --yona.ldap.email-property=mail --yona.ldap.english-name-attribute-name=givenName"
variant() { # 이름 v16추가 v2추가
  if [ "$#" -gt 0 ] && [ -n "${WANT:-}" ] && [[ " $WANT " != *" $1 "* ]]; then return; fi
  printf '%s' "$COMMON16 $2" > "$WORK/cfg.v16"; printf '%s' "$COMMON2 $3" > "$WORK/cfg.v2"
  ./servers.sh restore data2 >/dev/null 2>&1; : > "$WORK/ldap.log"
  echo "=== LDAP 변형: $1"; python3 ldap.py ${LDAP_SCEN:-} 2>&1 | tail -${LINES_MAX:-60}
}
WANT="$*"
variant base "" ""
variant email "-Dldap.options.useEmailBaseLogin=true -Dldap.options.fallbackToLocalLogin=true" "--yona.ldap.use-email-base-login=true --yona.ldap.fallback-to-local-login=true"
variant guest "-Dapplication.guest.user.login.id.prefix=g-" "--yona.guest-user-login-id-prefix=g-"
variant confirm "-Dsignup.require.admin.confirm=true" "--yona.signup.require-admin-confirm=true"
if [ -z "$WANT" ] || [[ " $WANT " == *" down "* ]]; then
  printf '%s' "$COMMON16 -Dldap.options.fallbackToLocalLogin=true" > "$WORK/cfg.v16"; printf '%s' "$COMMON2 --yona.ldap.fallback-to-local-login=true" > "$WORK/cfg.v2"
  ./servers.sh restore data2 >/dev/null 2>&1
  kill $LPID 2>/dev/null; sleep 2
  echo "=== LDAP 변형: down (서버 중지, 로컬로 되돌림)"; python3 ldap.py a_basic b_collisions 2>&1 | tail -30
else
  kill $LPID 2>/dev/null
fi
: > "$WORK/cfg.v16"; : > "$WORK/cfg.v2"
./servers.sh restore data2 >/dev/null 2>&1
kill $LPID 2>/dev/null
