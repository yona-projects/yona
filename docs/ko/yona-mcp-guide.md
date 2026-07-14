# Yona MCP 사용 가이드

## 지원 범위

Yona MCP 서버는 프로젝트, 이슈, 게시글, 마일스톤을 읽는 읽기 전용
Streamable HTTP 서버입니다. 엔드포인트는 `POST /mcp`이며, `GET /mcp`는
`405 Method Not Allowed`를 반환합니다. 서버가 비활성화되어 있으면 `/mcp`는
`404 Not Found`를 반환합니다.

## 1. 서버 활성화

`conf/application.conf`에 다음을 설정하고 Yona를 재시작합니다.

```hocon
mcp.enabled = true
mcp.allowedOrigins = ""
```

`mcp.allowedOrigins`가 비어 있으면 동일 호스트와 `localhost` Origin 요청을
허용합니다. Origin 헤더가 없는 요청은 설정값과 관계없이 항상 허용됩니다. 따라서
이 설정은 Origin 헤더가 있는 요청만 필터링하며, 일반 클라이언트나 네트워크
allowlist가 아닙니다. 외부 웹 클라이언트를 허용하려면 정확한 Origin을 쉼표로
구분해 설정합니다.

```hocon
mcp.allowedOrigins = "https://ai.example.com,https://assistant.example.com"
```

## 2. 사용자 토큰 발급

Yona에 로그인한 뒤 사용자 메뉴에서 `Account` > `User Token`으로 이동해 토큰을
발급합니다. 직접 경로는 `/user/editform/token`입니다.

토큰은 다음 인증 헤더 중 하나로 보낼 수 있습니다.

```http
Authorization: Bearer <user-token>
Authorization: token <user-token>
Yona-Token: <user-token>
```

아래 점검과 클라이언트 설정에는 환경변수를 사용합니다. 토큰을 설정 파일, 저장소,
채팅, 로그에 넣지 마십시오.

```bash
export YONA_MCP_URL='https://yona.example.com/mcp'
read -r -s -p 'Yona user token: ' YONA_MCP_TOKEN
export YONA_MCP_TOKEN
printf '\n'
```

## 3. 연결 확인

각 요청은 `POST /mcp`로 JSON-RPC 객체 하나를 전송합니다.

`initialize`:

```bash
curl -sS -X POST "$YONA_MCP_URL" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $YONA_MCP_TOKEN" \
  --data '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2025-11-25",
      "capabilities": {},
      "clientInfo": {"name": "yona-mcp-check", "version": "1.0"}
    }
  }'
```

`tools/list`:

```bash
curl -sS -X POST "$YONA_MCP_URL" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $YONA_MCP_TOKEN" \
  --data '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

`tools/call`:

```bash
curl -sS -X POST "$YONA_MCP_URL" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $YONA_MCP_TOKEN" \
  --data '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "yona_get_project",
      "arguments": {"owner": "<owner>", "project": "<project>"}
    }
  }'
```

## 4. MCP 클라이언트 연결

### Codex CLI

Codex CLI에서는 다음 명령을 실행하거나 `~/.codex/config.toml`에 설정을 추가한 뒤
클라이언트를 재시작합니다.

```bash
codex mcp add yona --url https://yona.example.com/mcp \
  --bearer-token-env-var YONA_MCP_TOKEN
```

```toml
[mcp_servers.yona]
url = "https://yona.example.com/mcp"
bearer_token_env_var = "YONA_MCP_TOKEN"
```

### Codex IDE 확장

Codex IDE 확장에서는 MCP 서버 추가 화면에서 Streamable HTTP를 선택합니다.

Codex의 공식 문서는 `bearer_token_env_var`를 지원합니다. `YONA_MCP_URL`의
TOML 변수 확장은 문서화되어 있지 않으므로 URL은 실제 엔드포인트로 입력합니다.
연결 후 Codex에서 `/mcp`로 활성 서버를 확인할 수 있습니다.

### Claude Code

Claude Code는 원격 HTTP MCP 서버의 헤더와 URL에서 환경변수 확장을 지원합니다.
프로젝트의 `.mcp.json`에 다음을 추가합니다.

```json
{
  "mcpServers": {
    "yona": {
      "type": "http",
      "url": "${YONA_MCP_URL}",
      "headers": {
        "Authorization": "Bearer ${YONA_MCP_TOKEN}"
      }
    }
  }
}
```

Claude Code에서 `/mcp`로 연결 상태를 확인합니다.

### Claude 웹 및 Claude Desktop

공식 원격 커넥터 흐름은 `Customize` > `Connectors` > `+` > `Add custom
connector`에서 URL을 추가하고, 필요하면 OAuth Client ID와 Client Secret을
입력하는 방식입니다. 이 흐름에서 임의 Bearer 토큰 헤더 설정은 공식 문서에
없습니다. 따라서 현재 Yona의 사용자 토큰 인증 서버는 Claude 웹·Desktop 원격
커넥터에 이 방식으로 연결할 수 없습니다. 지원되지 않는 설정 형식이나 토큰 값을
입력하지 마십시오.

## 5. 제공 기능

### 도구

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

### 리소스

`resources/read`에 다음 URI를 사용합니다.

```text
yona://projects/{owner}/{project}
yona://projects/{owner}/{project}/issues/{number}
yona://projects/{owner}/{project}/posts/{number}
yona://projects/{owner}/{project}/milestones/{id}
yona://users/me/issues
```

프로젝트 URI는 프로젝트 요약과 카운터를, 이슈와 게시글 URI는 본문과 댓글을,
마일스톤 URI는 마일스톤 상세를 반환합니다. `yona://users/me/issues`는 현재
사용자가 작성했거나 담당자인 열린 이슈를 반환합니다.

### 프롬프트

- `summarize_issue`: 이슈의 문제, 현재 상태, 열린 질문, 다음 조치를 요약합니다.
- `draft_issue_comment`: 의도에 맞는 간결하고 실행 가능한 이슈 댓글 초안을 만듭니다.
- `project_status_summary`: 열린 이슈, 최근 게시글, 마일스톤 위험을 바탕으로 프로젝트 상태를 요약합니다.

## 6. 권한과 보안

- 토큰과 비공개 프로젝트 데이터는 웹 UI와 동일한 사용자 권한을 따릅니다.
- 각 사용자는 자신이 읽을 수 있는 프로젝트와 데이터만 MCP로 읽을 수 있습니다.
- 토큰은 개인 자격 증명입니다. 환경변수로만 주입하고 공유하거나 저장소에 커밋하지 마십시오.
- `mcp.allowedOrigins`를 설정한 경우 허용할 정확한 Origin만 추가하십시오.
- 운영 환경에서는 HTTPS/TLS로만 `/mcp`를 제공하십시오. 요청에는 사용자 토큰이
  포함되고 비공개 데이터를 반환할 수 있습니다. 평문 HTTP는 통제된 loopback 개발
  환경에서만 사용하십시오.

## 7. 문제 해결

| 증상 | 원인 | 조치 |
| --- | --- | --- |
| HTTP 404 | MCP 서버가 비활성화됨 | `mcp.enabled = true`를 설정하고 Yona를 재시작합니다. |
| HTTP 401 | 토큰이 없거나 유효하지 않음 | 사용자 토큰을 다시 발급하고 `Authorization: Bearer` 또는 지원 헤더를 확인합니다. |
| HTTP 403 | Origin이 허용되지 않음 | 요청 Origin을 확인하거나 `mcp.allowedOrigins`에 정확한 Origin을 추가합니다. |
| HTTP 405 | `GET /mcp` 요청 | JSON-RPC 본문을 포함한 `POST /mcp`를 사용합니다. |
| JSON-RPC `-32601` | 지원하지 않는 메서드 또는 도구 | `tools/list`, `resources/list`, `prompts/list`에서 제공 항목을 확인합니다. |
| JSON-RPC `-32602` | 필수 인자가 없거나 값이 유효하지 않음 | 도구 표의 필수 인자와 값 범위를 확인합니다. |
| JSON-RPC `-32603` | 서버 내부 오류 | Yona 서버 로그를 확인하고 요청을 단순화해 다시 시도합니다. |

## 제한 사항

- 모든 도구와 리소스는 읽기 전용이며 쓰기 작업을 제공하지 않습니다.
- 한 요청에는 JSON-RPC 객체 하나만 보낼 수 있습니다.
- Streamable HTTP의 `POST /mcp`만 지원합니다.
- SSE 전송은 지원하지 않습니다.

## 공식 클라이언트 문서

- [OpenAI Codex MCP 문서](https://learn.chatgpt.com/docs/extend/mcp.md)
- [Anthropic Claude Code MCP 문서](https://code.claude.com/docs/en/mcp)
- [Anthropic Claude 원격 커넥터 문서](https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp)
