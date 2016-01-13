/**
 * Yobire, Project Hosting SW
 *
 * @author Suwon Chae
 * Copyright 2016 the original author or authors.
 */

package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.IssueLabel;
import models.Posting;
import models.Project;
import play.db.ebean.Transactional;
import play.libs.Json;
import play.mvc.Result;

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
            System.out.println("node: " + node);
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

}
