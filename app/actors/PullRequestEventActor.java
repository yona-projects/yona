package actors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import playRepository.GitCommit;
import playRepository.GitConflicts;
import playRepository.GitRepository;
import playRepository.GitRepository.AfterCloneAndFetchOperation;
import playRepository.GitRepository.CloneAndFetch;
import utils.JodaDateUtil;

import models.ConflictCheckMessage;
import models.NotificationEvent;
import models.PullRequest;
import models.PullRequestCommit;
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
        if (!(object instanceof ConflictCheckMessage)) {
            return;
        }
        ConflictCheckMessage message = (ConflictCheckMessage) object;
        List<PullRequest> pullRequests = PullRequest.findRelatedPullRequests(
                message.getProject(), message.getBranch());
                
        for (PullRequest pullRequest : pullRequests) {
            PullRequestMergeResult mergeResult = getMergeResult(pullRequest);

            if (mergeResult.commitChanged()) {
                NotificationEvent.addCommitChange(message.getSender(), pullRequest, message.getRequest(), mergeResult);
                PullRequestEvent.addCommitEvents(message.getSender(), pullRequest, mergeResult.getCommits());
            }
            
            GitConflicts conflicts = mergeResult.getConflicts();
            
            if (conflicts != null) {
                NotificationEvent notiEvent = NotificationEvent.addPullRequestMerge(message.getSender(),
                        pullRequest, conflicts, message.getRequest(), State.CONFLICT);
                
                PullRequestEvent.addMergeEvent(notiEvent.getSender(), EventType.PULL_REQUEST_MERGED, State.CONFLICT, pullRequest);
                
                pullRequest.isConflict = true;
                pullRequest.update();
                
            } else if (pullRequest.isConflict != null && pullRequest.isConflict) {
                
                NotificationEvent notiEvent = NotificationEvent.addPullRequestMerge(message.getSender(),
                        pullRequest, conflicts, message.getRequest(), State.RESOLVED);
            
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

}
