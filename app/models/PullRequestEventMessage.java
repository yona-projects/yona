/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Jungkook Kim
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

import models.enumeration.EventType;
import play.mvc.Http.Request;

public class PullRequestEventMessage {
    private User sender;
    private Request request;
    private Project project;
    private String branch;
    private PullRequest pullRequest;
    private EventType eventType;

    public PullRequestEventMessage(User sender, Request request, Project project, String branch) {
        this.sender = sender;
        this.request = request;
        this.project = project;
        this.branch = branch;
    }

    public PullRequestEventMessage(User sender, Request request, PullRequest pullRequest) {
        this.sender = sender;
        this.request = request;
        this.pullRequest = pullRequest;
    }

    public PullRequestEventMessage(User sender, Request request, PullRequest pullRequest, EventType eventType) {
        this(sender, request, pullRequest);
        this.eventType = eventType;
    }

    public User getSender() {
        return sender;
    }

    public Request getRequest() {
        return request;
    }

    public Project getProject() {
        return project;
    }

    public String getBranch() {
        return branch;
    }

    public PullRequest getPullRequest() {
        return pullRequest;
    }

    public boolean isNewPullRequest() {
        return eventType != null && eventType == EventType.NEW_PULL_REQUEST;
    }
}
