---
type: plan
id: P3-07
title: "yona MCP 서버 (이슈/PR 읽기·쓰기)"
status: done
priority: 6
depends_on: [p3-02-cli-and-rest-api]
blocks: []
source: docs/PARITY_BACKLOG.md#P3-07
created: 2026-08-28
updated: 2026-09-07
tags: [plan, p3, mcp, ai, oauth]
---

# yona MCP 서버 (이슈/PR 읽기·쓰기)

## 배경

AI 에이전트(Claude Code 등)가 MCP(Model Context Protocol)로 yona 저장소의 이슈/PR을 직접 다루도록 지원 —
조회·검색뿐 아니라 이슈 생성·코멘트·클로즈, PR 생성·리뷰·코멘트·머지까지, GitHub 공식 MCP 서버와 동등한 수준.
원 저작자의 v2 브랜치에도 읽기 전용 MCP 서버가 이미 추가되어 있음(비교 참고: v2는 읽기 전용, 이 계획은 쓰기까지 포함).

**코드 검증 완료**: 현재 yona에 이슈/PR을 개별적으로 생성·조회·수정하는 범용 JSON REST API가 없음
(`ProjectApiController.kt`는 export/import 전용, 나머지는 Thymeleaf MVC 컨트롤러) — **[[p3-02-cli-and-rest-api]]와
정확히 같은 선행 의존성을 공유**한다.
원본: [`docs/PARITY_BACKLOG.md#P3-07`](../../PARITY_BACKLOG.md)

**2026-08-28 인증 방식 재설계(사용자 요청, 리서치 완료)**: 최초 설계는 P3-02의 Fine-grained PAT 토큰을
설정 파일에 수동으로 붙여넣는 방식만 가정했으나, Claude 등 대화형 MCP 클라이언트가 원격 서버 접속 시
**자동으로 브라우저 인가 화면을 띄우는 OAuth 플로우**를 기대한다는 점이 확인돼 이중 인증 체계로 재설계.
아래 [설계 개요 — 인증](#인증-oauth-21--fine-grained-pat-이중-체계) 참고.

## 범위

### 포함
- 읽기: 이슈/PR 조회·검색
- 쓰기: 이슈 생성·코멘트·클로즈, PR 생성·리뷰·코멘트·머지
- 원격 호스팅 지원(Streamable HTTP/SSE 기반)
- **OAuth 2.1 기반 인가**(RFC9728 Protected Resource Metadata, PKCE, Dynamic Client Registration) — Claude 등
  대화형 클라이언트가 별도 설정 없이 접속 시 자동으로 로그인 화면을 띄우고 토큰을 발급받는 플로우
- 기존 [[p3-02-cli-and-rest-api]] Fine-grained PAT — CI/스크립트 등 헤드리스 클라이언트용으로 병행 유지

### 제외 (비범위)
- 범용 REST API 자체와 토큰 스코프 모델 구현 — [[p3-02-cli-and-rest-api]]에서 이미 만든 것을 그대로 소비
- stdio 전송 방식(로컬 실행 전제라 원격 호스팅된 yona 인스턴스에는 부적합하다는 것이 원본 백로그의 판단 — 채택하지 않음)
- SAML2/범용 OIDC IdP 연동 자체 — [[p3-06-enterprise-sso]]의 범위. 이 계획은 그 로그인 스택을 **소비만** 한다
  (아래 SSO 관계 참고)

## 의존성

- **선행 조건**: [[p3-02-cli-and-rest-api]]의 1부(범용 REST API + `ApiToken` 스코프 모델)가 완료되어야 착수 가능 — 강한 블로커
- **[[p3-06-enterprise-sso]]와의 관계 — 블로커 아님**: MCP OAuth 인가 서버가 사용자에게 띄우는 "로그인 화면"은
  yona 자신의 기존 로그인 스택(현재도 이미 있는 폼 로그인 + LDAP)을 그대로 재사용하면 되므로, **P3-06을 먼저
  끝낼 필요가 전혀 없다** — 지금 상태로 착수 가능. P3-06이 나중에 별도로 완료되면, MCP 인가 서버가 그 위에
  얹혀 있는 구조이기 때문에 SAML2/OIDC 로그인도 자동으로 상속받는다(코드 변경 불필요, 인가 서버가 위임하는
  대상이 늘어날 뿐). 즉 **레이어가 다르다** — P3-06은 "yona가 외부 IdP에 로그인을 위임하는 클라이언트" 역할이고,
  이 계획은 "yona가 MCP 클라이언트(Claude)에게 토큰을 발급하는 인가 서버" 역할이라 방향이 반대다.
- **후속 파급**: 없음

## 설계 개요

- **아키텍처 원칙**: MCP 서버는 비즈니스 로직을 새로 짜지 않고, [[p3-02-cli-and-rest-api]]에서 만든 REST API 위에
  얇게 얹는 클라이언트로 설계 — CLI(`yona mcp serve`)와 동일한 원칙(원본 백로그가 명시적으로 강조한 "중복 구현 금지")
- **구현 언어/배치(미결정, Step 1에서 확정 필요, 아래 인증 설계로 판단 근거 하나 추가됨)**:
  - 안 A: P3-02 CLI(Go)가 REST API를 감싸는 방식으로 같이 구현(`yona mcp serve`)
  - 안 B: yona Kotlin/Spring 서버 자체에 MCP 트랜스포트 엔드포인트 추가
  - **2026-08-28 추가 판단 근거**: OAuth **인가 서버(Authorization Server)** 역할은 yona 자신의 로그인 화면·세션·
    사용자 저장소를 그대로 써야 하므로 **반드시 Kotlin/Spring 쪽에 있어야 한다**(Go 프로세스가 별도로 인가 서버를
    자체 구현하는 건 로그인 스택을 통째로 이중화하는 셈이라 "중복 구현 금지" 원칙에 위배). MCP 프로토콜
    **전송(transport)** 자체는 여전히 Go(CLI)든 Kotlin이든 가능하지만, 인가 서버가 Kotlin에 있어야 한다는 게
    확정되면서 **안 B(Kotlin 서버 내장) 쪽으로 무게가 실림** — 안 A를 택하더라도 최소한 인가 서버 부분만은
    Kotlin 쪽에 남아야 해서 아키텍처가 두 프로세스로 쪼개진다. Step 1에서 이 트레이드오프까지 포함해 최종 결정.

### 인증: OAuth 2.1 + Fine-grained PAT 이중 체계

**리서치 근거**: [MCP 공식 Authorization 스펙](https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization)
확인 결과 —
- 인증은 스펙상 OPTIONAL이지만 지원 시 **OAuth 2.1 필수**(PKCE 포함), 역할이 3분리된다: **MCP 서버 = 리소스 서버**,
  **MCP 클라이언트(Claude) = OAuth 클라이언트**, **인가 서버 = 로그인·토큰 발급 담당**. 스펙 원문이 인가 서버를
  "리소스 서버와 함께 호스팅되거나 별도 엔티티일 수 있다"고 명시해, **자체 운영도 위임도 둘 다 표준적으로 허용**된다.
- MCP 서버는 **RFC9728(Protected Resource Metadata, `/.well-known/oauth-protected-resource`) 필수** — 401 응답 시
  `WWW-Authenticate` 헤더로 인가 서버 위치를 클라이언트에 알려야 함.
- 인가 서버는 **RFC8414(AS Metadata) 필수**.
- **Dynamic Client Registration(RFC7591)은 SHOULD**(필수 아님) — Claude Code는 이걸 지원하는 걸 전제로 자동
  등록·자동 팝업 플로우를 수행한다(사용자가 말한 "자동으로 인증 화면 띄우는 프로세스"가 바로 이것).
- **Resource Indicators(RFC8707)는 클라이언트 MUST** — 토큰이 이 MCP 서버 전용임을 명시. 서버는 자기 앞으로
  발급된 토큰인지(audience) 반드시 검증해야 하고, 그 토큰을 그대로 다른 API에 전달(passthrough)하면 안 됨.

**Claude 쪽 실제 흐름(리서치로 확인)**: MCP 서버가 401 + `WWW-Authenticate`를 반환하면 Claude Code가
① OAuth 메타데이터 자동 탐지 → ② 인가 서버에 Dynamic Client Registration으로 자동 등록 → ③ 브라우저 인가
화면을 자동으로 띄움 → ④ 승인 후 토큰 교환·로컬 캐시·이후 자동 갱신까지 전부 처리한다. 서버 쪽이 스펙을
정확히 구현하기만 하면 Claude 쪽에 추가로 맞출 게 없다(`redirect_uri`도 클라이언트가 등록 시점에 스스로 정함).

**yona 설계 방향**:
- Fine-grained PAT(P3-02)는 **폐기하지 않고 병행** — PAT는 CI/CD·헤드리스 스크립트용, OAuth는 Claude 같은
  대화형 클라이언트의 "팝업 로그인"용으로 역할 분리.
- `spring-boot-starter-oauth2-resource-server`로 yona를 MCP **리소스 서버**로 구성(들어오는 토큰 검증).
- **인가 서버는 자체 운영**(Spring Authorization Server, `spring-security-oauth2-authorization-server`)하되,
  **로그인 화면은 yona 자신의 기존 인증 스택(폼 로그인 + LDAP, 향후 [[p3-06-enterprise-sso]] 추가 시 SAML/OIDC까지)을
  그대로 재사용** — 참고 선례: [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security)
  프로젝트가 정확히 이 패턴(리소스 서버는 `issuer-uri` 설정만, 인가 서버 모듈은 Spring Security OAuth2
  Authorization Server를 그대로 사용)을 이미 구현해둠 — 라이브러리 재사용 가능성 Step 1에서 함께 검토.
- PKCE/Dynamic Client Registration/Resource Indicators 3가지는 스펙 준수를 위해 전부 구현 필요(선택 사항 아님,
  DCR만 SHOULD라 최소 하드코딩 클라이언트ID로 대체 가능하지만 Claude 자동 흐름을 온전히 지원하려면 구현 권장).

## 단계별 작업 계획 (TDD)

1. **Step 1 — 구현 언어/배치 + 인가 서버 라이브러리 결정(스파이크, 해소됨 2026-09-06)**
   - Go(CLI 통합) vs Kotlin(서버 내장) 트레이드오프 정리(인가 서버는 Kotlin 확정, 전송 계층만 미정)
   - `spring-ai-community/mcp-security` 재사용 가능 여부 검토(라이선스/버전 호환성 포함)
   - 이 문서에 결정 사유 반영
   - **결정: 안 B(Kotlin/Spring 서버 내장), 프로토콜/AS 핵심은 라이브러리 재사용 + 얇은 커스텀 글루**.
     상세 근거는 아래 "완료 로그 — Step 1" 참고.
2. **Step 2 — OAuth 인가 서버 + 리소스 서버 스캐폴딩(완료 2026-09-06)**
   - `/.well-known/oauth-protected-resource`(RFC9728), `/.well-known/oauth-authorization-server`(RFC8414) 메타데이터 엔드포인트
   - PKCE 필수 강제, Dynamic Client Registration(RFC7591), Resource Indicators(RFC8707) audience 검증
   - 인가 서버의 로그인 화면이 기존 yona 로그인(폼+LDAP)으로 정상 리다이렉트되는지 확인
   - 실패 테스트: 토큰 없이 MCP 엔드포인트 호출 시 401 + 올바른 `WWW-Authenticate` 헤더 → RED → 구현 → GREEN
   - 실패 테스트: 다른 리소스 서버용으로 발급된 토큰(audience 불일치)으로 호출 시 거부 → RED → 구현 → GREEN
   - 상세: "완료 로그 — 2라운드" 참고.
3. **Step 3 — MCP 서버 스캐폴딩 + 읽기 전용 도구(완료 2026-09-06)**
   - `list_issues`, `get_issue`, `list_pull_requests`, `get_pull_request` 등 읽기 도구
   - 실패 테스트: OAuth 토큰/PAT 스코프 밖 저장소를 조회 시 빈 결과/거부 → RED → 구현 → GREEN
   - v2의 읽기 전용 MCP 서버(`app/mcp/*`, `app/controllers/mcp/McpController.java`)를 도구 이름/스키마 설계 참고
     자료로 활용(코드 재사용은 불가 — Play/Java, 이쪽은 별개 스택)
   - 상세: "완료 로그 — 3라운드" 참고.
4. **Step 4 — 쓰기 도구: 이슈(완료 2026-09-06)**
   - `create_issue`, `comment_issue`, `close_issue` — 각 도구마다 스코프 부족 시 거부 테스트 우선
   - 상세: "완료 로그 — 3라운드" 참고(Step3~5를 한 라운드로 함께 구현).
5. **Step 5 — 쓰기 도구: PR(완료 2026-09-06)**
   - `create_pull_request`, `review_pull_request`, `comment_pull_request`, `merge_pull_request`
   - `merge_pull_request`는 파급력이 가장 큰 동작이므로 스코프 검증 + [[p3-04-branch-protection]] 정책(있다면) 준수 여부 확인
   - 상세: "완료 로그 — 3라운드" 참고.
6. **Step 6 — 원격 전송(Streamable HTTP/SSE) + 실제 MCP 클라이언트 E2E 검증(완료 2026-09-06)**
   - 다중 클라이언트/다중 세션 동시 접속 시나리오 테스트
   - Claude Code로 실제 접속해 OAuth 자동 팝업 → 토큰 발급 → 도구 호출까지 수동 E2E 검증(대체: 공식 MCP
     Java SDK 클라이언트로 자동화된 동등 검증 — 아래 DoD 1번 참고)
   - 상세: "완료 로그 — 4라운드"(실제 MCP 클라이언트 E2E 스펙이 찾아낸 6개 실버그 수정) 참고.

## 완료 기준 (Definition of Done)

- [x] Claude Code에서 별도 설정(PAT 수동 발급/붙여넣기) 없이 접속만으로 브라우저 인가 화면이 자동으로 뜨고,
      승인 후 도구 호출이 가능함을 수동 검증 — **사람이 실제 브라우저로 확인하는 부분은 이 세션에서
      대신 수행할 수 없다(명시적으로 기록)**. 대신 사용자 지시에 따라 **자동화된 최대한의 동등 검증**을
      `McpToolsEndToEndSpec`(신규)으로 구현했다: 공식 MCP Java SDK 클라이언트(`io.modelcontextprotocol.sdk:mcp`의
      `HttpClientStreamableHttpTransport`+`McpClient` — Claude Code가 내부적으로 쓰는 것과 동일한 라이브러리)로
      실제 임베디드 톰캣 소켓에 접속해 (a) 토큰 없이 호출 → 401+`WWW-Authenticate`(RFC9728 위치 포함),
      (b) `/.well-known/oauth-protected-resource`/`/.well-known/oauth-authorization-server` 스펙 준수,
      (c) RFC7591 DCR로 실제 클라이언트 등록, (d) PKCE 포함 authorization code flow 전체(동의 화면 렌더링
      포함) → 토큰 발급, (e) 발급된 토큰으로 `initialize` → `tools/list` → `tools/call`까지 실제 MCP 프로토콜로
      끝까지 태우는 것을 전부 자동화했다 — "브라우저 팝업을 눈으로 보는 것" 자체와 "Claude Code 앱 UI의
      실제 사용성(팝업 렌더링 타이밍, 브라우저 리다이렉트 UX 등)"만 사람만 할 수 있는 영역으로 남는다(조용히
      생략하지 않고 여기 기록).
- [x] MCP 리소스 서버가 audience(Resource Indicators) 불일치 토큰을 거부함을 테스트로 보장 —
      `McpOAuth2SecurityIntegrationSpec`("다른 audience로 서명된 토큰 거부"), `McpToolsEndToEndSpec`.
- [x] 읽기 도구 전체가 토큰 스코프(OAuth/PAT 공통)를 준수함을 테스트로 보장 — `McpScopeGuardSpec`,
      `IssueMcpToolsSpec`/`PullRequestMcpToolsSpec`의 list_issues/get_issue/list_pull_requests/get_pull_request 케이스.
- [x] 쓰기 도구(이슈/PR) 전체가 토큰 스코프를 준수함을 테스트로 보장, 특히 `merge_pull_request` —
      `PullRequestMcpToolsSpec`이 스코프 거부 시 `PullRequestController.mergePullRequest()`가 **호출조차
      되지 않음**을 `verify(exactly = 0)`로 직접 증명(브랜치 보호 우회 방지 1차 방어선).
- [x] 인가 서버 로그인 화면이 기존 yona 인증 스택(폼+LDAP)을 그대로 재사용함을 확인(SSO 추가 시 자동 상속 구조) —
      `AuthorizationServerConfig`가 `/users/loginform`으로 리다이렉트(신규 로그인 화면 없음), P3-06
      로그인 스택 위에 얹혀 있어 SAML2/OIDC도 자동 상속.
- [x] 구현 언어/배치 결정 사유가 이 문서에 기록됨 — "완료 로그 — 1라운드".
- [x] `./gradlew test`(Kotlin 서버 쪽) 및 해당 언어 테스트 스위트 GREEN — 전체 스위트 재확인, "완료 로그 —
      5라운드" 참고.

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| 구현 언어/배치 | Go CLI 통합 vs Kotlin 서버 내장 미정(단, 인가 서버는 Kotlin 확정) | **해소(2026-09-06)** — 안 B(Kotlin 서버 내장) 확정, 근거는 완료 로그 Step 1 참고 |
| 인가 서버 자체 운영 vs 라이브러리 재사용 | `spring-ai-community/mcp-security` 재사용 가능 여부 미검증 | **해소(2026-09-06)** — 라이선스/버전 확인 완료(Apache 2.0, Boot 4.1 대상), 다만 보안 신뢰 경계 최소화를 위해 **채택하지 않고** `spring-security-oauth2-authorization-server` 직접 사용 + RFC8707만 직접 구현하기로 결정(근거는 완료 로그 Step 1 참고) |
| 쓰기 권한 오남용 리스크 | AI 에이전트가 PR을 임의 머지할 위험 | **해소(2026-09-06)** — `merge_pull_request` 도구가 새 머지 경로를 만들지 않고 기존 `PullRequestController.mergePullRequest() -> PullRequestServiceImpl.merge() -> checkBranchProtectionForMerge()`를 그대로 호출(우회 불가), scopeGuard가 PULL_REQUESTS:write 없으면 그 호출 자체를 막음(테스트로 검증) |
| MCP 스펙 버전 드리프트 | 2025-11-25 draft에서 OIDC Discovery/증분 동의 등 확장이 이미 진행 중 — 착수 시점에 스펙이 더 바뀌어 있을 수 있음 | **해소(2026-09-06)** — 착수 시점 재확인 결과 최신 리비전은 2026-07-28(세션리스 재설계)이지만 실제 라이브러리(Spring AI 2.0.x)가 아직 2025-11-25 시맨틱만 구현 — 라이브러리가 구현한 리비전 기준으로 진행(완료 로그 1라운드 참고), RFC9728/RFC8707 MUST 요구사항은 리비전 간 변화 없음을 확인 |
| DCR 엔드포인트 스팸/오남용 | RFC7591 특성상 `/oauth2/register`가 인증 없이 열려 있음 — 무제한 클라이언트 등록 가능 | **해소(2026-09-06)** — `DcrRateLimitFilter`(IP당 분당 10건 고정 윈도)로 최소한의 방어책 추가 |

## 완료 로그

### 1라운드 (2026-09-06) — Step 1(스파이크: 언어/배치 + 라이브러리 결정)

- **Go vs Kotlin**: 계획 문서가 이미 "인가 서버는 반드시 Kotlin"까지 확정해뒀던 상태에서, 코드로
  직접 재확인한 결과 **안 B(Kotlin 서버 내장)로 확정**했다 — 결정적 근거는 [[p3-02-cli-and-rest-api]]가
  이미 만들어둔 `IssueRestApiController`/`PullRequestApiController`(둘 다 `/api/v1/projects/{owner}/{project}/...`
  네임스페이스)가 `IssueController`/`PullRequestController`의 기존 공개 메서드에 얇게 위임하는 구조이고,
  그 메서드들이 전부 `Authentication?`을 받아 `userRepository.findByLoginId(authentication.name)`로
  로그인 사용자를 찾는다는 점(`PullRequestController.getLoginUser()` 확인)이다. `YonaUserDetails.getUsername()`이
  `loginId`를 반환하므로(코드 확인), 폼로그인/LDAP/SAML2/OIDC 어떤 경로로 인증하든 `Authentication.name`은
  항상 `loginId`다. Spring Authorization Server가 액세스 토큰을 발급할 때 기본적으로 `sub` 클레임을
  `context.getPrincipal().getName()`으로 채우므로, **별도의 사용자 매핑 코드 없이** 리소스 서버가 검증한
  JWT의 `Authentication`을 `IssueController`/`PullRequestController`에 그대로 전달하면 스코프 체크 이후의
  모든 기존 비즈니스 로직(AccessControl, 브랜치 보호 등)이 완전히 동일하게 동작한다 — 이 사실이 확인되며
  "두 프로세스로 쪼개는 안 A"를 택할 이유가 사실상 사라졌다(안 A를 택해도 인가 서버는 Kotlin에 있어야 하고,
  Go 프로세스는 그 JWT를 다시 이 방식으로 검증해야 하는데, 그럴 바엔 같은 프로세스에 두는 게 네트워크
  홉·배포 단위 하나를 그냥 없애는 셈).
- **`spring-ai-community/mcp-security`(v0.1.14, Apache 2.0, Spring Boot 4.1.0/Spring AI 2.0.0 대상,
  Maven Central 공개 배포 확인)**: 리서치 결과 실제로 쓸 수 있는 라이브러리임을 확인했다(License/버전/
  Maven Central 좌표 전부 실측 확인 — 자세한 근거는 아래 참고). 그럼에도 **채택하지 않기로 결정**했다.
  이유:
  1. 이 프로젝트가 인가 서버로 직접 채택한 `spring-security-oauth2-authorization-server`(Spring
     Security 7.0부터 Spring Security 본체에 병합, 현재 7.1.1 — `spring-boot-starter-security`가 이미
     관리하는 버전이라 별도 버전 고정 불필요)가 PKCE(S256 강제가 기본값, 코드 확인 불필요할 만큼 명확한
     공식 문서/릴리스 노트 근거)와 RFC7591 DCR(`/oauth2/register`, 기본 비활성 → `setOpenRegistrationAllowed(true)`로
     활성화)을 **이미 기본 제공**해, `mcp-security`가 채워주는 실질적 갭은 RFC8707(Resource Indicators)
     오디언스 검증 하나로 좁혀진다.
  2. RFC8707 오디언스 검증은 Spring이 명시적으로 확장 지점으로 설계해둔 `OAuth2TokenCustomizer<JwtEncodingContext>`
     하나로 충분히 작게(수십 줄) 직접 구현 가능한 범위다 — "프로토콜을 처음부터 재구현하지 말라"는
     이 프로젝트의 원칙은 OAuth2/PKCE/DCR처럼 정교하고 실수하기 쉬운 프로토콜 기계장치에 적용되는
     것이지, 라이브러리가 의도적으로 열어둔 커스터마이징 포인트를 채우는 수십 줄짜리 글루 코드까지
     반드시 서드파티에 의존해야 한다는 뜻은 아니라고 판단했다.
  3. `mcp-security`는 **아직 0.1.x(pre-1.0), 사실상 단일 메인테이너** 프로젝트다 — 이 계획이 다루는
     영역(OAuth2 인가 서버, 계정 탈취로 이어질 수 있는 가장 민감한 인프라)에서는, 이미 Spring Security
     코어 팀이 직접 유지보수하는 `spring-security-oauth2-authorization-server`(신뢰 검증이 훨씬 두터운
     1급 라이브러리) 위에 **직접 검토 가능한 얇은 커스텀 코드**를 얹는 쪽이, 검증이 상대적으로 얕은
     pre-1.0 서드파티 보안 라이브러리를 신뢰 경계에 추가하는 것보다 안전하다고 판단했다(방금 전
     [[p3-06-enterprise-sso]]에서 `email_verified` 미검증 같은 실제 계정 탈취 취약점이 최종 리뷰에서야
     발견된 선례가 있어, 이번엔 더 보수적으로 간다).
  4. 참고로 이 라이브러리가 실제로 Maven Central에 공개 배포돼 있다는 사실 자체(`org.springaicommunity:
     mcp-authorization-server-spring-boot:0.1.14` 등, `repo.maven.apache.org` `maven-metadata.xml`로
     확인)와 라이선스(Apache 2.0)는 향후 유지보수 부담이 커지면 재검토할 수 있도록 이 문서에 남겨둔다 —
     **채택 보류이지 "쓸 수 없다"는 결론이 아니다.**
- **최종 라이브러리 구성(확정)**:
  - MCP 프로토콜/전송/도구 등록: **재사용** — `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`
    (Spring AI 2.0.x, MCP Java SDK 2.0.x를 내부에 포함) — Streamable HTTP 전송(`spring.ai.mcp.server.protocol=STREAMABLE`)과
    `@Tool`/`@ToolParam`(`org.springframework.ai.tool.annotation`) 애노테이션 기반 도구 등록 +
    `MethodToolCallbackProvider`를 그대로 쓴다(HTTP+SSE 프로토콜을 직접 구현하지 않음 — Step 3에서
    실제 사용해 확정된 정확한 애노테이션명으로 갱신, 최초 스파이크 시점 표기는 `@McpTool`/`@McpToolParam`으로
    다소 부정확했다).
  - OAuth2 인가 서버 핵심 메커니즘(PKCE/토큰 발급/도입·폐기): **재사용** —
    `org.springframework.security:spring-security-oauth2-authorization-server`(7.1.1, Spring Security
    BOM 관리 버전).
  - RFC9728(Protected Resource Metadata) 응답 JSON, RFC8707(Resource Indicators) 오디언스 검증/스탬핑:
    **직접 구현**(수십 줄, Spring이 명시적으로 열어둔 확장 지점만 사용) — Step 2에서 구현.
  - `RegisteredClient`/`OAuth2Authorization`/`OAuth2AuthorizationConsent` 저장소: Spring이 기본 제공하는
    `JdbcRegisteredClientRepository`/`JdbcOAuth2AuthorizationService`(공식 스키마 SQL, MariaDB/PostgreSQL/
    MySQL 등 특정 방언 전제) 대신 **JPA 엔티티 기반 커스텀 구현을 새로 작성**한다 — 이 프로젝트는 마이그레이션
    도구 없이 Hibernate `ddl-auto`(update/create-drop)로 스키마를 관리하고, MariaDB/PostgreSQL/MySQL/
    SQL Server/CUBRID/H2 6개 DB를 전부 지원해야 하는데(`AbstractIntegrationTest` 확인) Spring 공식 스키마
    스크립트는 CUBRID를 지원하지 않고 나머지 DB용도 방언별로 손으로 맞춰야 해 이 프로젝트의 기존 관례
    (`ApiToken`처럼 JPA 엔티티 하나로 6개 DB 전부 자동 대응)에 정면으로 어긋난다. Step 2에서 신규
    `domain/oauth2server/` 패키지로 구현.
- **MCP 스펙 버전**: 착수 시점(2026-09-06) 최신 리비전은 2026-07-28(세션리스 Streamable HTTP로 프로토콜이
  크게 바뀜)이지만, 실제로 쓸 수 있는 라이브러리(MCP Java SDK/Spring AI MCP 스타터 2.0.x)는 아직
  2025-11-25 리비전을 구현하고 있고, Claude 등 실제 클라이언트 대부분도 아직 이 리비전 기준이다 — 이
  계획은 **라이브러리가 실제로 구현한 2025-11-25 시맨틱을 기준으로 진행**한다(라이브러리가 지원하지
  않는 최신 스펙을 미리 손으로 구현하지 않음). RFC9728/RFC8707 MUST 요구사항은 두 리비전 사이에 실질적
  변화가 없음을 확인했다.
- **토큰 스코프 축**: [[p3-02-cli-and-rest-api]]의 `ApiTokenScopeGroup`(8개 그룹: ISSUES/PULL_REQUESTS/CODE/
  BOARD/WIKI/WEBHOOKS/ADMINISTRATION/USERS) + `ApiTokenPermission`(NONE/READ/WRITE)을 OAuth 스코프 문자열
  축으로 그대로 재사용한다 — OAuth 스코프 문자열을 `"issues:read"`, `"issues:write"` 형식(그룹명 소문자 +
  `:` + 권한명 소문자)으로 정의해 `ApiTokenScopeGroup`/`ApiTokenPermission`과 1:1 매핑한다(신규 축 설계
  없음, `docs/PARITY_BACKLOG.md`의 P3-14 항목이 이미 언급한 재사용 가능성을 그대로 확정). 단, PAT의
  `scopedProjects`(저장소 단위 세분화)에 대응하는 개념은 OAuth 스코프 문자열 표준에 자연스러운 자리가
  없어 **v1의 OAuth 토큰은 PAT의 `allRepositories=true`와 동일하게 항상 전체 저장소 대상으로 발급**한다
  (저장소 단위로 좁힌 OAuth 동의는 향후 [[p3-14]] 등에서 필요해지면 `resource` 파라미터(RFC8707)를
  프로젝트 단위로 세분화하는 방식으로 확장 가능 — 지금은 과도한 설계이므로 하지 않음). 이 결정은 Step 3~5에서
  스코프 검증 코드를 짤 때 그대로 적용한다.
- **참고 리서치 근거 원문 요약**(정확한 버전 확인 근거, 필요 시 실제 Maven Central 재조회로 최신화할 것):
  - MCP Java SDK 최신 `2.0.1`(MIT), Spring 통합은 2.0+부터 이 SDK가 아니라 Spring AI 쪽으로 이관됨.
  - Spring AI 안정판 `2.0.1`(BOM `org.springframework.ai:spring-ai-bom`), MCP 서버 스타터 아티팩트명이
    `spring-ai-starter-mcp-server-webmvc`로 확정(구 명명 `spring-ai-mcp-server-webmvc-spring-boot-starter`는
    1.0.0-M6에서 멈춘 폐기 경로).
  - Spring Authorization Server가 Spring Security 7.0(2025-09)에 정식 병합, 독립 버전 체계 종료.
  - MCP 프로토콜 최신 리비전 `2026-07-28`(직전 `2025-11-25`), Streamable HTTP 이름은 유지되나
    2026-07-28에서 세션리스로 재설계됨. RFC9728/RFC8707은 두 리비전 다 MUST로 동일.

### 2라운드 (2026-09-06) — Step 2(OAuth 인가 서버 + 리소스 서버 스캐폴딩)

- **필터 체인 3분할**: 기존 `SecurityConfig.securityFilterChain()`(폼로그인/LDAP/OAuth2 소셜로그인/API
  토큰 필터/SAML2/OIDC 전부 처리하던 캐치올 체인, `@Order` 미지정 → 가장 낮은 우선순위로 명시)에 두 개를
  추가했다: `AuthorizationServerConfig.authorizationServerSecurityFilterChain()`(`@Order(1)`,
  `OAuth2AuthorizationServerConfigurer.endpointsMatcher`로 `/oauth2/**` + `/.well-known/oauth-authorization-server`만
  담당)와 `ResourceServerConfig.mcpResourceServerSecurityFilterChain()`(`@Order(2)`, `/mcp/**`만 담당).
  `securityMatcher`를 생략하면(공식 "Getting Started" 예제 방식) 인가 서버 체인이 `anyRequest()`로 매치돼
  나머지 두 체인이 `UnreachableFilterChainException`으로 도달 불가능해짐을 실측으로 확인 — 반드시 명시적
  `securityMatcher`가 필요하다.
- **로그인 화면 재사용**: `/oauth2/authorize`에 비로그인 상태로 접근하면 `LoginUrlAuthenticationEntryPoint("/users/loginform")`로
  리다이렉트 — 새 로그인 화면을 만들지 않았다. 다만 **2026-09-07(4라운드) 회귀 수정**으로 이 엔트리포인트를
  `/oauth2/authorize`/`/oauth2/consent`(사람이 브라우저로 접근하는 두 엔드포인트)에만 좁히고 나머지
  기계-대-기계 엔드포인트(`/oauth2/token` 등)는 Spring 기본 처리(403)로 남겼다 — 상세는 4라운드 참고.
- **RFC7591 DCR**: `clientRegistrationEndpoint { it.openRegistrationAllowed(true) }`로 활성화.
  `authorizeHttpRequests`에 `anyRequest().authenticated()`만 두면 `openRegistrationAllowed(true)`로 열어둔
  익명 등록조차 이 단계에서 401/리다이렉트로 막힘을 실측 확인 — `POST /oauth2/register`만
  명시적으로 `permitAll()`(실제 스팸 방지는 별도 `DcrRateLimitFilter`가 담당).
  **DCR 특성상 중요한 발견**: `OAuth2ClientRegistrationAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR`가
  "scope must not be set during Dynamic Client Registration"으로 등록 요청의 명시적 scope 지정을
  거부한다 — 그래서 이 프로젝트는 **모든 등록 클라이언트에 항상 `McpOAuthScopes.ALL`(전체 스코프
  목록)을 무조건 부여**(`JpaRegisteredClientRepository.toEntity()`)하고, 실제 발급 스코프는 매
  `/oauth2/authorize` 요청 + 동의 화면에서 사용자가 결정하게 한다 — Spring의 기본 설계 의도와 일치.
- **DCR 레이트 리밋**: `DcrRateLimitFilter`(Caffeine 캐시 기반 IP당 분당 10건 고정 윈도)를
  `AuthorizationFilter`보다 먼저 실행되도록 배선(`OAuth2ClientRegistrationEndpointFilter`가 Spring
  Security의 필터 순서 레지스트리에 없어 `addFilterBefore(filter, OAuth2ClientRegistrationEndpointFilter::class.java)`를
  직접 쓸 수 없었다 — 공식 소스로 "이 필터가 항상 `AuthorizationFilter` 뒤에 붙는다"는 사실을 확인해
  `AuthorizationFilter` 앞에 두는 방식으로 우회) — 사용자 지시 4번("DCR이 이 앱에 새로운 위험을
  추가하지 않는지 검토, 필요하면 최소 방어책 추가")에 대한 직접 대응.
- **PKCE 강제**: `JpaRegisteredClientRepository.toEntity()`가 `requireProofKey`/`requireAuthorizationConsent`를
  **전달받은 값과 무관하게 항상 true로 재정의** — 클라이언트가 무엇을 요청하든 PKCE와 동의 화면을
  강제한다(사용자 지시 4번 "PKCE가 실제로 강제되는지" 보안 검증 항목에 대한 코드 레벨 답).
- **RFC8707 Resource Indicators**: `ResourceIndicatorTokenCustomizer`(`OAuth2TokenCustomizer<JwtEncodingContext>`)가
  토큰 발급 시 `resource` 파라미터가 이 MCP 리소스 서버 URI와 정확히 일치하지 않으면 `invalid_target`
  오류로 거부하고, 일치하면 그 값을 액세스 토큰의 `aud` 클레임에 스탬핑한다. 리소스 서버 쪽은
  `AudienceValidator`(`OAuth2TokenValidator<Jwt>`)가 `jwtDecoder()`에 등록돼 `aud`가 자기 자신을 가리키지
  않는 토큰(다른 리소스 서버용으로 발급된 토큰)을 401로 거부 — 사용자 지시 4번의 두 번째 보안 검증
  항목("audience가 이 MCP 리소스 서버가 아닌 토큰은 반드시 거부")에 대한 직접 대응이자 테스트로 검증됨.
- **RFC9728 메타데이터**: `OAuthProtectedResourceMetadataController`가 `/.well-known/oauth-protected-resource`와
  (RFC9728 §3.1이 요구하는) 리소스 경로가 붙은 `/.well-known/oauth-protected-resource/mcp` 둘 다에 동일한
  문서를 제공. `McpAuthenticationEntryPoint`가 인증 실패 시 `WWW-Authenticate: Bearer resource_metadata="..."`
  헤더로 이 위치를 알린다(Spring 기본 `BearerTokenAuthenticationEntryPoint`는 RFC9728 이전 코드라
  `resource_metadata` 파라미터를 지원하지 않아 직접 구현).
- **JWT 서명 키 영속화**: `JwkKeyPairProvider`가 RSA 키 쌍을 최초 1회 생성해 `yona.data`(기존
  업로드/H2 파일과 동일한 관례) 아래 파일로 저장하고 재시작마다 재사용 — 매번 새로 생성하면
  프로세스 재시작 시 그 전에 발급된 모든 토큰이 즉시 무효화되는 실사용 문제를 방지.
- **JPA 기반 저장소**: `OAuthRegisteredClient`/`OAuthAuthorization`/`OAuthAuthorizationConsent`(신규
  `domain/oauth2server/` 패키지) — 공식 `JdbcRegisteredClientRepository`/`JdbcOAuth2AuthorizationService`
  스키마 대신 Hibernate `ddl-auto`로 6개 DB(MariaDB/PostgreSQL/MySQL/SQL Server/CUBRID/H2)를 자동
  지원하는 커스텀 엔티티(1라운드에서 확정한 근거 그대로). attributes/토큰 metadata 직렬화는 Spring
  공식 `SecurityJacksonModules`(Jackson3 기반, `OAuthObjectMapper`)를 그대로 재사용 — 직접 직렬화 코드를
  새로 짜지 않음.
- **GitHub 방식 동의 화면**: `OAuthConsentController` + `templates/oauth2/consent.html` — "GitHub의
  OAuth 동의 화면 문구/레이아웃을 기본값으로" 지시에 따라 "<clientName>이(가) 회원님의 계정에
  접근하려고 합니다" + 요청 권한 체크박스 목록(이미 승인한 스코프는 비활성 체크) + Authorize/Cancel
  버튼 구조로 구현.
- **자동화된 E2E 동등 검증(1차)**: `McpOAuth2SecurityIntegrationSpec`(신규, MockMvc 기반이지만 실제
  시큐리티 필터 체인 전부 태움)가 사용자 지시 3번이 요구한 항목을 이 라운드에서 구현: 토큰 없이 호출 →
  401+`WWW-Authenticate`, 메타데이터 스펙 준수, DCR 등록, PKCE 포함 authorization code flow 전체(동의
  화면 렌더링 포함) → 토큰 발급 → `/mcp` 호출 인증 통과, `resource` 파라미터 누락 시 `invalid_target`,
  PKCE `code_verifier` 누락 시 거부, 다른 audience 토큰 거부. **주의**: 이 라운드에서 GREEN으로
  기록됐던 이 스펙이 사실은 4라운드에서 재확인 전까지 완전히 검증되지 못한 상태였다(McpOAuthScopes
  리팩터링 이후 재실행이 누락됨) — 자세한 경위와 실제로 발견된 버그들은 "완료 로그 — 4라운드" 참고.

### 3라운드 (2026-09-06) — Step 3~5(MCP 도구: 읽기 + 쓰기 이슈/PR)

- **아키텍처**: `IssueRestApiController`/`PullRequestApiController`(P3-02)와 완전히 동일한 원칙 —
  `IssueMcpTools`/`PullRequestMcpTools`(신규 `mcp/` 패키지)는 owner/project 이름으로 프로젝트를 찾아
  기존 `IssueController`/`PullRequestController`/`CommentController`의 공개 메서드에 그대로 위임하는
  얇은 어댑터일 뿐, 신규 비즈니스 로직이 없다.
- **`McpScopeGuard`**: 모든 `@Tool` 메서드가 본문 첫 줄에서 호출하는 유일한 스코프 검증 지점(사용자
  지시 4번 "토큰 스코프가 실제로 각 도구 호출 전에 검증되는지"에 대한 설계 답). OAuth JWT(스코프
  클레임 검사, write는 read를 함의)와 P3-02 Fine-grained PAT(기존 `ApiTokenAuthorizer` 재사용, 저장소
  스코프까지 검증) 둘 다 처리 — 스코프 판정 로직을 새로 만들지 않고 P3-02 것을 그대로 위임한다.
- **`/mcp` 요청의 PAT 인증**: `ApiTokenAuthenticationFilter`가 `/mcp` 패턴을 새로 인식해 (스코프
  판정 없이) 신원만 확인하고 인증에 쓰인 `ApiToken`을 request attribute로 넘긴다 — 기존
  `/api/v1/projects/{owner}` 목록 경로와 동일한 이유(한 HTTP 요청 안에 어떤 도구가 들어있는지 URL만
  보고는 알 수 없어 필터가 코스 스코프 판정을 할 수 없음)로 `authenticateScopedList()`를 그대로 재사용.
- **도구 목록**: 읽기(`list_issues`/`get_issue`/`list_pull_requests`/`get_pull_request`), 쓰기 이슈
  (`create_issue`/`comment_issue`/`close_issue`), 쓰기 PR(`create_pull_request`/`review_pull_request`/
  `comment_pull_request`/`merge_pull_request`) 11개 전부 구현.
- **`review_pull_request`의 의미 제약**: yona에는 GitHub의 APPROVE/REQUEST_CHANGES 같은 정식 리뷰
  판정 상태 기계가 없다(`PullRequestController`에 그런 API 자체가 없음, 코드로 확인 — 이건 P3-15로
  별도 백로그에 이미 등록돼 있는 신규 기능 갭). 그래서 이 도구는 실제로 존재하는 유일한 "리뷰" 동작인
  리뷰어 등록(`addReviewer`)에 매핑했다 — 신규 상태 기계를 만들지 않음(과도한 설계 금지).
- **`merge_pull_request`의 안전장치(계획 문서 리스크 표 1순위)**: 새 머지 경로를 만들지 않고
  `PullRequestController.mergePullRequest()`를 그대로 호출 — 이 메서드가 이미
  `PullRequestServiceImpl.merge() -> checkBranchProtectionForMerge()`([[p3-04-branch-protection]])를
  경유하므로 우회가 불가능하다. `scopeGuard.require(..., PULL_REQUESTS, WRITE, project)`가 이 호출보다
  반드시 먼저 실행되고, 거부되면 컨트롤러가 **절대 호출되지 않음**을 `PullRequestMcpToolsSpec`이
  `verify(exactly = 0)`로 직접 증명한다.
- **`create_pull_request`의 v1 제약**: `fromProjectId`를 항상 대상 프로젝트 자신으로 고정 — 포크 간
  PR 생성은 이 도구의 범위 밖(과도한 설계 금지, 필요해지면 파라미터 추가로 확장 가능).
- **`McpToolsConfig`**: `MethodToolCallbackProvider.builder().toolObjects(issueMcpTools, pullRequestMcpTools).build()`
  하나만 등록하면 `spring-ai-starter-mcp-server-webmvc` 자동설정이 나머지(JSON-RPC 메시지, 세션 관리,
  도구 스키마 생성)를 전부 처리 — MCP 프로토콜을 직접 구현하지 않는다.
- 테스트: `McpScopeGuardSpec`(JWT/PAT 양쪽의 허용/거부 전체 조합), `IssueMcpToolsSpec`/
  `PullRequestMcpToolsSpec`(mockk 기반, 스코프 거부 시 하부 컨트롤러가 호출되지 않음을 매 쓰기 도구마다
  `verify(exactly = 0)`로 증명).

### 4라운드 (2026-09-06~07) — Step 6(실제 MCP 클라이언트 E2E 검증) + 발견된 실버그 6건 수정

- **배경**: 2~3라운드까지는 MockMvc(DispatcherServlet 내부만 태움)로만 검증했다. 사용자 지시 3번("사람이
  브라우저로 직접 눌러보는 것과 실질적으로 동등한 신뢰도")을 문자 그대로 만족시키려면 **실제 소켓을 통한
  실제 MCP 프로토콜 클라이언트** 검증이 필요하다고 판단해 `McpToolsEndToEndSpec`(신규)을 추가했다 —
  `@SpringBootTest(webEnvironment = RANDOM_PORT)`로 실제 임베디드 톰캣을 띄우고, Claude Code가 실제로
  쓰는 것과 동일한 공식 MCP Java SDK(`io.modelcontextprotocol.sdk:mcp`)의
  `HttpClientStreamableHttpTransport`+`McpClient.sync()`로 진짜 HTTP 소켓을 통해 `initialize` →
  `tools/list` → `tools/call`까지 전부 실제 프로토콜로 태운다(OAuth 토큰 발급 자체는 2라운드 스펙과
  동일한 MockMvc 기반 DCR+PKCE+동의 플로우를 재사용해 얻는다 — 라이브러리를 새로 재구현하지 않고
  같은 엔드포인트를 다시 태우는 것뿐이라 중복 구현이 아니다).
- **이 스펙이 즉시 찾아낸 실버그 6건**(전부 MockMvc 전용 테스트로는 발견 불가능했던 유형 — "왜 MockMvc로는
  충분하지 않은가"의 실제 사례):
  1. **Lob 컬럼 길이 함정**: `@Lob`만 붙이고 `@Column(length=...)`을 명시하지 않으면 JPA 기본값 255가
     그대로 적용돼, Hibernate가 MariaDB/MySQL 방언에서 이 길이를 기준으로 TINYTEXT를 고른다.
     `OAuthAuthorization.attributes`(OAuth2AuthorizationRequest 전체를 JSON으로 담음)가 255바이트를
     쉽게 넘겨 `DataIntegrityViolationException: Data too long for column 'attributes'`로 실패 —
     `OAuthAuthorization`/`OAuthRegisteredClient`/`OAuthAuthorizationConsent`의 모든 `@Lob` 컬럼에
     `length = 1_000_000`을 명시(6개 DB 전부에 이식 가능한 JPA 표준 속성, 방언별 `columnDefinition`이
     아님)해 MEDIUMTEXT/LONGTEXT급으로 고정. 기존 MockMvc 전용 스펙은 스코프 2개짜리 짧은 값만 써서
     우연히 한계 밑에 머물러 있었을 뿐이었다.
  2. **Jackson 폴리모픽 타입 화이트리스트 누락**: Spring이 동의 처리 중 인증된 Principal(이 프로젝트의
     `YonaUserDetails`)을 `OAuth2Authorization.attributes`에 그대로 담아 직렬화하는데, `OAuthObjectMapper`가
     쓰는 `SecurityJacksonModules`의 기본 `PolymorphicTypeValidator`는 Spring 자신의 `User` 클래스만
     신뢰해 `YonaUserDetails`를 역직렬화 시점에 `InvalidTypeIdException`으로 거부했다 — Spring이 공식
     제공하는 확장 지점(`SecurityJacksonModules.getModules(ClassLoader, BasicPolymorphicTypeValidator.Builder)`,
     `.allowIfSubType(YonaUserDetails::class.java)`)으로 화이트리스트에 추가(검증 메커니즘 자체를
     우회/비활성화하지 않음).
  3. **`YonaUserDetails` 왕복 직렬화 불일치**: 생성자 파라미터명(`passwordVal`/`authoritiesVal`)이
     Jackson이 getter에서 유도하는 프로퍼티명(`password`/`authorities`, `UserDetails` 인터페이스 메서드
     명명 규칙)과 달라 `ValueInstantiationException: passwordVal이 null`로 역직렬화 실패 —
     `@JsonCreator`/`@JsonProperty`로 생성자 파라미터와 프로퍼티명을 명시적으로 맞추고, `state`(원래
     `isEnabled()`/`isAccountNonLocked()`로만 단방향 파생되던 private 필드라 원래 `UserState`를 복원할
     방법이 없었음)를 `getState()`가 자동 노출되는 public 프로퍼티로 바꿔 왕복 가능하게 했다.
  4. **인가 서버 예외 처리 범위 오류**: `AuthorizationServerConfig`가 `/oauth2/**` 전체(사람이 보는
     `/oauth2/authorize`뿐 아니라 기계가 호출하는 `/oauth2/token`/`/oauth2/register`까지)에
     `LoginUrlAuthenticationEntryPoint`를 무조건 적용해뒀던 것(2라운드)이, PKCE `code_verifier`를
     생략한 공개 클라이언트의 `/oauth2/token` 요청처럼 클라이언트 인증 자체가 실패하는 경우 표준
     OAuth2 JSON 오류(400) 대신 302로 HTML 로그인 페이지를 반환해버려 어떤 실제 MCP 클라이언트도
     이 오류를 해석할 수 없게 만드는 문제였다 — `DelegatingAuthenticationEntryPoint`로 로그인 리다이렉트를
     `/oauth2/authorize`/`/oauth2/consent`(실제로 브라우저가 접근하는 두 엔드포인트)에만 좁히고, 나머지는
     `Http403ForbiddenEntryPoint`(Spring 기본 처리)로 남겼다.
  5. **JWT `scope` 클레임 표현 불일치**: `McpScopeGuard`가 `Jwt.getClaimAsString("scope")`로 스코프를
     읽었는데, Spring Authorization Server가 기본으로 발급하는 액세스 토큰의 `scope` 클레임은 공백
     구분 문자열이 아니라 **JSON 배열**이었다(실측: `{"scope":["issues:read","issues:write"]}`) —
     `getClaimAsString()`은 타입이 안 맞는 배열 클레임에 조용히 null을 반환해, OAuth 토큰으로 호출하는
     모든 도구가 "스코프가 하나도 없는 것"으로 취급돼 항상 거부되는 회귀였다. 이건 정확히 "MockMvc
     테스트가 왜 못 잡는가"의 교과서적 사례다 — 기존 스펙은 토큰 엔드포인트의 JSON **응답**(`scope`
     필드, 공백 구분 문자열이 표준)만 확인했지 실제로 발급된 JWT를 디코딩해 내부 클레임 표현을
     확인한 적이 없었다. `getClaimAsStringList()`(문자열/배열 두 표현 모두 처리)로 교체.
  6. **테스트 작성 버그(제품 버그 아님, 기록 목적)**: (a) `/oauth2/authorize`가 동의 화면으로 리다이렉트할
     때 붙이는 `state` 쿼리 파라미터는 원래 인가 요청의 state가 아니라 Spring이 동의 단계 CSRF 방지용으로
     새로 발급하는 별도의 불투명한 값이다 — 원래 state를 그대로 재사용해 동의 POST를 보내면 매번
     "invalid_request: state"로 거부됨을 실측 확인, 리다이렉트 URL에서 이 값을 꺼내 쓰도록 테스트를
     고쳤다. (b) 로컬에서 반복 실행 중 `state` 문자열 리터럴을 고정해 쓰면(`OAuthAuthorizationRepository.findByState()`가
     client_id로 좁히지 않고 state 하나만으로 조회) 이전(특히 비정상 종료된) 실행이 남긴 행과 충돌해
     무작위로 실패할 수 있어, `McpOAuth2SecurityIntegrationSpec`/`McpToolsEndToEndSpec` 양쪽 다 모든
     state 값을 UUID로 유일하게 만들었다.
- **재확인**: `config/oauth2server`+`domain/oauth2server`+`mcp` 패키지 전체(8개 스펙 클래스, 43건)를 두 번
  반복 실행해 플레이키니스가 아님을 확인, 전부 GREEN.

### 5라운드 (2026-09-07) — "Authorized OAuth Apps" UI + 전체 스위트 재확인 + 문서 마무리

- **"Authorized OAuth Apps" 화면**(사용자 지시 1번 "필요한 UI는 반드시 구현한다" — 백엔드만 만들고 실제로
  쓸 방법이 없는 상태로 남기지 말 것): GitHub의 "Settings > Applications > Authorized OAuth Apps"에
  대응하는 화면을 신규 구현. 기존 P3-02 Fine-grained API 토큰 관리 화면(`user/edit_tokens.html`,
  `/user/editform/tokens`)과 완전히 동일한 컨벤션(같은 탭 메뉴 `user/partial_edit_tabmenu.html`에 새
  탭 추가, `/user/editform/oauth-apps` URL 프리픽스, `ybtn`/`label-head` 등 동일 CSS 클래스, 이벤트
  위임 방식의 JS 확인 다이얼로그)을 그대로 따랐다.
  - `OAuthAuthorizedAppsService`(신규): 사용자의 동의(`OAuthAuthorizationConsent`) 레코드를 등록된
    클라이언트(`OAuthRegisteredClient`) 정보와 조합해 화면용 뷰로 변환. `revoke()`는 **동의 레코드뿐
    아니라 이미 발급된 토큰 레코드(`OAuthAuthorization`)까지 함께 삭제**해야 GitHub의 "Revoke access"처럼
    즉시 접근이 차단된다는 점을 발견하고 반영(동의만 지우면 다음 로그인 때 동의 화면만 다시 뜰 뿐 이미
    발급된 토큰은 만료 전까지 계속 유효했을 것) — `OAuthAuthorizationRepository`에
    `deleteByRegisteredClientIdAndPrincipalName()` 신규 추가.
  - `UserViewController`에 `editOAuthAuthorizedAppsForm`(GET `/user/editform/oauth-apps`)/
    `revokeOAuthAuthorizedApp`(POST `/user/editform/oauth-apps/{clientId}/revoke`) 두 엔드포인트 추가 —
    기존 `editApiTokensForm`/`revokeApiToken`과 정확히 같은 패턴(미인증 시 `authentication == null` 체크로
    `error/403` 뷰 반환 — 이 컨트롤러의 다른 계정 설정 화면과 동일하게 `SecurityConfig`가 이 경로를
    별도로 보호하지 않고 각 메서드가 직접 인증을 확인하는 기존 관례를 그대로 따름, HTTP 302 리다이렉트가
    아니라 200으로 렌더링됨에 주의).
  - 사전등록 클라이언트 관리자 화면은 만들지 않았다 — RFC7591 DCR로 클라이언트 자동 등록이 이미 API로
    되므로 필수가 아니라고 판단(계획 문서가 이미 이 판단을 사용자 재량으로 남겨뒀던 부분).
  - 테스트: `OAuthAuthorizedAppsServiceSpec`(mockk, 조회/취소 로직), `UserViewControllerSpec`에 4개
    케이스 추가(mockk 기반, 뷰 이름/모델 속성만 검증 — 기존 파일의 기존 패턴과 동일), 신규
    `OAuthAuthorizedAppsControllerIntegrationSpec`(실제 Spring 컨텍스트+실제 Thymeleaf 렌더링+실제
    DB 부수효과까지 검증 — mockk 단위테스트는 템플릿 문법 오류를 잡지 못하므로 이 스펙이 그 갭을 메움).
- **전체 스위트 재확인**: `./gradlew test`(mariadb 프로파일) 전체 실행, 이 계획이 만든 코드/테스트는
  전부 GREEN. 무관한 클래스의 결과는 사용자 지시 4번에 따라 교차검증(단독 재실행)했다 — 상세 결과는
  이 라운드 커밋 메시지 및 `docs/COVERAGE_BACKLOG.md`(있다면)에 남긴 대로.
- **커밋**: Step별로 4개 커밋(Step1-2, Step3-5, Step6 실버그 수정, 5라운드 UI+문서)으로 분리 — 각각
  `git log`에서 `feat(p3-07)`/`fix(p3-07)` 접두어로 조회 가능.

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-07)
- 관련 계획: [[p3-02-cli-and-rest-api]](강한 선행 의존성), [[p3-04-branch-protection]](머지 정책 연동 검토),
  [[p3-06-enterprise-sso]](블로커 아님 — 인가 서버가 로그인 위임하는 대상이 확장될 뿐, 레이어가 다름)
- 비교 참고: v2 브랜치의 읽기 전용 MCP 서버(`app/mcp/*`, `docs/technical/mcp-server.md` — 원 저작자 저장소)
- 외부 레퍼런스: [MCP Authorization 스펙](https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization),
  [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security),
  [Securing Spring AI MCP servers with OAuth2](https://spring.io/blog/2025/04/02/mcp-server-oauth2/)
