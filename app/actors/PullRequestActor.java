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
package actors;

import akka.actor.UntypedActor;
import models.*;
import models.enumeration.EventType;
import models.enumeration.State;

import java.util.List;

public abstract class PullRequestActor extends UntypedActor {

    protected void processPullRequestMerging(PullRequestEventMessage message, PullRequest pullRequest) {
        try {
            String oldMergeCommitId = pullRequest.mergedCommitIdTo;
            boolean wasConflict = pullRequest.isConflict != null ? pullRequest.isConflict : false;

            PullRequestMergeResult mergeResult = pullRequest.updateMerge();

            if (mergeResult.hasDiffCommits()) {
                mergeResult.saveCommits();
                if (!mergeResult.getNewCommits().isEmpty()) {
                    if (!message.isNewPullRequest()) {
                        NotificationEvent.afterPullRequestCommitChanged(message.getSender(), pullRequest);
                    }
                    PullRequestEvent.addCommitEvents(message.getSender(), pullRequest,
                            mergeResult.getNewCommits(),
                            getCommitEventOldValue(oldMergeCommitId, pullRequest.mergedCommitIdTo));
                    pullRequest.clearReviewers();
                }
            } else {
                mergeResult.setMergedStateOfPullRequest(message.getSender());
                NotificationEvent notiEvent = NotificationEvent.afterPullRequestUpdated(message.getSender(),
                        pullRequest, pullRequest.state, State.MERGED);
                PullRequestEvent.addFromNotificationEvent(notiEvent, pullRequest);
            }

            if (!wasConflict && mergeResult.conflicts()) {
                mergeResult.setConflictStateOfPullRequest();
                NotificationEvent notiEvent = NotificationEvent.afterMerge(message.getSender(),
                        pullRequest, State.CONFLICT);
                PullRequestEvent.addMergeEvent(notiEvent.getSender(), EventType.PULL_REQUEST_MERGED, State.CONFLICT, pullRequest);
            }

            if (wasConflict && !mergeResult.conflicts()) {
                mergeResult.setResolvedStateOfPullRequest();
                NotificationEvent notiEvent = NotificationEvent.afterMerge(message.getSender(),
                        pullRequest, State.RESOLVED);
                PullRequestEvent.addMergeEvent(notiEvent.getSender(), EventType.PULL_REQUEST_MERGED, State.RESOLVED, pullRequest);
            }

            mergeResult.save();
        } catch (Exception e) {
            play.Logger.error("Failed to check merging from " + pullRequest, e);
        }
    }

    private String getCommitEventOldValue(String oldMergeCommitId, String newMergeCommitId) {
        if (oldMergeCommitId == null) {
            return null;
        }
        return oldMergeCommitId + PullRequest.DELIMETER + newMergeCommitId;
    }
}
