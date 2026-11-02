---
title: MCP 서버
type: architecture
updated: 2026-11-02
---

# MCP 서버 (X-6, D-72)

AI 에이전트(Claude Code, Codex, Antigravity 등)가 MCP(Model Context Protocol)로 Yona의 프로젝트, 이슈, 풀 리퀘스트, 위키를 읽고 일부는 쓰는 서버이다. 인증은 X-1의 OAuth 인가 서버가 맡는다. 계획과 결정 근거는 [mcp-server-plan.md](mcp-server-plan.md)와 [decisions.md](../decisions.md) D-72에 있다. v1.6에 없는 추가 항목이며 v1.6의 화면과 계약은 바뀌지 않는다.

## 1. 전송

**Streamable HTTP만** 구현한다(MCP 규격 2025-06-18). 옛 HTTP+SSE는 폐기되었고 stdio는 원격 서버에 맞지 않는다. 주소는 `/mcp` 하나이다.

- 구현은 Spring AI 2.0.1의 `spring-ai-starter-mcp-server-webmvc`가 맡는다(프로토콜, 초기화, 도구 목록과 호출, 오류 형식). `application.yml`의 `spring.ai.mcp.server.protocol: STATELESS`이다.
- **세션 없이 일반 JSON으로 답한다.** `POST`만 받고 `GET`은 405이다(서버가 먼저 말하는 SSE 스트림은 제공하지 않는다). 서버가 먼저 말해야 하는 기능이 필요해지면 SSE 응답을 더한다.

## 2. 접근 규칙 (`McpAccessFilter`)

`/mcp`와 그 아래는 세션과 쿠키를 보지 않고 **OAuth 액세스 토큰(`yona_oat_`)만** 받는다. 개인용 토큰(PAT)은 받지 않는다(D-72 7번). 순서는 다음과 같다.

| 순서 | 검사 | 실패 |
|---|---|---|
| 1 | 관리자가 MCP를 껐는지(`security.mcp-enabled`) | 404 |
| 2 | `Origin` 머리글이 있으면 이 서버의 출처와 같아야 한다(DNS 재바인딩 방지, 없으면 통과) | 403 |
| 3 | `MCP-Protocol-Version`이 있으면 `2025-03-26`, `2025-06-18`, `2025-11-25` 중 하나 | 400 |
| 4 | 같은 주소에서 토큰 인증이 연달아 실패했는지(외부 API와 같은 제한) | 429 |
| 5 | 토큰이 맞는지 | 401 + `WWW-Authenticate: Bearer resource_metadata="<출처>/.well-known/oauth-protected-resource/mcp"` |
| 6 | 토큰이 이 서버(`<출처>/mcp`)를 가리켜 발급되었는지(RFC 8707) | 401 + `error="invalid_token"` |
| 7 | 읽기 범위(`read:repo`, `repo`가 포함)가 있는지 | 403 + `error="insufficient_scope", scope="read:repo"` |

보호 자원 메타데이터(RFC 9728)는 `/.well-known/oauth-protected-resource/mcp`이고 `resource`는 `<출처>/mcp`이다. 클라이언트는 401의 이 주소로 인가 서버를 찾고 X-1의 승인 흐름(PKCE, CIMD)으로 토큰을 받는다.

## 3. 토큰의 대상 (RFC 8707)

토큰이 엉뚱한 곳에 쓰이지 않도록 승인 요청의 `resource`를 토큰에 묶는다.

- `resource`는 이 서버의 보호 자원인 `<출처>/mcp`나 `<출처>/-_-api/v1`이어야 한다. 아니면(조각 `#`이 있는 주소 포함) 앱의 리디렉션 주소로 `error=invalid_target`을 돌려보낸다. 여러 개를 함께 요청할 수 있다.
- 반복한 `resource` 매개변수는 `ResourceJoiningConverter`가 공백으로 이은 한 값으로 바꾼다. 문자열 배열은 인가 저장소의 직렬화가 거부하기 때문이다. 값은 승인 요청의 추가 매개변수로 `attributes`에 저장되고 `OAuthAccessTokenService`가 읽어 `OAuthGrant.resources`로 돌려준다.
- **`resource` 없이 발급된 토큰은 외부 API에서만 쓸 수 있고 `/mcp`는 거부한다.** `/mcp`를 가리킨 토큰은 외부 API(`/-_-api/v1`)에서 거부한다(401).

## 4. 도구

도구는 기존 외부 REST API를 **그대로 부른다**(`McpRestBridge`). 컨트롤러 메서드를 직접 부르면 인터셉터가 맡는 접근 검사(`@AnonymousCheck`, `@IsCreatable` 등)와 범위 검사(`ApiScopeInterceptor`)를 건너뛰므로, 서블릿 디스패처에 가상 요청(`VirtualRequest`)을 넣어 인터셉터, 예외 처리, 메시지 변환을 REST와 똑같이 거친다. 필터 체인은 다시 지나지 않는다(인증은 접근 필터가 이미 채웠다). 그래서 **도구의 권한과 범위는 REST와 항상 같다**: 읽기는 `read:repo`나 `repo`, 쓰기는 `repo`, 프로젝트 접근은 사용자의 실제 권한.

| 영역 | 읽기 도구 | 쓰기 도구 |
|---|---|---|
| 프로젝트 | `list_projects`, `get_project` | 없음 |
| 이슈 | `list_issues`, `get_issue`, `list_issue_comments` | `create_issue`, `comment_issue`, `close_issue`, `reopen_issue` |
| 풀 리퀘스트 | `list_pull_requests`, `get_pull_request`, `get_pull_request_diff` | `comment_pull_request` |
| 위키 | `list_wiki_pages`, `get_wiki_page` | `create_wiki_page`, `update_wiki_page` |
| 검색 | `search_project` | 없음 |

풀 리퀘스트 만들기, 리뷰 판정, 병합, 삭제 도구는 없다(로드맵 X-41).

- **쪽 크기**: 목록은 기본 30, 최대 100이다(`McpRestBridge.pageSize`). 결과 글이 20만 자를 넘으면 앞부분만 보내고 `truncated: true`로 알린다.
- **오류**: 2xx가 아니면 MCP 도구 오류(`isError`)로 상태와 이유를 알린다. 예외 내용은 노출하지 않는다.
- **쓰기 스위치**: 관리자가 "MCP 쓰기 도구 허용"(`security.mcp-write-enabled`)을 끄면 쓰기 도구는 호출할 때 거부된다. 계획은 "도구 목록에서 숨긴다"였으나 스타터가 시작할 때 목록을 한 번 정하므로 **호출 거부**로 구현했다(A-330).
- 위키 페이지 이름에는 `/`를 쓸 수 없다(Yona의 규칙). 하위 경로는 없다.

## 5. 감사와 지표

- 쓰기 도구는 호출마다 감사 기록 `mcp.tool_called`(성공과 실패 모두, `details.tool`, `targetLabel`은 `소유자/프로젝트`)를 남긴다. 읽기 도구는 감사 기록 대신 지표로만 센다. REST를 부르므로 실제 변경의 감사 기록도 REST와 같이 남는다.
- 지표 `yona.mcp.tool_calls{tool, result}`(`ok`, `error`).
- 관리자 설정은 사이트 관리의 "보안" 화면의 "MCP 서버" 영역이다.

## 6. 알려진 한계

- **개인용 토큰으로는 쓸 수 없다.** 브라우저 인증을 못 하는 환경(`claude -p` 등)은 지원하지 않는다(D-72 7번).
- 서버가 먼저 보내는 알림과 요청 취소(SSE)는 없다.
- 도구 호출 속도 제한은 없다. 실패한 토큰 인증의 제한만 있다.
- `/mcp`가 v1.6의 `/{사용자}` 주소와 겹친다. 로그인 ID가 `mcp`인 사용자의 프로필 주소가 보호 자원 체인에 가려진다(X-1의 `oauth2`와 같은 한계, v1.6 예약어는 바꾸지 않는다).
- 공식 MCP SDK(TypeScript 1.32.2) 클라이언트로 실제 jar에 붙어 확인했다: 401의 `resource_metadata` 발견, 인가 서버 찾기, PKCE와 `resource` 승인, 토큰 교환, 도구 목록(18개)과 호출, MCP 토큰이 외부 API에서 401, PAT가 `/mcp`에서 401. 실제 Claude Code, Codex, Antigravity 연결은 이 환경에서 직접 확인하지 못했다. 확인 절차는 아래 7절에 있다.
- 승인 요청의 범위가 앱에 허용된 범위를 넘으면(공식 SDK는 401에 `scope`가 없으면 보호 자원 메타데이터의 `scopes_supported` 전부를 요청한다) 앱의 리디렉션 주소로 `invalid_scope` 오류가 돌아온다. 앱을 만들 때 필요한 범위를 모두 허용해야 한다.

## 7. 클라이언트 연결 확인 절차

운영 주소가 `https://yona.example.com`이라고 할 때 이다.

- **Claude Code**: `claude mcp add --transport http yona https://yona.example.com/mcp`를 실행하고 Claude Code 안에서 `/mcp`로 `yona`의 인증을 시작한다. 브라우저가 열려 Yona의 승인 화면이 나온다. CIMD 자동 등록이 켜져 있으면(관리자 설정, 기본 켬) 별도 앱 등록이 필요 없다. 승인 화면의 범위가 `read:repo`면 읽기만, `repo`면 쓰기까지 허용한다.
- **Codex**: `~/.codex/config.toml`의 `[mcp_servers.yona]`에 `url = "https://yona.example.com/mcp"`를 쓰고 `codex mcp login yona`를 실행한다.
- **Antigravity**: MCP 서버 설정에 같은 주소를 넣는다. 자동 등록(DCR)을 쓰므로 Yona에서는 OAuth 앱을 수동으로 만들고(설정의 "OAuth 앱") 안내된 클라이언트 ID와 콜백 `https://antigravity.google/oauth-callback`을 넣는다(D-71).
