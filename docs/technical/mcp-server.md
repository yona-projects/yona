# Yona MCP Server

Yona exposes a read-only Model Context Protocol (MCP) endpoint at `POST /mcp`.

For installation, client setup, usage examples, and troubleshooting, see the
[Korean Yona MCP usage guide](../ko/yona-mcp-guide.md).

The endpoint is disabled by default and must be enabled explicitly.

## Configuration

Set the following values in `conf/application.conf` or as JVM system
properties:

```hocon
mcp.enabled = true
mcp.allowedOrigins = ""
```

`mcp.allowedOrigins` is optional. When it is empty, the server accepts requests
without an `Origin` header, same-host requests, and localhost requests. When it
is set, use a comma-separated list of exact origins.

## Authentication

MCP requests use existing Yona user tokens. The server accepts any of these
headers:

```http
Authorization: Bearer <user-token>
Authorization: token <user-token>
Yona-Token: <user-token>
```

The authenticated user is bound to the existing Yona request context, so project
visibility and read permission checks use the same access control rules as the
web application.

## Capabilities

The first version is read-only and exposes:

- Tools: list/search projects, get project, list/get issues, list/get board
  posts, list milestones.
- Resources: project, issue, post, milestone, and current user's open issues
  through `yona://...` URIs.
- Prompts: issue summary, issue comment draft, and project status summary
  prompt templates.

GET `/mcp` returns `405 Method Not Allowed`; this server currently supports the
Streamable HTTP POST request path only.

## Example

```bash
curl -X POST http://127.0.0.1:9000/mcp \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <user-token>' \
  --data '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "yona_get_issue",
      "arguments": {
        "owner": "yona",
        "project": "project-name",
        "number": 1
      }
    }
  }'
```
