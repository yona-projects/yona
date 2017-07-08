/**
 * Yona, Project Hosting SW
 *
 * Copyright 2016 the original author or authors.
 */

package controllers.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.*;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.enumeration.State;
import org.joda.time.DateTime;
import play.db.ebean.Transactional;
import play.libs.Json;
import play.mvc.Result;
import utils.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static controllers.api.UserApi.createUserNode;
import static play.libs.Json.toJson;

public class IssueApi extends AbstractPostingApp {

    @Transactional
    public static Result updateIssueLabel(String owner, String projectName, Long number) {
        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest("Expecting Json data");
        }
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Issue issue = Issue.findByNumber(project, number);
        Set<IssueLabel> labels = new HashSet<>();

        for(JsonNode node: json){
            Long labelId = Long.parseLong(node.asText());
            labels.add(IssueLabel.finder.byId(labelId));
        }

        issue.labels = labels;
        issue.save();

        ObjectNode result = Json.newObject();
        result.put("id", project.owner);
        result.put("labels", toJson(issue.labels.size()));
        return ok(result);
    }

    @IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)
    public static Result getIssue(String owner, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Issue issue = Issue.findByNumber(project, number);
        JsonNode json = ProjectApi.getResult(issue);
        return ok(json);
    }

    @Transactional
    @IsCreatable(ResourceType.ISSUE_POST)
    public static Result newIssues(String owner, String projectName) {
        ObjectNode result = Json.newObject();
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest(result.put("message", "Expecting Json data"));
        }

        JsonNode issuesNode = json.findValue("issues");
        if (issuesNode == null || !issuesNode.isArray()) {
            return badRequest(result.put("message", "No issues key exists or value wasn't array!"));
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        List<JsonNode> createdIssues = new ArrayList<>();
        for (JsonNode issueNode : issuesNode) {
            createdIssues.add(createIssuesNode(issueNode, project));
        }

        return created(toJson(createdIssues));
    }

    private static JsonNode createIssuesNode(JsonNode json, Project project) {
        JsonNode files = json.findValue("temporaryUploadFiles");

        final Issue issue = new Issue();

        issue.setAuthor(findAuthor(json.findValue("author")));
        issue.project = project;
        issue.title = json.findValue("title").asText();
        issue.body = json.findValue("body").asText();
        issue.state = findIssueState(json);
        issue.createdDate = parseDateString(json.findValue("createdAt"));
        issue.updatedDate = parseDateString(json.findValue("updatedAt"));
        issue.assignee = findAssginee(json.findValue("assignees"), project);
        issue.milestone = findMilestone(json.findValue("milestoneTitle"), project);
        issue.dueDate = findDueDate(json.findValue("dueDate"));
        updateLabels(json, issue, project);
        issue.numOfComments = 0;

        if(json.findValue("number") != null && json.findValue("number").asLong() > 0){
            issue.saveWithNumber(json.findValue("number").asLong());
        } else {
            issue.save();
        }
        attachUploadFilesToPost(files, issue.asResource());

        ObjectNode result = Json.newObject();
        result.put("status", 201);
        result.put("location", controllers.routes.IssueApp.issue(project.owner, project.name, issue.getNumber()).toString());
        return result;
    }

    private static void updateLabels(JsonNode json, Issue issue, Project project) {
        JsonNode labelsNode = json.findValue("labels");
        if (labelsNode != null && labelsNode.isArray()) {
            for (JsonNode labelNode : labelsNode) {
                IssueLabel issueLabel = IssueLabel.findByName(
                        labelNode.findValue("labelName").asText(),
                        labelNode.findValue("category").asText(),
                        project);
                if(issueLabel != null){
                    play.Logger.warn("added " + issueLabel);
                    if(issue.labels == null) {
                        issue.labels = new HashSet<>();
                    }
                    issue.labels.add(issueLabel);
                }
            }
        }
    }

    private static Milestone findMilestone(JsonNode milestoneTitle, Project project) {
        if(milestoneTitle != null){
            return Milestone.findMilestoneByTitle(project, milestoneTitle.asText());
        }
        return null;
    }

    private static Date findDueDate(JsonNode dueDateNode) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH);
        if(dueDateNode != null){
            try {
                return df.parse(dueDateNode.asText());
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static State findIssueState(JsonNode json){
        JsonNode issueNode = json.findValue("state");
        State state = State.OPEN;
        if(issueNode != null) {
            if ("CLOSED".equalsIgnoreCase(issueNode.asText())) {
                state = State.CLOSED;
            }
        }
        return state;
    }

    @Transactional
    @IsCreatable(ResourceType.ISSUE_COMMENT)
    public static Result newIssueComment(String ownerName, String projectName, Long number)
            throws IOException {
        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest("Expecting Json data");
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        final Issue issue = Issue.findByNumber(project, number);

        if (!AccessControl.isResourceCreatable(
                UserApp.currentUser(), issue.asResource(), ResourceType.ISSUE_COMMENT)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        User user = findAuthor(json.findValue("author"));
        String body = json.findValue("body").asText();

        final IssueComment comment = new IssueComment(issue, user, body);

        comment.createdDate = parseDateString(json.findValue("createdAt"));
        comment.setAuthor(user);
        comment.issue = issue;
        comment.save();

        play.Logger.warn(json.findValue("temporaryUploadFiles").asText());
        attachUploadFilesToPost(json.findValue("temporaryUploadFiles"), comment.asResource());

        ObjectNode result = Json.newObject();
        result.put("status", 201);
        result.put("location", RouteUtil.getUrl(comment));

        return created(result);
    }

    public static User findAuthor(JsonNode authorNode){
        if (authorNode != null) {
            String email = authorNode.findValue("email").asText();
            User originalAuthor = User.findByEmail(email);
            if (originalAuthor != null) {
                return originalAuthor;
            } else {
                createUserNode(authorNode);
                return User.findByEmail(email);
            }
        }

        User user = User.findUserIfTokenExist(UserApp.currentUser());
        if (user.isAnonymous()) {
            play.Logger.error("Import error caused by unknown user import!");
        }
        return user;
    }

    private static Assignee findAssginee(JsonNode assigneesNode, @Nonnull Project project) {
        if ( assigneesNode != null && assigneesNode.isArray() && assigneesNode.size() > 0) {
            JsonNode assigneeNode = assigneesNode.get(0);
            User user = User.findByEmail(assigneeNode.findValue("email").asText());
            if(!user.isAnonymous()) {
                return Assignee.add(user.id, project.id);
            }
        }
        return null;
    }

    public static Date parseDateString(JsonNode dateStringNode){
        if(dateStringNode == null) {
            return null;
        }
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH);
        try {
            return df.parse(dateStringNode.asText());
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return null;
    }
}
