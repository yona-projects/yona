/**
 * Yona, Project Hosting SW
 * <p>
 * Copyright 2016 the original author or authors.
 */

package controllers.api;

import static controllers.api.IssueApi.*;
import static play.libs.Json.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import controllers.AbstractPostingApp;
import controllers.UserApp;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import models.IssueLabel;
import models.Posting;
import models.PostingComment;
import models.Project;
import models.User;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import play.db.ebean.Transactional;
import play.libs.Json;
import play.mvc.Result;
import utils.AccessControl;
import utils.ErrorViews;
import utils.RouteUtil;

public class BoardApi extends AbstractPostingApp {

    @Transactional
    public static Result updatePostLabel(String owner, String projectName, Long number) {
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest("Expecting Json data");
        }
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Posting posting = Posting.findByNumber(project, number);
        Set<IssueLabel> labels = new HashSet<>();

        for (JsonNode node : json) {
            Long labelId = Long.parseLong(node.asText());
            labels.add(IssueLabel.finder.byId(labelId));
        }

        posting.labels = labels;
        posting.save();

        ObjectNode result = Json.newObject();
        result.put("id", project.owner);
        result.put("labels", toJson(posting.labels.size()));
        return ok(result);
    }

    @IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)
    public static Result getPosts(String owner, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Posting post = Posting.findByNumber(project, number);

        ObjectNode json = Json.newObject();
        json.put("title", post.title);
        json.put("body", post.body);
        json.put("author", post.authorLoginId);
        return ok(json);
    }

    @Transactional
    @IsCreatable(ResourceType.BOARD_POST)
    public static Result newPostings(String owner, String projectName) {
        ObjectNode result = Json.newObject();
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest(result.put("message", "Expecting Json data"));
        }

        JsonNode postingsNode = json.findValue("posts");
        if (postingsNode == null || !postingsNode.isArray()) {
            return badRequest(result.put("message", "No posts key exists or value wasn't array!"));
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        List<JsonNode> createdPostings = new ArrayList<>();
        for (JsonNode postingNode : postingsNode) {
            createdPostings.add(createPostingNode(postingNode, project));
        }

        return created(toJson(createdPostings));
    }

    private static JsonNode createPostingNode(JsonNode json, Project project) {
        JsonNode files = json.findValue("temporaryUploadFiles");

        final Posting posting = new Posting();

        posting.setAuthor(findAuthor(json.findValue("author")));
        posting.project = project;
        posting.title = json.findValue("title").asText();
        posting.body = json.findValue("body").asText();
        posting.createdDate = parseDateString(json.findValue("createdAt"));
        posting.updatedDate = parseDateString(json.findValue("updatedAt"));
        posting.numOfComments = 0;

        if (json.findValue("number") != null && json.findValue("number").asLong() > 0) {
            posting.saveWithNumber(json.findValue("number").asLong());
        } else {
            posting.save();
        }
        attachUploadFilesToPost(files, posting.asResource());

        ObjectNode result = Json.newObject();
        result.put("status", 201);
        result.put("location",
                controllers.routes.BoardApp.post(project.owner, project.name, posting.getNumber()).toString());
        return result;

    }

    @Transactional
    public static Result updatePostingContent(String owner, String projectName, Long number) {
        User user = UserApp.currentUser();
        if (user.isAnonymous()) {
            return unauthorized(Json.newObject().put("message", "unauthorized request"));
        }

        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest(Json.newObject().put("message", "Expecting Json data"));
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        final Posting posting = Posting.findByNumber(project, number);

        if (!AccessControl.isAllowed(user, posting.asResource(), Operation.UPDATE)) {
            return forbidden(Json.newObject().put("message", "Forbidden request"));
        }

        // TODO: It is TOO bulky comparing whole text
        String content = json.findValue("content").asText();
        String original = json.findValue("original").asText();

        if (isModifiedByOthers(posting.body, original)) {
            return conflicted(posting.body);
        }

        posting.body = content;
        posting.update();

        return ok(ProjectApi.getResult(posting));
    }

    @Transactional
    @IsCreatable(ResourceType.NONISSUE_COMMENT)
    public static Result newPostingComment(String ownerName, String projectName, Long number)
            throws IOException {
        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest("Expecting Json data");
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        final Posting posting = Posting.findByNumber(project, number);

        if (!AccessControl.isResourceCreatable(
                UserApp.currentUser(), posting.asResource(), ResourceType.NONISSUE_COMMENT)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        User user = findAuthor(json.findValue("author"));
        String body = json.findValue("body").asText();

        final PostingComment comment = new PostingComment(posting, user, body);

        comment.createdDate = parseDateString(json.findValue("createdAt"));
        comment.setAuthor(user);
        comment.posting = posting;
        comment.save();

        play.Logger.warn(json.findValue("temporaryUploadFiles").asText());
        attachUploadFilesToPost(json.findValue("temporaryUploadFiles"), comment.asResource());

        ObjectNode result = Json.newObject();
        result.put("status", 201);
        result.put("location", RouteUtil.getUrl(comment));

        return created(result);
    }

    @Transactional
    public static Result updatePostingComment(String ownerName, String projectName, Long number, Long commentId) {
        ObjectNode result = Json.newObject();

        User user = UserApp.currentUser();
        if (user.isAnonymous()) {
            return unauthorized(result.put("message", "unauthorized request"));
        }

        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest(result.put("message", "Expecting Json data"));
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        final Posting posting = Posting.findByNumber(project, number);
        PostingComment postingComment = posting.findCommentByCommentId(commentId);

        if (!AccessControl.isAllowed(user, postingComment.asResource(), Operation.UPDATE)) {
            return forbidden(Json.newObject().put("message", "Forbidden request"));
        }

        String comment = json.findValue("content").asText();
        // TODO: It is TOO bulky comparing whole text
        String original = json.findValue("original").asText();

        if (isModifiedByOthers(postingComment.contents, original)) {
            return conflicted(postingComment.contents);
        }

        postingComment.contents = comment;
        postingComment.save();

        ObjectNode commentNode = getCommentJsonNode(postingComment);
        ObjectNode authorNode = getAuthorJsonNode(user);

        commentNode.set("author", toJson(authorNode));
        result.set("result", commentNode);

        return ok(result);
    }
}
