/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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
package models.enumeration;

import play.i18n.Messages;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public enum EventType {

    NEW_ISSUE("notification.type.new.issue", 1),
    NEW_POSTING("notification.type.new.posting", 2),
    NEW_PULL_REQUEST("notification.type.new.pullrequest", 3),
    ISSUE_STATE_CHANGED("notification.type.issue.state.changed", 4),
    ISSUE_ASSIGNEE_CHANGED("notification.type.issue.assignee.changed", 5),
    PULL_REQUEST_STATE_CHANGED("notification.type.pullrequest.state.changed", 6),
    NEW_COMMENT("notification.type.new.comment", 7),
    NEW_REVIEW_COMMENT("notification.type.new.simple.comment", 8),
    MEMBER_ENROLL_REQUEST("notification.type.member.enroll", 9),
    PULL_REQUEST_MERGED("notification.type.pullrequest.merged", 10),
    ISSUE_REFERRED_FROM_COMMIT("notification.type.issue.referred.from.commit", 11),
    PULL_REQUEST_COMMIT_CHANGED("notification.type.pullrequest.commit.changed", 12),
    NEW_COMMIT("notification.type.new.commit", 13),
    PULL_REQUEST_REVIEW_STATE_CHANGED("notification.type.pullrequest.review.action.changed",14),
    ISSUE_BODY_CHANGED("notification.type.issue.body.changed", 17),
    ISSUE_REFERRED_FROM_PULL_REQUEST("notification.type.issue.referred.from.pullrequest", 16),
    REVIEW_THREAD_STATE_CHANGED("notification.type.review.state.changed", 18),
    ORGANIZATION_MEMBER_ENROLL_REQUEST("notification.organization.type.member.enroll",19),
    COMMENT_UPDATED("notification.type.comment.updated", 20);

    private String descr;

    private int order;

    private String messageKey;

    EventType(String messageKey, int order) {
        this.messageKey = messageKey;
        this.order = order;
    }

    public String getDescr() {
        return Messages.get(messageKey);
    }

    public int getOrder() {
        return order;
    }

    public static final List<EventType> notiTypes;

    static {
        notiTypes = Arrays.asList(EventType.values());
        Collections.sort(notiTypes, new Comparator<EventType>() {
            @Override
            public int compare(EventType o1, EventType o2) {
                return o1.getOrder() - o2.getOrder();
            }
        });
    }

    public boolean isCreating() {
        switch(this) {
            case NEW_ISSUE:
            case NEW_POSTING:
            case NEW_PULL_REQUEST:
            case NEW_COMMENT:
            case NEW_REVIEW_COMMENT:
            // We consider "NEW_COMMIT" as "UPDATE" because it updates a project.
                return true;
            default:
                return false;
        }
    }
}
