# Yona MCP Usage Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자와 사용자가 Yona MCP 서버를 활성화하고, 토큰을 발급해 클라이언트를 연결하고, 읽기 전용 기능을 점검할 수 있는 간결한 한국어 통합 가이드를 제공한다.

**Architecture:** `docs/ko/yona-mcp-guide.md`를 사용 순서 중심의 단일 진입점으로 만든다. 기존 `docs/technical/mcp-server.md`는 짧은 프로토콜 요약으로 유지하고 통합 가이드 링크를 추가한다. 모든 기능 이름과 제한값은 `app/mcp` 구현에서 검증한다.

**Tech Stack:** Markdown, Play Framework 설정(HOCON), MCP Streamable HTTP(JSON-RPC), Yona 사용자 토큰

## Global Constraints

- 문장과 절을 짧게 유지한다.
- 설정 후 바로 검증할 수 있는 순서로 설명한다.
- 토큰은 환경변수로 전달하고 예제에 실제 값을 넣지 않는다.
- 구현된 기능만 설명한다.
- 쓰기 기능과 실시간 SSE 전송은 지원하지 않는다고 명시한다.
- 프로젝트 접근 결과는 웹 UI와 같은 사용자 권한을 따른다고 설명한다.
- 도구 인자, 제한값, 리소스 URI는 현재 코드와 일치시킨다.

---

### Task 1: 통합 가이드 작성과 기술 문서 연결

**Files:**
- Create: `docs/ko/yona-mcp-guide.md`
- Modify: `docs/technical/mcp-server.md`
- Reference: `app/controllers/mcp/McpController.java`
- Reference: `app/mcp/McpAuth.java`
- Reference: `app/mcp/McpToolRegistry.java`
- Reference: `app/mcp/McpResourceRegistry.java`
- Reference: `app/mcp/McpPromptRegistry.java`
- Reference: `conf/application.conf.default`
- Reference: `conf/routes`

**Interfaces:**
- Consumes: `POST /mcp`, `mcp.enabled`, `mcp.allowedOrigins`, Yona 사용자 토큰, 읽기 전용 MCP 레지스트리
- Produces: 관리자·사용자용 한국어 통합 가이드와 기존 기술 문서의 안내 링크

- [ ] **Step 1: 공식 클라이언트 연결 방식 확인**

Codex와 Claude의 공식 문서에서 원격 Streamable HTTP 서버 등록 방식과 Bearer 토큰 환경변수 지원 여부를 확인한다. 공식 문서에서 확인되지 않은 설정 형식은 가이드에 넣지 않는다. 제품 UI가 필요한 방식이면 메뉴 경로만 설명하고, 파일 설정을 지원하면 최소 설정 예제를 제공한다.

Expected: 각 클라이언트 예제가 공식 문서의 현재 연결 방식과 일치한다.

- [ ] **Step 2: 한국어 통합 가이드 작성**

`docs/ko/yona-mcp-guide.md`를 다음 순서로 작성한다.

```markdown
# Yona MCP 사용 가이드

## 지원 범위
## 1. 서버 활성화
## 2. 사용자 토큰 발급
## 3. 연결 확인
## 4. MCP 클라이언트 연결
## 5. 제공 기능
### 도구
### 리소스
### 프롬프트
## 6. 권한과 보안
## 7. 문제 해결
## 제한 사항
```

반드시 아래 내용을 포함한다.

- 엔드포인트는 `POST /mcp`이며 `GET /mcp`는 `405 Method Not Allowed`이다.
- 비활성 상태에서는 `/mcp`가 `404 Not Found`를 반환한다.
- 인증은 `Authorization: Bearer`, `Authorization: token`, `Yona-Token`을 지원한다.
- 토큰 발급 경로는 사용자 메뉴의 `Account` > `User Token`이며 직접 경로는 `/user/editform/token`이다.
- `mcp.allowedOrigins`가 비어 있으면 Origin 없는 요청, 동일 호스트, localhost를 허용한다.
- 토큰과 비공개 프로젝트 데이터는 웹 UI와 동일한 사용자 권한을 따른다.
- 다음 도구 8개와 인자 범위를 표로 정리한다.

| 도구 | 필수 인자 | 선택 인자 |
| --- | --- | --- |
| `yona_list_projects` | `limit`(1~50) | `query` (`limit` 직접 생략 시 기본 20) |
| `yona_get_project` | `owner`, `project` | 없음 |
| `yona_list_issues` | `owner`, `project` | `query`, `state`(`open`, `closed`, `all`), `limit`(1~50, 기본 20) |
| `yona_get_issue` | `owner`, `project`, `number` | 없음 |
| `yona_list_posts` | `owner`, `project` | `query`, `limit`(1~50, 기본 20) |
| `yona_get_post` | `owner`, `project`, `number` | 없음 |
| `yona_list_milestones` | `owner`, `project` | `state`(`open`, `closed`, `all`), `limit`(1~50, 기본 20), `query`(현재 필터 미적용) |
| `yona_search` | `query` | `type`(`all`, `projects`, `issues`, `posts`), `limit`(1~25, 기본 10) |

- 다음 리소스 URI를 설명한다.

```text
yona://projects/{owner}/{project}
yona://projects/{owner}/{project}/issues/{number}
yona://projects/{owner}/{project}/posts/{number}
yona://projects/{owner}/{project}/milestones/{id}
yona://users/me/issues
```

- 프롬프트 `summarize_issue`, `draft_issue_comment`, `project_status_summary`의 용도를 설명한다.
- 환경변수 `YONA_MCP_TOKEN`과 `YONA_MCP_URL`을 사용하는 `initialize`, `tools/list`, `tools/call` 점검 예제를 제공한다.
- 오류 표에 404(비활성), 401(토큰), 403(Origin), 405(GET), JSON-RPC `-32601`, `-32602`, `-32603`을 포함한다.
- 읽기 전용, 단일 JSON-RPC 요청, Streamable HTTP POST 전용, SSE 미지원 제한을 명시한다.

- [ ] **Step 3: 기존 기술 문서에 통합 가이드 링크 추가**

`docs/technical/mcp-server.md`의 첫 설명 다음에 아래 안내를 추가한다.

```markdown
For installation, client setup, usage examples, and troubleshooting, see the
[Korean Yona MCP usage guide](../ko/yona-mcp-guide.md).
```

- [ ] **Step 4: 문서와 구현의 일치 여부 검증**

Run:

```bash
git diff --check
rg -n 'yona_(list_projects|get_project|list_issues|get_issue|list_posts|get_post|list_milestones|search)' docs/ko/yona-mcp-guide.md
rg -n 'yona://projects|yona://users/me/issues|summarize_issue|draft_issue_comment|project_status_summary' docs/ko/yona-mcp-guide.md
rg -n 'mcp.enabled|mcp.allowedOrigins|Authorization: Bearer|Yona-Token|POST /mcp' docs/ko/yona-mcp-guide.md
rg -n 'Korean Yona MCP usage guide' docs/technical/mcp-server.md
```

Expected: `git diff --check`가 종료 코드 0을 반환하고, 8개 도구·5개 리소스·3개 프롬프트·설정·인증·기술 문서 링크가 모두 검색된다.

- [ ] **Step 5: 문서 자체 검토**

다음을 직접 확인하고 발견한 문제를 문서에서 바로 수정한다.

- 미완료 표시, 실제 토큰, 미지원 쓰기 예제가 없다.
- 설치부터 점검까지 순서가 끊기지 않는다.
- 같은 설정 설명을 반복하지 않는다.
- 긴 문단은 목록이나 표로 줄였다.
- 외부 클라이언트 설정은 공식 문서와 일치한다.

- [ ] **Step 6: 커밋**

```bash
git add docs/ko/yona-mcp-guide.md docs/technical/mcp-server.md
git commit -m "docs: add Yona MCP usage guide"
```
