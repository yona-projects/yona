/**
 *  Yona, 21st Century Project Hosting SW
 *  <p>
 *  Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 *  https://yona.io
 **/

package controllers.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.UserApp;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ProjectScope;
import models.enumeration.ResourceType;
import models.enumeration.RoleType;
import org.apache.commons.lang3.StringUtils;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.Config;
import utils.JodaDateUtil;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static models.AbstractPosting.findByProject;
import static play.libs.Json.toJson;
import static utils.CacheStore.getProjectCacheKey;
import static utils.CacheStore.projectMap;

public class ProjectApi extends Controller {
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @IsAllowed(Operation.DELETE)
    public static Result exports(String owner, String projectName) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        ObjectNode json = Json.newObject();
        json.put("owner", project.owner);
        json.put("projectName", project.name);
        json.put("projectDescription", project.overview);
        json.put("projectCreatedDate", getDateString(project.createdDate));
        json.put("projectVcs", project.vcs);
        json.put("projectScope", getProjectScope(project));
        json.put("assignees", toJson(getAssginees(project).toArray()));
        json.put("authors", toJson(getAuthors(project).toArray()));
        json.put("memberCount", project.members().size());
        json.put("members", project.members().size());
        Optional.ofNullable(project.members())
                .ifPresent(members -> json.put("members", composeMembersJson(project)));
        json.put("issueCount", project.issues.size());
        json.put("postCount", project.posts.size());
        json.put("milestoneCount", project.milestones.size());
        json.put("labels", getAllLabels(project.issueLabels));
        json.put("issues", composePosts(project, Issue.finder));
        json.put("posts", composePosts(project, Posting.finder));
        json.put("milestones", toJson(project.milestones.stream()
                .map(ProjectApi::getMilestoneNode).collect(Collectors.toList())));
        return ok(json);
    }

    private static String getProjectScope(Project project) {
        switch (project.projectScope) {
            case PRIVATE:
                return "PRIVATE";
            case PROTECTED:
                return "PROTECTED";
            case PUBLIC:
                return "PUBLIC";
            default:
                return "PRIVATE";
        }
    }

    public static List<ObjectNode> getAssginees(Project project) {
        List<ObjectNode> members = new ArrayList<>();
        for (Assignee assignee : project.assignees) {
            ObjectNode member = Json.newObject();
            member.put("loginId", assignee.user.loginId);
            member.put("name", assignee.user.name);
            member.put("email", assignee.user.email);
            members.add(member);
        }
        return members;
    }

    public static List<ObjectNode> getAuthors(Project project) {
        List<ObjectNode> authors = new ArrayList<>();
        for (User user : project.findAuthors()) {
            ObjectNode member = Json.newObject();
            member.put("loginId", user.loginId);
            member.put("name", user.name);
            member.put("email", user.email);
            authors.add(member);
        }
        return authors;
    }

    @Transactional
    public static Result newProject(String owner) throws Exception {
        ObjectNode result = Json.newObject();
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest(result.put("message", "Expecting Json data"));
        }

        User currentUser = UserApp.currentUser();
        if (!currentUser.isSiteManager()) {
            return badRequest(result.put("message", "User creation with api is allowed by Site admin only."));
        }

        Project existed = Project.findByOwnerAndProjectName(owner, json.findValue("projectName").asText());
        if (existed != null) {
            result.put("status", 409);
            result.put("reason", "Conflict");
            result.put("project", createdProjectNode(existed));
            return badRequest(result);
        }

        Organization organization = Organization.findByName(owner);

        if ((!AccessControl.isGlobalResourceCreatable(currentUser))
                || (Organization.isNameExist(owner) && !OrganizationUser.isAdmin(organization.id, currentUser.id))) {
            return forbidden(result.put("message", Messages.get("'" + currentUser.name + "' has no permission")));
        }

        Project project = new Project();
        project.owner = owner;
        project.name = json.findValue("projectName").asText();
        project.overview = getProjectDescription(json);
        project.vcs = getProjectVcs(json);
        project.createdDate = IssueApi.parseDateString(json.findValue("projectCreatedDate"));
        project.projectScope = parseProjectScope(json);

        if (Organization.isNameExist(owner)) {
            project.organization = organization;
        }

        ProjectUser.assignRole(User.SITE_MANAGER_ID, Project.create(project), RoleType.SITEMANAGER);
        RepositoryService.createRepository(project);

        // TODO project settings 도 export를 하는 것이 좋을 것 같다
        saveMenuSettingsToDefault(project);
        addProjectMembers(json, project);

        projectMap.put(getProjectCacheKey(project.owner, project.name), project.id);

        return created(createdProjectNode(project));
    }

    private static ProjectScope parseProjectScope(JsonNode json) {
        JsonNode scopeNode = json.findValue("projectScope");
        if (scopeNode == null || StringUtils.isEmpty(scopeNode.asText())) {
            return ProjectScope.PRIVATE;
        }

        switch (scopeNode.asText()) {
            case "PRIVATE":
                return ProjectScope.PRIVATE;
            case "PUBLIC":
                return ProjectScope.PUBLIC;
            case "PROTECTED":
                return ProjectScope.PROTECTED;
            default:
                return ProjectScope.PRIVATE;
        }
    }

    private static String getProjectDescription(JsonNode json) {
        JsonNode projectDescription = json.findValue("projectDescription");
        if (projectDescription == null) {
            return "";
        }
        return projectDescription.asText();
    }

    private static void addProjectMembers(JsonNode json, Project project) {
        JsonNode membersNode = json.findValue("members");

        if (membersNode != null && membersNode.isArray()) {
            // find members and add members
            for (JsonNode memberNode : membersNode) {
                String mail = memberNode.findValue("email").asText();
                User member = User.findByEmail(mail);
                if (!member.isAnonymous()) {
                    String role = memberNode.findValue("role").asText();
                    if ("member".equalsIgnoreCase(role)) {
                        ProjectUser.assignRole(member.id, project.id, RoleType.MEMBER);
                    } else if ("manager".equalsIgnoreCase(role)) {
                        ProjectUser.assignRole(member.id, project.id, RoleType.MANAGER);
                    } else {
                        play.Logger.warn("Unknown role type: " + member);
                    }
                }
            }
            project.cleanEnrolledUsers();
        }
    }

    private static String getProjectVcs(JsonNode json) {
        JsonNode projectVcs = json.findValue("projectVcs");
        if (projectVcs == null) {
            return "GIT";
        }
        return projectVcs.asText();
    }

    private static JsonNode createdProjectNode(Project project) {
        ObjectNode created = Json.newObject();
        created.put("id", project.id);
        created.put("owner", project.owner);
        created.put("name", project.name);
        created.put("overview", project.overview);
        created.put("vcs", project.vcs);
        return created;
    }

    private static void saveMenuSettingsToDefault(Project project) {
        ProjectMenuSetting projectMenuSetting = new ProjectMenuSetting();
        projectMenuSetting.code = true;
        projectMenuSetting.issue = true;
        projectMenuSetting.pullRequest = true;
        projectMenuSetting.review = true;
        projectMenuSetting.milestone = true;
        projectMenuSetting.board = true;
        projectMenuSetting.project = project;

        projectMenuSetting.save();
        project.menuSetting = projectMenuSetting;
        project.update();
    }

    @IsAllowed(Operation.READ)
    public static JsonNode projectLabels(Project project) {

        Map<Long, Map<String, String>> labels = new HashMap<>();
        for (Label label : project.labels) {
            labels.put(label.id, convertToMap(label));
        }

        return toJson(labels);
    }

    /**
     * convert from some part of {@link models.Label} to {@link java.util.Map}
     * @param label {@link models.Label} object
     * @return label's map data
     */
    private static Map<String, String> convertToMap(Label label) {
        Map<String, String> tagMap = new HashMap<>();
        tagMap.put("category", label.category);
        tagMap.put("name", label.name);
        return tagMap;
    }

    private static <T> JsonNode composePosts(Project project, Model.Finder<Long, T> finder) {
        List<ObjectNode> result = findByProject(finder, project).stream()
                .map(posting -> getResult((AbstractPosting) posting))
                .collect(Collectors.toList());

        return toJson(result);
    }

    public static ObjectNode getResult(AbstractPosting posting) {
        ObjectNode json = Json.newObject();
        json.put("number", posting.getNumber());
        json.put("id", posting.id);
        json.put("title", posting.title);
        json.put("type", posting.asResource().getType().toString());
        json.put("author", composeAuthorJson(posting.getAuthor()));
        json.put("createdAt", JodaDateUtil.getDateString(posting.createdDate, JodaDateUtil.ISO_FORMAT));
        json.put("updatedAt", JodaDateUtil.getDateString(posting.updatedDate, JodaDateUtil.ISO_FORMAT));
        json.put("body", posting.body);
        json.put("owner", posting.project.owner);
        json.put("projectName", posting.project.name);

        if (posting.asResource().getType() == ResourceType.ISSUE_POST) {
            Issue issue = ((Issue) posting);
            Optional.ofNullable(issue.assignee)
                    .ifPresent(assignee -> json.put("assignees", composeAssigneeJson(issue)));
            json.put("state", issue.state.toString());
            Optional.ofNullable(issue.getLabels()).ifPresent(labels -> {
                if (labels.size() > 0) {
                    json.put("labels", composeLabelJson(labels));
                }
            });
            Optional.ofNullable(issue.milestone).
                    ifPresent(milestone -> json.put("milestoneId", milestone.id));
            Optional.ofNullable(issue.milestone)
                    .ifPresent(milestone -> json.put("milestoneTitle", milestone.title));
            Optional.ofNullable(issue.dueDate).ifPresent(dueDate ->
                    json.put("dueDate", getDateString(dueDate)));

            String refUrl = Config.getScheme() + "://" + Config.getHostport()
                    + controllers.routes.IssueApp.issue(posting.project.owner, posting.project.name, posting.getNumber()).url();
            json.put("refUrl", refUrl);
        }
        List<Attachment> attachments = Attachment.findByContainer(posting.asResource());
        if (attachments.size() > 0) {
            json.put("attachments", toJson(attachments));
        }

        List<ObjectNode> comments = composePlainCommentsJson(posting);
        if (comments.size() > 0) {
            json.put("comments", toJson(comments));
        }
        return json;
    }

    private static String getDateString(Date date) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH);
        return df.format(date);
    }

    private static JsonNode composeAuthorJson(User user) {
        ObjectNode authorNode = Json.newObject();
        authorNode.put("loginId", user.loginId);
        authorNode.put("name", user.name);
        authorNode.put("email", user.email);
        return authorNode;
    }

    // It may be looks like weired. But it is intended for future
    // which may introduce multiple assignees feature
    public static JsonNode composeAssigneeJson(Issue issue) {
        List<ObjectNode> assignees = new ArrayList<>();
        Assignee assignee = issue.assignee;

        ObjectNode assigneelNode = Json.newObject();
        assigneelNode.put("loginId", assignee.user.loginId);
        assigneelNode.put("name", assignee.user.name);
        assigneelNode.put("email", assignee.user.email);
        assignees.add(assigneelNode);

        return toJson(assignees);
    }

    private static JsonNode composeMembersJson(Project project) {
        List<ObjectNode> members = new ArrayList<>();

        for (ProjectUser projectUser : project.members()) {
            User user = projectUser.user;
            ObjectNode memberNode = Json.newObject();
            memberNode.put("loginId", user.loginId);
            memberNode.put("name", user.name);
            memberNode.put("role", projectUser.role.name);
            memberNode.put("email", user.email);
            members.add(memberNode);
        }

        return toJson(members);
    }

    private static JsonNode composeLabelJson(Set<IssueLabel> issueLabels) {
        List<ObjectNode> labels = new ArrayList<>();
        for (IssueLabel label : issueLabels) {
            ObjectNode labelNode = Json.newObject();
            labelNode.put("labelName", label.name);
            labelNode.put("labelColor", label.color);
            labelNode.put("category", label.category.name);
            labels.add(labelNode);
        }
        return toJson(labels);
    }

    public static JsonNode getAllLabels(List<IssueLabel> issueLabels) {
        List<ObjectNode> labels = new ArrayList<>();
        for (IssueLabel label : issueLabels) {
            ObjectNode labelNode = Json.newObject();
            labelNode.put("labelName", label.name);
            labelNode.put("labelColor", label.color);
            labelNode.put("category", label.category.name);
            labelNode.put("isExclusive", label.category.isExclusive);
            labels.add(labelNode);
        }
        return toJson(labels);
    }

    public static List<ObjectNode> composePlainCommentsJson(AbstractPosting posting) {
        Map<Long, ObjectNode> commentMap = new HashMap<>();
        Map<Long, List<ObjectNode>> childCommentMap = new HashMap<>();

        for (Comment comment: posting.getComments()) {
            Comment parentComment = comment.getParentComment();

            if (parentComment != null) {
                Long parentId = comment.getParentComment().id;
                ObjectNode childCommentNode = getCommentNode(comment);
                List<ObjectNode> childCommentList = childCommentMap.get(parentId);

                if (childCommentList == null) {
                    childCommentList = new ArrayList<>();
                }

                childCommentList.add(childCommentNode);
                childCommentMap.put(parentId,  childCommentList);
            } else {
                ObjectNode commentNode = getCommentNode(comment);
                commentMap.put(comment.id, commentNode);
            }
        }

        for (Long key: childCommentMap.keySet()) {
            ObjectNode comment = commentMap.get(key);
            comment.set("childComments", toJson(childCommentMap.get(key)));
        }

        return new ArrayList<ObjectNode>(commentMap.values());
    }

    private static ObjectNode getCommentNode(Comment comment) {
        ObjectNode commentNode = Json.newObject();

        commentNode.put("id", comment.id);
        commentNode.put("type", comment.asResource().getType().toString());
        User commentAuthor = User.find.byId(comment.authorId);
        commentNode.put("author", composeAuthorJson(commentAuthor));
        commentNode.put("createdAt", JodaDateUtil.getDateString(comment.createdDate, JodaDateUtil.ISO_FORMAT));
        commentNode.put("body", comment.contents);

        List<Attachment> attachments = Attachment.findByContainer(comment.asResource());
        if (attachments.size() > 0) {
            commentNode.put("attachments", toJson(attachments));
        }

        return commentNode;
    }

    public static ObjectNode getMilestoneNode(Milestone m) {
        ObjectNode node = Json.newObject();
        node.put("id", m.id);
        node.put("title", m.title);
        node.put("state", m.state.state());
        node.put("description", m.contents);
        Optional.ofNullable(m.dueDate).ifPresent(dueDate
                -> node.put("dueDate", getDateString(dueDate)));
        return node;
    }

    @Transactional
    @IsCreatable(ResourceType.ISSUE_LABEL)
    public static Result newLabel(String owner, String projectName) {
        ObjectNode result = Json.newObject();
        JsonNode json = request().body().asJson();
        if (json == null) {
            return badRequest(result.put("message", "Expecting Json data"));
        }

        JsonNode labelsNode = json.findValue("labels");
        if (labelsNode == null || !labelsNode.isArray()) {
            return badRequest(result.put("message", "No issues key exists or value wasn't array!"));
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        List<JsonNode> createdLabels = new ArrayList<>();

        for (JsonNode labelNode : labelsNode) {
            createdLabels.add(createLabelNode(labelNode, project));
        }
        project.update();

        return created(toJson(createdLabels));
    }

    private static JsonNode createLabelNode(JsonNode json, Project project) {
        String labelName = json.findValue("labelName").asText();
        String labelColor = json.findValue("labelColor").asText();
        String categoryName = json.findValue("category").asText();
        boolean isExclusive = json.findValue("isExclusive").isBoolean();

        IssueLabelCategory category = IssueLabelCategory.findByName(categoryName, project);
        if (category == null) {
            category = new IssueLabelCategory();
            category.project = project;
            category.name = categoryName;
            category.save();
        }

        IssueLabel label = IssueLabel.findByName(labelName, categoryName, project);
        if( label == null ) {
            label = new IssueLabel();
            label.category = category;
            label.color = labelColor;
            label.name = labelName;
            label.project = project;
            label.save();
        } else {
            return existedLabel(json);
        }

        ObjectNode result = Json.newObject();
        result.put("status", 201);
        result.put("label", label.name);
        result.put("category", category.name);
        result.put("labelColor", label.color);
        result.put("isExclusive", category.isExclusive);

        return result;
    }

    private static JsonNode existedLabel(JsonNode labelNode) {
        ObjectNode createdUserNode = Json.newObject();
        String message = Messages.get("label.error.duplicated");

        createdUserNode.put("status", 409);
        createdUserNode.put("reason", "Conflict");
        createdUserNode.put("message", message);
        createdUserNode.put("user", labelNode);

        return createdUserNode;
    }

    @Transactional
    @IsAllowed(Operation.READ)
    public static Result titleHeads(String owner, String projectName, String query) {
        if (!request().accepts("application/json")) {
            return status(Http.Status.NOT_ACCEPTABLE);
        }

        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        List<ObjectNode> searched = new ArrayList<>();
        searched.addAll(getherTitleHeads(project, query));
        searched.addAll(getherProjectLabels(project));

        ObjectNode result = Json.newObject();
        result.put("result", toJson(searched));
        return ok(result);
    }

    private static List<ObjectNode> getherProjectLabels(Project project) {
        List<ObjectNode> searched = new ArrayList<>();
        for (IssueLabel label : project.issueLabels) {
            searched.add(getIssueLabelNode(label));
        }
        return searched;
    }

    private static List<ObjectNode> getherTitleHeads(Project project, String query) {
        List<ObjectNode> searched = new ArrayList<>();
        List<TitleHead> titleHeads = TitleHead.findByProject(project, query);
        for (TitleHead titleHead : titleHeads) {
            searched.add(getTitleHeadNode(titleHead));
        }
        return searched;
    }

    private static ObjectNode getTitleHeadNode(TitleHead titleHead) {
        ObjectNode titleHeadNode = Json.newObject();
        titleHeadNode.put("name", titleHead.headKeyword);
        titleHeadNode.put("frequency", titleHead.frequency);
        titleHeadNode.put("category", "");
        titleHeadNode.put("searchText", titleHead.headKeyword);
        return titleHeadNode;
    }

    private static ObjectNode getIssueLabelNode(IssueLabel label) {
        ObjectNode labelNode = Json.newObject();
        labelNode.put("name", label.name);
        labelNode.put("frequency", 0);
        labelNode.put("category", label.category.name);
        labelNode.put("categoryId", label.category.id);
        labelNode.put("id", label.id);
        labelNode.put("labelColor", label.color);
        labelNode.put("isExclusive", label.category.isExclusive);
        labelNode.put("searchText", label.name + "/" + label.category.name);
        return labelNode;
    }
}
