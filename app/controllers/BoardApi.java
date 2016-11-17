/**
 * Yobire, Project Hosting SW
 *
 * @author Suwon Chae
 * Copyright 2016 the original author or authors.
 */

package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import org.joda.time.DateTime;
import play.db.ebean.Transactional;
import play.libs.Json;
import play.mvc.Result;
import utils.JodaDateUtil;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static play.libs.Json.toJson;

public class BoardApi extends AbstractPostingApp {

    @Transactional
    public static Result updatePostLabel(String owner, String projectName, Long number) {
        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest("Expecting Json data");
        }
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Posting posting = Posting.findByNumber(project, number);
        Set<IssueLabel> labels = new HashSet<>();

        for(JsonNode node: json){
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
    public static Result newPostByJson(String owner, String projectName) {
        ObjectNode result = Json.newObject();
        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest("Expecting Json data");
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        User user = User.findUserIfTokenExist(UserApp.currentUser());
        JsonNode files = json.findValue("temporaryUploadFiles");

        final Posting post = new Posting();

        post.createdDate = getCreatedDate(json.findValue("created").asLong());
        post.updatedDate = getCreatedDate(json.findValue("created").asLong());
        post.setAuthor(user);
        post.project = project;
        post.title = json.findValue("title").asText();
        post.body = json.findValue("body").asText();
        if(json.findValue("id") != null && json.findValue("id").asLong() > 0){
            post.saveWithNumber(json.findValue("id").asLong());
        } else {
            post.save();
        }
        attachUploadFilesToPost(files, post.asResource());

        return ok(result);
    }

    private static Date getCreatedDate(long timestamp){
        if(timestamp == 0){
            return JodaDateUtil.now();
        }
        return new DateTime(timestamp * 1000).toDate();
    }
}
