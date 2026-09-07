---
type: plan
id: P3-09
title: "Swagger/OpenAPI UI 노출"
status: done
priority: 1
depends_on: []
blocks: []
source: docs/parity/tickets/p3-09.md
created: 2026-08-28
updated: 2026-08-28
tags: [plan, p3, api, docs]
---

# Swagger/OpenAPI UI 노출

## 배경

legacy Yona에는 없던 신규 기능(yona 동치성과 무관) — 사용자 제안(2026-08-28). yona에는 이미 `@RestController`가
수십 개(`IssueController`, `BoardController`, `ProjectController`, `IssueApiController`, `BoardApiController`,
`MigrationApiController`, `SiteApiController`, `CommentController` 등) 있고, 개발이 상당히 진행된 지금 시점에
API 표면을 한눈에 보고 테스트해볼 수 있는 문서화 도구가 없다는 게 아쉬운 지점이었다.
원본: [`docs/parity/tickets/p3-09.md`](../../parity/index.md)

## 범위

### 포함
- `springdoc-openapi-starter-webmvc-ui` 의존성 추가 — 기존 `@RestController` 자동 스캔, 컨트롤러 코드 변경 불필요
- `SecurityConfig.kt`에 Swagger UI/OpenAPI 문서 경로 접근 정책 추가
- (선택) 컨트롤러에 `@Operation`/`@Schema` 등 springdoc 어노테이션을 점진적으로 붙여 문서 품질 개선

### 제외 (비범위)
- 기존 컨트롤러의 응답/요청 DTO를 OpenAPI 친화적으로 재설계하는 것(문서화만 목적, API 계약 자체는 안 건드림)
- [[p3-02-cli-and-rest-api]]에서 신설할 예정인 legacy Open API 호환 REST API의 문서화 — 그건 그 계획이 완료된 후
  자연히 같은 스캔 대상에 포함되므로 별도 작업 불필요

## 의존성

- **선행 조건**: 없음 — 완전히 독립적, 즉시 착수 가능
- **후속 파급**: 없음. [[p3-02-cli-and-rest-api]]가 나중에 REST API를 추가해도 springdoc이 자동으로 스캔하므로
  순서 무관

## 설계 개요

- **핵심 통찰**: 의존성 하나만 추가하면 끝 — springdoc-openapi는 클래스패스의 `@RestController`를 런타임에
  스캔해 OpenAPI 3 스펙을 자동 생성하고 `/swagger-ui.html`에서 대화형 UI로 제공한다.
- **보안 고려(코드 확인 완료)**: `SecurityConfig.kt:41-48`을 보면 `/site/**`,`/sites/**`만 명시적으로
  `hasAnyRole("ADMIN","SITE_ADMIN")`로 제한되고, 나머지는 `.anyRequest().permitAll()`로 열려 있다 — 즉 아무
  설정도 안 하면 Swagger UI/`/v3/api-docs`가 **비로그인 사용자에게도 그대로 노출**된다. 자체 호스팅 도구라도
  전체 API 표면(관리자용 엔드포인트 포함)이 인증 없이 보이는 건 정보 노출 리스크라, `/site/**`와 동일한 패턴으로
  `hasAnyRole("ADMIN","SITE_ADMIN")` 제한을 권장(Step 2에서 최종 결정).
- **경로**: springdoc 기본값 — UI는 `/swagger-ui.html`(리다이렉트 대상은 `/swagger-ui/index.html`), OpenAPI
  JSON은 `/v3/api-docs`.

## 단계별 작업 계획 (TDD)

1. **Step 1 — 의존성 추가 + 기본 노출 확인**
   - `build.gradle.kts`에 `springdoc-openapi-starter-webmvc-ui` 추가(Spring Boot 4.x/springdoc 2.x 호환 버전 확인)
   - `./gradlew bootRun` 후 `/swagger-ui.html` 접속해 기존 컨트롤러들이 스캔되는지 수동 확인
2. **Step 2 — 접근 정책 결정 및 적용**
   - 관리자 전용으로 제한할지, 로그인 사용자 전체에 허용할지 결정(권장: 관리자 전용, `/site/**`와 동일 패턴)
   - 실패 테스트: 비로그인 사용자가 `/swagger-ui.html` 또는 `/v3/api-docs` 접근 시 정책에 맞는 응답(403/302) → RED → `SecurityConfig.kt` 수정 → GREEN
3. **Step 3 — 문서 품질 개선(선택, 후속 이터레이션으로 분리 가능)**
   - 그룹핑(이슈/PR/프로젝트/관리자 API 등 `GroupedOpenApi`)
   - 주요 컨트롤러에 `@Operation(summary = ...)` 점진적 추가

## 완료 기준 (Definition of Done)

- [x] `/swagger-ui.html`(`/v3/api-docs`)에서 기존 REST 컨트롤러 전체가 자동으로 나열됨을 확인 — 테스트로
      `IssueController`의 실제 매핑 경로(`/api/projects/{projectId}/issues`)가 스펙에 포함됨을 검증
- [x] 접근 정책(관리자 전용)이 결정되고 테스트로 고정됨 — `/site/**`와 동일하게 `hasAnyRole("ADMIN","SITE_ADMIN")`
- [x] `./gradlew test` 전체 GREEN(5572건, 기존 SecurityConfig 테스트 회귀 없음)

## 완료 로그 (2026-08-28)

TDD로 진행 — `SwaggerUiAccessIntegrationSpec.kt`를 먼저 작성해 RED 확인 후 `SecurityConfig.kt`에
`/swagger-ui/**`,`/swagger-ui.html`,`/v3/api-docs/**`,`/v3/api-docs` 4개 패턴을 `/site/**`와 동일한
`hasAnyRole("ADMIN","SITE_ADMIN")`으로 제한해 GREEN.

**TDD 과정에서 발견한 함정 2가지**(둘 다 계획 단계에서는 안 보였던 것):
1. `BootstrapSetupInterceptor`(별개 기능 — "DB에 회원 0명이면 무조건 `/bootstrap-setup`으로 리다이렉트")가
   비로그인 접근 테스트와 우연히 같은 상태코드(302)로 겹쳐서, 관리자 유저를 먼저 시딩하지 않으면 테스트가
   "엉뚱한 이유로 통과"하는 거짓 양성이 남. `beforeSpec`에서 SITE_ADMIN 유저를 먼저 만들어 이 게이트를
   우회하도록 고쳐서 실제 접근 제어만 검증하게 함.
2. 비로그인 요청의 기대 상태코드가 계획서 초안의 "403/302" 추정과 달리 실제로는 **401**(`SecurityConfig.kt`에
   `httpBasic { }`도 함께 설정돼 있어, MockMvc의 비-브라우저 요청은 formLogin 리다이렉트가 아니라
   `WWW-Authenticate` 기반 401 응답을 받음) — `/site/**`와 동일한 방언이라 일관성 있는 정상 동작.

**의존성**: `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0`(springdoc 3.x가 Spring Boot 4.x/Spring
Framework 7.x용 메이저 버전).

Step 3(문서 품질 개선 — `@Operation` 어노테이션, `GroupedOpenApi` 그룹핑)은 이번 범위에서 제외, 후속 이터레이션으로 분리.

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| ~~접근 정책~~ | ~~관리자 전용 vs 로그인 사용자 전체 허용 미정~~ | **해소** — 관리자 전용으로 확정, 테스트로 고정 |
| ~~springdoc 버전 호환성~~ | ~~Spring Boot 4.x + Spring Security 6.x 조합에서의 정확한 springdoc 버전 확인 필요~~ | **해소** — 3.1.0으로 확인, 정상 동작 |

## 관련

- 백로그 원본: [`docs/parity/index.md`](../../parity/tickets/p3-09.md)
- 관련 소스: `config/SecurityConfig.kt:41-51`, `config/BootstrapSetupInterceptor.kt`
- 테스트: `src/test/kotlin/com/github/yonaprojects/yona/config/SwaggerUiAccessIntegrationSpec.kt`
