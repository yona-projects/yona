---
title: MCP 서버(X-6) 구현 계획
type: plan
updated: 2026-11-02
---

# MCP 서버(X-6) 구현 계획

> **상태: 계획만 있다. 선생님이 착수를 지시하기 전에는 구현하지 않는다.** 결정은 [decisions.md](../decisions.md) D-72에서 2026-11-02에 확정되었다(PAT는 받지 않고 OAuth 토큰만, 새 읽기 전용 범위 `read:repo`를 더한다, 첫 도구는 읽기와 안전한 쓰기). 10번(git 읽기 허용)만 답변을 기다린다.

## 1. 목적

AI 에이전트(Claude Code, Codex, Antigravity 등)가 MCP(Model Context Protocol)로 Yona의 이슈, 풀 리퀘스트, 위키를 조회하고 일부는 쓰게 한다. 인증은 X-1의 OAuth 인가 서버가 맡는다. 이 항목이 끝나야 X-1의 CIMD 자동 등록, 로컬 콜백, `WWW-Authenticate` 흐름을 **실제 클라이언트로 끝까지 확인**할 수 있다(지금은 외부 Node 클라이언트까지만 확인했다).

## 2. 전송: Streamable HTTP만

MCP 규격(2025-06-18)의 표준 전송은 stdio와 Streamable HTTP 둘이다. 옛 HTTP+SSE는 폐기되었고 새 클라이언트(Antigravity 공식 문서는 "옛 HTTP+SSE는 지원하지 않는다"고 적었다)는 Streamable HTTP를 쓴다. stdio는 사용자의 컴퓨터에서 프로그램을 직접 실행하는 방식이라 원격 서버인 Yona에는 맞지 않는다. 그래서 **Streamable HTTP만** 구현한다(선생님 확인 2026-11-02).

- 엔드포인트 하나(`/mcp`)가 `POST`와 `GET`을 받는다. 클라이언트의 모든 메시지는 새 `POST`이다.
- 서버는 요청마다 응답을 일반 JSON(`application/json`) 한 번으로 줄 수도 있고 SSE 스트림(`text/event-stream`)으로 줄 수도 있다. **첫 구현은 일반 JSON 응답과 세션 없음(stateless)으로 시작한다.** 서버가 먼저 말하는 기능(진행 알림, 취소 등)이 필요해지면 SSE 응답을 더한다. `GET`은 SSE를 제공하지 않으면 405를 돌려준다.
- `Origin` 헤더를 검증한다(규격이 필수로 요구한다, DNS 재바인딩 방지). `Origin`이 없는 요청(비브라우저 클라이언트)은 받는다. `MCP-Protocol-Version` 헤더를 확인한다(없으면 2025-03-26으로 본다, 모르는 값은 400).

## 3. 구현 방식

`next`는 **Spring AI 2.0.1의 `spring-ai-starter-mcp-server-webmvc`**(Streamable HTTP 전송과 `@Tool` 등록)를 쓰고, 이 저장소와 같은 Spring Boot 4.1.1이다. 프로토콜을 직접 만들지 않고 이 라이브러리를 쓰는 것을 권장한다(D-72 1번). Spring AI가 Boot 4.1.1, Jackson, 우리 보안 체인과 같이 도는지는 **단계 0 스파이크에서 확인**한다. 라이브러리가 stateless 모드를 제공하는지도 함께 확인한다.

## 4. 인증과 권한

- **보안 체인**: `/mcp`와 `/mcp/**`만 별도 체인이 맡는다(X-1의 `AuthorizationServerConfig`와 같은 방식). 로그인 세션과 쿠키는 보지 않고 **Bearer 토큰만** 받는다. 토큰이 없거나 틀리면 401과 `WWW-Authenticate: Bearer resource_metadata="<origin>/.well-known/oauth-protected-resource/mcp"`를 준다(RFC 9728).
- **받는 토큰**: **OAuth 액세스 토큰(`yona_oat_`)만** 받는다. 개인용 토큰(`yona_pat_`)은 받지 않는다(D-72 7번, 선생님 결정). 브라우저 인증을 못 하는 환경(`claude -p` 등)은 지원하지 않는다.
- **대상 검증(RFC 8707)**: MCP 규격은 서버가 "자기 앞으로 발급된 토큰인지" 확인하라고 한다. 승인 요청의 `resource` 값(`<origin>/mcp` 또는 `<origin>/-_-api/v1`)을 인가에 기록하고, **`resource` 없이 발급된 토큰은 외부 API에서만 쓸 수 있고 `/mcp`는 `resource`가 `/mcp`인 토큰만 받는다**(D-72 6번). 다른 자원용 토큰을 그대로 다른 API에 넘기지 않는다.
- **범위**: 도구마다 필요한 범위를 검사한다. **읽기 도구는 `read:repo`**(또는 `repo`, 공개 프로젝트는 `public_repo`)이고 **쓰기 도구는 `repo`**이다. `read:repo`는 이번에 더하는 읽기 전용 범위이다(D-72 5번): 지금은 프로젝트 내용 범위가 `repo`와 `public_repo`뿐이고 둘 다 읽기와 쓰기를 함께 주므로, 읽기만 하려는 에이전트에 쓰기 권한까지 주지 않으려는 것이다. `repo`는 `read:repo`를 포함한다. 새 범위는 개인용 토큰과 OAuth 앱 모두에서 고를 수 있고 외부 API의 읽기 요청도 이 범위로 통과한다. 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의 교집합이다(기존과 같다). 도구는 기존 REST 서비스를 부르므로 권한 검사가 웹과 REST와 같은 규칙을 따른다.

## 5. 도구(첫 구현의 권장안, D-72 4번)

| 영역 | 읽기 | 쓰기 |
|---|---|---|
| 프로젝트 | `list_projects`, `get_project` | 없음 |
| 이슈 | `list_issues`, `get_issue`, `list_issue_comments` | `create_issue`, `comment_issue`, `close_issue`, `reopen_issue` |
| 풀 리퀘스트 | `list_pull_requests`, `get_pull_request`, `get_pull_request_diff` | `comment_pull_request` |
| 위키 | `list_wiki_pages`, `get_wiki_page` | `create_wiki_page`, `update_wiki_page` |
| 검색 | `search_issues` | 없음 |

**첫 구현에 넣지 않는 것**: 풀 리퀘스트 만들기, 리뷰 판정(승인, 변경 요청), **병합**, 삭제 도구, 프로젝트 설정 변경. 병합은 브랜치 보호 규칙과 맞물리고 되돌리기 어려워 에이전트에 처음부터 맡기지 않는다. **나머지 쓰기 도구는 추가 로드맵 X-41로 둔다**(선생님 결정).

공통 규칙: 목록 도구는 쪽 크기를 제한하고(기본 30, 최대 100) 본문은 길이를 제한한다. 오류는 MCP 도구 오류(`isError`)로 돌려주고 내부 예외 내용은 노출하지 않는다. 쓰기 도구는 응답에 만든 것의 주소를 담는다. 도구 설명에는 한국어와 영어를 함께 쓴다.

## 6. 감사, 지표, 안전

- 감사: 쓰기 도구의 호출마다 `mcp.tool_called`(도구 이름, 대상 프로젝트, 결과, 토큰 종류와 앱)를 남긴다. 읽기 도구는 감사 기록 대신 지표로 센다(양이 많다).
- 지표: `yona.mcp.tool_calls{tool, result}`.
- 한도: 기존 토큰 실패 제한을 따르고, 도구 호출 속도 제한은 첫 구현에서는 두지 않는다(필요해지면 추가).
- 관리자 설정: "MCP 서버 사용"(끄면 `/mcp`가 404), "MCP 쓰기 도구 허용"(끄면 읽기 도구만 보인다)을 "보안" 화면에 더한다. 둘 다 기본 켬.

## 7. 단계 (모두 TDD, 단계마다 위키와 커밋)

| 단계 | 내용 | 확인 |
|---|---|---|
| 0 | **스파이크**: Spring AI 2.0.1(BOM)을 Boot 4.1.1에 더하고, `/mcp` Streamable HTTP가 뜨는지, stateless 모드, 우리 보안 체인과의 공존, `@Tool` 등록, Jackson 충돌 여부 | 작은 시험 |
| 0b | **읽기 범위 `read:repo`**: `ApiScopes`(포함 관계), `ApiScopeRules`의 읽기 요청 규칙, PAT 화면과 승인 화면의 설명(5개 언어), 구조 시험(`ApiScopeRules` 표). 기존 토큰의 동작은 바뀌지 않는다 | 범위 규칙 시험, 외부 API 읽기와 쓰기 시험 |
| 1 | 보안 체인과 인증 다리(OAuth 토큰, 401 안내, 보호 자원 메타데이터 `/.well-known/oauth-protected-resource/mcp`), `Origin`과 `MCP-Protocol-Version` 검증, 범위 검사기, 오류 변환, 관리자 설정 | MockMvc 시험 |
| 2 | 읽기 도구(프로젝트, 이슈, 풀 리퀘스트, 위키, 검색)와 쪽 크기와 길이 제한 | 도구 시험, 권한 시험(남의 비공개 프로젝트는 없는 것처럼) |
| 3 | 쓰기 도구(이슈 만들기, 댓글, 닫기와 다시 열기, 풀 리퀘스트 댓글, 위키 만들기와 수정)와 감사 기록, 지표 | 도구 시험, 범위 부족 거부, 감사 시험 |
| 4 | 대상 검증(RFC 8707): 승인 요청의 `resource` 기록, 토큰의 대상 확인 | X-1 흐름 시험 확장 |
| 5 | 문서(`architecture/mcp-server.md`, 구성 키, 감사, 차이 장부), 구조 시험(도구마다 범위 검사가 있는지, 감사 이벤트 문서화) | 전체 시험 |
| 6 | **실제 클라이언트 확인**: 공식 MCP Java SDK 클라이언트로 인가부터 도구 호출까지 자동 시험, 외부 Node 클라이언트, 그리고 가능하면 실제 Claude Code와 Codex로 `claude mcp add`, `codex mcp login` 흐름 확인(CIMD 자동 등록, 로컬 콜백) | 캡처와 로그 |

## 8. 위험과 확인할 것

- **PAT를 받지 않으므로** 브라우저 인증이 안 되는 환경은 쓸 수 없다. 사용자가 PAT로 MCP를 쓰려는 요청이 오면 D-72 7번을 다시 연다.
- Spring AI 2.0.1의 Boot 4.1.1 호환과 stateless 모드는 단계 0에서 확인한다(`next`는 이 조합으로 동작하지만 우리 보안 구성은 다르다).
- `/mcp`가 v1.6의 `/{사용자}` 주소와 겹친다. 로그인 ID가 `mcp`인 사용자의 프로필은 `/mcp`가 보호 자원 체인으로 가려진다. v1.6 예약어는 바꾸지 않고 차이 장부에 한계로 적는다(X-1의 `oauth2`와 같다).
- 도구가 기존 REST 서비스를 부를 때 웹 세션 문맥에 기대는 코드가 있으면 토큰 문맥에서 깨질 수 있다. 단계 2에서 도구마다 시험으로 확인한다.
- 실제 Claude Code, Codex는 이 환경에서 직접 돌려 보지 못할 수 있다. 그 경우 확인 절차를 문서로 남기고 선생님이 직접 확인하시게 한다.
