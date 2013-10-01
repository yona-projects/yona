package actors;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import controllers.routes;

import play.mvc.Http.Request;
import playRepository.GitConflicts;
import playRepository.GitRepository;
import playRepository.GitRepository.AfterCloneAndFetchOperation;
import playRepository.GitRepository.CloneAndFetch;

import models.ConflictCheckMessage;
import models.NotificationEvent;
import models.Project;
import models.PullRequest;
import models.User;
import models.enumeration.EventType;
import models.resource.Resource;
import akka.actor.UntypedActor;

/**
 * 변경된 branch 와 관련있는 pull-request 에 충돌이 발생 했을 때
 * 해당 pull-request 를 보낸 사람에게 알림을 준다
 */
public class ConflictCheckActor extends UntypedActor {
    @Override
    public void onReceive(Object object) throws Exception {
        if (!(object instanceof ConflictCheckMessage)) {
            return;
        }
        ConflictCheckMessage message = (ConflictCheckMessage) object;
        List<PullRequest> pullRequests = PullRequest.findRelatedPullRequests(
                message.getProject(), message.getBranch());
        for (PullRequest pullRequest : pullRequests) {
            GitConflicts conflicts = findConflicts(pullRequest);
            if (conflicts != null) {
                addPullRequestConflictsNotification(message.getSender(),
                        pullRequest, conflicts, message.getRequest());
            }
        }
    }

    private GitConflicts findConflicts(PullRequest pullRequest) {
        final GitConflicts[] conflicts = {null};
        GitRepository.cloneAndFetch(pullRequest, new AfterCloneAndFetchOperation() {
            @Override
            public void invoke(CloneAndFetch cloneAndFetch) throws IOException,
                    GitAPIException {
                Repository clonedRepository = cloneAndFetch.getRepository();
                GitRepository.checkout(clonedRepository, cloneAndFetch.getDestToBranchName());
                MergeResult mergeResult = GitRepository.merge(clonedRepository, cloneAndFetch.getDestFromBranchName());
                if(mergeResult.getMergeStatus() == MergeResult.MergeStatus.CONFLICTING) {
                    conflicts[0] = new GitConflicts(clonedRepository, mergeResult);
                }
            }
        });
        return conflicts[0];
    }

    private void addPullRequestConflictsNotification(User sender,
            PullRequest pullRequest, GitConflicts conflicts, Request request) {
        String title = NotificationEvent.formatReplyTitle(pullRequest);
        Resource resource = pullRequest.asResource();
        Set<User> receivers = new HashSet<>();
        receivers.add(pullRequest.contributor);
        Project toProject = pullRequest.toProject;

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = title;
        notiEvent.senderId = sender.id;
        notiEvent.receivers = receivers;
        notiEvent.urlToView = routes.PullRequestApp.pullRequest(
                toProject.owner, toProject.name, pullRequest.number).absoluteURL(
                request);
        notiEvent.resourceId = resource.getId();
        notiEvent.resourceType = resource.getType();
        notiEvent.eventType = EventType.PULL_REQUEST_CONFLICTS;
        notiEvent.newValue = StringUtils.join(conflicts.conflictFiles, "\n");
        NotificationEvent.add(notiEvent);
    }
}
