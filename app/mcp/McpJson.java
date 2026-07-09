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

public final class McpJson {
    private McpJson() {
    }

    public static ObjectNode response(JsonNode id, JsonNode result) {
        ObjectNode response = Json.newObject();
        response.put("jsonrpc", "2.0");
        setId(response, id);
        response.set("result", result);
        return response;
    }

    public static ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = Json.newObject();
        response.put("jsonrpc", "2.0");
        setId(response, id);

        ObjectNode error = Json.newObject();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        return response;
    }

    public static ObjectNode textResult(String text) {
        ObjectNode result = Json.newObject();
        ArrayNode content = Json.newArray();
        content.add(textContent(text));
        result.set("content", content);
        result.put("isError", false);
        return result;
    }

    public static ObjectNode structuredResult(JsonNode structuredContent) {
        ObjectNode result = Json.newObject();
        ArrayNode content = Json.newArray();
        content.add(textContent(structuredContent.toPrettyString()));
        result.set("content", content);
        result.set("structuredContent", structuredContent);
        result.put("isError", false);
        return result;
    }

    public static ObjectNode textContent(String text) {
        ObjectNode content = Json.newObject();
        content.put("type", "text");
        content.put("text", text == null ? "" : text);
        return content;
    }

    public static ObjectNode stringProperty(String description) {
        ObjectNode property = Json.newObject();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    public static ObjectNode integerProperty(String description, int minimum, int maximum) {
        ObjectNode property = Json.newObject();
        property.put("type", "integer");
        property.put("description", description);
        property.put("minimum", minimum);
        property.put("maximum", maximum);
        return property;
    }

    public static ObjectNode booleanProperty(String description) {
        ObjectNode property = Json.newObject();
        property.put("type", "boolean");
        property.put("description", description);
        return property;
    }

    public static ObjectNode schema(ObjectNode properties, String... required) {
        ObjectNode schema = Json.newObject();
        schema.put("type", "object");
        schema.set("properties", properties);
        if (required.length > 0) {
            ArrayNode requiredNode = Json.newArray();
            for (String name : required) {
                requiredNode.add(name);
            }
            schema.set("required", requiredNode);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    public static String requiredText(JsonNode params, String field) {
        JsonNode value = params == null ? null : params.path(field);
        if (value == null || value.isMissingNode() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + field);
        }
        return value.asText();
    }

    public static int optionalInt(JsonNode params, String field, int defaultValue, int minimum, int maximum) {
        JsonNode value = params == null ? null : params.path(field);
        int resolved = value == null || value.isMissingNode() || value.isNull() ? defaultValue : value.asInt(defaultValue);
        if (resolved < minimum) {
            return minimum;
        }
        if (resolved > maximum) {
            return maximum;
        }
        return resolved;
    }

    public static String optionalText(JsonNode params, String field, String defaultValue) {
        JsonNode value = params == null ? null : params.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        return value.asText(defaultValue);
    }

    private static void setId(ObjectNode response, JsonNode id) {
        if (id == null || id.isMissingNode()) {
            response.putNull("id");
        } else {
            response.set("id", id);
        }
    }
}
