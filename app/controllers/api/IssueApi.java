/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 *  https://yona.io
 **/

package controllers.api;

import com.avaje.ebean.ExpressionList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.AbstractPostingApp;
import controllers.UserApp;
import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import controllers.routes;
import models.*;
import models.enumeration.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import play.api.mvc.Codec;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.F;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import play.mvc.Http;
import play.mvc.Result;
import utils.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static controllers.UserApp.MAX_FETCH_USERS;
import static controllers.UserApp.currentUser;
import static controllers.api.UserApi.*;
import static play.libs.Json.toJson;

public class IssueApi extends AbstractPostingApp {
    public static String TRANSLATION_API = play.Configuration.root().getString("application.extras.translation.api", "");
    public static String TRANSLATION_HEADER_KEY = play.Configuration.root().getString("application.extras.translation.headerKey", "");
    public static String TRANSLATION_HEADER_VALUE = play.Configuration.root().getString("application.extras.translation.headerValue", "");
    public static final int TRANSLATE_TEXT_LENGTH_LIMIT = 4500;
    public static final String NEWLINE = "\r\n";

    @Transactional
    public static Result imports(String owner, String projectName) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        try {
            String postNumber = request().getQueryString("postNumber");
            Long number = Optional.ofNullable(postNumber)
                    .map(Long::parseLong)
                    .orElseThrow(NumberFormatException::new);

            Posting posting = Posting.findByNumber(project, number);


            Issue issue = Issue.from(posting);
            issue.save();

            Map<String, String> postingCommentIdToIssueCommentIdMap = copyCommentsToIssue(posting.comments, issue);
            copyAttachmentsToIssue(posting, issue);
            copyAttachmentsToIssueComments(postingCommentIdToIssueCommentIdMap);

            removePosting(posting);

            ObjectNode json = Json.newObject();
            json.put("number", issue.getNumber());

            return ok(json);
        } catch (NumberFormatException numberFormatException) {
            String errorMessage = String.format("IssueApi.imports() error with NumberFormatException. owner: %s, projectName: %s - ", owner, projectName);
            play.Logger.error(errorMessage, numberFormatException);
        }

        return badRequest();
    }

    private static void copyAttachmentsToIssue(Posting from, Issue to) {
        List<Attachment> attachments = Attachment.findByContainer(ResourceType.BOARD_POST, String.valueOf(from.id));
        attachments.forEach(attachment -> {
            Attachment newAttachment = Attachment.copyAs(attachment);
            newAttachment.containerId = String.valueOf(to.id);
            newAttachment.containerType = ResourceType.ISSUE_POST;
            newAttachment.save();
            attachment.delete();
        });
    }

    private static void copyAttachmentsToIssueComments(Map<String, String> postingCommentIdToIssueCommentIdMap) {
        List<Attachment> attachments = postingCommentIdToIssueCommentIdMap.keySet().stream()
                .flatMap(postingCommentId -> Attachment.findByContainer(ResourceType.NONISSUE_COMMENT, String.valueOf(postingCommentId)).stream())
                .collect(Collectors.toList());

        attachments.forEach(attachment -> {
            String containerId = postingCommentIdToIssueCommentIdMap.get(attachment.containerId);

            Attachment newAttachment = Attachment.copyAs(attachment);
            newAttachment.containerId = containerId;
            newAttachment.containerType = ResourceType.ISSUE_COMMENT;
            newAttachment.save();
            attachment.delete();
        });
    }

    private static void removePosting(Posting posting) {
        posting.deleteOnly();
    }

    private static Map<String, String> copyCommentsToIssue(Collection<PostingComment> postingComments, Issue issue) {
        // 최상위 댓글
        List<PostingComment> topLevelPostingComments = postingComments.stream()
                .filter(postingComment -> Objects.isNull(postingComment.getParentComment()))
                .collect(Collectors.toList());

        // 대댓글
        List<PostingComment> secondLevelPostingComments = postingComments.stream()
                .filter(postingComment -> Objects.nonNull(postingComment.getParentComment()))
                .collect(Collectors.toList());

        // 최상위 댓글의 postingCommentId와 새로 생성될 issueCommentId의 mapping
        // XXX: id는 Long 타입이지만, parentId는 String 타입이다.
        Map<String, String> postingCommentIdToIssueCommentIdMap = new HashMap<>();

        // 최상위 댓글을 issueComment에 생성하고, 이때 발급된 issueCommentId를 보관한다.
        List<IssueComment> issueComments = new ArrayList<>();
        topLevelPostingComments.forEach(topLevelPostingComment -> {
            IssueComment issueComment = IssueComment.from(topLevelPostingComment, issue);
            issueComment.save();
            postingCommentIdToIssueCommentIdMap.put(String.valueOf(topLevelPostingComment.id), String.valueOf(issueComment.id));

            issueComments.add(issueComment);
        });

        // 대댓글을 issueComment에 생성하고, 이때 새로 발급된 issueCommentId를 parentCommentId에 넣어준다.
        secondLevelPostingComments.forEach(secondLevelPostingComment -> {
            String parentCommentId = postingCommentIdToIssueCommentIdMap.get(String.valueOf(secondLevelPostingComment.getParentComment().id));

            IssueComment issueComment = IssueComment.from(secondLevelPostingComment, issue);
            issueComment.parentCommentId = parentCommentId;
            issueComment.setParentComment(IssueComment.find.byId(Long.valueOf(parentCommentId)));
            issueComment.save();

            issueComments.add(issueComment);
        });

        return postingCommentIdToIssueCommentIdMap;
    }

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

    @Transactional
    public static Result getIssue(String owner, String projectName, Long number) {
        ObjectNode result = Json.newObject();
        if (!isAuthored(request())) {
            return unauthorized(result.put("message", "unauthorized request"));
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        if (project == null) {
            return badRequest(result.put("message", "no project by request"));
        }

        Issue issue = Issue.findByNumber(project, number);
        if (issue == null) {
            return badRequest(result.put("message", "no issue by request"));
        }
        ObjectNode json = ProjectApi.getResult(issue);

        return ok(Json.newObject().set("result", toJson(addIssueEvents(issue, json))));
    }

    private static ObjectNode addIssueEvents(Issue issue, ObjectNode json) {
        if (issue.events.size() > 0) {
            json.put("events", getIssueEvents(issue));
        }

        return json;
    }

    private static ArrayNode getIssueEvents(Issue issue) {
        ArrayNode array = Json.newObject().arrayNode();

        if (issue.events.size() > 0) {
            for (IssueEvent event: issue.events) {
                ObjectNode result = Json.newObject();
                User sender = User.findByLoginId(event.senderLoginId);
                result.put("id", event.id);
                result.put("createdDate", JodaDateUtil.getDateString(event.created, JodaDateUtil.ISO_FORMAT));
                result.put("eventType", event.eventType.toString());
                result.put("eventDescription", event.eventType.getDescr());
                result.put("oldValue", event.oldValue);
                result.put("newValue", event.newValue);
                result.put("actor", getActorJson(sender));
                array.add(result);
            }
        }

        return array;
    }

    private static JsonNode getActorJson(User user) {
        ObjectNode result = Json.newObject();
        result.put("name", user.getPureNameOnly());
        result.put("loginId", user.loginId);
        result.put("englishName", user.englishName);
        return result;
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

        boolean sendNotification = json.findValue("sendNotification") != null && json.findValue("sendNotification").asBoolean();

        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        List<JsonNode> createdIssues = new ArrayList<>();
        for (JsonNode issueNode : issuesNode) {
            createdIssues.add(createIssuesNode(issueNode, project, sendNotification));
        }

        return created(toJson(createdIssues));
    }

    @Transactional
    public static Result updateIssue(String owner, String projectName, Long number) {
        ObjectNode result = Json.newObject();

        if (!isAuthored(request())) {
            return unauthorized(result.put("message", "unauthorized request"));
        }

        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest(result.put("message", "Expecting Json data"));
        }

        User user = getAuthorizedUser(getAuthorizationToken(request()));

        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        final Issue issue = Issue.findByNumber(project, number);

        return updateIssueNode(json, project, issue, user);
    }

    @Transactional
    public static Result updateIssueState(String owner, String projectName, Long number) {
        ObjectNode result = Json.newObject();

        if (!isAuthored(request())) {
            return unauthorized(result.put("message", "unauthorized request"));
        }

        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest(result.put("message", "Expecting Json data"));
        }

        User user = getAuthorizedUser(getAuthorizationToken(request()));

        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        final Issue issue = Issue.findByNumber(project, number);
        State newIssueState = findIssueState(json);
        if (!newIssueState.equals(issue.state)) {
            addNewIssueEvent(issue, user, EventType.ISSUE_STATE_CHANGED, issue.state.state(), newIssueState.state());
        }
        issue.state = newIssueState;
        issue.save();

        result = ProjectApi.getResult(issue);
        return ok(Json.newObject().set("result", toJson(addIssueEvents(issue, result))));
    }

    @Transactional
    public static Result updateIssueContent(String owner, String projectName, Long number) {

        User user = UserApp.currentUser();
        if (user.isAnonymous()) {
            return unauthorized(Json.newObject().put("message", "unauthorized request"));
        }

        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest(Json.newObject().put("message", "Expecting Json data"));
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        final Issue issue = Issue.findByNumber(project, number);

        if (!AccessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) {
            return forbidden(Json.newObject().put("message", "Forbidden request"));
        }

        // TODO: It is TOO bulky comparing whole text
        String content = json.findValue("content").asText();
        String original = json.findValue("original").asText();

        if (isModifiedByOthers(issue.body, original)) {
            return conflicted(issue.body);
        }

        issue.body = content;
        issue.update();

        return ok(ProjectApi.getResult(issue));
    }

    private static Result updateIssueNode(JsonNode json, Project project, Issue issue, User user) {

        issue.title = json.findValue("title").asText();
        issue.body = json.findValue("body").asText();
        issue.milestone = findMilestone(json.findValue("milestoneTitle"), project);
        issue.updatedDate = JodaDateUtil.now();

        // TODO: Separate function for adding possible events
        String state = json.findValue("state").asText();
        if (!state.equals(issue.state.toString())) {
            addNewIssueEvent(issue, user, EventType.ISSUE_STATE_CHANGED, issue.state.state(), State.valueOf(state).state());
        }
        issue.state = findIssueState(json);

        JsonNode assigneeNode = json.findValue("assignees").get(0);
        String oldAssignee = issue.assignee != null ? issue.assignee.user.loginId : "";
        String newAssignee = assigneeNode != null ? assigneeNode.findValue("loginId").asText() : "";
        if (!oldAssignee.equals(newAssignee)) {
            oldAssignee = oldAssignee.length() == 0 ? null : oldAssignee;
            newAssignee = newAssignee.length() == 0 ? null : newAssignee;
            addNewIssueEvent(issue, user, EventType.ISSUE_ASSIGNEE_CHANGED, oldAssignee, newAssignee);
        }
        issue.assignee = findAssginee(json.findValue("assignees"), project);
        issue.save();

        ObjectNode issueNode = ProjectApi.getResult(issue);
        return ok(Json.newObject().set("result", toJson(addIssueEvents(issue, issueNode))));
    }

    private static void addNewIssueEvent(Issue issue, User user, EventType eventType, String oldValue, String newValue) {
        IssueEvent issueEvent = new IssueEvent();
        issueEvent.issue = issue;
        issueEvent.senderLoginId = user.loginId;
        issueEvent.oldValue = oldValue;
        issueEvent.newValue = newValue;
        issueEvent.created = new Date();
        issueEvent.eventType = eventType;
        issueEvent.save();
    }

    private static JsonNode createIssuesNode(JsonNode json, Project project, boolean sendNotification) {
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

        if (sendNotification) {
            NotificationEvent.afterNewIssue(issue);
        }
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
        if( issueNode == null) {
            return State.OPEN;
        }
        if ("OPEN".equalsIgnoreCase(issueNode.asText())) {
            return State.OPEN;
        } else {
            return State.CLOSED;
        }
    }

    public static Result commentNotiRecivers(String ownerName, String projectName, Long number) {
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest(Json.newObject().put("message", "Expecting Json data"));
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        final Issue issue = Issue.findByNumber(project, number);
        User user = UserApp.currentUser();

        String commentText = json.findValue("comment").asText();
        String parentCommentId = json.findValue("parentCommentId").asText();

        final IssueComment comment = new IssueComment(issue, user, commentText);

        comment.createdDate = JodaDateUtil.now();
        comment.setAuthor(user);
        comment.issue = issue;

        if (StringUtils.isNotBlank(parentCommentId)) {
           comment.parentCommentId = parentCommentId;
           comment.setParentComment(IssueComment.find.byId(json.findValue("parentCommentId").asLong()));
        }

        Set<User> receivers = NotificationEvent.getMandatoryReceivers(comment, EventType.NEW_COMMENT);

        List<ObjectNode> users = new ArrayList<>();
        for(User receiver: receivers) {
            addUserToUsers(receiver, users);
        }

        ObjectNode result = Json.newObject();
        result.put("receivers", toJson(users));

        return ok(result);
    }

    @Transactional
    public static Result newIssueComment(String ownerName, String projectName, Long number)
            throws IOException {
        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest("Expecting Json data");
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        final Issue issue = Issue.findByNumber(project, number);

        if (request().getHeader("Authorization") != null) {
            ObjectNode result = Json.newObject();
            if (!isAuthored(request())) {
                return unauthorized(result.put("message", "unauthorized request"));
            }

            User user = getAuthorizedUser(getAuthorizationToken(request()));
            String comment = json.findValue("comment").asText();
            return createCommentUsingToken(issue, user, comment);
        } else {
            return createCommentByUser(project, issue, json);
        }
    }

    public static boolean isModifiedByOthers(String current, String fromView){
        // At present, using .val() on textarea elements strips carriage return characters
        // https://stackoverflow.com/a/8601601/1450196
        // At first, I added hook of above link at the front page.
        // But I found that it introduce another problem, cursor location detection error.
        // So, decided to calculate sha1 without \r char.
        String currentChecksum = DigestUtils.sha1Hex(current.replaceAll("\r","").trim());
        String fromViewChecksum = DigestUtils.sha1Hex(fromView.replaceAll("\r","").trim());

        return !currentChecksum.equals(fromViewChecksum);
    }

    public static Result detectChange(String ownerName, String projectName, Long number) {
        if (UserApp.currentUser().isAnonymous()) {
            return unauthorized(Json.newObject().put("message", "unauthorized request"));
        }

        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest(Json.newObject().put("message", "Expecting Json data"));
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        final Issue issue = Issue.findByNumber(project, number);

        ObjectNode result = Json.newObject();

        String receivedChecksum = json.findValue("issueBodyChecksum").asText();
        int receivedNumOfComments = json.findValue("numOfComments").asInt();

        int currentNumOfComments = issue.computeNumOfComments();

        if( receivedNumOfComments <  currentNumOfComments) {
            IssueComment issueComment = issue.comments.get(issue.comments.size() - 1);
            result.put("commentAuthorName", User.findByLoginId(issueComment.authorLoginId).getDisplayName());
        }

        String hex = DigestUtils.sha1Hex(issue.body);
        result.put("issueBodyChanged", !hex.equals(receivedChecksum));
        result.put("numOfComments", currentNumOfComments);
        result.put("issueBodyChecksum", hex);
        result.put("issueUpdateDate", issue.updatedDate.getTime());

        result.put("result", "ok");

        return ok(result);

    }

    public static Status conflicted(String content) {
        ObjectNode result = Json.newObject();
        result.put("message", "Already modified by someone.");
        result.put("storedContent", content);
        return new Status(play.core.j.JavaResults.Conflict(), result, Codec.javaSupported("utf-8"));
    }

    @Transactional
    public static Result updateIssueComment(String ownerName, String projectName, Long number, Long commentId) {
        User user = UserApp.currentUser();
        if (user.isAnonymous()) {
            return unauthorized(Json.newObject().put("message", "unauthorized request"));
        }

        JsonNode json = request().body().asJson();
        if(json == null) {
            return badRequest(Json.newObject().put("message", "Expecting Json data"));
        }

        String comment = json.findValue("content").asText();
        // TODO: It is TOO bulky comparing whole text
        String original = json.findValue("original").asText();

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        final Issue issue = Issue.findByNumber(project, number);
        IssueComment issueComment = issue.findCommentByCommentId(commentId);

        if (isModifiedByOthers(issueComment.contents, original)) {
            return conflicted(issueComment.contents);
        }

        if (!AccessControl.isAllowed(user, issueComment.asResource(), Operation.UPDATE)) {
            return forbidden(Json.newObject().put("message", "Forbidden request"));
        }

        issueComment.contents = comment;
        issueComment.save();

        ObjectNode commentNode = getCommentJsonNode(issueComment);
        ObjectNode authorNode = getAuthorJsonNode(user);

        commentNode.set("author", toJson(authorNode));

        ObjectNode result = Json.newObject();
        result.set("result", commentNode);

        return ok(result);
    }

    private static Result createCommentByUser(Project project, Issue issue, JsonNode json) {
        if (!AccessControl.isResourceCreatable(
                UserApp.currentUser(), issue.asResource(), ResourceType.ISSUE_COMMENT)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        User user = findAuthor(json.findValue("author"));
        String body = json.findValue("body").asText();

        IssueComment issueComment = createComment(issue, user, body, json.findValue("createdAt"));

        attachUploadFilesToPost(json.findValue("temporaryUploadFiles"), issueComment.asResource());

        ObjectNode result = Json.newObject();
        result.put("status", 201);
        result.put("location", RouteUtil.getUrl(issueComment));

        return created(result);
    }

    private static Result createCommentUsingToken(Issue issue, User user, String comment) {
        createComment(issue, user, comment, null);
        ObjectNode result = ProjectApi.getResult(issue);
        return created(Json.newObject().set("result", toJson(addIssueEvents(issue, result))));
    }

    private static IssueComment createComment(Issue issue, User user, String comment, JsonNode dateNode) {
        final IssueComment issueComment = new IssueComment(issue, user, comment);

        issueComment.createdDate = dateNode == null ? JodaDateUtil.now() : parseDateString(dateNode);
        issueComment.setAuthor(user);
        issueComment.issue = issue;
        issueComment.save();

        return issueComment;
    }

    public static ObjectNode getCommentJsonNode(Comment comment) {
        ObjectNode commentNode = Json.newObject();

        commentNode.put("id", comment.id);
        commentNode.put("contents", comment.contents);
        commentNode.put("createdDate", JodaDateUtil.getDateString(comment.createdDate, JodaDateUtil.ISO_FORMAT));

        return commentNode;
    }

    public static ObjectNode getAuthorJsonNode(User user) {
        ObjectNode authorNode = Json.newObject();

        authorNode.put("id", user.id);
        authorNode.put("loginId", user.loginId);
        authorNode.put("name", user.name);

        return authorNode;
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

    private static ExpressionList<Project> getProjectExpressionList(String query, String searchType) {

        ExpressionList<Project> el = Project.find.select("id, name").where()
                .eq("projectScope", ProjectScope.PUBLIC).disjunction();

        el.icontains("name", query);
        el.endJunction();

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
        userNode.put("pureNameOnly", user.getPureNameOnly());
        userNode.put("avatarUrl", user.avatarUrl());
        userNode.put("type", "user");

        if(!users.contains(userNode)) {
            users.add(userNode);
        }
    }

    static void addProjectToProjects(Project project, List<ObjectNode> projects) {
        ObjectNode projectNode = Json.newObject();
        projectNode.put("loginId", project.id);
        projectNode.put("name", project.owner + "/" + project.name);
        projectNode.put("avatarUrl", "");
        projectNode.put("type", "project");

        if(!projects.contains(projectNode)) {
            projects.add(projectNode);
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
                text = "Title: " + issue.title + NEWLINE + NEWLINE + issue.body;
                break;
            case "posting":
                Posting posting = Posting.findByNumber(project, number);
                text = "Title: " + posting.title + NEWLINE + NEWLINE + posting.body;
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

        return getTranslation(text, project, translatorWsRequestHolderSupplier);
    }

    private static F.Promise<WSResponse> translate(String text, WSRequestHolder translator) {
        if (StringUtils.isBlank(text)) {
            return F.Promise.pure(null);
        } else {
            return translator.post("source=ko&target=en&text=" + text);
        }
    }

    private static Supplier<WSRequestHolder> translatorWsRequestHolderSupplier = () -> WS.url(TRANSLATION_API)
            .setContentType("application/x-www-form-urlencoded; charset=UTF-8")
            .setHeader("Accept", "application/json,application/x-www-form-urlencoded,text/html,*/*")
            .setHeader(TRANSLATION_HEADER_KEY, TRANSLATION_HEADER_VALUE);

    private static List<String> merge(List<String> texts) {

        List<String> results = new ArrayList<>();

        int chunkLength = 0;
        String chunk = "";
        for (int i  = 0 ; i < texts.size(); i += 1) {
            String text = texts.get(i);
            if (chunkLength + text.length() < TRANSLATE_TEXT_LENGTH_LIMIT) {
                chunk += text;
                chunk += NEWLINE;
                chunkLength += text.length();
            } else {
                results.add(chunk);
                chunk = text;
                chunk += NEWLINE;
                chunkLength = text.length();
            }
        }
        results.add(chunk);
        return results;
    }

    private static F.Promise<Result> getTranslations(List<String> texts, Project project, Supplier<WSRequestHolder> translatorSupplier) {
        WSRequestHolder translator = translatorSupplier.get();

        List<String> mergedTexts = merge(texts);

        List<F.Promise<WSResponse>> promises = mergedTexts.stream()
                .map(text -> translate(text, translator))
                .collect(Collectors.toList());

        return F.Promise.sequence(promises)
                .map(results -> results.stream()
                        .map(jsonNode -> {
                            if (jsonNode == null) {
                                return NEWLINE;
                            }
                            JsonNode resultNode = jsonNode.asJson().findPath("result");
                            JsonNode translatedTextNode = resultNode.findPath("translatedText");
                            return translatedTextNode.textValue();
                        })
                        .collect(Collectors.toList()))
                .map(translatedList -> {
                    String translated = String.join(NEWLINE, translatedList);
                    ObjectNode node = Json.newObject();
                    node.put("translated", Markdown.render(translated, project));
                    return ok(node);
                });
    }

    private static F.Promise<Result> getTranslation(String text, Project project, Supplier<WSRequestHolder> by) {
        List<String> texts = Arrays.asList(text.replaceAll("&", "%26").split(NEWLINE));
        return getTranslations(texts, project, by);
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

        List<ObjectNode> results = new ArrayList<>();

        ExpressionList<User> userExpressionList = getUserExpressionList(query, request().getQueryString("type"));
        ExpressionList<Project> projectExpressionList = getProjectExpressionList(query, request().getQueryString("type"));

        int total = userExpressionList.findRowCount() + projectExpressionList.findRowCount();
        if (total > MAX_FETCH_USERS) {
            userExpressionList.setMaxRows(MAX_FETCH_USERS / 2);
            projectExpressionList.setMaxRows(MAX_FETCH_USERS / 2);
            response().setHeader("Content-Range", "items " + MAX_FETCH_USERS + "/" + total);
        }

        for (User user :userExpressionList.findList()) {
            addUserToUsers(user, results);
        }

        for (Project project: projectExpressionList.findList()) {
            addProjectToProjects(project, results);
        }

        return ok(toJson(results));
    }

    public static Result updateSharer(String owner, String projectName, Long number){
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest(Json.newObject().put("message", "Expecting Json data"));
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Issue issue = Issue.findByNumber(project, number);
        if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(),
                Operation.UPDATE)) {
            return forbidden(Json.newObject().put("message", "Permission denied"));
        }

        JsonNode sharer = json.findValue("sharer");
        if(noSharer(sharer)){
            return badRequest(Json.newObject().put("message", "No sharer"));
        }

        final String action = json.findValue("action").asText();

        ObjectNode result = changeSharer(sharer, issue, action);

        return ok(result);
    }

    public static Result upvoteWeight(String owner, String projectName, Long number){
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Issue issue = Issue.findByNumber(project, number);

        if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(),
                Operation.UPDATE)) {
            return forbidden(Json.newObject().put("message", "Permission denied"));
        }

        issue.weight = issue.weight + 1;
        issue.update();

        ObjectNode result = Json.newObject();
        result.put("weight", issue.weight);

        return ok(result);
    }

    public static Result downvoteWeight(String owner, String projectName, Long number){
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Issue issue = Issue.findByNumber(project, number);

        if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(),
                Operation.UPDATE)) {
            return forbidden(Json.newObject().put("message", "Permission denied"));
        }

        issue.weight = issue.weight - 1;
        issue.update();

        ObjectNode result = Json.newObject();
        result.put("weight", issue.weight);

        return ok(result);
    }


    private static ObjectNode changeSharer(JsonNode sharer, Issue issue, String action) {
        ObjectNode result = Json.newObject();
        List<String> users = new ArrayList<>();

        if(sharer.findValue("type").asText().equals("project")) {
            changeSharerByProject(sharer.findValue("loginId").asLong(), issue, action, result, users);
        } else {
            changeSharerByUser(sharer.findValue("loginId").asText(), issue, action, result, users);
        }

        sendNotification(users, issue, action);
        return result;
    }

    private static void changeSharerByUser(String loginId, Issue issue, String action, ObjectNode result, List<String> users) {
        if ("add".equalsIgnoreCase(action)) {
            addSharer(issue, loginId);
        } else if ("delete".equalsIgnoreCase(action)) {
            removeSharer(issue, loginId);
        } else {
            play.Logger.error("Unknown issue sharing action: " + issue + ":" + action + " by " + currentUser());
        }

        users.add(loginId);
        setShareActionToResponse(action, result);
        result.put("sharer", User.findByLoginId(loginId).getDisplayName());
    }

    private static void changeSharerByProject(Long projectId, Issue issue, String action, ObjectNode result, List<String> users) {
        List<ProjectUser> projectUsers = ProjectUser.findMemberListByProject(projectId);

        for (ProjectUser projectUser: projectUsers) {
            if ("add".equalsIgnoreCase(action)) {
                addSharer(issue, projectUser.user.loginId);
            } else if ("delete".equalsIgnoreCase(action)) {
                removeSharer(issue, projectUser.user.loginId);
            } else {
                play.Logger.error("Unknown issue sharing action: " + issue + ":" + action + " by " + currentUser());
            }
            users.add(projectUser.user.loginId);
        }

        setShareActionToResponse(action, result);

        result.put("sharer", Project.find.byId(projectId).name);
    }

    private static void setShareActionToResponse(String action, ObjectNode result) {
        if ("add".equalsIgnoreCase(action)) {
            result.put("action", "added");
        } else if ("delete".equalsIgnoreCase(action)) {
            result.put("action", "deleted");
        } else {
            result.put("action", "Do nothing. Unsupported action: " + action);
        }
    }

    private static void sendNotification(List<String> users, Issue issue, String action) {
        Runnable preUpdateHook = new Runnable() {
            @Override
            public void run() {
                for(String sharerLoginId: users){
                    addSharerChangedNotification(issue, sharerLoginId, action);
                }
            }
        };
        preUpdateHook.run();
    }


    private static void addSharerChangedNotification(Issue issue, String sharerLoginId, String action) {
        NotificationEvent notiEvent = NotificationEvent.afterIssueSharerChanged(issue, sharerLoginId, action);
        IssueEvent.addFromNotificationEventWithoutSkipEvent(notiEvent, issue, UserApp.currentUser().loginId);
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
