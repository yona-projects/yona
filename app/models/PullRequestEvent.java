/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Wansoon Park
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
import models.enumeration.State;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import play.db.ebean.Model;
import utils.EventConstants;
import utils.JodaDateUtil;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Entity
public class PullRequestEvent extends Model implements TimelineItem {

    private static final long serialVersionUID = 1981361242582594128L;
    public static final Finder<Long, PullRequestEvent> finder = new Finder<>(Long.class, PullRequestEvent.class);

    @Id
    public Long id;
    public String senderLoginId;

    @ManyToOne
    public PullRequest pullRequest;

    @Enumerated(EnumType.STRING)
    public EventType eventType;

    public Date created;

    @Lob
    public String oldValue;
    @Lob
    public String newValue;

    @Override
    public Date getDate() {
        return created;
    }

    public static void addFromNotificationEvent(NotificationEvent notiEvent, PullRequest pullRequest) {
        PullRequestEvent event = new PullRequestEvent();
        event.created = notiEvent.created;
        event.senderLoginId = notiEvent.getSender().loginId;
        event.pullRequest = pullRequest;
        event.eventType = notiEvent.eventType;
        event.oldValue = notiEvent.getOldValue();
        event.newValue = notiEvent.newValue;

        add(event);
    }

    private static void add(PullRequestEvent event) {
        PullRequestEvent lastEvent = getLatestEventInDraftTime(event);
        if (needToDeleteEvent(lastEvent, event)) {
            lastEvent.delete();
        } else {
            event.save();
        }
    }

    private static PullRequestEvent getLatestEventInDraftTime(PullRequestEvent event) {
        Date draftDate = DateTime.now().minusMillis(EventConstants.DRAFT_TIME_IN_MILLIS).toDate();

        return PullRequestEvent.finder.where()
                .eq("pull_request_id", event.pullRequest.id)
                .gt("created", draftDate)
                .orderBy("created desc")
                .setMaxRows(1)
                .findUnique();
    }

    private static boolean needToDeleteEvent(PullRequestEvent lastEvent, PullRequestEvent currentEvent) {
        return lastEvent != null &&
                currentEvent.eventType == EventType.PULL_REQUEST_REVIEW_STATE_CHANGED &&
                lastEvent.eventType == EventType.PULL_REQUEST_REVIEW_STATE_CHANGED &&
                StringUtils.equals(currentEvent.senderLoginId, lastEvent.senderLoginId);
    }

    public static void addStateEvent(User sender, PullRequest pullRequest, State state) {
        PullRequestEvent event = new PullRequestEvent();
        event.created = JodaDateUtil.now();
        event.senderLoginId = sender.loginId;
        event.pullRequest = pullRequest;
        event.eventType = EventType.PULL_REQUEST_STATE_CHANGED;
        event.newValue = state.state();

        event.save();
    }

    public static void addMergeEvent(User sender, EventType eventType, State state, PullRequest pullRequest) {
        PullRequestEvent event = new PullRequestEvent();
        event.created = new Date();
        event.senderLoginId = sender.loginId;
        event.pullRequest = pullRequest;
        event.eventType = eventType;
        event.newValue = state.state();

        event.save();
    }

    public static void addCommitEvents(User sender, PullRequest pullRequest,
            List<PullRequestCommit> commits, String oldValue) {
        Date createdDate = new Date();
        PullRequestEvent event = new PullRequestEvent();
        event.created = createdDate;
        event.senderLoginId = sender.loginId;
        event.pullRequest = pullRequest;
        event.eventType = EventType.PULL_REQUEST_COMMIT_CHANGED;
        event.newValue = StringUtils.EMPTY;
        event.oldValue = oldValue;

        for (int i = 0; i < commits.size(); i++) {
            event.newValue += commits.get(i).id;
            if (i != commits.size() - 1) {
                event.newValue += PullRequest.DELIMETER;
            }
        }

        event.save();
    }

    public static List<PullRequestEvent> findByPullRequest(PullRequest pullRequest) {
        return finder.where().eq("pullRequest", pullRequest).findList();
    }

    @Transient
    public List<PullRequestCommit> getPullRequestCommits() {
        List<PullRequestCommit> commits = new ArrayList<>();

        String[] commitIds = this.newValue.split(PullRequest.DELIMETER);
        for (String commitId: commitIds) {
            commits.add(PullRequestCommit.findById(commitId));
        }

        Collections.sort(commits, TimelineItem.DESC);

        return commits;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }
}
