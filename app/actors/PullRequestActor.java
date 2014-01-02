/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park
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
package actors;

import akka.actor.UntypedActor;
import models.NotificationEvent;
import models.PullRequest;
import models.PullRequestEvent;
import models.PullRequestEventMessage;
import models.PullRequestMergeResult;
import models.enumeration.EventType;
import models.enumeration.State;

/**
 * @author Wansoon Park
 *
 */
public abstract class PullRequestActor extends UntypedActor {
    /**
     * PullRequest 병합을 시도하고 병합결과를 저장한다.
     *
     * Diff커밋중 신규커밋이 있을경우 커밋이벤트를 등록한다.
     * Diff커밋이 없을경우 PullRequest 상태를 병합으로 변경하고 알림과 이벤트를 등록한다.
     * 병합결과가 충돌일 경우 알림과 이벤트를 등록한다.
     * 병합결과가 충돌해결일 경우 알림과 이벤트를 등록한다.
     *
     * @param message
     * @param pullRequest
     */
    protected void processPullRequestMerging(PullRequestEventMessage message, PullRequest pullRequest) {
        try {
            PullRequestMergeResult mergeResult = pullRequest.attemptMerge();

            if (mergeResult.hasDiffCommits()) {
                mergeResult.saveCommits();
                if (!mergeResult.getNewCommits().isEmpty()) {
                    PullRequestEvent.addCommitEvents(message.getSender(), pullRequest, mergeResult.getNewCommits());
                    pullRequest.clearReviewers();
                }
            } else {
                mergeResult.setMergedStateOfPullRequest(message.getSender());
                NotificationEvent notiEvent = NotificationEvent.addPullRequestUpdate(message.getSender(),
                        pullRequest, pullRequest.state, State.MERGED);
                PullRequestEvent.addEvent(notiEvent, pullRequest);
            }

            if (mergeResult.conflicts()) {
                mergeResult.setConflictStateOfPullRequest();
                NotificationEvent notiEvent = NotificationEvent.addPullRequestMerge(message.getSender(),
                        pullRequest, mergeResult.getGitConflicts(), message.getRequest(), State.CONFLICT);
                PullRequestEvent.addMergeEvent(notiEvent.getSender(), EventType.PULL_REQUEST_MERGED, State.CONFLICT, pullRequest);
            }

            if (mergeResult.resolved()) {
                mergeResult.setResolvedStateOfPullRequest();
                NotificationEvent notiEvent = NotificationEvent.addPullRequestMerge(message.getSender(),
                        pullRequest, mergeResult.getGitConflicts(), message.getRequest(), State.RESOLVED);
                PullRequestEvent.addMergeEvent(notiEvent.getSender(), EventType.PULL_REQUEST_MERGED, State.RESOLVED, pullRequest);
            }

            mergeResult.save();
        } catch (Exception e) {
            play.Logger.error("Failed to check merging from " + pullRequest, e);
        }
    }
}
