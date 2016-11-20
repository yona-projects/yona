/**
 * Yona, 21st Century Project Hosting SW
 *
 * Copyright 2016 the original author or authors.
 */

package controllers.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.MigrationApp;
import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import play.db.ebean.Model;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.*;
import java.util.stream.Collectors;

import static controllers.MigrationApp.composePlainCommentsJson;
import static controllers.MigrationApp.getAssginees;
import static models.AbstractPosting.findByProject;
import static play.libs.Json.toJson;

public class ProjectApi extends Controller {

    @IsAllowed(Operation.DELETE)
    public static Result exports(String owner, String projectName) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        ObjectNode json = Json.newObject();
        json.put("owner", project.owner);
        json.put("projectName", project.name);
        json.put("assignees", toJson(getAssginees(project).toArray()));
        json.put("memberCount", project.members().size());
        json.put("issueCount", project.issues.size());
        json.put("postCount", project.posts.size());
        json.put("milestoneCount", project.milestones.size());
        json.put("issues", composePosts(project, Issue.finder));
        json.put("posts", composePosts(project, Posting.finder));
        json.put("milestones", toJson(project.milestones.stream()
                .map(MigrationApp::composeMilestoneJson).collect(Collectors.toList())));
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
        json.put("id", posting.getNumber());
        json.put("title", posting.title);
        json.put("type", posting.asResource().getType().toString());
        json.put("author", posting.authorLoginId);
        json.put("authorName", posting.authorName);
        json.put("created_at", posting.createdDate.getTime());
        json.put("body", posting.body);

        if(posting.asResource().getType() == ResourceType.ISSUE_POST){
            Optional.ofNullable(((Issue)posting).assignee).ifPresent(assignee -> json.put("assignee", assignee.user.loginId));
            Optional.ofNullable(((Issue)posting).milestone).ifPresent(milestone -> json.put("milestone", milestone.title));
            Optional.ofNullable(((Issue)posting).milestone).ifPresent(milestone -> json.put("milestoneId", milestone.id));
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
}
