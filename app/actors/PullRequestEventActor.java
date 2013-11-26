package actors;

import java.util.List;

import models.NotificationEvent;
import models.PullRequest;
import models.PullRequestEvent;
import models.PullRequestEventMessage;
import models.PullRequestMergeResult;
import models.enumeration.EventType;
import models.enumeration.State;
import akka.actor.UntypedActor;

/**
 * Push가 되었을때 관련있는 PullRequest의 충돌/해결/커밋변경 이벤트 등록
 *
 * 변경된 branch 와 관련있는 pull-request 에 충돌이 발생하거나 충돌이 해결되었을때
 * 해당 pull-request 를 보낸 사람에게 알림을 주고 이벤트를 추가한다.
 *
 * 커밋목록이 변경되었을때 알림을 주고 커밋변경 이벤트를 추가한다.
 */
public class PullRequestEventActor extends UntypedActor {
    @Override
    public void onReceive(Object object) {
            if (!(object instanceof PullRequestEventMessage)) {
                return;
            }

            PullRequestEventMessage message = (PullRequestEventMessage) object;
            List<PullRequest> pullRequests = PullRequest.findRelatedPullRequests(
                    message.getProject(), message.getBranch());

            for (PullRequest pullRequest : pullRequests) {
                try {
                    PullRequestMergeResult mergeResult = pullRequest.attemptMerge();

                    if (mergeResult.hasDiffCommits()) {
                        mergeResult.saveCommits();
                        if (!mergeResult.getNewCommits().isEmpty()) {
                            PullRequestEvent.addCommitEvents(message.getSender(), pullRequest, mergeResult.getNewCommits());
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
}
