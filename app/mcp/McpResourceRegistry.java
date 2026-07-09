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
import io.ebean.ExpressionList;
import models.Issue;
import models.Milestone;
import models.Posting;
import models.Project;
import models.User;
import models.enumeration.State;
import play.libs.Json;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class McpResourceRegistry {
    private McpResourceRegistry() {
    }

    public static ObjectNode list(User user) {
        ObjectNode result = Json.newObject();
        ArrayNode resources = Json.newArray();
        resources.add(resource("yona://users/me/issues", "My issues", "Open issues authored by or assigned to me."));

        List<Project> projects = Project.find.query()
                .fetch("menuSetting")
                .orderBy().desc("id")
                .setMaxRows(200)
                .findList();
        for (Project project : projects) {
            if (resources.size() >= 51) {
                break;
            }
            if (McpYona.canRead(user, project)) {
                resources.add(resource(projectUri(project), project.owner + "/" + project.name, project.overview));
            }
        }
        result.set("resources", resources);
        return result;
    }

    public static ObjectNode listTemplates() {
        ObjectNode result = Json.newObject();
        ArrayNode templates = Json.newArray();
        templates.add(template("yona://projects/{owner}/{project}", "Project", "Project summary and counters."));
        templates.add(template("yona://projects/{owner}/{project}/issues/{number}", "Issue", "Issue body and comments."));
        templates.add(template("yona://projects/{owner}/{project}/posts/{number}", "Post", "Board post body and comments."));
        templates.add(template("yona://projects/{owner}/{project}/milestones/{id}", "Milestone", "Milestone details."));
        templates.add(template("yona://users/me/issues", "My issues", "Open issues authored by or assigned to me."));
        result.set("resourceTemplates", templates);
        return result;
    }

    public static ObjectNode read(JsonNode params, User user) {
        String uri = McpJson.requiredText(params, "uri");
        JsonNode content = readUri(uri, user);

        ObjectNode result = Json.newObject();
        ArrayNode contents = Json.newArray();
        ObjectNode item = Json.newObject();
        item.put("uri", uri);
        item.put("mimeType", "application/json");
        item.put("text", content.toPrettyString());
        contents.add(item);
        result.set("contents", contents);
        return result;
    }

    private static JsonNode readUri(String uri, User user) {
        URI parsed;
        try {
            parsed = URI.create(uri);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid resource URI: " + uri);
        }

        if (!"yona".equals(parsed.getScheme())) {
            throw new IllegalArgumentException("Unsupported resource URI scheme: " + uri);
        }

        if ("users".equals(parsed.getHost()) && "/me/issues".equals(parsed.getPath())) {
            return myIssues(user);
        }

        if (!"projects".equals(parsed.getHost())) {
            throw new IllegalArgumentException("Unsupported resource URI host: " + uri);
        }

        String[] parts = trimLeadingSlash(parsed.getPath()).split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Project resource URI requires owner and project: " + uri);
        }

        String owner = decode(parts[0]);
        String projectName = decode(parts[1]);
        Project project = McpYona.readableProject(user, owner, projectName);
        if (parts.length == 2) {
            return McpYona.projectDetail(project);
        }
        if (parts.length == 4 && "issues".equals(parts[2])) {
            return issue(project, parts[3], user);
        }
        if (parts.length == 4 && "posts".equals(parts[2])) {
            return post(project, parts[3], user);
        }
        if (parts.length == 4 && "milestones".equals(parts[2])) {
            return milestone(project, parts[3], user);
        }
        throw new IllegalArgumentException("Unsupported resource URI: " + uri);
    }

    private static JsonNode myIssues(User user) {
        ArrayNode issues = Json.newArray();
        ExpressionList<Issue> where = Issue.finder.query()
                .fetch("project")
                .fetch("project.menuSetting")
                .fetch("assignee")
                .fetch("assignee.user")
                .fetch("milestone")
                .where()
                .eq("state", State.OPEN)
                .isNull("parent");
        where.disjunction()
                .eq("authorId", user.id)
                .eq("assignee.user.id", user.id)
                .endJunction();

        for (Issue issue : where
                .orderBy().desc("updatedDate")
                .setMaxRows(100)
                .findList()) {
            if (McpYona.canRead(user, issue)) {
                issues.add(McpYona.issueSummary(issue));
            }
        }

        ObjectNode content = Json.newObject();
        content.set("issues", issues);
        return content;
    }

    private static JsonNode issue(Project project, String numberPart, User user) {
        long number = longPart(numberPart, "issue number");
        Issue issue = Issue.findByNumber(project, number);
        if (!McpYona.canRead(user, issue)) {
            throw new IllegalArgumentException("Issue not found or not readable: " + number);
        }
        return McpYona.issueDetail(issue);
    }

    private static JsonNode post(Project project, String numberPart, User user) {
        long number = longPart(numberPart, "post number");
        Posting posting = Posting.findByNumber(project, number);
        if (!McpYona.canRead(user, posting)) {
            throw new IllegalArgumentException("Post not found or not readable: " + number);
        }
        return McpYona.postingDetail(posting);
    }

    private static JsonNode milestone(Project project, String idPart, User user) {
        long id = longPart(idPart, "milestone id");
        Milestone milestone = Milestone.findById(id);
        if (milestone == null || milestone.project == null || !project.id.equals(milestone.project.id)
                || !McpYona.canRead(user, milestone)) {
            throw new IllegalArgumentException("Milestone not found or not readable: " + id);
        }
        return McpYona.milestoneDetail(milestone);
    }

    private static ObjectNode resource(String uri, String name, String description) {
        ObjectNode resource = Json.newObject();
        resource.put("uri", uri);
        resource.put("name", name);
        resource.put("title", name);
        resource.put("mimeType", "application/json");
        if (description != null) {
            resource.put("description", description);
        }
        return resource;
    }

    private static ObjectNode template(String uriTemplate, String name, String description) {
        ObjectNode template = Json.newObject();
        template.put("uriTemplate", uriTemplate);
        template.put("name", name);
        template.put("title", name);
        template.put("description", description);
        template.put("mimeType", "application/json");
        return template;
    }

    private static String projectUri(Project project) {
        return "yona://projects/" + encode(project.owner) + "/" + encode(project.name);
    }

    private static String trimLeadingSlash(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "";
        }
        return path.charAt(0) == '/' ? path.substring(1) : path;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static long longPart(String value, String label) {
        try {
            return Long.parseLong(decode(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }
}
