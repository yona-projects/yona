/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package controllers.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import mcp.McpAuth;
import mcp.McpJson;
import mcp.McpPromptRegistry;
import mcp.McpResourceRegistry;
import mcp.McpToolRegistry;
import models.User;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import utils.LegacyController;

public class McpController extends LegacyController {
    private static final String PROTOCOL_VERSION = "2025-11-25";

    public Result endpoint() {
        if (!McpAuth.isEnabled()) {
            return notFound();
        }

        if (!McpAuth.isAllowedOrigin(request())) {
            return status(Http.Status.FORBIDDEN, McpJson.error(null, -32000, "Origin is not allowed"));
        }

        User user = McpAuth.authenticate(request());
        if (user.isAnonymous()) {
            response().setHeader("WWW-Authenticate", "Bearer");
            return unauthorized(McpJson.error(null, -32001, "Missing or invalid Yona token"));
        }
        McpAuth.bind(user);

        JsonNode message = request().body().asJson();
        if (message == null || !message.isObject()) {
            return badRequest(McpJson.error(null, -32700, "Expected one JSON-RPC object"));
        }

        JsonNode id = message.get("id");
        String method = message.path("method").asText(null);
        if (method == null) {
            return badRequest(McpJson.error(id, -32600, "Missing JSON-RPC method"));
        }

        if (id == null && method.startsWith("notifications/")) {
            return status(Http.Status.ACCEPTED);
        }

        try {
            ObjectNode result = dispatch(method, message.path("params"), user);
            return ok(McpJson.response(id, result));
        } catch (IllegalArgumentException e) {
            return ok(McpJson.error(id, -32602, e.getMessage()));
        } catch (UnsupportedOperationException e) {
            return ok(McpJson.error(id, -32601, e.getMessage()));
        } catch (Exception e) {
            play.Logger.error("MCP request failed: " + method, e);
            return ok(McpJson.error(id, -32603, "Internal error"));
        }
    }

    public Result stream() {
        if (!McpAuth.isEnabled()) {
            return notFound();
        }
        return status(Http.Status.METHOD_NOT_ALLOWED);
    }

    private ObjectNode dispatch(String method, JsonNode params, User user) {
        switch (method) {
            case "initialize":
                return initialize(params);
            case "ping":
                return Json.newObject();
            case "tools/list":
                return McpToolRegistry.listTools();
            case "tools/call":
                return McpToolRegistry.call(params, user);
            case "resources/list":
                return McpResourceRegistry.list(user);
            case "resources/templates/list":
                return McpResourceRegistry.listTemplates();
            case "resources/read":
                return McpResourceRegistry.read(params, user);
            case "prompts/list":
                return McpPromptRegistry.list();
            case "prompts/get":
                return McpPromptRegistry.get(params);
            default:
                throw new UnsupportedOperationException("Unsupported MCP method: " + method);
        }
    }

    private ObjectNode initialize(JsonNode params) {
        ObjectNode result = Json.newObject();
        result.put("protocolVersion", chooseProtocolVersion(params.path("protocolVersion").asText(null)));

        ObjectNode capabilities = Json.newObject();
        capabilities.set("tools", Json.newObject().put("listChanged", false));
        capabilities.set("resources", Json.newObject().put("listChanged", false));
        capabilities.set("prompts", Json.newObject().put("listChanged", false));
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = Json.newObject();
        serverInfo.put("name", "yona");
        serverInfo.put("title", "Yona MCP Server");
        serverInfo.put("version", yona.BuildInfo.version());
        serverInfo.put("description", "Read-only MCP access to Yona projects, issues, posts, and milestones.");
        result.set("serverInfo", serverInfo);
        result.put("instructions", "Use Yona resources and tools only for data the authenticated user can read.");
        return result;
    }

    private String chooseProtocolVersion(String requested) {
        if ("2025-11-25".equals(requested) || "2025-06-18".equals(requested) || "2025-03-26".equals(requested)) {
            return requested;
        }
        return PROTOCOL_VERSION;
    }
}
