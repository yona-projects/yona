package actors;

import java.io.IOException;
import java.util.ArrayList;
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
import playRepository.GitCommit;
import playRepository.GitConflicts;
import playRepository.GitRepository;
import playRepository.GitRepository.AfterCloneAndFetchOperation;
import playRepository.GitRepository.CloneAndFetch;
import utils.JodaDateUtil;

import models.ConflictCheckMessage;
import models.NotificationEvent;
import models.Project;
import models.PullRequest;
import models.PullRequestCommit;
import models.PullRequestEvent;
import models.PullRequestMergeResult;
import models.User;
import models.enumeration.EventType;
import models.enumeration.State;
import models.resource.Resource;
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
        if (!(object instanceof ConflictCheckMessage)) {
            return;
        }
        ConflictCheckMessage message = (ConflictCheckMessage) object;
        List<PullRequest> pullRequests = PullRequest.findRelatedPullRequests(
                message.getProject(), message.getBranch());
                
        for (PullRequest pullRequest : pullRequests) {
            PullRequestMergeResult mergeResult = getMergeResult(pullRequest);

            if (mergeResult.commitChanged()) {
                addCommitChangedNotification(message.getSender(), pullRequest, message.getRequest(), mergeResult);
                PullRequestEvent.addCommitEvents(message.getSender(), pullRequest, mergeResult.getCommits());
            }
            
            GitConflicts conflicts = mergeResult.getConflicts();
            
            if (conflicts != null) {
                NotificationEvent notiEvent = addConflictsNotification(message.getSender(),
                        pullRequest, conflicts, message.getRequest());
                
                PullRequestEvent.addMergeEvent(notiEvent.getSender(), EventType.PULL_REQUEST_MERGED, State.CONFLICT, pullRequest);
                
                pullRequest.isConflict = true;
                pullRequest.update();
                
            } else if (pullRequest.isConflict != null && pullRequest.isConflict) {
                
                NotificationEvent notiEvent = addConflictsResolvedNotification(message.getSender(),
                        pullRequest, conflicts, message.getRequest());
            
                PullRequestEvent.addMergeEvent(notiEvent.getSender(), EventType.PULL_REQUEST_MERGED, State.RESOLVED, pullRequest);
                pullRequest.isConflict = false;
                pullRequest.update();
                
            }
        }
    }

    private PullRequestMergeResult getMergeResult(final PullRequest pullRequest) {
        final GitConflicts[] conflicts = {null};
        final List<GitCommit> commits = new ArrayList<>();
        
        GitRepository.cloneAndFetch(pullRequest, new AfterCloneAndFetchOperation() {
            @Override
            public void invoke(CloneAndFetch cloneAndFetch) throws IOException,
                    GitAPIException {
                Repository clonedRepository = cloneAndFetch.getRepository();
                
                List<GitCommit> commitList = GitRepository.diffCommits(clonedRepository,
                    cloneAndFetch.getDestFromBranchName(), cloneAndFetch.getDestToBranchName());
                    
                for (GitCommit gitCommit : commitList) {
                    commits.add(gitCommit);
                }
                
                GitRepository.checkout(clonedRepository, cloneAndFetch.getDestToBranchName());
                MergeResult mergeResult = GitRepository.merge(clonedRepository, cloneAndFetch.getDestFromBranchName());
                if(mergeResult.getMergeStatus() == MergeResult.MergeStatus.CONFLICTING) {
                    conflicts[0] = new GitConflicts(clonedRepository, mergeResult);
                }
            }
        });
        
        List<PullRequestCommit> pullRequestCommits = pullRequest.pullRequestCommits;
        List<PullRequestCommit> currentList = getCurrentCommits(pullRequest, commits, pullRequestCommits);
        
        PullRequestMergeResult pullRequestMerge = new PullRequestMergeResult();
        pullRequestMerge.setCommits(currentList);
        pullRequestMerge.setConflicts(conflicts[0]);
        pullRequestMerge.setPullRequest(pullRequest);

        return pullRequestMerge;
    }

    private static List<PullRequestCommit> getCurrentCommits(final PullRequest pullRequest, final List<GitCommit> commits, List<PullRequestCommit> pullRequestCommits) {
        List<PullRequestCommit> list = new ArrayList<PullRequestCommit>();
        for (GitCommit commit: commits) {
            boolean existCommit = false;
            for (PullRequestCommit prCommit: pullRequestCommits) {
                if(commit.getId().equals(prCommit.commitId)) {  // 저장된 커밋과 같은 커밋이 있는지 체크
                    existCommit = true;
                    break;
                }
            }
            
            if(!existCommit) {  // 같은 커밋이 없으면 추가
                PullRequestCommit pullRequestCommit = bindPullRequestCommit(commit);
                pullRequestCommit.pullRequest = pullRequest;
                
                pullRequestCommit.save();
                list.add(pullRequestCommit);
            }
        }
        return list;
    }
    
    private static PullRequestCommit bindPullRequestCommit(GitCommit commit) {
        PullRequestCommit pullRequestCommit = new PullRequestCommit();
        pullRequestCommit.commitId = commit.getId();
        pullRequestCommit.commitShortId = commit.getShortId();
        pullRequestCommit.commitMessage = commit.getMessage();
        pullRequestCommit.authorEmail = commit.getAuthorEmail();
        pullRequestCommit.authorDate = commit.getAuthorDate();
        pullRequestCommit.created = JodaDateUtil.now(); 
        pullRequestCommit.state = PullRequestCommit.State.CURRENT;
        
        return pullRequestCommit;
    }
    
    private NotificationEvent addConflictsNotification(User sender,
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
        notiEvent.eventType = EventType.PULL_REQUEST_MERGED;
        notiEvent.newValue = State.CONFLICT.state();
        notiEvent.oldValue = StringUtils.join(conflicts.conflictFiles, "\n");

        NotificationEvent.add(notiEvent);
        
        return notiEvent;
    }
    
    private NotificationEvent addConflictsResolvedNotification(User sender, PullRequest pullRequest,
            GitConflicts conflicts, Request request) {
            
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
        notiEvent.eventType = EventType.PULL_REQUEST_MERGED;
        notiEvent.newValue = State.RESOLVED.state();

        NotificationEvent.add(notiEvent);
        
        return notiEvent;
    }


    private NotificationEvent addCommitChangedNotification(User sender, PullRequest pullRequest, Request request,
            PullRequestMergeResult mergeResult) {
            
        String title = NotificationEvent.formatReplyTitle(pullRequest);
        Resource resource = pullRequest.asResource();
        Set<User> watchers = pullRequest.getWatchers();
        watchers.addAll(NotificationEvent.getMentionedUsers(pullRequest.body));
        watchers.remove(pullRequest.contributor);
        
        Project toProject = pullRequest.toProject;

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = title;
        notiEvent.senderId = sender.id;
        notiEvent.receivers = watchers;
        notiEvent.urlToView = routes.PullRequestApp.pullRequest(
                toProject.owner, toProject.name, pullRequest.number).absoluteURL(
                request);
        notiEvent.resourceId = resource.getId();
        notiEvent.resourceType = resource.getType();
        notiEvent.eventType = EventType.PULL_REQUEST_COMMIT_CHANGED;
        notiEvent.oldValue = makeCommitList(pullRequest.pullRequestCommits);
        notiEvent.newValue = makeCommitList(mergeResult.getCommits());
        
        NotificationEvent.add(notiEvent);
        
        return notiEvent;
    }

    private String makeCommitList(List<PullRequestCommit> commits) {
        StringBuilder sb = new StringBuilder();
        for (PullRequestCommit commit : commits) {
            sb.append(commit.commitShortId).append(": ").append(commit.commitMessage).append("\n");
        }
        return sb.toString();
    }
}
