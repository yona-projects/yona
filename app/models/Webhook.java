/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2015 NAVER Corp.
 * http://yobi.io
 *
 * @author Jihwan Chun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.enumeration.EventType;
import models.enumeration.PullRequestReviewAction;
import models.enumeration.ResourceType;
import models.resource.GlobalResource;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.eclipse.jgit.revwalk.RevCommit;
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
import playRepository.GitCommit;
import utils.RouteUtil;

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
     * Type of webhook (true = git only push, false = all cases)
     */
    public Boolean gitPushOnly;

    /**
     * Payload URL of webhook.
     */
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

    public static Webhook findByIds(Long webhookId, Long projectId) {
        return find.where()
                .eq("webhook.id", webhookId)
                .eq("project.id", projectId)
                .findUnique();
    }

    private void sendRequest(String payload) {
        try {
            WSRequestHolder requestHolder = WS.url(this.payloadUrl);
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
    
    public void sendRequestToPayloadUrl(List<RevCommit> commits, List<String> refNames, User sender, String title) {
        String requestBodyString = buildRequestBody(commits, refNames, sender, title);
        sendRequest(requestBodyString);
    }

    public void sendRequestToPayloadUrl(EventType eventType, User sender, Issue eventIssue) {
        String requestBodyString = buildRequestBody(eventType, sender, eventIssue);
        sendRequest(requestBodyString);
    }

    public void sendRequestToPayloadUrl(EventType eventType, User sender, PullRequest eventPullRequest) {
        String requestBodyString = buildRequestBody(eventType, sender, eventPullRequest);
        sendRequest(requestBodyString);
    }

    public void sendRequestToPayloadUrl(EventType eventType, User sender, PullRequest eventPullRequest, PullRequestReviewAction reviewAction) {
        String requestBodyString = buildRequestBody(eventType, sender, eventPullRequest, reviewAction);
        sendRequest(requestBodyString);
    }

    public void sendRequestToPayloadUrl(EventType eventType, User sender, Comment eventComment) {
        String requestBodyString = buildRequestBody(eventType, sender, eventComment);
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

    private String buildRequestBody(EventType eventType, User sender, PullRequest eventPullRequest) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode detailFields = mapper.createArrayNode();
        ArrayNode attachments = mapper.createArrayNode();

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
        requestMessage += " <" + project.siteurl + "/pullRequest/" + eventPullRequest.number + "|#" + eventPullRequest.number + ": " + eventPullRequest.title + ">";

        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "pullRequest.sender"), eventPullRequest.contributor.name, false));
        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "pullRequest.from"), eventPullRequest.fromBranch, true));
        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "pullRequest.to"), eventPullRequest.toBranch,true));
        attachments.add(buildAttachmentJSON(eventPullRequest.body, detailFields));

        return Json.stringify(buildRequestJSON(requestMessage, attachments));
    }

    private String buildRequestBody(EventType eventType, User sender, PullRequest eventPullRequest, PullRequestReviewAction reviewAction) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode detailFields = mapper.createArrayNode();
        ArrayNode attachments = mapper.createArrayNode();

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
        requestMessage += " <" + project.siteurl + "/pullRequest/" + eventPullRequest.number + "|#" + eventPullRequest.number + ": " + eventPullRequest.title + ">";

        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "pullRequest.sender"), eventPullRequest.contributor.name, false));
        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "pullRequest.from"), eventPullRequest.fromBranch, true));
        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "pullRequest.to"), eventPullRequest.toBranch, true));
        attachments.add(buildAttachmentJSON(eventPullRequest.body, detailFields));

        return Json.stringify(buildRequestJSON(requestMessage, attachments));
    }

    private String buildRequestBody(EventType eventType, User sender, Issue eventIssue) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode detailFields = mapper.createArrayNode();
        ArrayNode attachments = mapper.createArrayNode();

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
            case ISSUE_MOVED:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.issue.moved");
                break;
        }
        requestMessage += " <" + project.siteurl + "/issue/" + eventIssue.number + "|#" + eventIssue.number + ": " + eventIssue.title + ">";

        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "issue.assignee"), eventIssue.assigneeName(), true));
        detailFields.add(buildTitleValueJSON(Messages.get(Lang.defaultLang(), "issue.state"), eventIssue.state.toString(), true));
        attachments.add(buildAttachmentJSON(eventIssue.body, detailFields));

        return Json.stringify(buildRequestJSON(requestMessage, attachments));
    }

    private String buildRequestBody(EventType eventType, User sender, Comment eventComment) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode attachments = mapper.createArrayNode();

        String requestMessage = "[" + project.name + "] "+ sender.name + " ";
        switch (eventType) {
            case NEW_COMMENT:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.new.comment");
                break;
            case COMMENT_UPDATED:
                requestMessage += Messages.get(Lang.defaultLang(), "notification.type.comment.updated");
                break;
        }
        switch (eventComment.asResource().getType()) {
            case ISSUE_COMMENT:
                requestMessage += " <" + project.siteurl + "/issue/" + eventComment.getParent().number + "#comment-" + eventComment.id + "|#" + eventComment.getParent().number + ": " + eventComment.getParent().title + ">";
                break;
            case NONISSUE_COMMENT:
                requestMessage += " <" + project.siteurl + "/post/" + eventComment.getParent().number + "#comment-" + eventComment.id + "|#" + eventComment.getParent().number + ": " + eventComment.getParent().title + ">";
                break;
        }

        attachments.add(buildAttachmentJSON(eventComment.contents, null));

        return Json.stringify(buildRequestJSON(requestMessage, attachments));
    }

    private ObjectNode buildJSONFromCommit(Project project, RevCommit commit) {
        GitCommit gitCommit = new GitCommit(commit);
        ObjectNode commitJSON = Json.newObject();
        ObjectNode authorJSON = Json.newObject();
        ObjectNode committerJSON = Json.newObject();

        commitJSON.put("id", gitCommit.getFullId());
        commitJSON.put("message", gitCommit.getMessage());
        commitJSON.put("timestamp",
                new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").
                        format(new Date(gitCommit.getCommitTime() * 1000L)));
        commitJSON.put("url", project.siteurl+"/commit/"+gitCommit.getFullId());

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

    private ObjectNode buildTitleValueJSON(String title, String value, Boolean shorten) {
        ObjectNode outputJSON = Json.newObject();
        outputJSON.put("title", title);
        outputJSON.put("value", value);
        outputJSON.put("short", shorten);
        return outputJSON;
    }

    private ObjectNode buildAttachmentJSON(String text, ArrayNode detailFields) {
        ObjectNode attachmentsJSON = Json.newObject();
        attachmentsJSON.put("text", text);
        attachmentsJSON.put("fields", detailFields);
        return attachmentsJSON;
    }

    private ObjectNode buildRequestJSON(String requestMessage, ArrayNode attachments) {
        ObjectNode requestBody = Json.newObject();
        requestBody.put("text", requestMessage);
        requestBody.put("username", "YonaBot");
        requestBody.put("attachments", attachments);
        return requestBody;
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
}
