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
import models.enumeration.Direction;
import models.enumeration.State;
import org.apache.commons.lang3.StringUtils;
import play.libs.Json;

import java.util.List;
import java.util.Locale;

public final class McpToolRegistry {
    private McpToolRegistry() {
    }

    public static ObjectNode listTools() {
        ObjectNode result = Json.newObject();
        ArrayNode tools = Json.newArray();
        tools.add(tool("yona_list_projects", "List readable projects",
                "Find projects the authenticated user can read.",
                schema(projectSearchProperties(), "limit")));
        tools.add(tool("yona_get_project", "Get project",
                "Read one project summary and counters.",
                schema(projectIdentityProperties(), "owner", "project")));
        tools.add(tool("yona_list_issues", "List project issues",
                "List issues in one readable project.",
                schema(projectScopedListProperties("Issue state: open, closed, or all."), "owner", "project")));
        tools.add(tool("yona_get_issue", "Get issue",
                "Read one issue with body and comments.",
                schema(numberedResourceProperties(), "owner", "project", "number")));
        tools.add(tool("yona_list_posts", "List project posts",
                "List board posts in one readable project.",
                schema(projectScopedListProperties(null), "owner", "project")));
        tools.add(tool("yona_get_post", "Get post",
                "Read one board post with body and comments.",
                schema(numberedResourceProperties(), "owner", "project", "number")));
        tools.add(tool("yona_list_milestones", "List project milestones",
                "List milestones in one readable project.",
                schema(projectScopedListProperties("Milestone state: open, closed, or all."), "owner", "project")));
        tools.add(tool("yona_search", "Search readable Yona content",
                "Search readable projects, issue titles, and board post titles.",
                schema(searchProperties(), "query")));
        result.set("tools", tools);
        return result;
    }

    public static ObjectNode call(JsonNode params, User user) {
        String name = McpJson.requiredText(params, "name");
        JsonNode arguments = params == null ? null : params.path("arguments");
        switch (name) {
            case "yona_list_projects":
                return listProjects(arguments, user);
            case "yona_get_project":
                return getProject(arguments, user);
            case "yona_list_issues":
                return listIssues(arguments, user);
            case "yona_get_issue":
                return getIssue(arguments, user);
            case "yona_list_posts":
                return listPosts(arguments, user);
            case "yona_get_post":
                return getPost(arguments, user);
            case "yona_list_milestones":
                return listMilestones(arguments, user);
            case "yona_search":
                return search(arguments, user);
            default:
                throw new UnsupportedOperationException("Unknown MCP tool: " + name);
        }
    }

    private static ObjectNode listProjects(JsonNode arguments, User user) {
        String query = McpJson.optionalText(arguments, "query", "").trim();
        int limit = McpJson.optionalInt(arguments, "limit", 20, 1, 50);

        ArrayNode projects = Json.newArray();
        List<Project> candidates = Project.find.query()
                .fetch("menuSetting")
                .orderBy().desc("id")
                .setMaxRows(Math.max(limit * 10, 100))
                .findList();
        for (Project project : candidates) {
            if (projects.size() >= limit) {
                break;
            }
            if (McpYona.canRead(user, project) && matchesProject(project, query)) {
                projects.add(McpYona.projectSummary(project));
            }
        }

        ObjectNode content = Json.newObject();
        content.set("projects", projects);
        content.put("limit", limit);
        return McpJson.structuredResult(content);
    }

    private static ObjectNode getProject(JsonNode arguments, User user) {
        Project project = readableProject(arguments, user);
        return McpJson.structuredResult(McpYona.projectDetail(project));
    }

    private static ObjectNode listIssues(JsonNode arguments, User user) {
        Project project = readableProject(arguments, user);
        int limit = McpJson.optionalInt(arguments, "limit", 20, 1, 50);
        String filter = McpJson.optionalText(arguments, "query", "").trim();
        State state = stateArgument(arguments, State.ALL);

        ExpressionList<Issue> where = Issue.finder.query()
                .fetch("project")
                .fetch("project.menuSetting")
                .fetch("assignee")
                .fetch("assignee.user")
                .fetch("milestone")
                .where()
                .eq("project.id", project.id)
                .isNull("parent")
                .ne("state", State.DRAFT);
        if (state != State.ALL) {
            where.eq("state", state);
        }
        if (StringUtils.isNotBlank(filter)) {
            where.icontains("title", filter);
        }

        ArrayNode issues = Json.newArray();
        for (Issue issue : where.orderBy().desc("number").setMaxRows(limit).findList()) {
            issues.add(McpYona.issueSummary(issue));
        }

        ObjectNode content = Json.newObject();
        content.set("issues", issues);
        return McpJson.structuredResult(content);
    }

    private static ObjectNode getIssue(JsonNode arguments, User user) {
        Project project = readableProject(arguments, user);
        Long number = numberArgument(arguments);
        Issue issue = Issue.findByNumber(project, number);
        if (!McpYona.canRead(user, issue)) {
            throw new IllegalArgumentException("Issue not found or not readable: " + number);
        }
        return McpJson.structuredResult(McpYona.issueDetail(issue));
    }

    private static ObjectNode listPosts(JsonNode arguments, User user) {
        Project project = readableProject(arguments, user);
        int limit = McpJson.optionalInt(arguments, "limit", 20, 1, 50);
        String filter = McpJson.optionalText(arguments, "query", "").trim();

        ExpressionList<Posting> where = Posting.finder.query()
                .fetch("project")
                .where()
                .eq("project.id", project.id);
        if (StringUtils.isNotBlank(filter)) {
            where.icontains("title", filter);
        }

        ArrayNode posts = Json.newArray();
        for (Posting posting : where.orderBy().desc("number").setMaxRows(limit).findList()) {
            posts.add(McpYona.postingSummary(posting));
        }

        ObjectNode content = Json.newObject();
        content.set("posts", posts);
        return McpJson.structuredResult(content);
    }

    private static ObjectNode getPost(JsonNode arguments, User user) {
        Project project = readableProject(arguments, user);
        Long number = numberArgument(arguments);
        Posting posting = Posting.findByNumber(project, number);
        if (!McpYona.canRead(user, posting)) {
            throw new IllegalArgumentException("Post not found or not readable: " + number);
        }
        return McpJson.structuredResult(McpYona.postingDetail(posting));
    }

    private static ObjectNode listMilestones(JsonNode arguments, User user) {
        Project project = readableProject(arguments, user);
        int limit = McpJson.optionalInt(arguments, "limit", 20, 1, 50);
        State state = stateArgument(arguments, State.ALL);

        ArrayNode milestones = Json.newArray();
        for (Milestone milestone : Milestone.findMilestones(project.id, state, Milestone.DEFAULT_SORTER, Direction.ASC)) {
            if (milestones.size() >= limit) {
                break;
            }
            milestones.add(McpYona.milestoneSummary(milestone));
        }

        ObjectNode content = Json.newObject();
        content.set("milestones", milestones);
        return McpJson.structuredResult(content);
    }

    private static ObjectNode search(JsonNode arguments, User user) {
        String query = McpJson.requiredText(arguments, "query").trim();
        String type = McpJson.optionalText(arguments, "type", "all").trim().toLowerCase(Locale.ROOT);
        int limit = McpJson.optionalInt(arguments, "limit", 10, 1, 25);

        ObjectNode content = Json.newObject();
        if ("all".equals(type) || "projects".equals(type)) {
            content.set("projects", searchProjects(query, user, limit));
        }
        if ("all".equals(type) || "issues".equals(type)) {
            content.set("issues", searchIssues(query, user, limit));
        }
        if ("all".equals(type) || "posts".equals(type)) {
            content.set("posts", searchPosts(query, user, limit));
        }
        if (!"all".equals(type) && !"projects".equals(type) && !"issues".equals(type) && !"posts".equals(type)) {
            throw new IllegalArgumentException("Unsupported search type: " + type);
        }
        return McpJson.structuredResult(content);
    }

    private static ArrayNode searchProjects(String query, User user, int limit) {
        ArrayNode projects = Json.newArray();
        ExpressionList<Project> where = Project.find.query()
                .fetch("menuSetting")
                .where();
        where.disjunction()
                .icontains("owner", query)
                .icontains("name", query)
                .icontains("overview", query)
                .endJunction();

        for (Project project : where
                .orderBy().desc("id")
                .setMaxRows(Math.max(limit * 5, 50))
                .findList()) {
            if (projects.size() >= limit) {
                break;
            }
            if (McpYona.canRead(user, project)) {
                projects.add(McpYona.projectSummary(project));
            }
        }
        return projects;
    }

    private static ArrayNode searchIssues(String query, User user, int limit) {
        ArrayNode issues = Json.newArray();
        for (Issue issue : Issue.finder.query()
                .fetch("project")
                .fetch("project.menuSetting")
                .fetch("assignee")
                .fetch("assignee.user")
                .fetch("milestone")
                .where()
                .icontains("title", query)
                .isNull("parent")
                .ne("state", State.DRAFT)
                .orderBy().desc("updatedDate")
                .setMaxRows(Math.max(limit * 5, 50))
                .findList()) {
            if (issues.size() >= limit) {
                break;
            }
            if (McpYona.canRead(user, issue)) {
                issues.add(McpYona.issueSummary(issue));
            }
        }
        return issues;
    }

    private static ArrayNode searchPosts(String query, User user, int limit) {
        ArrayNode posts = Json.newArray();
        for (Posting posting : Posting.finder.query()
                .fetch("project")
                .where()
                .icontains("title", query)
                .orderBy().desc("updatedDate")
                .setMaxRows(Math.max(limit * 5, 50))
                .findList()) {
            if (posts.size() >= limit) {
                break;
            }
            if (McpYona.canRead(user, posting)) {
                posts.add(McpYona.postingSummary(posting));
            }
        }
        return posts;
    }

    private static Project readableProject(JsonNode arguments, User user) {
        String owner = McpJson.requiredText(arguments, "owner");
        String projectName = McpJson.requiredText(arguments, "project");
        return McpYona.readableProject(user, owner, projectName);
    }

    private static Long numberArgument(JsonNode arguments) {
        int number = McpJson.optionalInt(arguments, "number", -1, 1, Integer.MAX_VALUE);
        return (long) number;
    }

    private static State stateArgument(JsonNode arguments, State defaultState) {
        String state = McpJson.optionalText(arguments, "state", defaultState.state()).toLowerCase(Locale.ROOT);
        if ("all".equals(state)) {
            return State.ALL;
        }
        if ("open".equals(state)) {
            return State.OPEN;
        }
        if ("closed".equals(state)) {
            return State.CLOSED;
        }
        throw new IllegalArgumentException("Unsupported state: " + state);
    }

    private static boolean matchesProject(Project project, String query) {
        if (StringUtils.isBlank(query)) {
            return true;
        }
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        return contains(project.owner, lowerQuery)
                || contains(project.name, lowerQuery)
                || contains(project.overview, lowerQuery);
    }

    private static boolean contains(String value, String lowerQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private static ObjectNode tool(String name, String title, String description, ObjectNode inputSchema) {
        ObjectNode tool = Json.newObject();
        tool.put("name", name);
        tool.put("title", title);
        tool.put("description", description);
        tool.set("inputSchema", inputSchema);

        ObjectNode annotations = Json.newObject();
        annotations.put("readOnlyHint", true);
        annotations.put("destructiveHint", false);
        annotations.put("idempotentHint", true);
        annotations.put("openWorldHint", false);
        tool.set("annotations", annotations);
        return tool;
    }

    private static ObjectNode schema(ObjectNode properties, String... required) {
        return McpJson.schema(properties, required);
    }

    private static ObjectNode projectSearchProperties() {
        ObjectNode properties = Json.newObject();
        properties.set("query", McpJson.stringProperty("Optional owner, project name, or overview text."));
        properties.set("limit", McpJson.integerProperty("Maximum projects to return.", 1, 50));
        return properties;
    }

    private static ObjectNode projectIdentityProperties() {
        ObjectNode properties = Json.newObject();
        properties.set("owner", McpJson.stringProperty("Project owner login id or organization name."));
        properties.set("project", McpJson.stringProperty("Project name."));
        return properties;
    }

    private static ObjectNode numberedResourceProperties() {
        ObjectNode properties = projectIdentityProperties();
        properties.set("number", McpJson.integerProperty("Issue or post number.", 1, Integer.MAX_VALUE));
        return properties;
    }

    private static ObjectNode projectScopedListProperties(String stateDescription) {
        ObjectNode properties = projectIdentityProperties();
        properties.set("query", McpJson.stringProperty("Optional title filter."));
        properties.set("limit", McpJson.integerProperty("Maximum items to return.", 1, 50));
        if (stateDescription != null) {
            properties.set("state", McpJson.stringProperty(stateDescription));
        }
        return properties;
    }

    private static ObjectNode searchProperties() {
        ObjectNode properties = Json.newObject();
        properties.set("query", McpJson.stringProperty("Search text."));
        properties.set("type", McpJson.stringProperty("Search type: all, projects, issues, or posts."));
        properties.set("limit", McpJson.integerProperty("Maximum items per type.", 1, 25));
        return properties;
    }
}
