/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;

public final class McpPromptRegistry {
    private McpPromptRegistry() {
    }

    public static ObjectNode list() {
        ObjectNode result = Json.newObject();
        ArrayNode prompts = Json.newArray();
        prompts.add(prompt("summarize_issue", "Summarize issue",
                "Summarize one Yona issue for project members.",
                argument("owner", "Project owner.", true),
                argument("project", "Project name.", true),
                argument("number", "Issue number.", true)));
        prompts.add(prompt("draft_issue_comment", "Draft issue comment",
                "Draft a concise issue comment from a requested intent.",
                argument("owner", "Project owner.", true),
                argument("project", "Project name.", true),
                argument("number", "Issue number.", true),
                argument("intent", "What the comment should communicate.", false)));
        prompts.add(prompt("project_status_summary", "Project status summary",
                "Build a short status summary from recent readable project issues, posts, and milestones.",
                argument("owner", "Project owner.", true),
                argument("project", "Project name.", true)));
        result.set("prompts", prompts);
        return result;
    }

    public static ObjectNode get(JsonNode params) {
        String name = McpJson.requiredText(params, "name");
        JsonNode arguments = params == null ? null : params.path("arguments");
        ObjectNode result = Json.newObject();
        switch (name) {
            case "summarize_issue":
                result.put("description", "Summarize one Yona issue.");
                result.set("messages", messages(String.format(
                        "Read yona://projects/%s/%s/issues/%s and summarize the problem, current status, open questions, and next action.",
                        required(arguments, "owner"), required(arguments, "project"), required(arguments, "number"))));
                return result;
            case "draft_issue_comment":
                result.put("description", "Draft a Yona issue comment.");
                result.set("messages", messages(String.format(
                        "Read yona://projects/%s/%s/issues/%s and draft a concise, actionable comment. Intent: %s",
                        required(arguments, "owner"), required(arguments, "project"), required(arguments, "number"),
                        McpJson.optionalText(arguments, "intent", "Ask for the next concrete step."))));
                return result;
            case "project_status_summary":
                result.put("description", "Summarize one Yona project.");
                result.set("messages", messages(String.format(
                        "Use the Yona MCP tools for %s/%s to produce a short project status summary with notable open issues, recent board posts, and milestone risks.",
                        required(arguments, "owner"), required(arguments, "project"))));
                return result;
            default:
                throw new UnsupportedOperationException("Unknown MCP prompt: " + name);
        }
    }

    private static ObjectNode prompt(String name, String title, String description, ObjectNode... arguments) {
        ObjectNode prompt = Json.newObject();
        prompt.put("name", name);
        prompt.put("title", title);
        prompt.put("description", description);
        ArrayNode args = Json.newArray();
        for (ObjectNode argument : arguments) {
            args.add(argument);
        }
        prompt.set("arguments", args);
        return prompt;
    }

    private static ObjectNode argument(String name, String description, boolean required) {
        ObjectNode argument = Json.newObject();
        argument.put("name", name);
        argument.put("description", description);
        argument.put("required", required);
        return argument;
    }

    private static ArrayNode messages(String text) {
        ArrayNode messages = Json.newArray();
        ObjectNode message = Json.newObject();
        message.put("role", "user");
        message.set("content", McpJson.textContent(text));
        messages.add(message);
        return messages;
    }

    private static String required(JsonNode arguments, String field) {
        return McpJson.requiredText(arguments, field);
    }
}
