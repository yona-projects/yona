package actors;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import models.PullRequestEventMessage;
import models.NotificationEvent;
import models.PullRequest;
import models.PullRequestEvent;
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
    public void onReceive(Object object) throws Exception {
        if (!(object instanceof PullRequestEventMessage)) {
            return;
        }

        PullRequestEventMessage message = (PullRequestEventMessage) object;
        List<PullRequest> pullRequests = PullRequest.findRelatedPullRequests(
                message.getProject(), message.getBranch());

        for (PullRequest pullRequest : pullRequests) {
            PullRequestMergeResult mergeResult = pullRequest.attemptPullRequestMerge();

            if (mergeResult.commitChanged()) {
                NotificationEvent.addCommitChange(message.getSender(), pullRequest, message.getRequest(), mergeResult);
                PullRequestEvent.addCommitEvents(message.getSender(), pullRequest, mergeResult.getCommits());
            }

            if (mergeResult.conflicts()) {            
            
                NotificationEvent notiEvent = NotificationEvent.addPullRequestMerge(message.getSender(),
                    pullRequest, mergeResult.getGitConflicts(), message.getRequest(), State.CONFLICT);
                
                PullRequestEvent.addMergeEvent(notiEvent.getSender(), EventType.PULL_REQUEST_MERGED, State.CONFLICT, pullRequest);
                
                pullRequest.isConflict = true;
                pullRequest.conflictFiles = mergeResult.getConflictFilesToString();
                
            } else if (mergeResult.resolved()) {
            
                NotificationEvent notiEvent = NotificationEvent.addPullRequestMerge(message.getSender(),
                    pullRequest, mergeResult.getGitConflicts(), message.getRequest(), State.RESOLVED);
                
                PullRequestEvent.addMergeEvent(notiEvent.getSender(), EventType.PULL_REQUEST_MERGED, State.RESOLVED, pullRequest);
                
                pullRequest.isConflict = false;
                pullRequest.conflictFiles = StringUtils.EMPTY;
            
            }
            
            pullRequest.isMerging = false;
            pullRequest.update();
        }
    }
}
