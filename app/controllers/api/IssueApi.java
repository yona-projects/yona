/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 *  https://yona.io
 **/

package controllers.api;

import com.avaje.ebean.ExpressionList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.AbstractPostingApp;
import controllers.UserApp;
import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import controllers.routes;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.enumeration.UserState;
import org.apache.commons.lang3.StringUtils;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.F;
import play.libs.Json;
import play.libs.ws.WS;
import play.mvc.Http;
import play.mvc.Result;
import utils.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static controllers.UserApp.MAX_FETCH_USERS;
import static controllers.api.UserApi.createUserNode;
import static play.libs.Json.toJson;

public class IssueApi extends AbstractPostingApp {
    public static String TRANSLATION_API = play.Configuration.root().getString("application.extras.translation.api", "");
    public static String TRANSLATION_HEADER = play.Configuration.root().getString("application.extras.translation.header", "");
    public static String TRANSLATION_SVCID = play.Configuration.root().getString("application.extras.translation.svcid", "");

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

    @IsAllowed(Operation.READ)
    public static Result findAssignableUsersOfProject(String ownerName, String projectName, String query) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        List<ObjectNode> users = new ArrayList<>();

        if(StringUtils.isEmpty(query)){
            addUserToUsersWithCustomName(UserApp.currentUser(), users, Messages.get("issue.assignToMe"));

            for(User user: project.getAssignableUsers()){
                addUserToUsers(user, users);
            }

            return ok(toJson(users));
        }

        ExpressionList<User> el = getUserExpressionList(query, request().getQueryString("type"));

        int total = el.findRowCount();
        if (total > MAX_FETCH_USERS) {
            el.setMaxRows(MAX_FETCH_USERS);
            response().setHeader("Content-Range", "items " + MAX_FETCH_USERS + "/" + total);
        }

        gatheringUsersFromExpressionList(project, users, el);

        return ok(toJson(users));
    }

    private static void gatheringUsersFromExpressionList(Project project, List<ObjectNode> users, ExpressionList<User> el) {
        for (User user : el.findList()) {
            if (project.isPublic()) {
                addUserToUsers(user, users);
            } else {
                if (user.isMemberOf(project)
                        || project.hasGroup() && (user.isMemberOf(project.organization) || user.isAdminOf(project.organization))) {
                    addUserToUsers(user, users);
                }
            }
        }
    }

    @IsAllowed(Operation.READ)
    public static Result findAssignableUsers(String ownerName, String projectName, Long number, String query) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, number);

        List<ObjectNode> users = new ArrayList<>();


        if(StringUtils.isEmpty(query)){
            User issueAuthor = issue.getAuthor();

            if (issue.hasAssignee()) {
                addMyself(issue, users);
                addAuthorIfNotMeAndNotAssginee(issue, users, issueAuthor);
                addUserToUsersWithCustomName(User.anonymous, users, Messages.get("issue.noAssignee"));
                addUserToUsers(issue.assignee.user, users);  // To positioned up rank of list
            } else {
                addUserToUsersWithCustomName(UserApp.currentUser(), users, Messages.get("issue.assignToMe"));
                addAuthorIfNotMe(issue, users, issueAuthor);
            }

            for(User user: project.getAssignableUsersAndAssignee(issue)){
                addUserToUsers(user, users);
            }

            return ok(toJson(users));
        }

        ExpressionList<User> el = getUserExpressionList(query, request().getQueryString("type"));

        int total = el.findRowCount();
        if (total > MAX_FETCH_USERS) {
            el.setMaxRows(MAX_FETCH_USERS);
            response().setHeader("Content-Range", "items " + MAX_FETCH_USERS + "/" + total);
        }

        gatheringUsersFromExpressionList(project, users, el);

        return ok(toJson(users));
    }

    private static ExpressionList<User> getUserExpressionList(String query, String searchType) {
        ExpressionList<User> el = User.find.select("loginId, name").where()
                .eq("state", UserState.ACTIVE).disjunction();
        if( StringUtils.isNotBlank(searchType)){
            el.eq(searchType, query);
        } else {
            el.icontains("loginId", query);
            el.icontains("name", query);
            el.icontains("englishName", query);
            el.endJunction();
        }
        return el;
    }

    private static void addAuthorIfNotMe(Issue issue, List<ObjectNode> users, User issueAuthor) {
        if (!issue.getAuthor().loginId.equals(UserApp.currentUser().loginId)) {
            addUserToUsersWithCustomName(issueAuthor, users, Messages.get("issue.assignToAuthor"));
        }
    }

    private static void addAuthorIfNotMeAndNotAssginee(Issue issue, List<ObjectNode> users, User issueAuthor) {
        if (!issue.getAuthor().loginId.equals(UserApp.currentUser().loginId)
                && !issue.getAuthor().loginId.equals(issue.assignee.user.loginId)) {
            addUserToUsersWithCustomName(issueAuthor, users, Messages.get("issue.assignToAuthor"));
        }
    }

    private static void addMyself(Issue issue, List<ObjectNode> users) {
        if (!UserApp.currentUser().loginId.equals(issue.assignee.user.loginId)) {
            addUserToUsersWithCustomName(UserApp.currentUser(), users, Messages.get("issue.assignToMe"));
        }
    }

    static void addUserToUsers(User user, List<ObjectNode> users) {
        ObjectNode userNode = Json.newObject();
        userNode.put("loginId", user.loginId);
        userNode.put("name", user.getDisplayName());
        userNode.put("avatarUrl", user.avatarUrl());

        if(!users.contains(userNode)) {
            users.add(userNode);
        }
    }

    private static void addUserToUsersWithCustomName(User user, List<ObjectNode> users, String name) {
        ObjectNode userNode = Json.newObject();
        userNode.put("loginId", user.loginId);
        userNode.put("name", name);
        userNode.put("avatarUrl", "");

        if(!users.contains(userNode)) {
            users.add(userNode);
        }
    }

    public static Result updateAssginees(String owner, String projectName, Long number){
        ObjectNode result = Json.newObject();
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest(result.put("message", "Expecting Json data"));
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Issue issue = Issue.findByNumber(project, number);

        if (AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(),
                Operation.UPDATE)) {

            JsonNode assignees = json.findValue("assignees");
            if(assignees == null || assignees.size() == 0){
                return badRequest(result.put("message", "No assignee"));
            }

            boolean assigneeChanged = false;

            for(JsonNode assgineeNode: assignees){
                User assigneeUser = User.findByLoginId(assgineeNode.asText());

                User oldAssignee = null;

                if (issue.hasAssignee()) {
                    oldAssignee = issue.assignee.user;
                }
                Assignee newAssignee = getAssignee(project, assigneeUser);
                assigneeChanged = !issue.assignedUserEquals(newAssignee);

                issue.assignee = newAssignee;
                issue.updatedDate = JodaDateUtil.now();
                issue.update();

                if(assigneeChanged) {
                    NotificationEvent notiEvent = NotificationEvent.afterAssigneeChanged(oldAssignee, issue);
                    IssueEvent.addFromNotificationEvent(notiEvent, issue, UserApp.currentUser().loginId);
                }

                composeResultJson(result, assigneeUser);
            }
        }

        result.put("issue", routes.IssueApp.issue(owner, projectName, number).url());
        return ok(result);
    }

    private static void composeResultJson(ObjectNode result, User assigneeUser) {
        ObjectNode node = Json.newObject();
        node.put("loginId", assigneeUser.loginId);
        if(assigneeUser.isAnonymous()){
            node.put("name", Messages.get("common.none"));
        } else {
            node.put("name", assigneeUser.getDisplayName());
        }
        result.put("assignee", node);
    }

    private static Assignee getAssignee(Project project, User assigneeUser) {
        Assignee newAssignee;
        if (assigneeUser.isAnonymous()) {
            newAssignee = null;
        } else {
            newAssignee = Assignee.add(assigneeUser.id, project.id);
        }
        return newAssignee;
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static F.Promise<Result> translate() {
        ObjectNode result = Json.newObject();
        if(StringUtils.isBlank(TRANSLATION_API)) {
            return F.Promise.promise( () -> status(412, "Precondition Failed"));
        }

        JsonNode json = request().body().asJson();

        String owner = json.findValue("owner").asText();
        String projectName = json.findValue("projectName").asText();
        String type = json.findValue("type").asText();
        long number = json.findValue("number").asLong();

        String text = "";

        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        switch (type) {
            case "issue":
                Issue issue = Issue.findByNumber(project, number);
                text = "Title: " + issue.title + "\n\n" + issue.body;
                break;
            case "posting":
                Posting posting = Posting.findByNumber(project, number);
                text = "Title: " + posting.title + "\n\n" + posting.body;
                break;
            case "issue-comment":
                text = IssueComment.find.byId(number).contents;
                break;
            case "post-comment":
                text = PostingComment.find.byId(number).contents;
                break;
            default:
                break;
        }

        return getTranslation(text, project);
    }

    private static F.Promise<Result> getTranslation(String text, Project project) {

        return WS.url(TRANSLATION_API)
                .setContentType("application/x-www-form-urlencoded")
                .setHeader("Accept", "application/json,application/x-www-form-urlencoded,text/html,*/*")
                .setHeader(TRANSLATION_HEADER, TRANSLATION_SVCID)
                .post("source=ko&target=en&text=" + text)
                .map(response -> {
                    ObjectNode node = Json.newObject();

                    play.Logger.debug(response.getBody());
                    JsonNode jsonNode = response.asJson();
                    JsonNode resultNode = jsonNode.findValue("result");

                    String translated = resultNode.findValue("translatedText").asText();
                    node.put("translated", Markdown.render(translated, project));
                    return ok(node);
                });
    }

    @AnonymousCheck
    public static Result findSharerByloginIds(String ownerName, String projectName, Long number,
                                              String commaSeperatedIds) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, number);

        List<IssueSharer> list = getExpressionListByExtractingLoginIds(issue, commaSeperatedIds).findList();
        sortListByAddedDate(list);

        List<ObjectNode> users = new ArrayList<>();
        for (IssueSharer sharer :list) {
            addUserToUsers(sharer.user, users);
        }
        return ok(toJson(users));
    }

    private static void sortListByAddedDate(List<IssueSharer> list) {
        list.sort(new Comparator<IssueSharer>() {
            @Override
            public int compare(IssueSharer o1, IssueSharer o2) {
                return o1.created.compareTo(o2.created);
            }
        });
    }

    private static ExpressionList<IssueSharer> getExpressionListByExtractingLoginIds(Issue issue, String query) {
        String[] queryItems = query.split(",");
        ExpressionList<IssueSharer> el = IssueSharer.find
                .where()
                .in("loginId", Arrays.asList(queryItems))
                .eq("issue.id", issue.id);
        return el;
    }

    @IsAllowed(Operation.READ)
    public static Result findSharableUsers(String ownerName, String projectName, Long number, String query) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        List<ObjectNode> users = new ArrayList<>();

        ExpressionList<User> el = getUserExpressionList(query, request().getQueryString("type"));

        int total = el.findRowCount();
        if (total > MAX_FETCH_USERS) {
            el.setMaxRows(MAX_FETCH_USERS);
            response().setHeader("Content-Range", "items " + MAX_FETCH_USERS + "/" + total);
        }

        for (User user :el.findList()) {
            addUserToUsers(user, users);
        }

        return ok(toJson(users));
    }

    public static Result updateSharer(String owner, String projectName, Long number){
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest(Json.newObject().put("message", "Expecting Json data"));
        }

        if(noSharer(json.findValue("sharer"))){
            return badRequest(Json.newObject().put("message", "No sharer"));
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Issue issue = Issue.findByNumber(project, number);
        if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(),
                Operation.UPDATE)) {
            return forbidden(Json.newObject().put("message", "Permission denied"));
        }

        ObjectNode result = Json.newObject();
        String action = json.findValue("action").asText();

        for(JsonNode sharerLoginId: json.findValue("sharer")){
            switch (action) {
                case "delete":
                    removeSharer(issue, sharerLoginId.asText());
                    result.put("action", "deleted");
                    break;
                case "add":
                    addSharer(issue, sharerLoginId.asText());
                    result.put("action", "added");
                    break;
                default:
                    result.put("action", "Do nothing");
            }
            result.put("sharer", User.findByLoginId(sharerLoginId.asText()).getDisplayName());
        }

        return ok(result);
    }

    private static boolean noSharer(JsonNode sharers) {
        return sharers == null || sharers.size() == 0;
    }

    private static void addSharer(Issue issue, String loginId) {
        IssueSharer issueSharer = IssueSharer.find.where()
                .eq("loginId", loginId)
                .eq("issue.id", issue.id).findUnique();
        if(issueSharer == null) {
            issueSharer = IssueSharer.createSharer(loginId, issue);
            issueSharer.save();
        }
        issue.sharers.add(issueSharer);
    }

    private static void removeSharer(Issue issue, String loginId) {
        IssueSharer issueSharer =
                IssueSharer.find.where()
                        .eq("loginId", loginId)
                        .eq("issue.id", issue.id)
                        .findUnique();
        issueSharer.delete();
        issue.sharers.remove(issueSharer);
    }
}
