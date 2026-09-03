---
type: plan
id: P3-06
title: "엔터프라이즈 SSO(SAML2 / 범용 OIDC)"
status: planned
priority: 5
depends_on: []
blocks: []
source: docs/PARITY_BACKLOG.md#P3-06
created: 2026-08-28
updated: 2026-08-28
tags: [plan, p3, auth, sso]
---

# 엔터프라이즈 SSO(SAML2 / 범용 OIDC)

## 배경

LDAP 연동(`LdapService.kt`/`LdapUserProvisioningService.kt`/`LdapQueryBuilder.kt`/`LdapUser.kt`,
`YonaAuthenticationProvider.kt`)은 이미 구현·테스트 완료 상태임이 코드로 확인됨 — 이 계획의 신규 대상은
**SAML2와 범용 OIDC 두 가지뿐**이며 LDAP은 범위에서 제외한다.
원본: [`docs/PARITY_BACKLOG.md#P3-06`](../../PARITY_BACKLOG.md)

## 범위

### 포함
- SAML2 SP(Service Provider) 연동 — `spring-security-saml2-service-provider`
- 범용 OIDC 연동(Okta/Azure AD/Keycloak 등 임의 IdP) — `spring-security-oauth2-client`
- LDAP과 동일한 JIT(Just-In-Time) 프로비저닝 패턴 재사용

### 제외 (비범위)
- LDAP 관련 변경 없음(이미 완료)
- 기존 소셜 로그인(OAuth2, `LinkedAccount.kt`)과의 통합/병합 UI는 다루지 않음(P1-03에서 이미 "미이식 확정"으로 종결된 영역)

## 의존성

- **선행 조건**: 없음 — 완전히 독립적
- **후속 파급**: 없음

## 설계 개요

- **프로비저닝 패턴**: `LdapUserProvisioningService`처럼 `SamlUserProvisioningService`/`OidcUserProvisioningService`를
  두어 이메일 기준 기존 유저 매칭 또는 신규 생성 — LDAP과 동일한 "외부 IdP 인증 성공 → 로컬 User로 JIT 재조정" 패턴
- **인증 흐름 통합 지점(미결정, Step 1에서 확정)**: `YonaAuthenticationProvider`의 LDAP 분기(`if (ldapService.enabled) ...`)와
  나란히 SAML/OIDC 분기를 추가할지, 별도 `SecurityFilterChain` 다중 등록으로 분리할지 — SAML/OIDC는 리다이렉트 기반
  흐름이라 LDAP처럼 단순 `AuthenticationProvider` 위임만으로는 부족할 가능성이 높다는 것이 원본 백로그의 판단

## 단계별 작업 계획 (TDD)

1. **Step 1 — 인증 흐름 통합 방식 결정(스파이크)**
   - Spring Security의 SAML2/OAuth2 리다이렉트 흐름이 기존 `YonaAuthenticationProvider` 단일 분기 구조에
     끼워질 수 있는지 검증 → 안 되면 `SecurityFilterChain` 다중 등록으로 확정하고 이 문서 갱신
2. **Step 2 — OIDC 연동(먼저 착수 — SAML2보다 설정 표준화가 쉬움)**
   - 실패 테스트: 임의 OIDC IdP(테스트용 mock 또는 Keycloak testcontainer)로 로그인 성공 시 로컬 User 자동 생성 → RED → `OidcUserProvisioningService` 구현 → GREEN
   - 이메일 매칭 우선순위(기존 유저 있으면 매칭, 없으면 신규 생성) 테스트
3. **Step 3 — SAML2 연동**
   - 실패 테스트: SAML 어서션 수신 후 `SamlUserProvisioningService`가 동일한 JIT 로직으로 유저를 생성/매칭 → RED → 구현 → GREEN
4. **Step 4 — 관리자 설정 UI/설정 항목**
   - IdP 메타데이터(엔티티 ID, 인증서, 엔드포인트) 등록 화면 또는 `application.yml` 기반 설정 — 범위 확정 필요(이 계획 착수 시점에 결정)
5. **Step 5 — 다중 IdP 동시 지원 여부 확인**
   - LDAP + OIDC + SAML2를 한 인스턴스가 동시에 지원해야 하는지(대부분의 엔터프라이즈 요구사항) 테스트로 검증

## 완료 기준 (Definition of Done)

- [ ] OIDC 로그인 → JIT 프로비저닝 → 세션 생성까지 테스트로 검증
- [ ] SAML2 로그인 → JIT 프로비저닝 → 세션 생성까지 테스트로 검증
- [ ] LDAP 기존 동작(`LdapUserProvisioningServiceSpec` 등)에 회귀 없음 확인
- [ ] 인증 흐름 통합 방식(Step 1 결정)이 이 문서에 반영되고 실제 구현과 일치
- [ ] `./gradlew test` 전체 GREEN

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| 인증 흐름 통합 방식 | `AuthenticationProvider` 위임 vs `SecurityFilterChain` 다중 등록 미정 | Step 1 스파이크로 착수 즉시 해소 |
| 관리자 설정 방식 | UI로 IdP 등록할지 설정 파일 기반일지 미정 | Step 4에서 결정 — 최소 범위는 설정 파일 기반으로 잡고 UI는 후속 과제로 분리 가능 |

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-06)
- 관련 계획: [[p3-07-mcp-server]] — MCP 서버가 자체 운영할 OAuth 인가 서버의 로그인 화면이 이 계획의 SAML/OIDC
  로그인도 그대로 상속받는 구조(이 계획이 P3-07의 블로커는 아님, 순서 무관하게 나중에 합류)
- 관련 소스: `config/YonaAuthenticationProvider.kt`, `domain/user/LdapUserProvisioningService.kt`, `domain/user/LdapService.kt`
