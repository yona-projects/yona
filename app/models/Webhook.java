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
import models.enumeration.ResourceType;
import models.resource.GlobalResource;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.eclipse.jgit.revwalk.RevCommit;
import play.Logger;
import play.data.validation.Constraints.Required;
import play.db.ebean.Model;
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
     * Payload URL of webhook.
     */
    public Date createdAt;

    /**
     * Construct a webhook by the given {@code payloadUrl} and {@code secret}.
     *
     * @param projectId the ID of project which will have this webhook
     * @param payloadUrl the payload URL for this webhook
     * @param secret the secret token for server identity
     */
    public Webhook(Long projectId, String payloadUrl, String secret) {
        if (secret == null) {
            secret = "";
        }
        this.project = Project.find.byId(projectId);
        this.payloadUrl = payloadUrl;
        this.secret = secret;
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

    public static void create(Long projectId, String payloadUrl, String secret) {
        if (!payloadUrl.isEmpty()) {
            Webhook webhook = new Webhook(projectId, payloadUrl, secret);
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
    
    public void sendRequestToPayloadUrl(String[] eventTypes, List<RevCommit> commits, List<String> refNames, User sender, String title) {
        String requestBodyString = buildRequestBody(eventTypes, commits, refNames, sender, title);

        try {
            WSRequestHolder requestHolder = WS.url(this.payloadUrl);
            requestHolder
                    .setHeader("Content-Type", "application/json")
                    .setHeader("User-Agent", "Yobi-Hookshot")
                    .post(requestBodyString)
                    .map(
                            new Function<WSResponse, Integer>() {
                                public Integer apply(WSResponse response) {
                                    int statusCode = response.getStatus();
                                    String statusText = response.getStatusText();
                                    if (statusCode < 200 || statusCode >= 300) {
                                        // Unsuccessful status code - log some information in server.
                                        Logger.info("[Webhook] Request responded code " + Integer.toString(statusCode) + ": " + statusText);
                                        Logger.info("[Webhook] Request payload: " + requestBodyString);
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

    private String buildRequestBody(String[] eventTypes, List<RevCommit> commits, List<String> refNames, User sender, String title) {
        ObjectNode requestBody = Json.newObject();
        ObjectNode senderJSON = Json.newObject();
        ObjectNode pusherJSON = Json.newObject();
        ObjectNode repositoryJSON = Json.newObject();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode eventTypesArray = mapper.createArrayNode();
        ArrayNode refNamesArray = mapper.createArrayNode();
        ArrayNode commitsArray = mapper.createArrayNode();

        for (String refName : refNames) {
            refNamesArray.add(refName);
        }
        requestBody.put("ref", refNamesArray);

        for (RevCommit commit : commits) {
            commitsArray.add(buildJSONFromCommit(project, commit));
        }
        requestBody.put("commits", commitsArray);
        requestBody.put("head_commit", commitsArray.get(0));

        senderJSON.put("login", sender.loginId);
        senderJSON.put("id", sender.id);
        senderJSON.put("avatar_url", sender.avatarUrl());
        senderJSON.put("type", "User");
        senderJSON.put("site_admin", sender.isSiteManager());
        requestBody.put("sender", senderJSON);

        pusherJSON.put("name", sender.name);
        pusherJSON.put("email", sender.email);
        requestBody.put("pusher", pusherJSON);

        repositoryJSON.put("id", project.id);
        repositoryJSON.put("name", project.name);
        repositoryJSON.put("owner", project.owner);
        repositoryJSON.put("html_url", RouteUtil.getUrl(project));
        repositoryJSON.put("overview", project.overview);   // Description.
        repositoryJSON.put("private", project.isPrivate());
        requestBody.put("repository", repositoryJSON);

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
