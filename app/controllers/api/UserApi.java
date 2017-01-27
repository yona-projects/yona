/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package controllers.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.UserApp;
import models.FavoriteOrganization;
import models.FavoriteProject;
import play.db.ebean.Transactional;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.List;

import static play.libs.Json.toJson;

public class UserApi extends Controller {

    @Transactional
    public static Result toggleFoveriteProject(String projectId) {
        if(projectId == null) {
            return badRequest("Wrong project id");
        }
        boolean isFavored = UserApp.currentUser().toggleFavoriteProject(Long.valueOf(projectId));
        ObjectNode json = Json.newObject();
        json.put("projectId", projectId);
        json.put("favored", isFavored);
        return ok(json);
    }

    @Transactional
    public static Result getFoveriteProjects() {
        ObjectNode json = Json.newObject();
        List<ObjectNode> projects = new ArrayList<>();
        List<Long> projectIds = new ArrayList<>();
        for (FavoriteProject favoriteProject : UserApp.currentUser().favoriteProjects) {
            ObjectNode project = Json.newObject();
            project.put("projectId", favoriteProject.project.id);
            project.put("projectName", favoriteProject.projectName);
            project.put("owner", favoriteProject.owner);
            projects.add(project);
            projectIds.add(favoriteProject.project.id);
        }
        json.put("projectIds", toJson(projectIds));
        json.put("projects", toJson(projects));
        return ok(json);
    }


    @Transactional
    public static Result toggleFoveriteOrganization(String organizationId) {
        if(organizationId == null) {
            return badRequest("Wrong organization id");
        }
        boolean isFavored = UserApp.currentUser().toggleFavoriteOrganization(Long.valueOf(organizationId));
        ObjectNode json = Json.newObject();
        json.put("organizationId", organizationId);
        json.put("favored", isFavored);
        return ok(json);
    }

    @Transactional
    public static Result getFoveriteOrganizations() {
        ObjectNode json = Json.newObject();
        List<ObjectNode> organizations = new ArrayList<>();
        List<Long> organizationIds = new ArrayList<>();
        for (FavoriteOrganization favoriteOrganization : UserApp.currentUser().favoriteOrganizations) {
            ObjectNode organization = Json.newObject();
            organization.put("organizationId", favoriteOrganization.organization.id);
            organization.put("organizationName", favoriteOrganization.organizationName);
            organizations.add(organization);
            organizationIds.add(favoriteOrganization.organization.id);
        }
        json.put("organizationIds", toJson(organizationIds));
        json.put("organizations", toJson(organizations));
        return ok(json);
    }
}
