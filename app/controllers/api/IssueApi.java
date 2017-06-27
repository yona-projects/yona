/**
 * Yona, Project Hosting SW
 *
 * Copyright 2016 the original author or authors.
 */

package controllers.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.AbstractPostingApp;
import controllers.UserApp;
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
    @IsCreatable(ResourceType.BOARD_POST)
    public static Result newIssueByJson(String owner, String projectName) {
        ObjectNode result = Json.newObject();
        System.out.println("-----------" + request().body().asJson().textValue());
        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest("Expecting Json data");
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        JsonNode files = json.findValue("temporaryUploadFiles");

        final Issue issue = new Issue();

        issue.createdDate = getCreatedDate(json.findValue("createdAt").asLong());
        issue.updatedDate = getCreatedDate(json.findValue("updatedAt").asLong());
        User user = findAuthor(json.findValue("author"));
        issue.setAuthor(user);
        issue.project = project;
        issue.title = json.findValue("title").asText();
        issue.body = json.findValue("body").asText();
        if(json.findValue("number") != null && json.findValue("number").asLong() > 0){
            issue.saveWithNumber(json.findValue("number").asLong());
        } else {
            issue.save();
        }
        attachUploadFilesToPost(files, issue.asResource());

        return ok(result);
    }

    private static User findAuthor(JsonNode authorNode){
        if (authorNode != null) {
            String email = authorNode.findValue("email").asText();
            User originalAuthor = User.findByEmail(email);
            if (originalAuthor != null) {
                return originalAuthor;
            }
        }

        User user = User.findUserIfTokenExist(UserApp.currentUser());
        if (user.isAnonymous()) {
            play.Logger.error("Import error caused by unknown user import!");
        }
        return user;
    }

    private static Date getCreatedDate(long timestamp){
        if(timestamp == 0){
            return JodaDateUtil.now();
        }
        return new DateTime(timestamp).toDate();
    }
}
