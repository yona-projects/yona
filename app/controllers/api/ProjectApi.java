/**
 * Yona, 21st Century Project Hosting SW
 *
 * Copyright 2016 the original author or authors.
 */

package controllers.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.MigrationApp;
import controllers.annotation.IsAllowed;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import play.db.ebean.Model;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static controllers.MigrationApp.composePlainCommentsJson;
import static controllers.MigrationApp.*;
import static models.AbstractPosting.findByProject;
import static play.libs.Json.toJson;

public class ProjectApi extends Controller {

    @IsAllowed(Operation.DELETE)
    public static Result exports(String owner, String projectName) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        ObjectNode json = Json.newObject();
        json.put("owner", project.owner);
        json.put("projectName", project.name);
        json.put("projectDescription", project.overview);
        json.put("assignees", toJson(getAssginees(project).toArray()));
        json.put("authors", toJson(getAuthors(project).toArray()));
        json.put("memberCount", project.members().size());
        json.put("members", project.members().size());
        Optional.ofNullable(project.members())
                .ifPresent(members -> json.put("members", composeMembersJson(project)));
        json.put("issueCount", project.issues.size());
        json.put("postCount", project.posts.size());
        json.put("milestoneCount", project.milestones.size());
        json.put("issues", composePosts(project, Issue.finder));
        json.put("posts", composePosts(project, Posting.finder));
        json.put("milestones", toJson(project.milestones.stream()
                .map(MigrationApp::getMilestoneNode).collect(Collectors.toList())));
        return ok(json);
    }

    private static <T> JsonNode composePosts(Project project, Model.Finder<Long, T> finder) {
        List<ObjectNode> result = findByProject(finder, project).stream()
                .map(posting -> getResult((AbstractPosting) posting))
                .collect(Collectors.toList());

        return toJson(result);
    }

    private static ObjectNode getResult(AbstractPosting posting) {
        ObjectNode json = Json.newObject();
        json.put("number", posting.getNumber());
        json.put("id", posting.id);
        json.put("title", posting.title);
        json.put("type", posting.asResource().getType().toString());
        json.put("author", composeAuthorJson(posting.getAuthor()));
        json.put("createdAt", posting.createdDate.getTime());
        json.put("body", posting.body);

        if(posting.asResource().getType() == ResourceType.ISSUE_POST){
            Issue issue = ((Issue)posting);
            Optional.ofNullable(issue.assignee)
                    .ifPresent(assignee -> json.put("assignee", composeAssigneeJson(issue)));
            Optional.ofNullable(issue.getLabels()).ifPresent(labels -> {
                if (labels.size() > 0) {
                    json.put("labels", composeLabelJson(labels));
                }
            });
            Optional.ofNullable(issue.milestone).
                    ifPresent(milestone -> json.put("milestoneId", milestone.id));
            Optional.ofNullable(issue.milestone)
                    .ifPresent(milestone -> json.put("milestoneTitle", milestone.title));

        }
        List<Attachment> attachments = Attachment.findByContainer(posting.asResource());
        if(attachments.size() > 0) {
            json.put("attachments", toJson(attachments));
        }

        List <ObjectNode> comments = composePlainCommentsJson(posting, ResourceType.NONISSUE_COMMENT);
        if(comments.size() > 0){
            json.put("comments", toJson(comments));
        }
        return json;
    }

    private static JsonNode composeAuthorJson(User user) {
        ObjectNode authorNode = Json.newObject();
        authorNode.put("loginId", user.loginId);
        authorNode.put("name", user.name);
        return authorNode;
    }

    // It may be looks like weired. But it is intended for future
    // which may introduce multiple assignees feature
    private static JsonNode composeAssigneeJson(Issue issue) {
        List<ObjectNode> assignees = new ArrayList<>();
        Assignee assignee = issue.assignee;

        ObjectNode assigneelNode = Json.newObject();
        assigneelNode.put("loginId", assignee.user.loginId);
        assigneelNode.put("name", assignee.user.name);
        assignees.add(assigneelNode);

        return toJson(assignees);
    }

    private static JsonNode composeMembersJson(Project project){
        List<ObjectNode> members = new ArrayList<>();

        for(ProjectUser projectUser: project.members()){
            User user = projectUser.user;
            ObjectNode memberNode = Json.newObject();
            memberNode.put("loginId", user.loginId);
            memberNode.put("name", user.name);
            memberNode.put("role", projectUser.role.name);
            memberNode.put("email", user.email);
            members.add(memberNode);
        }

        return toJson(members);
    }

    private static JsonNode composeLabelJson(Set<IssueLabel> issueLabels) {
        List<ObjectNode> labels = new ArrayList<>();
        for(IssueLabel label: issueLabels){
            ObjectNode labelNode = Json.newObject();
            labelNode.put("labelName", label.name);
            labelNode.put("labelColor", label.color);
            labelNode.put("labelCategory", label.category.name);
            labels.add(labelNode);
        }
        return toJson(labels);
    }
}
