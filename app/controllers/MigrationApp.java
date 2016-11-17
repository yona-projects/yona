/**
 * Yobire, Project Hosting SW
 *
 * @author Suwon Chae
 * Copyright 2016 the original author or authors.
 */
package controllers;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.Query;
import com.avaje.ebean.RawSql;
import com.avaje.ebean.RawSqlBuilder;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.annotation.AnonymousCheck;
import models.*;
import models.enumeration.ResourceType;
import models.support.IssueLabelAggregate;
import org.apache.commons.lang.StringUtils;
import play.libs.F;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.mvc.Result;
import views.html.migration.home;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static play.libs.Json.toJson;
import static play.mvc.Http.Context.Implicit.request;
import static play.mvc.Results.ok;

@AnonymousCheck
public class MigrationApp {

    static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final String YONA_SERVER = "/";


    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Promise<Result> migration() {
        String authProcessingCode = request().getQueryString("code");

        if(StringUtils.isNotBlank(authProcessingCode)){
            return getOAuthToken(authProcessingCode).map((F.Function<String, Result>) token
                    -> ok(home.render("Migration", authProcessingCode, token)));
        } else {
            return Promise.promise((F.Function0<Result>) ()
                    -> ok(home.render("Migration", null, null)));
        }
    }

    private static Promise<String> getOAuthToken(String code) {
        final String CLIENT_ID = "e7f9ad76a3a4ba19b2a5";
        final String CLIENT_SECRET = "32e7fb33ee5c42501cb2aac9a6f6c485bf285cf5";
        final String ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";

        return WS.url(ACCESS_TOKEN_URL)
                .setContentType("application/x-www-form-urlencoded")
                .setHeader("Accept", "application/json,application/x-www-form-urlencoded,text/html,*/*")
                .post("client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET + "&code=" + code)
                .map(response -> {
                    play.Logger.debug(response.getBody());
                    String accessToken = "";
                    try {
                        Pattern p = Pattern.compile("access_token=([^&]+)");
                        Matcher m = p.matcher(response.getBody());
                        if(m.find() ){
                            accessToken = m.group(1);
                        }
                    } catch (PatternSyntaxException ex) {
                        play.Logger.error("Couldn't find access_token");
                    }
                    play.Logger.error("token=" + accessToken);
                    return accessToken;
                });
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result projects(){
        Set<Project> sourceProjects = new HashSet<>();

        getheringOrgProjects(sourceProjects);
        gatheringUserProjects(sourceProjects);

        List<ObjectNode> projects = new ArrayList<>();
        for(Project project: sortProjectsByOwnerAndName(sourceProjects)){
            ObjectNode projectNode = Json.newObject();
            projectNode.put("owner", project.owner);
            projectNode.put("projectName", project.name);
            projectNode.put("private", project.isPrivate());
            projectNode.put("members", project.members().size());
            projectNode.put("full_name", project.owner + "/" + project.name);
            projects.add(projectNode);
        }
        return ok(toJson(projects));
    }

    private static List<Project> sortProjectsByOwnerAndName(Set<Project> projects) {
        Comparator<Project> comparator = Comparator.comparing(project -> project.owner);
        comparator = comparator.thenComparing(Comparator.comparing(project -> project.name));
        List<Project> list = new ArrayList<>(projects);
        Collections.sort(list, comparator);
        return list;
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result project(String owner, String projectName){
        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        ObjectNode result = Json.newObject();
        result.put("owner", project.owner);
        result.put("projectName", project.name);
        result.put("full_name", project.owner + "/" + project.name);
        result.put("assignees", toJson(getAssginees(project).toArray()));
        result.put("memberCount", project.members().size());
        result.put("issueCount", project.issues.size());
        result.put("postCount", project.posts.size());
        result.put("milestoneCount", project.milestones.size());
        return ok(result);
    }

    private static List<ObjectNode> getAssginees(Project project) {
        List<ObjectNode> members = new ArrayList<>();
        for(Assignee assignee: project.assignees){
            ObjectNode member = Json.newObject();
            member.put("name", assignee.user.name);
            member.put("login", assignee.user.loginId);
            members.add(member);
        }
        return members;
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result exportIssueLabelPairs(String owner, String projectName){
        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        Query<IssueLabelAggregate> query = Ebean.find(IssueLabelAggregate.class);
        String sql = "select issue_id, issue_label_id \n" +
                "from issue i, issue_issue_label iil \n" +
                "where project_id = " + project.id + "\n" +
                "and i.id = iil.issue_id";
        RawSql rawSql = RawSqlBuilder.parse(sql).create();
        query.setRawSql(rawSql);
        List<IssueLabelAggregate> results = query.findList();

        ObjectNode issueLabelPairs = Json.newObject();
        issueLabelPairs.put("issueLabelPairs", toJson(results));
        return ok(issueLabelPairs);
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result exportLabels(String owner, String projectName){
        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        ObjectNode labels = Json.newObject();
        for (IssueLabel label : IssueLabel.findByProject(project)) {
            ObjectNode node = Json.newObject();
            node.put("id", label.id);
            node.put("name", label.name);
            node.put("categoryId", label.category.id);
            node.put("categoryName", label.category.name);
            labels.put(String.valueOf(label.id), node);
        }

        ObjectNode exportData = Json.newObject();
        exportData.put("labels", toJson(labels));
        return ok(exportData);
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result exportMilestones(String owner, String projectName){
        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        List<ObjectNode> milestones = project.milestones.stream()
                .map(MigrationApp::composeMilestoneJson).collect(Collectors.toList());

        ObjectNode exportData = Json.newObject();
        exportData.put("milestones", toJson(milestones));
        return ok(exportData);
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result exportPosts(String owner, String projectName){
        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        List<ObjectNode> issues = project.posts.stream()
                .map(MigrationApp::composePostJson).collect(Collectors.toList());

        ObjectNode exportData = Json.newObject();
        exportData.put("issues", toJson(issues));
        return ok(exportData);
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    public static Result exportIssues(String owner, String projectName){
        Project project = Project.findByOwnerAndProjectName(owner, projectName);

        List<ObjectNode> issues = project.issues.stream()
                .map(MigrationApp::composeIssueJson).collect(Collectors.toList());

        ObjectNode exportData = Json.newObject();
        exportData.put("issues", toJson(issues));
        return ok(exportData);
    }

    private static ObjectNode composeMilestoneJson(Milestone m) {
        ObjectNode node = Json.newObject();
        node.put("id", m.id);
        node.put("title", m.title);
        node.put("state", m.state.state());
        node.put("description", m.contents);
        Optional.ofNullable(m.dueDate).ifPresent(dueDate -> node.put("due_on",
                LocalDateTime.ofInstant(m.dueDate.toInstant(), ZoneId.systemDefault()).format(formatter)));

        ObjectNode milestoneJson = Json.newObject();
        milestoneJson.put("milestone", node);
        return milestoneJson;
    }

    private static String addOriginalAuthorName(String bodyText, String authorLoginId,
                                                String authorName, String type, String link){
        return String.format("@%s (%s) 님이 작성한 [%s](%s)입니다. \n\\---\n\n%s",
                authorLoginId, authorName, type, link, bodyText);
    }

    private static String relativeLinksToAbsolutePath(String text){
        // replace relative img tag src to absolute path
        // and replace relative markdown link path to absolute path
        return text.replaceAll("(<img src=[\"\'])/(?<link>.*)([\"\']>)", "$1" + YONA_SERVER + "$2$3")
                .replaceAll("\\[(?<text>[^\\]]*)\\]\\(/(?<link>[^\\)]*)\\)", "[$1](" + YONA_SERVER + "$2)");
    }

    private static String relativeLinksToWikiCommitPath(String text){
        // replace relative img tag src to absolute path
        // and replace relative markdown link path to wiki commit file path
        return text.replaceAll("(<img src=[\"\'])/(?<link>.*)([\"\']>)", "$1" + YONA_SERVER + "$2$3")
                .replaceAll("\\[(?<text>[^\\]]*)\\]\\(/(?<link>[^\\)]*)\\)", "[$1](../wiki/$2/$1)");
    }

    private static StringBuilder addAttachmentsString(@NotNull StringBuilder sb, ResourceType type, String id){
        try {
            List<Map<String, String>> attachments = AttachmentApp.getFileList(type.toString(), id).get("attachments");
            if(attachments.size()>0){
                addListHeader(sb);
            }
            for(Map<String, String> attachment: attachments){
                sb.append(String.format("\n[%s](%s)", attachment.get("name"), YONA_SERVER + attachment.get("url")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb;
    }

    private static void addListHeader(@NotNull StringBuilder sb) {
        sb.append("\n\n--- attachments ---");
    }

    private static StringBuilder addAttachmentsStringUsingWikiCommit(@NotNull StringBuilder sb, ResourceType type, String id){
        try {
            List<Map<String, String>> attachments = AttachmentApp.getFileList(type.toString(), id).get("attachments");
            if(attachments.size()>0){
                addListHeader(sb);
            }
            for(Map<String, String> attachment: attachments){
                sb.append(String.format("\n[%s](../wiki/files/%s/%s)", attachment.get("name"), attachment.get("id"),
                        attachment.get("name").replaceAll("#", "%23")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb;
    }

    private static StringBuilder addAttachmentsStringWithLocalDir(@NotNull StringBuilder sb, ResourceType type, String id){
        try {
            List<Map<String, String>> attachments = AttachmentApp.getFileList(type.toString(), id).get("attachments");
            if(attachments.size()>0){
                addListHeader(sb);
            }
            for(Map<String, String> attachment: attachments){
                sb.append(String.format("\n[%s](./attachments/%s/%s)", attachment.get("name"), attachment.get("id"),
                        attachment.get("name").replaceAll("#", "%23")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb;
    }

    private static ObjectNode composePostJson(Posting posting) {
        String originalPostingLink = String.format("%s/%s/post/%s",
                YONA_SERVER + posting.project.owner, posting.project.name, posting.getNumber());

        ObjectNode node = Json.newObject();
        node.put("title", posting.title);

        // body 작성
        StringBuilder sb = new StringBuilder();

        if(usingWikiCommitForAttachment()){
            sb.append(addOriginalAuthorName(
                    relativeLinksToWikiCommitPath(posting.body), posting.authorLoginId,
                    posting.authorName, "게시글", originalPostingLink));
            sb = addAttachmentsStringUsingWikiCommit(sb, ResourceType.BOARD_POST, posting.id.toString());
        } else {
            sb.append(addOriginalAuthorName(
                    relativeLinksToAbsolutePath(posting.body), posting.authorLoginId, posting.authorName,
                    "게시글", originalPostingLink));
            sb = addAttachmentsString(sb, ResourceType.BOARD_POST, posting.id.toString());
        }
        node.put("body", sb.toString());
        node.put("created_at", LocalDateTime.ofInstant(posting.createdDate.toInstant(),
                ZoneId.systemDefault()).format(formatter));

        ObjectNode postingJson = Json.newObject();
        postingJson.put("issue", node);  // intentionally 'issue' key name is used for Github api compatibility
        postingJson.put("comments", toJson(composeCommentsJson(posting, originalPostingLink, ResourceType.NONISSUE_COMMENT)));
        return postingJson;
    }

    private static boolean usingWikiCommitForAttachment() {
        String withWikiCommit = request().getQueryString("withWikiCommit");
        boolean usingWikiCommit = StringUtils.isNotBlank(withWikiCommit) && withWikiCommit.endsWith("true");
        return usingWikiCommit;
    }

    private static ObjectNode composeIssueJson(Issue issue) {
        String originalIssueLink = String.format("%s/%s/issue/%s",
                YONA_SERVER + issue.project.owner, issue.project.name, issue.getNumber());

        ObjectNode node = Json.newObject();
        node.put("id", issue.id);
        node.put("title", issue.title);

        // body 작성
        StringBuilder sb = new StringBuilder();

        if(usingWikiCommitForAttachment()){
            sb.append(addOriginalAuthorName(
                    relativeLinksToWikiCommitPath(issue.body), issue.authorLoginId, issue.authorName, "이슈", originalIssueLink));
            sb = addAttachmentsStringUsingWikiCommit(sb, ResourceType.ISSUE_POST, issue.id.toString());
        } else {
            sb.append(addOriginalAuthorName(
                    relativeLinksToAbsolutePath(issue.body), issue.authorLoginId, issue.authorName, "이슈", originalIssueLink));
            sb = addAttachmentsString(sb, ResourceType.ISSUE_POST, issue.id.toString());
        }
        node.put("body", sb.toString());

        node.put("created_at", LocalDateTime.ofInstant(issue.createdDate.toInstant(),
                ZoneId.systemDefault()).format(formatter));
        Optional.ofNullable(issue.assignee).ifPresent(assignee -> node.put("assignee", assignee.user.loginId));
        Optional.ofNullable(issue.milestone).ifPresent(milestone -> node.put("milestone", milestone.title));
        Optional.ofNullable(issue.milestone).ifPresent(milestone -> node.put("milestoneId", milestone.id));

        node.put("closed", issue.isClosed());

        ObjectNode issueJson = Json.newObject();
        issueJson.put("issue", node);
        issueJson.put("comments", toJson(composeCommentsJson(issue, originalIssueLink, ResourceType.ISSUE_COMMENT)));
        return issueJson;
    }

    public static List<ObjectNode> composeCommentsJson(AbstractPosting posting, String orgLink, ResourceType type) {
        List<ObjectNode> comments = new ArrayList<>();
        for (Comment comment : posting.getComments()) {
            StringBuilder sb = new StringBuilder();
            ObjectNode commentNode = Json.newObject();
            commentNode.put("created_at", LocalDateTime.ofInstant(comment.createdDate.toInstant(),
                    ZoneId.systemDefault()).format(formatter));

            if(usingWikiCommitForAttachment()){
                sb.append(addOriginalAuthorName(
                        relativeLinksToWikiCommitPath(comment.contents), comment.authorLoginId, comment.authorName,
                        "코멘트", orgLink + "#comment-" + comment.id));
                sb = addAttachmentsStringUsingWikiCommit(sb, type, comment.id.toString());
            } else {
                sb.append(addOriginalAuthorName(
                        relativeLinksToAbsolutePath(comment.contents), comment.authorLoginId, comment.authorName,
                        "코멘트", orgLink + "#comment-" + comment.id));
                sb = addAttachmentsString(sb, type, comment.id.toString());
            }
            commentNode.put("body", sb.toString());
            comments.add(commentNode);
        }
        return comments;
    }

    public static List<ObjectNode> composePlainCommentsJson(AbstractPosting posting, ResourceType type) {
        List<ObjectNode> comments = new ArrayList<>();
        for (Comment comment : posting.getComments()) {
            StringBuilder sb = new StringBuilder();
            ObjectNode commentNode = Json.newObject();
            commentNode.put("created_at",comment.createdDate.getTime());
            sb = addAttachmentsStringWithLocalDir(sb, type, comment.id.toString());
            commentNode.put("authorId", comment.authorLoginId);
            commentNode.put("authorName", comment.authorName);
            commentNode.put("body", sb.toString());
            commentNode.put("attachments", toJson(Attachment.findByContainer(comment.asResource())));
            comments.add(commentNode);
        }
        return comments;
    }

    private static void gatheringUserProjects(Set<Project> targetProjects) {
        User worker = UserApp.currentUser();
        targetProjects.addAll(worker.projectUser.stream().
                filter(projectUser -> ProjectUser.isAllowedToSettings(worker.loginId, projectUser.project))
                .map(projectUser -> projectUser.project).collect(Collectors.toList()));
    }

    private static void getheringOrgProjects(Set<Project> targetProjects) {
        User worker = UserApp.currentUser();
        for (OrganizationUser organizationUser : OrganizationUser.findByAdmin(worker.id)) {
            targetProjects.addAll(organizationUser.organization.projects);
        }
    }
}
