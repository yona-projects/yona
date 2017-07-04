/**
 * Yona, Project Hosting SW
 *
 * Copyright 2016 the original author or authors.
 */

package controllers.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.AbstractPostingApp;
import controllers.annotation.IsCreatable;
import models.*;
import models.enumeration.ResourceType;
import models.enumeration.State;
import org.joda.time.DateTime;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.Result;
import utils.*;

import java.util.*;

import static controllers.MigrationApp.getMilestoneNode;
import static play.libs.Json.toJson;

public class MilestoneApi extends AbstractPostingApp {
    @Transactional
    @IsCreatable(ResourceType.MILESTONE)
    public static Result newMilestone(String owner, String projectName) {
        ObjectNode result = Json.newObject();
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest(result.put("message", "Expecting Json data"));
        }

        JsonNode milestonesNode = json.findValue("milestones");
        if (milestonesNode == null || !milestonesNode.isArray()) {
            return badRequest(result.put("message", "No milestones key exists or value wasn't array!"));
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        List<JsonNode> createdMilestones = new ArrayList<>();
        for (JsonNode milestoneNode : milestonesNode) {
            createdMilestones.add(createMilestoneNode(milestoneNode, project));
        }

        return created(toJson(createdMilestones));
    }

    private static JsonNode createMilestoneNode(JsonNode milestoneNode, Project project) {
        ObjectNode result = Json.newObject();
        if (!Milestone.isUniqueProjectIdAndTitle(project.id, milestoneNode.findValue("title").asText())) {
            result.put("milestone", milestoneNode);
            return result.put("message", Messages.get("milestone.title.duplicated"));
        }

        Milestone newMilestone = new Milestone();
        newMilestone.title = parseMilestoneTitle(milestoneNode);
        newMilestone.contents = parseMilestoneContents(milestoneNode);
        newMilestone.project = project;
        newMilestone.dueDate = parseDuedate(milestoneNode);
        newMilestone.state = parseMilestoneState(milestoneNode);

        Milestone.create(newMilestone);
        return getMilestoneNode(newMilestone);
    }

    private static State parseMilestoneState(JsonNode json) {
        JsonNode stateNode = json.findValue("state");
        if (stateNode == null) {
            return State.OPEN;
        }
        if ("closed".equalsIgnoreCase(stateNode.asText())) {
            return State.CLOSED;
        }
        return State.OPEN;
    }

    private static Date parseDuedate(JsonNode json) {
        JsonNode dueOnNode = json.findValue("due_on");
        if (dueOnNode == null) {
            return null;
        }
        DateTime dateTime = new DateTime(dueOnNode.asText());
        return JodaDateUtil.lastSecondOfDay(dateTime.toDate());
    }

    private static String parseMilestoneTitle(JsonNode json) {
        JsonNode contentsNode = json.findValue("title");
        if (contentsNode == null) {
            return "No title";
        }
        return contentsNode.asText();
    }

    private static String parseMilestoneContents(JsonNode json) {
        JsonNode contentsNode = json.findValue("description");
        if (contentsNode == null) {
            return "";
        }
        return contentsNode.asText();
    }
}
