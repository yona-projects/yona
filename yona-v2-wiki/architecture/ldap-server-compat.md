---
title: LDAP 서버별 호환성 시험 결과
type: architecture
sources:
  - ../decisions.md
  - src/main/kotlin/dev/yona/domain/ldap/Ldap.kt
  - src/main/kotlin/dev/yona/domain/ldap/LdapAuthService.kt
  - src/test/kotlin/dev/yona/domain/ldap/LdapServerCompatibilityTest.kt
  - tools/ldap-compat/run.sh
updated: 2026-11-10
---

# LDAP 서버별 호환성 시험 결과

`SpringLdapDirectory`(D-132)를 실제 LDAP 서버에 붙여 본 결과이다. 메모리 서버(UnboundID) 시험은 서버별 접근 규칙(ACL)과 익명 접근 설정을 알 수 없어서 따로 만들었다.
시험은 `tools/ldap-compat/run.sh`가 컨테이너를 띄우고 돌린다(`-Dyona.ldap.compat=true`일 때만 켜지고, 평소 시험에서는 건너뛴다).

## 동작 방식

이 앱은 **익명 접근을 쓰지 않는다.** 입력으로 바인드 주체를 만들어 **그 사용자로 바인드**하고, 같은 연결로 기준 DN 아래에서 그 사용자를 검색한다(v1.6과 같다). 그래서 서버의 익명 허용 여부는 로그인에 영향이 없고, 대신 **로그인한 사용자가 검색 기준 항목을 읽을 수 있는지**가 결과를 가른다.

## 확인한 서버와 결과

| 서버와 설정 | 익명 검색 | 사용자 로그인 | 비고 |
|---|---|---|---|
| OpenLDAP 2.x, 익명 읽기 허용(`by * read`) | 된다 | **된다** | |
| OpenLDAP 2.x, 익명 차단(`olcDisallows: bind_anon`, `olcRequires: authc`), 인증한 사용자는 읽기(`by users read`) | 거부 | **된다** | 익명을 막아도 영향이 없다 |
| OpenLDAP 2.x, 익명 차단, **본인 항목만 읽기**(OpenLDAP 기본 규칙 `by self read, by * none`) | 거부 | **실패한다** | 바인드는 성공하지만 사용자가 기준 항목을 볼 수 없어 서버가 `No such object`(32)로 답한다. 비밀번호가 틀리면 인증 예외로 구분된다 |
| Apache Directory Server 2.0.0-M22, 기본 설정(익명 허용) | 된다 | **된다** | |
| Apache Directory Server, 익명 차단 | **확인하지 못했다** | | 아래 "확인하지 못한 것"을 본다 |

## 알아 둘 점

1. **본인 항목만 읽게 한 서버에서는 LDAP 로그인이 되지 않는다.** 서버 쪽에서 로그인 사용자에게 검색 기준 항목의 읽기(`search`, `read`)를 열어 주어야 한다. 이는 v1.6의 설계(사용자로 바인드한 뒤 검색)와 같다. 로그인 서비스는 이 오류를 일반 실패(`NamingException`)로 처리하고 로컬 비밀번호 폴백도 하지 않는다. 조회용 계정을 받아 먼저 검색하는 방식으로 바꾸면 해결되지만 설정 화면과 비밀 값 저장이 새로 생긴다(D-132의 선택지 (나)).
2. **이메일 형식 이름(`alice@corp.com`)으로 바인드하는 것은 OpenLDAP과 Apache DS 모두 받지 않는다.** 이 형식은 Active Directory의 UPN에서만 통한다. 이메일 로그인(`@`가 든 입력과 `useEmailBaseLogin`)은 AD 같은 서버에서만 쓸 수 있다.
3. **DN 형식 바인드(`CN=입력값,<접미사>`)는 사용자의 `CN`이 로그인 ID와 같을 때만 성립한다.** 시험 데이터는 이를 만족한다. 실제 AD는 `CN`이 보통 표시 이름이라 맞지 않을 수 있고, 이때는 이메일(UPN) 로그인이 대안이다.

## 확인하지 못한 것

- **Active Directory**: 컨테이너로 만들 수 없어 시험하지 못했다. 위 2, 3번은 서버 문서와 v1.6의 설계에서 온 추정이다.
- **Apache Directory Server의 익명 차단**: `itzg/apacheds`(2.0.0-M22) 이미지에서 익명을 막지 못했다. 설정 파일(`ads-dsallowanonymousaccess: FALSE`, 익명 인증기 `ads-enabled: FALSE`)을 처음 시작 전에 넣어도, 시작 뒤 서버에 접속해 `ou=config`를 수정해도 서버가 읽은 값은 익명 인증기가 켜진 채였고 익명 검색이 계속 통과했다. 수정 뒤 재시작은 `dc=example,dc=com already exists`로 실패했다. 접근 제어(`ads-dsaccesscontrolenabled`)는 기본이 꺼져 있어 ACI로 막는 길도 따로 구성해야 한다. 앱은 익명을 쓰지 않으므로 이 서버에서의 로그인 결과가 익명 설정에 좌우될 가능성은 낮지만(OpenLDAP 결과로 미루어), 시험으로 확인하지는 못했다.
- LDAPS와 인증서 신뢰, StartTLS, 참조(referral), 큰 디렉터리에서의 검색 제한은 다루지 않았다.

## 다시 돌리려면

`tools/ldap-compat/run.sh`(서버를 띄우고, 시험을 돌리고, 서버를 지운다). 필요한 것은 docker, 호스트의 `ldap-utils`, 이미지 `osixia/openldap:1.5.0`과 `itzg/apacheds:latest`이다. 포트는 33890~33893을 쓴다.
