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

D-133 이후 순서는 이렇다. **먼저 사용자를 검색해 DN을 찾고**(조회용 계정이 있으면 그 계정으로, 없으면 익명으로) 그 DN으로 바인드한다. 익명 검색이 안 되거나 사용자를 찾지 못하면 **v1.6 방식으로 되돌아간다**: 입력으로 바인드 주체를 만들어 그 사용자로 바인드하고 같은 연결로 검색한다. 조회용 계정이 있으면 되돌아가지 않는다.

## 확인한 서버와 결과

| 서버와 설정 | 조회용 계정 | `CN`이 다른 사용자 | 이메일 입력 | 비고 |
|---|---|---|---|---|
| OpenLDAP, 익명 읽기 허용 | 없음 | **된다** | **된다** | 익명 검색으로 DN을 찾는다 |
| OpenLDAP, 익명 차단, 인증한 사용자는 읽기 | 없음 | 안 된다 | 안 된다 | v1.6 방식으로 되돌아간다. `CN`이 로그인 ID와 같은 사용자만 된다 |
| OpenLDAP, 익명 차단, 인증한 사용자는 읽기 | **있음** | **된다** | **된다** | |
| OpenLDAP, 익명 차단, **본인 항목만 읽기**(기본 규칙) | 없음 | 안 된다 | 안 된다 | 비밀번호가 맞아도 검색 기준 항목을 읽지 못해 서버가 `No such object`로 답한다 |
| OpenLDAP, 익명 차단, 본인 항목만 읽기 | **있음** | **된다** | **된다** | 앞서 실패하던 서버가 된다 |
| Apache DS 2.0.0-M22(익명 허용) | 없음 | **된다** | **된다** | |
| Apache DS | 있음 | **된다** | **된다** | |
| Apache DS, 익명 차단 | | | | **확인하지 못했다.** 아래를 본다 |

시험 데이터의 `carol`은 `CN=Carol Park`이고 로그인 ID가 `carol`이다(실제 Active Directory처럼 `CN`이 표시 이름인 경우).

## 알아 둘 점

1. **본인 항목만 읽게 한 서버**는 조회용 계정을 주어야 한다. 계정이 없으면 이전과 같이 로그인되지 않는다.
2. **이메일 형식 이름으로 바인드하는 것**은 OpenLDAP과 Apache DS 모두 받지 않는다(Active Directory의 UPN에서만 통한다). 이제 이메일 입력은 검색(`mail` 속성)으로 DN을 찾아 로그인하므로, 익명 검색이나 조회용 계정이 되는 서버에서는 이메일로도 로그인된다. 둘 다 안 되는 서버에서만 이메일 로그인이 실패한다.
3. **조회용 계정의 비밀번호가 틀리면** 사용자의 비밀번호 오류가 아니라 일반 오류로 알린다(로그인이 실패하고 로컬 폴백도 하지 않는다). 설정 화면의 연결 확인이 계정 바인드까지 확인한다.

## 확인하지 못한 것

- **Active Directory**: 컨테이너로 만들 수 없어 시험하지 못했다. 위 2, 3번은 서버 문서와 v1.6의 설계에서 온 추정이다.
- **Apache Directory Server의 익명 차단**: `itzg/apacheds`(2.0.0-M22) 이미지에서 익명을 막지 못했다. 설정 파일(`ads-dsallowanonymousaccess: FALSE`, 익명 인증기 `ads-enabled: FALSE`)을 처음 시작 전에 넣어도, 시작 뒤 서버에 접속해 `ou=config`를 수정해도 서버가 읽은 값은 익명 인증기가 켜진 채였고 익명 검색이 계속 통과했다. 수정 뒤 재시작은 `dc=example,dc=com already exists`로 실패했다. 접근 제어(`ads-dsaccesscontrolenabled`)는 기본이 꺼져 있어 ACI로 막는 길도 따로 구성해야 한다. 앱은 익명을 쓰지 않으므로 이 서버에서의 로그인 결과가 익명 설정에 좌우될 가능성은 낮지만(OpenLDAP 결과로 미루어), 시험으로 확인하지는 못했다.
- LDAPS와 인증서 신뢰, StartTLS, 참조(referral), 큰 디렉터리에서의 검색 제한은 다루지 않았다.

## 다시 돌리려면

`tools/ldap-compat/run.sh`(서버를 띄우고, 시험을 돌리고, 서버를 지운다). 필요한 것은 docker, 호스트의 `ldap-utils`, 이미지 `osixia/openldap:1.5.0`과 `itzg/apacheds:latest`이다. 포트는 33890~33893을 쓴다.
