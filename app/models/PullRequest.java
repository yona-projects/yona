package models;

import com.avaje.ebean.Page;
import controllers.UserApp;
import controllers.routes;
import models.enumeration.EventType;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.Resource;
import models.resource.ResourceConvertible;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import org.joda.time.Duration;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.Expr;
import com.avaje.ebean.RawSql;
import com.avaje.ebean.RawSqlBuilder;
import com.avaje.ebean.SqlQuery;
import com.avaje.ebean.SqlRow;

import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.db.ebean.Transactional;

import play.i18n.Messages;

import playRepository.GitCommit;
import playRepository.GitConflicts;
import playRepository.GitRepository;
import playRepository.GitRepository.AfterCloneAndFetchOperation;
import playRepository.GitRepository.CloneAndFetch;
import utils.AccessControl;
import utils.Constants;
import utils.JodaDateUtil;
import utils.WatchService;

import javax.persistence.*;
import javax.validation.constraints.Size;

import java.util.*;
import java.util.regex.Matcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
public class PullRequest extends Model implements ResourceConvertible {

    private static final long serialVersionUID = 1L;

    public static final String DELIMETER = ",";
    public static final Finder<Long, PullRequest> finder = new Finder<>(Long.class, PullRequest.class);

    public static final int ITEMS_PER_PAGE = 15;

    @Id
    public Long id;

    @Constraints.Required
    @Size(max=255)
    public String title;

    @Lob
    public String body;

    @ManyToOne
    public Project toProject;

    @ManyToOne
    public Project fromProject;

    @Constraints.Required
    @Size(max=255)
    public String toBranch;

    @Constraints.Required
    @Size(max=255)
    public String fromBranch;

    @ManyToOne
    public User contributor;

    @ManyToOne
    public User receiver;

    @Temporal(TemporalType.TIMESTAMP)
    public Date created;

    @Temporal(TemporalType.TIMESTAMP)
    public Date updated;

    @Temporal(TemporalType.TIMESTAMP)
    public Date received;

    public State state = State.OPEN;
    
    public Boolean isConflict;
    public Boolean isMerging;
    
    @OneToMany(cascade = CascadeType.ALL)
    public List<PullRequestCommit> pullRequestCommits;
    
    @OneToMany(cascade = CascadeType.ALL)
    public List<PullRequestEvent> pullRequestEvents;
    
    @Lob
    public String patch;
    
    /**
     * {@link #fromBranch}의 가장 최근 커밋 ID
     *
     * when: 브랜치를 삭제한 뒤 복구할 때 사용한다.
     *
     */
    public String lastCommitId;

    /**
     * merge commit의 parent 중에서 코드를 받는 쪽 브랜치의 HEAD 커밋 ID.
     *
     * when: merge된 커밋 목록을 조회할 때 사용한다.
     *
     * 이 커밋 ID는 코드를 보내는쪽에도 존재하며 그 뒤의 커밋 ID부터 추가된 커밋 ID로 볼 수 있다.
     *
     * #mergedCommitIdFrom < 추가된 커밋 ID 목록 <= #mergedCommitIdTo
     *
     */
    public String mergedCommitIdFrom;

    /**
     * merge commit의 parent 중에서 코드를 보내는 쪽 브랜치의 HEAD 커밋 ID.
     *
     * when: merge된 커밋 목록을 조회할 때 사용한다.
     *
     * #mergedCommitIdFrom 뒤에 추가된 커밋 ID부터 이 커밋 ID까지를 추가된 커밋 ID로 불 수 있다.
     *
     * #mergedCommitIdFrom < 추가된 커밋 ID 목록 <= #mergedCommitIdTo
     */
    public String mergedCommitIdTo;

    /**
     * #toProject 마다 순차적으로 유일한 수를 가진다.
     */
    public Long number;

    public String conflictFiles;

    @Override
    public String toString() {
        return "PullRequest{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", body='" + body + '\'' +
                ", toProject=" + toProject +
                ", fromProject=" + fromProject +
                ", toBranch='" + toBranch + '\'' +
                ", fromBranch='" + fromBranch + '\'' +
                ", contributor=" + contributor +
                ", receiver=" + receiver +
                ", created=" + created +
                ", updated=" + updated +
                ", received=" + received +
                ", state=" + state +
                '}';
    }
    
    public void setPatch(String patch) {
        this.patch = patch;
    }
    
    public String getPatch() {
        return this.patch;
    }
    
    public Duration createdAgo() {
        return JodaDateUtil.ago(this.created);
    }

    public Duration receivedAgo() {
        return JodaDateUtil.ago(this.received);
    }
    
    public boolean isOpen() {
        return this.state == State.OPEN;
    }

    public boolean isRejected() {
        return this.state == State.REJECTED;
    }

    public static PullRequest findById(long id) {
        return finder.byId(id);
    }

    public static PullRequest findDuplicatedPullRequest(PullRequest pullRequest) {
        return finder.where()
                .eq("fromBranch", pullRequest.fromBranch)
                .eq("toBranch", pullRequest.toBranch)
                .eq("fromProject", pullRequest.fromProject)
                .eq("toProject", pullRequest.toProject)
                .eq("state", State.OPEN)
                .findUnique();
    }

    public static List<PullRequest> findOpendPullRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.OPEN)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findOpendPullRequestsByDaysAgo(Project project, int days) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.OPEN)
                .ge("created", JodaDateUtil.before(days))
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findClosedPullRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.CLOSED)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findSentPullRequests(Project project) {
        return finder.where()
                .eq("fromProject", project)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findAcceptedPullRequests(Project project) {
        return finder.where()
                .eq("fromProject", project)
                .eq("state", State.CLOSED)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findRejectedPullRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.REJECTED)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> allReceivedRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .order().desc("created")
                .findList();
    }

    public static List<PullRequest> findRecentlyReceived(Project project, int size) {
        return finder.where()
                .eq("toProject", project)
                .order().desc("created")
                .findPagingList(size).getPage(0)
                .getList();
    }

    public static int countOpenedPullRequests(Project project) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.OPEN)
                .findRowCount();
    }

    /**
     * 보내거나 받는 쪽에
     * {@code project} 의 {@code branch} 를 가지고 있는 pull-request 목록 조회
     * 
     * 병합(Closed)되지 않은 모든 보낸코드를 조회한다.
     *
     * @param project
     * @param branch
     * @return
     */
    public static List<PullRequest> findRelatedPullRequests(Project project, String branch) {
        return finder.where()
                .or(
                        Expr.and(
                                Expr.eq("fromProject", project),
                                Expr.eq("fromBranch", branch)),
                        Expr.and(
                                Expr.eq("toProject", project),
                                Expr.eq("toBranch", branch)))
                .ne("state", State.CLOSED)
                .findList();
    }

    @Override
    public Resource asResource() {
        return new Resource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public Project getProject() {
                return toProject;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.PULL_REQUEST;
            }

            @Override
            public Long getAuthorId() {
                return contributor.id;
            }
        };
    }

    /**
     * 새로운 코드 요청으로 기존 코드 요청을 수정한다.
     *
     * @param newPullRequest
     */
    public void updateWith(PullRequest newPullRequest) {
        this.toBranch = newPullRequest.toBranch;
        this.fromBranch = newPullRequest.fromBranch;
        this.title = newPullRequest.title;
        this.body = newPullRequest.body;
        updateIssueEvents();
        update();
    }

    /**
     * {@code pullRequest}와 동일한 브랜치로 코드를 주고받는지 확인한다.
     *
     * @param pullRequest
     * @return
     */
    public boolean hasSameBranchesWith(PullRequest pullRequest) {
        return this.toBranch.equals(pullRequest.toBranch) && this.fromBranch.equals(pullRequest.fromBranch);
    }

    public boolean isClosed() {
        return state == State.CLOSED;
    }

    /**
     * {@link #fromBranch}를 삭제하고 해당 브랜치의 최근 커밋 ID를 {@link #lastCommitId}에 저장한다.
     *
     * @see #lastCommitId
     */
    public void deleteFromBranch() {
        String lastCommitId = GitRepository.deleteFromBranch(this);
        this.lastCommitId = lastCommitId;
        update();
    }

    public void restoreFromBranch() {
        GitRepository.restoreBranch(this);
    }

    public void merge() {
        GitRepository.merge(this);
        if(this.state == State.CLOSED) {
            this.received = JodaDateUtil.now();
            this.receiver = UserApp.currentUser();
            this.update();
        }
    }

    public String getResourceKey() {
        return ResourceType.PULL_REQUEST.resource() + Constants.RESOURCE_KEY_DELIM + this.id;
    }

    public Set<User> getWatchers() {
        Set<User> actualWatchers = new HashSet<>();

        actualWatchers.add(this.contributor);
        for (SimpleComment c : getComments()) {
            User user = User.find.byId(c.authorId);
            if (user != null) {
                actualWatchers.add(user);
            }
        }

        actualWatchers.addAll(WatchService.findWatchers(toProject.asResource()));
        actualWatchers.addAll(WatchService.findWatchers(asResource()));
        actualWatchers.removeAll(WatchService.findUnwatchers(asResource()));

        Set<User> allowedWatchers = new HashSet<>();
        for (User watcher : actualWatchers) {
            if (AccessControl.isAllowed(watcher, asResource(), Operation.READ)) {
                allowedWatchers.add(watcher);
            }
        }

        return allowedWatchers;
    }

    /**
     * pull request에 대한 코멘트 목록을 반환한다.
     * @return
     */
    public List<SimpleComment> getComments() {
        return SimpleComment.findByResourceKey(this.getResourceKey());
    }

    public void reject() {
        this.state = State.REJECTED;
        this.received = JodaDateUtil.now();
        this.receiver = UserApp.currentUser();
        this.update();
    }

    public void reopen() {
        this.state = State.OPEN;
        this.received = JodaDateUtil.now();
        this.receiver = UserApp.currentUser();
        this.update();
    }

    public static List<PullRequest> findByToProject(Project project) {
        return finder.where().eq("toProject", project).order().asc("created").findList();
    }

    @Transactional
    @Override
    public void save() {
        this.number = nextPullRequestNumber(toProject);
        super.save();
        addNewIssueEvents();
    }

    public static long nextPullRequestNumber(Project project) {
        PullRequest maxNumberedPullRequest = PullRequest.finder.where()
                .eq("toProject", project)
                .order().desc("number")
                .setMaxRows(1).findUnique();

        if(maxNumberedPullRequest == null || maxNumberedPullRequest.number == null) {
            return 1;
        } else {
            return ++maxNumberedPullRequest.number;
        }
    }

    public static PullRequest findOne(Project toProject, long number) {
        if(toProject == null || number <= 0) {
            return null;
        }
        return finder.where().eq("toProject", toProject).eq("number", number).findUnique();
    }

    /**
     * #number가 null인 PullRequest가 있을 때 number 초기화 작업을 진행합니다.
     *
     * when: Global의 onStart가 실행될 때 호출됩니다.
     */
    @Transactional
    public static void regulateNumbers() {
        int nullNumberPullRequestCount = finder.where().eq("number", null).findRowCount();

        if(nullNumberPullRequestCount > 0) {
            List<Project> projects = Project.find.all();
            for(Project project : projects) {
                List<PullRequest> pullRequests = PullRequest.findByToProject(project);
                for(PullRequest pullRequest : pullRequests) {
                    if(pullRequest.number == null) {
                        pullRequest.number = nextPullRequestNumber(project);
                        pullRequest.update();
                    }
                }
            }
        }
    }

    /**
     * {@code project}에서 {@code state}에 해당하는 풀리퀘 목록 중 한 페이지를 가져온다.
     *
     * {@code pageNum}은 0부터 시작하고, 한 페이지당 {@code ITEMS_PER_PAGE} 만큼 가져온다.
     *
     * @param state
     * @param project
     * @param pageNum
     * @return
     */
    public static Page<PullRequest> findPagingList(State state, Project project, int pageNum) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", state)
                .order().desc("created")
                .findPagingList(ITEMS_PER_PAGE)
                .getPage(pageNum);
    }

    /**
     * {@code project}에서 보낸 풀리퀘 목록 중 한 페이지를 가져온다.
     *
     * {@code pageNum}은 0부터 시작하고, 한 페이지당 {@code ITEMS_PER_PAGE} 만큼 가져온다.
     *
     * @param project
     * @param pageNum
     * @return
     */
    public static Page<PullRequest> findSentPullRequests(Project project, int pageNum) {
        return finder.where()
                .eq("fromProject", project)
                .order().desc("created")
                .findPagingList(ITEMS_PER_PAGE)
                .getPage(pageNum);
    }

    /**
     * 새로운 풀리퀘가 저장될때 풀리퀘의 제목과 본문에서 참조한 이슈에 이슈 이벤트를 생성한다.
     */
    private void addNewIssueEvents() {
        List<Issue> referredIsseus = IssueEvent.findReferredIssue(this.title + this.body, this.toProject);
        String newValue = getNewEventValue();
        for(Issue issue : referredIsseus) {
            IssueEvent issueEvent = new IssueEvent();
            issueEvent.issue = issue;
            issueEvent.senderLoginId = this.contributor.loginId;
            issueEvent.newValue = newValue;
            issueEvent.created = new Date();
            issueEvent.eventType = EventType.ISSUE_REFERRED;
            issueEvent.save();
        }
    }

    private String getNewEventValue() {
        return Messages.get("issue.event.referred.from.pullrequest",
                this.contributor.loginId, this.fromBranch, this.toBranch,
                routes.PullRequestApp.pullRequest(this.toProject.owner, this.toProject.name, this.number));
    }

    /**
     * 풀리퀘가 수정될 때 기존의 모든 이슈 이벤트를 삭제하고 새로 추가한다.
     */
    public void updateIssueEvents() {
        deleteIssueEvents();
        addNewIssueEvents();
    }

    /**
     * 풀리퀘가 삭제될 때 관련있는 모든 이슈 이벤트를 삭제한다.
     */
    public void deleteIssueEvents() {
        String newValue = getNewEventValue();

        List<IssueEvent> oldEvents = IssueEvent.find.where()
                .eq("newValue", newValue)
                .eq("senderLoginId", this.contributor.loginId)
                .eq("eventType", EventType.ISSUE_REFERRED)
                .findList();

        for(IssueEvent event : oldEvents) {
            event.delete();
        }
    }

    @Override
    public void delete() {
        deleteIssueEvents();
        super.delete();
    }
    
    @Transient
    public String[] getConflictFiles() {
        return StringUtils.split(this.conflictFiles, ",");
    }

    /**
     * 코드 코멘트를 반환한다.
     * @return
     */
    @Transient
    public List<CodeComment> getCodeComments() {
        return CodeComment.findByCommits(fromProject, pullRequestCommits);
    }
    
    /**
     * 현재 커밋목록을 반환한다.
     * @return
     */
    @Transient
    public List<PullRequestCommit> getCurrentCommits() {
        return PullRequestCommit.getCurrentCommits(this);
    }
    
    /**
     * pull request의 모든 코멘트 정보를 가져오고 시간순으로 정렬 후 반환한다. (코멘트 + 코드코멘트 + 이벤트 )
     * @return
     */
    @Transient
    public List<TimelineItem> getTimelineComments() {
        List<SimpleComment> comments = getComments();
        List<CodeComment> codeComments = getCodeComments();
        
        List<TimelineItem> timelineComments = new ArrayList<>();
        timelineComments.addAll(comments);
        timelineComments.addAll(codeComments);
        timelineComments.addAll(pullRequestEvents);

        Collections.sort(timelineComments, new Comparator<TimelineItem>() {
            @Override
            public int compare(TimelineItem o1, TimelineItem o2) {
                return o1.getDate().compareTo(o2.getDate());
            }
            
        });
        
        return timelineComments;
    }


    /**
     * 보낸 코드를 병합해보고 결과 정보를 반환한다.
     * 
     * @param pullRequest
     * @return
     */
    public PullRequestMergeResult attemptPullRequestMerge() {
        final GitConflicts[] conflicts = {null};
        final List<GitCommit> commits = new ArrayList<>();
        final PullRequest pullRequest = this;
        
        GitRepository.cloneAndFetch(pullRequest, new AfterCloneAndFetchOperation() {
            @Override
            public void invoke(CloneAndFetch cloneAndFetch) throws IOException, GitAPIException {
                Repository clonedRepository = cloneAndFetch.getRepository();
                
                List<GitCommit> commitList = GitRepository.diffCommits(clonedRepository,
                    cloneAndFetch.getDestFromBranchName(), cloneAndFetch.getDestToBranchName());

                for (GitCommit gitCommit : commitList) {
                    commits.add(gitCommit);
                }
                
                GitRepository.checkout(clonedRepository, cloneAndFetch.getDestToBranchName());
                
                pullRequest.setPatch(GitRepository.getPatch(clonedRepository,
                    cloneAndFetch.getDestFromBranchName(), cloneAndFetch.getDestToBranchName()));
                    
                MergeResult mergeResult = GitRepository.merge(clonedRepository, cloneAndFetch.getDestFromBranchName());
                if (mergeResult.getMergeStatus() == MergeResult.MergeStatus.CONFLICTING) {
                    conflicts[0] = new GitConflicts(clonedRepository, mergeResult);
                }
            }
        });
        
        List<PullRequestCommit> currentList = saveCurrentCommits(commits);
        updatePriorCommits(commits);
        
        PullRequestMergeResult pullRequestMergeResult = new PullRequestMergeResult();
        pullRequestMergeResult.setCommits(currentList);
        pullRequestMergeResult.setGitConflicts(conflicts[0]);
        pullRequestMergeResult.setPullRequest(pullRequest);
        
        return pullRequestMergeResult;
    }

    /**
     * 현재 커밋을 저장하고 목록을 반환한다.
     * 
     * 코드저장소의 커밋이 DB에 저장되어 있지 않으면 현재커밋으로 판단하고 저장한다. 
     * 
     * @param pullRequest
     * @param commits
     * @return
     */
    private List<PullRequestCommit> saveCurrentCommits(List<GitCommit> commits) {
        List<PullRequestCommit> list = new ArrayList<PullRequestCommit>();
        for (GitCommit commit: commits) {
            boolean existCommit = false;
            for (PullRequestCommit pullRequestCommit: PullRequestCommit.find.where().eq("pullRequest", this).findList()) {
                if(commit.getId().equals(pullRequestCommit.commitId)) {  
                    existCommit = true;
                    break;
                }
            }
            
            if (!existCommit) {
                PullRequestCommit pullRequestCommit = PullRequestCommit.bindPullRequestCommit(commit, this);
                
                pullRequestCommit.save();
                list.add(pullRequestCommit);
            }
        }
        return list;
    }
    
    /**
     * 이전 커밋을 업데이트하고 목록을 반환한다.
     * 
     * DB의 커밋이 코드저장소의 커밋에 존재하지 않으면 이전커밋으로 판단하고 업데이트 한다.
     * 
     * @param pullRequest
     * @param commits
     * @return
     */
    private List<PullRequestCommit> updatePriorCommits(List<GitCommit> commits) {
        List<PullRequestCommit> list = new ArrayList<PullRequestCommit>();
        for (PullRequestCommit pullRequestCommit: PullRequestCommit.find.where().eq("pullRequest", this).findList()) {
            boolean existCommit = false;
            for (GitCommit commit: commits) {
                if(commit.getId().equals(pullRequestCommit.commitId)) {
                    existCommit = true;
                    break;
                }
            }

            if (!existCommit) {
                pullRequestCommit.state = PullRequestCommit.State.PRIOR;
                pullRequestCommit.update();
                list.add(pullRequestCommit);
            }
        }
        return list;
    }


    /**
     * pull request의 커밋정보를 초기화한다.
     * 
     * pull request의 커밋정보를 DB에 저장하는 것으로 구조가 변경되어 기존 pull request의 커밋을 모두 저장한다.   
     */
    public static void initPullRequestCommit() {
        
        String sql = "SELECT pull.id FROM pull_request pull LEFT JOIN pull_request_commit commit ON pull.id = commit.pull_request_id WHERE commit.id IS NULL";
        SqlQuery sqlQuery = Ebean.createSqlQuery(sql);
        List<SqlRow> sqlRows = sqlQuery.findList();

        for (SqlRow sqlRow : sqlRows) {
            PullRequest pullRequest = PullRequest.finder.byId(sqlRow.getLong("id"));
            if (pullRequest.pullRequestCommits.isEmpty()) {
                List<GitCommit> commitList = new ArrayList<GitCommit>();
                if(pullRequest.isClosed()) {
                    commitList = GitRepository.diffCommits(pullRequest);                
                } else {
                    commitList = GitRepository.getPullingCommits(pullRequest);
                }
                
                for(GitCommit commit : commitList) {
                    PullRequestCommit pullRequestCommit = PullRequestCommit.bindPullRequestCommit(commit, pullRequest);
                    pullRequestCommit.save();
                }                
            }    
        }
    }
    
    /**
     * 열려 있는 pull request에 대한 merge 결과를 초기화한다.
     * 
     * 유지보수와 관련된 코드로 서버 구동시 한번만 수행되며 DB에 merge 결과가 저장되지 않은 pull request에 대해서만 처리된다.  
     * 
     */
    public static void initPullRequestMergeResult() {
        List<PullRequest> pullRequests = PullRequest.finder.where().isNull("isConflict").findList();

        for (PullRequest pullRequest : pullRequests) {
        
            if (!pullRequest.isClosed()) {
                PullRequestMergeResult mergeResult = pullRequest.attemptPullRequestMerge();
                
                if (mergeResult.getGitConflicts() != null) {
                    pullRequest.isConflict = true;
                } else {
                    pullRequest.isConflict = false;
                }
                
            } else {
                pullRequest.isConflict = false;
                pullRequest.setPatch(GitRepository.getPatch(pullRequest));
            }
            
            pullRequest.update();
        }
    }
}
