/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package controllers.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.UserApp;
import models.FavoriteOrganization;
import models.FavoriteProject;
import models.User;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.List;

import static controllers.UserApp.createNewUser;
import static models.NotificationMail.isAllowedEmailDomains;
import static play.libs.Json.toJson;

public class UserApi extends Controller {

    @Transactional
    public static Result toggleFoveriteProject(String projectId) {
        if (projectId == null) {
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
        if (organizationId == null) {
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

    @Transactional
    public static Result newUser() {
        ObjectNode result = Json.newObject();
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest(result.put("message", "Expecting Json data"));
        }

        if (!UserApp.currentUser().isSiteManager()) {
            return badRequest(result.put("message", "User creation with api is allowed by Site admin only."));
        }

        JsonNode usersNode = json.findValue("users");
        if (usersNode == null || !usersNode.isArray()) {
            return badRequest(result.put("message", "No users key exists or value must be array!"));
        }

        List<JsonNode> createdUsers = new ArrayList<>();
        for (JsonNode userNode : usersNode) {
            createdUsers.add(createUserNode(userNode));
        }

        return created(toJson(createdUsers));
    }

    public static JsonNode createUserNode(JsonNode userNode) {
        ObjectNode createdUserNode = Json.newObject();

        String loginId = userNode.findValue("loginId").asText();
        String name = userNode.findValue("name").asText();
        String email = userNode.findValue("email").asText();

        String message;
        if (!isAllowedEmailDomains(email)) {
            return notAllowedDomainEmailUser(userNode);
        }

        User found = User.findByEmail(email);
        if (!found.isAnonymous()) {
            return alreadyExistedUser(userNode);
        }

        User user = new User();
        user.loginId = loginId;
        user.name = name;
        user.email = email;
        user.password = new SecureRandomNumberGenerator().nextBytes().toBase64();

        createdUserNode.put("status", 201);
        createdUserNode.put("reason", "Created");
        createdUserNode.put("user", successfullyCreatedUserNode(createNewUser(user)));
        return createdUserNode;
    }

    private static JsonNode successfullyCreatedUserNode(User created) {
        ObjectNode createdUserNode = Json.newObject();
        createdUserNode.put("id", created.id);
        createdUserNode.put("loginId", created.loginId);
        createdUserNode.put("name", created.name);
        createdUserNode.put("email", created.email);
        return createdUserNode;
    }

    private static JsonNode notAllowedDomainEmailUser(JsonNode userNode) {
        ObjectNode createdUserNode = Json.newObject();
        String message = Messages.get("user.unacceptable.email.domain");
        loggingUser(userNode, message);

        createdUserNode.put("status", 403);
        createdUserNode.put("reason", "Forbidden");
        createdUserNode.put("message", message);
        createdUserNode.put("user", userNode);
        return createdUserNode;
    }

    private static JsonNode alreadyExistedUser(JsonNode userNode) {
        ObjectNode createdUserNode = Json.newObject();
        String message = "Already exists!";
        loggingUser(userNode, message);

        createdUserNode.put("status", 409);
        createdUserNode.put("reason", "Conflict");
        createdUserNode.put("message", message);
        createdUserNode.put("user", userNode);

        return createdUserNode;
    }

    private static void loggingUser(JsonNode userNode, String message) {
        String name = userNode.findValue("name").asText();
        String email = userNode.findValue("email").asText();
        play.Logger.warn(message);
        play.Logger.warn("Rejected: " + name + " with " + email);
    }
}
