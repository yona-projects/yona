/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

package models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.eclipse.jgit.revwalk.RevCommit;
import org.apache.commons.lang3.StringUtils;

import models.enumeration.EventType;
import models.enumeration.PullRequestReviewAction;
import models.enumeration.ResourceType;
import models.enumeration.WebhookType;
import models.resource.GlobalResource;
import models.resource.Resource;
import models.resource.ResourceConvertible;

import utils.RouteUtil;

import play.Logger;
import play.api.i18n.Lang;
import play.data.validation.Constraints.Required;
import play.db.ebean.Model;
import play.i18n.Messages;
import play.libs.F.Function;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import play.Play;

import playRepository.GitCommit;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.validation.constraints.Size;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * A webhook to be sent by events in project
 */
@Entity
public class Webhook extends Model implements ResourceConvertible {

    private static final long serialVersionUID = 1L;
    public static final Finder<Long, Webhook> find = new Finder<>(Long.class, Webhook.class);

    /**
     * Primary Key.
     */
    @Id
    public Long id;

    /**
     * Project which have this webhook.
     */
    @ManyToOne
    public Project project;

    /**
     * Payload URL of webhook.
     */
    @Required
    @Size(max=2000, message="project.webhook.payloadUrl.tooLong")
    public String payloadUrl;

    /**
     * Secret token for server identity.
     */
    @Size(max=250, message="project.webhook.secret.tooLong")
    public String secret;

    /**
     * Condition of sending webhook (true: git only push, false: all cases)
     */
    public Boolean gitPushOnly;

    public WebhookType webhookType = WebhookType.SIMPLE;

    public Date createdAt;


    /**
     * Construct a webhook by the given {@code payloadUrl} and {@code secret}.
     *
     * @param projectId the ID of project which will have this webhook
     * @param payloadUrl the payload URL for this webhook
     * @param gitPushOnly type of webhook (true = git only push, false = all cases)
     * @param secret the secret token for server identity
     */
    public Webhook(Long projectId, String payloadUrl, String secret, Boolean gitPushOnly) {
        if (secret == null) {
            secret = "";
        }
        this.project = Project.find.byId(projectId);
        this.payloadUrl = payloadUrl;
        this.secret = secret;
        this.gitPushOnly = gitPushOnly;
        this.createdAt = new Date();
    }

    /**
     * Returns a {@link Resource} representation of this webhook.
     *
     * {@link utils.AccessControl}.may use this method to check if an user has
     * a permission to access this label.
     *
     * @return a {@link Resource} representation of this webhook
     */
    @Override
    public Resource asResource() {
        return new GlobalResource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public ResourceType getType() {
                return ResourceType.WEBHOOK;
            }
        };
    }
    
    public static List<Webhook> findByProject(Long projectId) {
        return find.where().eq("project.id", projectId).findList();
    }

    public static void create(Long projectId, String payloadUrl, String secret, Boolean gitPushOnly) {
        if (!payloadUrl.isEmpty()) {
            Webhook webhook = new Webhook(projectId, payloadUrl, secret, gitPushOnly);
            webhook.save();
        }
        // TODO : Raise appropriate error when required field is empty
    }

    public static void delete(Long webhookId, Long projectId) {
        Webhook.findByIds(webhookId, projectId).delete();
    }

    /**
     * Remove this webhook from a project.
     *
     * @param projectId ID of the project from which this webhook is removed
     */
    public void delete(Long projectId) {
        Project targetProject = Project.find.byId(projectId);
        targetProject.webhooks.remove(this);
        targetProject.update();
        super.delete();
    }

    public static Webhook findByIds(Long webhookId, Long projectId) {
        return find.where()
                .eq("webhook.id", webhookId)
                .eq("project.id", projectId)
                .findUnique();
    }

    public static Webhook findById(Long webhookId) {
        return find.where()
                .eq("id", webhookId)
                .findUnique();
    }

    private String getBaseUrl() {
        return utils.Config.getScheme() + "://" + utils.Config.getHostport("localhost:9000");
    }

    private String buildRequestMessage(String url, String message) {
        String requestMessage = " <" + getBaseUrl() + url + "|";
        if (this.webhookType == WebhookType.DETAIL_SLACK) {
            requestMessage += message.replace(">", "&gt;") + ">";
        } else {
            requestMessage += message + ">";
        }
        return requestMessage;
    }

    // Issue
    public void sendRequestToPayloadUrl(EventType eventType, User sender, Issue eventIssue) {
        String requestBodyString = "";
        String requestMessage = buildRequestBody(eventType, sender, eventIssue);

        if (this.webhookType == WebhookType.DETAIL_SLACK) {
            ArrayNode attachments = buildIssueDetails(eventIssue, eventType);
            requestBodyString = buildRequestJsonWithAttachments(requestMessage, attachments);
        } else if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            ObjectNode thread = buildThreadJSON(eventIssue.asResource());
            requestBodyString = buildRequestJsonWithThread(requestMessage, thread);
        } else {
            requestBodyString = buildTextPropertyOnlyJSON(requestMessage);
        }

        if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            sendRequest(requestBodyString, this.id, eventIssue.asResource());
        } else {
            sendRequest(requestBodyString);
        }
    }

    private String buildRequestBody(EventType eventType, User sender, Issue eventIssue) {
        String requestMessage = "[" + project.name + "] "+ sender.name + " ";

        switch (eventType) {
            case NEW_ISSUE:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.new.issue");
                break;
            case ISSUE_STATE_CHANGED:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.issue.state.changed");
                break;
            case ISSUE_ASSIGNEE_CHANGED:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.issue.assignee.changed");
                break;
            case ISSUE_BODY_CHANGED:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.issue.body.changed");
                break;
            case ISSUE_MILESTONE_CHANGED:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.milestone.changed");
                break;
            case RESOURCE_DELETED:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.issue.deleted");
                break;
            default:
                play.Logger.warn("Unknown webhook event: " + eventType);
        }

        String eventIssueUrl = controllers.routes.IssueApp.issue(eventIssue.project.owner, eventIssue.project.name, eventIssue.getNumber()).url();

        requestMessage += buildRequestMessage(eventIssueUrl, "#" + eventIssue.number + ": " + eventIssue.title);
        return requestMessage;
    }

    // Issue transfer
    public void sendRequestToPayloadUrl(EventType eventType, User sender, Issue eventIssue, Project previous) {
        String requestBodyString = "";
        String requestMessage = buildRequestBody(eventType, sender, eventIssue, previous);

        if (this.webhookType == WebhookType.DETAIL_SLACK) {
            ArrayNode attachments = buildIssueDetails(eventIssue, eventType);
            requestBodyString = buildRequestJsonWithAttachments(requestMessage, attachments);
        } else if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            ObjectNode thread = buildThreadJSON(eventIssue.asResource());
            requestBodyString = buildRequestJsonWithThread(requestMessage, thread);
        } else {
            requestBodyString = buildTextPropertyOnlyJSON(requestMessage);
        }

        if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            sendRequest(requestBodyString, this.id, eventIssue.asResource());
        } else {
            sendRequest(requestBodyString);
        }
    }

    private String buildRequestBody(EventType eventType, User sender, Issue eventIssue, Project previous) {
        String requestMessage = "[" + project.name + "] "+ sender.name + " ";

        requestMessage += Messages.get(Lang.defaultLang(), "notification.type.issue.moved", previous.name, project.name);

        String eventIssueUrl = controllers.routes.IssueApp.issue(eventIssue.project.owner, eventIssue.project.name, eventIssue.getNumber()).url();

        requestMessage += buildRequestMessage(eventIssueUrl, "#" + eventIssue.number + ": " + eventIssue.title);
        return requestMessage;
    }

    // Issue Detail (Slack)
    private ArrayNode buildIssueDetails(Issue eventIssue, EventType eventType) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode attachments = mapper.createArrayNode();
        ArrayNode detailFields = mapper.createArrayNode();

        if (eventIssue.milestone != null) {
            detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "notification.type.milestone.changed"), eventIssue.milestone.title, true));
        }
        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), ""), eventIssue.assigneeName(), true));
        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "issue.state"), eventIssue.state.toString(), true));

        attachments.add(buildAttachmentJSON(eventIssue.body, detailFields, eventType));

        return attachments;
    }

    // Posting
    public void sendRequestToPayloadUrl(EventType eventType, User sender, Posting eventPost) {
        String requestBodyString = "";
        String requestMessage = buildRequestBody(eventType, sender, eventPost);

        if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            ObjectNode thread = buildThreadJSON(eventPost.asResource());
            requestBodyString = buildRequestJsonWithThread(requestMessage, thread);
        } else {
            requestBodyString = buildTextPropertyOnlyJSON(requestMessage);
        }

        if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            sendRequest(requestBodyString, this.id, eventPost.asResource());
        } else {
            sendRequest(requestBodyString);
        }
    }

    private String buildRequestBody(EventType eventType, User sender, Posting eventPost) {
        StringBuilder requestMessage = new StringBuilder();
        requestMessage.append(String.format("[%s] %s ", project.name, sender.name));

        switch (eventType) {
            case NEW_POSTING:
                requestMessage.append(Messages.get(Lang.defaultLang(), "notification.type.new.posting"));
                break;
            default:
                play.Logger.warn("Unknown webhook event: " + eventType);
        }

        String eventPostUrl = RouteUtil.getUrl(eventPost);
        requestMessage.append(buildRequestMessage(eventPostUrl, "#" + eventPost.number + ": " + eventPost.title));
        return requestMessage.toString();
    }


    // Comment
    public void sendRequestToPayloadUrl(EventType eventType, User sender, Comment eventComment) {
        String requestBodyString = "";
        String requestMessage = buildRequestBody(eventType, sender, eventComment);

        if (this.webhookType == WebhookType.DETAIL_SLACK) {
            ArrayNode attachments = buildCommentDetails(eventComment, eventType);
            requestBodyString = buildRequestJsonWithAttachments(requestMessage, attachments);
        } else if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            ObjectNode thread = buildThreadJSON(eventComment.getParent().asResource());
            requestBodyString = buildRequestJsonWithThread(requestMessage, thread);
        } else {
            requestBodyString = buildTextPropertyOnlyJSON(requestMessage);
        }

        if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            sendRequest(requestBodyString, this.id, eventComment.getParent().asResource());
        } else {
            sendRequest(requestBodyString);
        }
    }

    private String buildRequestBody(EventType eventType, User sender, Comment eventComment) {
        String requestMessage = "[" + project.name + "] "+ sender.name + " ";

        switch (eventType) {
            case NEW_COMMENT:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.new.comment");
                break;
            case COMMENT_UPDATED:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.comment.updated");
                break;
        }

        requestMessage += buildRequestMessage(RouteUtil.getUrl(eventComment), "#" + eventComment.getParent().number + ": " + eventComment.getParent().title);
        return requestMessage;
    }

    // Comment Detail (Slack)
    private ArrayNode buildCommentDetails(Comment eventComment, EventType eventType) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode attachments = mapper.createArrayNode();

        attachments.add(buildAttachmentJSON(eventComment.contents, null, eventType));

        return attachments;
    }

    // Pull Request
    public void sendRequestToPayloadUrl(EventType eventType, User sender, PullRequest eventPullRequest) {
        String requestBodyString = "";
        String requestMessage = buildRequestBody(eventType, sender, eventPullRequest);

        if (this.webhookType == WebhookType.DETAIL_SLACK) {
            ArrayNode attachments = buildJsonWithPullReqtuestDetails(eventPullRequest, requestMessage, eventType);
            requestBodyString = buildRequestJsonWithAttachments(requestMessage, attachments);
        } else if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            ObjectNode thread = buildThreadJSON(eventPullRequest.asResource());
            requestBodyString = buildRequestJsonWithThread(requestMessage, thread);
        } else {
            requestBodyString = buildTextPropertyOnlyJSON(requestMessage);
        }

        if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            sendRequest(requestBodyString, this.id, eventPullRequest.asResource());
        } else {
            sendRequest(requestBodyString);
        }
    }

    private String buildRequestBody(EventType eventType, User sender, PullRequest eventPullRequest) {
        String requestMessage = "[" + project.name + "] "+ sender.name + " ";

        switch (eventType) {
            case NEW_PULL_REQUEST:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.new.pullrequest");
                break;
            case PULL_REQUEST_STATE_CHANGED:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.pullrequest.state.changed");
                break;
            case PULL_REQUEST_MERGED:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.pullrequest.merged");
                break;
            case PULL_REQUEST_COMMIT_CHANGED:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.pullrequest.commit.changed");
                break;
        }

        requestMessage += buildRequestMessage(RouteUtil.getUrl(eventPullRequest), "#" + eventPullRequest.number + ": " + eventPullRequest.title);
        return requestMessage;
    }

    // Pull Request Review
    public void sendRequestToPayloadUrl(EventType eventType, User sender, PullRequest eventPullRequest, PullRequestReviewAction reviewAction) {
        String requestBodyString = "";
        String requestMessage = buildRequestBody(eventType, sender, eventPullRequest, reviewAction);

        if (this.webhookType == WebhookType.DETAIL_SLACK) {
            ArrayNode attachments = buildJsonWithPullReqtuestDetails(eventPullRequest, requestMessage, eventType);
            requestBodyString = buildRequestJsonWithAttachments(requestMessage, attachments);
        } else if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            ObjectNode thread = buildThreadJSON(eventPullRequest.asResource());
            requestBodyString = buildRequestJsonWithThread(requestMessage, thread);
        } else {
            requestBodyString = buildTextPropertyOnlyJSON(requestMessage);
        }

        if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            sendRequest(requestBodyString, this.id, eventPullRequest.asResource());
        } else {
            sendRequest(requestBodyString);
        }
    }

    private String buildRequestBody(EventType eventType, User sender, PullRequest eventPullRequest, PullRequestReviewAction reviewAction) {
        String requestMessage = "[" + project.name + "] ";

        switch (eventType) {
            case PULL_REQUEST_REVIEW_STATE_CHANGED:
                if (PullRequestReviewAction.DONE.equals(reviewAction)) {
                    requestMessage += Messages.get(Lang.defaultLang(), "notification.pullrequest.reviewed", sender.name);
                } else {
                    requestMessage += Messages.get(Lang.defaultLang(), "notification.pullrequest.unreviewed", sender.name);;
                }
                break;
        }

        requestMessage += buildRequestMessage(RouteUtil.getUrl(eventPullRequest), "#" + eventPullRequest.number + ": " + eventPullRequest.title);
        return requestMessage;
    }

    // Pull Request Comment
    public void sendRequestToPayloadUrl(EventType eventType, User sender, PullRequest eventPullRequest, ReviewComment reviewComment) {
        String requestBodyString = "";
        String requestMessage = buildRequestBody(eventType, sender, eventPullRequest, reviewComment);

        if (this.webhookType == WebhookType.DETAIL_SLACK) {
            ArrayNode attachments = buildJsonWithPullReqtuestDetails(eventPullRequest, requestMessage, eventType);
            requestBodyString = buildRequestJsonWithAttachments(requestMessage, attachments);
        } else if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            ObjectNode thread = buildThreadJSON(eventPullRequest.asResource());
            requestBodyString = buildRequestJsonWithThread(requestMessage, thread);
        } else {
            requestBodyString = buildTextPropertyOnlyJSON(requestMessage);
        }

        if (this.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) {
            sendRequest(requestBodyString, this.id, eventPullRequest.asResource());
        } else {
            sendRequest(requestBodyString);
        }
    }

    private String buildRequestBody(EventType eventType, User sender, PullRequest eventPullRequest, ReviewComment reviewComment) {
        String requestMessage = "[" + project.name + "] " + sender.name + " ";

        requestMessage += Messages.get(Lang.defaultLang(), "notification.type.new.simple.comment");

        requestMessage += " <" + utils.Config.getScheme() + "://" + utils.Config.getHostport("localhost:9000") + RouteUtil.getUrl(reviewComment) + "|#" + eventPullRequest.number + ": " + eventPullRequest.title + ">";
        return requestMessage;
    }

    // Pull Request Detail (Slack)
    private ArrayNode buildJsonWithPullReqtuestDetails(PullRequest eventPullRequest, String requestMessage, EventType eventType) {
        ObjectMapper mapper = new ObjectMapper();

        ArrayNode detailFields = mapper.createArrayNode();
        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "pullRequest.sender"), eventPullRequest.contributor.name, false));
        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "pullRequest.from"), eventPullRequest.fromBranch, true));
        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "pullRequest.to"), eventPullRequest.toBranch, true));

        ArrayNode attachments = mapper.createArrayNode();
        attachments.add(buildAttachmentJSON(eventPullRequest.body, detailFields, eventType));

        return attachments;
    }

    private String buildTextPropertyOnlyJSON(String requestMessage) {
        ObjectNode requestBody = Json.newObject();
        requestBody.put("text", requestMessage);
        return Json.stringify(requestBody);
    }

    private String buildRequestJsonWithAttachments(String requestMessage, ArrayNode attachments) {
        ObjectNode requestBody = Json.newObject();
        requestBody.put("text", requestMessage);
        requestBody.put("attachments", attachments);
        return Json.stringify(requestBody);
    }

    private String buildRequestJsonWithThread(String requestMessage, ObjectNode thread) {
        ObjectNode requestBody = Json.newObject();
        requestBody.put("text", requestMessage);
        requestBody.put("thread", thread);
        return Json.stringify(requestBody);
    }

    private ObjectNode buildTitleValueJSON(String title, String value, Boolean shorten) {
        ObjectNode titleJSON = Json.newObject();
        titleJSON.put("title", title);
        titleJSON.put("value", value);
        titleJSON.put("short", shorten);
        return titleJSON;
    }

    private ObjectNode buildAttachmentJSON(String text, ArrayNode detailFields, EventType eventType) {
        ObjectNode attachmentsJSON = Json.newObject();
        attachmentsJSON.put("text", text);
        attachmentsJSON.put("fields", detailFields);
        String color = Play.application().configuration().getString("slack." + eventType, "");
        attachmentsJSON.put("color", color);
        return attachmentsJSON;
    }

    private ObjectNode buildSenderJSON(User sender) {
        ObjectNode senderJSON = Json.newObject();
        senderJSON.put("login", sender.loginId);
        senderJSON.put("id", sender.id);
        senderJSON.put("avatar_url", sender.avatarUrl());
        senderJSON.put("type", "User");
        senderJSON.put("site_admin", sender.isSiteManager());
        return senderJSON;
    }

    private ObjectNode buildPusherJSON(User sender) {
        ObjectNode pusherJSON = Json.newObject();
        pusherJSON.put("name", sender.name);
        pusherJSON.put("email", sender.email);
        return pusherJSON;
    }

    private ObjectNode buildRepositoryJSON() {
        ObjectNode repositoryJSON = Json.newObject();
        repositoryJSON.put("id", project.id);
        repositoryJSON.put("name", project.name);
        repositoryJSON.put("owner", project.owner);
        repositoryJSON.put("html_url", RouteUtil.getUrl(project));
        repositoryJSON.put("overview", project.overview);   // Description.
        repositoryJSON.put("private", project.isPrivate());
        return repositoryJSON;
    }

    private ObjectNode buildThreadJSON(Resource resource) {
        ObjectNode threadJSON = Json.newObject();
        WebhookThread webhookthread = WebhookThread.getWebhookThread(this.id, resource);
        if (webhookthread != null) {
            threadJSON.put("name", webhookthread.threadId);
        }
        return threadJSON;
    }

    private void sendRequest(String payload) {
        play.Logger.info(payload);
        try {
            WSRequestHolder requestHolder = WS.url(this.payloadUrl);
            if (StringUtils.isNotBlank(this.secret)) {
                requestHolder.setHeader("Authorization", "token " + this.secret);
            }
            requestHolder
                    .setHeader("Content-Type", "application/json")
                    .setHeader("User-Agent", "Yobi-Hookshot")
                    .post(payload)
                    .map(
                            new Function<WSResponse, Integer>() {
                                public Integer apply(WSResponse response) {
                                    int statusCode = response.getStatus();
                                    String statusText = response.getStatusText();
                                    if (statusCode < 200 || statusCode >= 300) {
                                        // Unsuccessful status code - log some information in server.
                                        Logger.info("[Webhook] Request responded code " + Integer.toString(statusCode) + ": " + statusText);
                                        Logger.info("[Webhook] Request payload: " + payload);
                                    }
                                    return 0;
                                }
                            }
                    );
        } catch (Exception e) {
            // Request failed (Dead end point or invalid payload URL) - log some information in server.
            Logger.info("[Webhook] Request failed at given payload URL: " + this.payloadUrl);
        }
    }

    private void sendRequest(String payload, Long webhookId, Resource resource) {
        play.Logger.info(payload);
        try {
            WSRequestHolder requestHolder = WS.url(this.payloadUrl);
            if (StringUtils.isNotBlank(this.secret)) {
                requestHolder.setHeader("Authorization", "token " + this.secret);
            }
            requestHolder
                    .setHeader("Content-Type", "application/json")
                    .setHeader("User-Agent", "Yobi-Hookshot")
                    .post(payload)
                    .map(
                            new Function<WSResponse, Integer>() {
                                public Integer apply(WSResponse response) {
                                    int statusCode = response.getStatus();
                                    String statusText = response.getStatusText();
                                    if (statusCode < 200 || statusCode >= 300) {
                                        // Unsuccessful status code - log some information in server.
                                        Logger.info("[Webhook] Request responded code " + Integer.toString(statusCode) + ": " + statusText);
                                        Logger.info("[Webhook] Request payload: " + payload);
                                    } else {
                                        WebhookThread webhookthread = WebhookThread.getWebhookThread(webhookId, resource);
                                        if (webhookthread == null) {
                                            String threadId = response.asJson().findPath("thread").findPath("name").asText();
                                            webhookthread = WebhookThread.create(webhookId, resource, threadId);
                                        }
                                    }
                                    return 0;
                                }
                            }
                    );
        } catch (Exception e) {
            // Request failed (Dead end point or invalid payload URL) - log some information in server.
            Logger.info("[Webhook] Request failed at given payload URL: " + this.payloadUrl);
        }
    }

    // Commit
    public void sendRequestToPayloadUrl(List<RevCommit> commits, List<String> refNames, User sender, String title) {
        String requestBodyString = buildRequestBody(commits, refNames, sender, title);
        sendRequest(requestBodyString);
    }

    private String buildRequestBody(List<RevCommit> commits, List<String> refNames, User sender, String title) {
        ObjectNode requestBody = Json.newObject();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode refNamesNodes = mapper.createArrayNode();
        ArrayNode commitsNodes = mapper.createArrayNode();

        for (String refName : refNames) {
            refNamesNodes.add(refName);
        }

        requestBody.put("ref", refNamesNodes);

        for (RevCommit commit : commits) {
            commitsNodes.add(buildJSONFromCommit(project, commit));
        }

        requestBody.put("commits", commitsNodes);
        requestBody.put("head_commit", commitsNodes.get(0));
        requestBody.put("sender", buildSenderJSON(sender));
        requestBody.put("pusher", buildPusherJSON(sender));
        requestBody.put("repository", buildRepositoryJSON());

        return Json.stringify(requestBody);
    }

    private ObjectNode buildJSONFromCommit(Project project, RevCommit commit) {
        GitCommit gitCommit = new GitCommit(commit);
        ObjectNode commitJSON = Json.newObject();
        ObjectNode authorJSON = Json.newObject();
        ObjectNode committerJSON = Json.newObject();

        commitJSON.put("id", gitCommit.getFullId());
        commitJSON.put("message", gitCommit.getMessage());
        commitJSON.put("timestamp",
                new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").format(new Date(gitCommit.getCommitTime() * 1000L)));
        commitJSON.put("url", getBaseUrl() + RouteUtil.getUrl(project) + "/commit/"+gitCommit.getFullId());

        authorJSON.put("name", gitCommit.getAuthorName());
        authorJSON.put("email", gitCommit.getAuthorEmail());
        committerJSON.put("name", gitCommit.getCommitterName());
        committerJSON.put("email", gitCommit.getCommitterEmail());
        // TODO : Add 'username' property (howto?)

        commitJSON.put("author", authorJSON);
        commitJSON.put("committer", committerJSON);

        // TODO : Add added, removed, modified file list (not supported by JGit?)

        return commitJSON;
    }

    @Override
    public String toString() {
        return "Webhook{" +
                "id=" + id +
                ", project=" + project +
                ", payloadUrl='" + payloadUrl + '\'' +
                ", secret='" + secret + '\'' +
                ", gitPushOnly=" + gitPushOnly +
                ", webhookType=" + webhookType +
                ", createdAt=" + createdAt +
                '}';
    }
}
