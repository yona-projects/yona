package models;

import actors.RelatedPullRequestMergingActor;
import akka.actor.Props;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;

import controllers.UserApp;
import controllers.routes;
import models.enumeration.EventType;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.Resource;
import models.resource.ResourceConvertible;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.joda.time.Duration;
import org.springframework.util.Assert;

import com.avaje.ebean.Expr;

import play.api.mvc.Call;
import play.api.templates.HtmlFormat;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.Akka;
import play.mvc.Controller;
import playRepository.*;
import playRepository.GitRepository.AfterCloneAndFetchOperation;
import playRepository.GitRepository.CloneAndFetch;
import utils.*;

import javax.persistence.*;
import javax.validation.constraints.Size;

import java.io.IOException;
import java.util.*;
import java.io.File;

import static com.avaje.ebean.Expr.eq;

@Entity
public class PullRequest extends Model implements ResourceConvertible {

    private static final long serialVersionUID = 1L;

    public static final String DELIMETER = ",";
    public static final Finder<Long, PullRequest> finder = new Finder<>(Long.class, PullRequest.class);

    public static final int ITEMS_PER_PAGE = 15;

    @Transient
    public Repository mergedRepo = null;

    @Id
    public Long id;

    @Constraints.Required
    @Size(max=255)
    public String title;

    @Lob
    public String body;

    @Transient
    public Long toProjectId;
    @Transient
    public Long fromProjectId;

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

    @OneToMany(mappedBy = "pullRequest")
    public List<PullRequestComment> comments;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
        name = "pull_request_reviewers",
        joinColumns = @JoinColumn(name = "pull_request_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id")
    )
    public List<User> reviewers = new ArrayList<>();

    public static PullRequest createNewPullRequest(Project fromProject, Project toProject, String fromBranch, String toBranch) {
        PullRequest pullRequest = new PullRequest();
        pullRequest.toProject = toProject;
        pullRequest.toBranch = toBranch;
        pullRequest.fromProject = fromProject;
        pullRequest.fromBranch = fromBranch;
        return pullRequest;
    }

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

    public Duration createdAgo() {
        return JodaDateUtil.ago(this.created);
    }

    public Duration receivedAgo() {
        return JodaDateUtil.ago(this.received);
    }

    public boolean isOpen() {
        return this.state == State.OPEN;
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
                .or(eq("state", State.CLOSED), eq("state", State.MERGED))
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
                .or(eq("state", State.CLOSED), eq("state", State.MERGED))
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
                                eq("fromProject", project),
                                eq("fromBranch", branch)),
                        Expr.and(
                                eq("toProject", project),
                                eq("toBranch", branch)))
                .ne("state", State.CLOSED)
                .ne("state", State.MERGED)
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
        return this.state == State.CLOSED;
    }

    public boolean isMerged() {
        return this.state == State.MERGED;
    }

    /**
     * {@link #fromBranch}를 삭제하고 해당 브랜치의 최근 커밋 ID를 {@link #lastCommitId}에 저장한다.
     *
     * @see #lastCommitId
     */
    public void deleteFromBranch() {
        this.lastCommitId = GitRepository.deleteFromBranch(this);
        update();
    }

    public void restoreFromBranch() {
        GitRepository.restoreBranch(this);
    }

    public void merge(final PullRequestEventMessage message, final Call call) {
        final PullRequest pullRequest = this;
        GitRepository.cloneAndFetch(pullRequest, new AfterCloneAndFetchOperation() {
            @Override
            public void invoke(CloneAndFetch cloneAndFetch) throws IOException, GitAPIException {
                Repository cloneRepository = cloneAndFetch.getRepository();
                String srcToBranchName = pullRequest.toBranch;
                String destToBranchName = cloneAndFetch.getDestToBranchName();

                // 코드를 받을 브랜치(toBranch)로 이동(checkout)한다.
                GitRepository.checkout(cloneRepository, cloneAndFetch.getDestToBranchName());

                String mergedCommitIdFrom;
                MergeResult mergeResult;

                mergedCommitIdFrom =
                        cloneRepository.getRef(org.eclipse.jgit.lib.Constants.HEAD).getObjectId().getName();

                // 코드를 보낸 브랜치(fromBranch)의 코드를 merge 한다.
                mergeResult = GitRepository.merge(cloneRepository, cloneAndFetch.getDestFromBranchName());

                if (mergeResult.getMergeStatus().isSuccessful()) {
                    // merge 커밋 메시지 수정
                    RevCommit mergeCommit = writeMergeCommitMessage(cloneRepository, UserApp.currentUser());
                    pullRequest.mergedCommitIdFrom = mergedCommitIdFrom;
                    pullRequest.mergedCommitIdTo = mergeCommit.getId().getName();

                    // 코드 받을 프로젝트의 코드 받을 브랜치(srcToBranchName)로 clone한 프로젝트의
                    // merge 한 브랜치(destToBranchName)의 코드를 push 한다.
                    GitRepository.push(cloneRepository, GitRepository.getGitDirectoryURL(pullRequest.toProject), destToBranchName, srcToBranchName);

                    // 풀리퀘스트 완료
                    pullRequest.state = State.MERGED;
                    pullRequest.received = JodaDateUtil.now();
                    pullRequest.receiver = UserApp.currentUser();
                    pullRequest.update();

                    NotificationEvent.addPullRequestUpdate(call, message.getRequest(), pullRequest, State.OPEN, State.MERGED);
                    PullRequestEvent.addStateEvent(pullRequest, State.MERGED);

                    Akka.system().actorOf(new Props(RelatedPullRequestMergingActor.class)).tell(message, null);
                }
            }
        });
    }

    public String getResourceKey() {
        return ResourceType.PULL_REQUEST.resource() + Constants.RESOURCE_KEY_DELIM + this.id;
    }

    public Set<User> getWatchers() {
        Set<User> actualWatchers = new HashSet<>();

        actualWatchers.add(this.contributor);
        for (PullRequestComment c : comments) {
            User user = User.find.byId(c.authorId);
            if (user != null) {
                actualWatchers.add(user);
            }
        }

        return WatchService.findActualWatchers(actualWatchers, asResource());
    }

    private RevCommit writeMergeCommitMessage(Repository cloneRepository, User user) throws GitAPIException {
        return new Git(cloneRepository).commit()
                .setAmend(true).setAuthor(user.name, user.email)
                .setMessage(makeMergeCommitMessage())
                .setCommitter(user.name, user.email)
                .call();
    }

    private String makeMergeCommitMessage() {
        return "Merge branch `" + this.fromBranch.replace("refs/heads/", "")
                + "` of " + fromProject.owner + "/" + fromProject.name + "\n\n"
            + "from pull request " + number;
    }

    /**
     * 코드 보내기 상태 변경
     * @param state
     */
    private void changeState(State state) {
        this.state = state;
        this.received = JodaDateUtil.now();
        this.receiver = UserApp.currentUser();
        this.update();
    }

    /**
     * 코드 보내기 다시 열림
     */
    public void reopen() {
        changeState(State.OPEN);
    }

    /**
     * 코드 보내기 닫힘
     */
    public void close() {
        changeState(State.CLOSED);
    }

    public static List<PullRequest> findByToProject(Project project) {
        return finder.where().eq("toProject", project).order().asc("created").findList();
    }

    public static List<PullRequest> findByFromProjectAndBranch(Project fromProject, String fromBranch) {
        return finder.where().eq("fromProject", fromProject).eq("fromBranch", fromBranch)
                .or(eq("state", State.OPEN), eq("state", State.REJECTED)).findList();
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

    public List<FileDiff> getDiff() throws IOException {
        if (mergedCommitIdFrom == null || mergedCommitIdTo == null) {
            throw new IllegalStateException("No mergedCommitIdFrom or mergedCommitIdTo");
        }
        return getDiff(mergedCommitIdFrom, mergedCommitIdTo);
    }

    public Repository getMergedRepository() throws IOException {
        if (mergedRepo == null) {
            File dir = new File(
                    GitRepository.getDirectoryForMerging(toProject.owner, toProject.name) + "/.git");
            mergedRepo = new RepositoryBuilder().setGitDir(dir).build();
        }

        return mergedRepo;
    }

    @Transient
    public List<FileDiff> getDiff(String revA, String revB) throws IOException {
        Repository mergedRepository = getMergedRepository();
        return GitRepository.getDiff(mergedRepository, revA, mergedRepository, revB);
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
                .order().desc("number")
                .findPagingList(ITEMS_PER_PAGE)
                .getPage(pageNum);
    }

    /**
     * 받은 코드 목록을 반환한다.
     *
     * 받은 코드는 닫혔(Closed)거나 병합(Merged)된 코드이다.
     * @param project
     * @param pageNum
     * @return
     */
    public static Page<PullRequest> findClosedPagingList(Project project, int pageNum) {
        return finder.where()
                .eq("toProject",  project)
                .or(eq("state", State.CLOSED), eq("state", State.MERGED))
                .order().desc("received")
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
        Set<Issue> referredIsseus = IssueEvent.findReferredIssue(this.title + this.body, this.toProject);
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
                this.contributor.loginId, TemplateHelper.branchItemName(this.fromBranch), TemplateHelper.branchItemName(this.toBranch),
                routes.PullRequestApp.pullRequest(this.toProject.owner, this.toProject.name, this.number), HtmlFormat.escape(this.title), this.number, HtmlFormat.escape(this.body));
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
    public List<CommitComment> getCommitComments() {
        return CommitComment.findByCommits(fromProject, pullRequestCommits);
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
     *
     * @return
     */
    @Transient
    public List<TimelineItem> getTimelineComments() {
        List<CommitComment> commitComment
                        = computeCommitCommentReplies(getCommitComments());

        List<TimelineItem> timelineComments = new ArrayList<>();
        timelineComments.addAll(comments);
        timelineComments.addAll(commitComment);
        timelineComments.addAll(pullRequestEvents);

        Collections.sort(timelineComments, TimelineItem.ASC);

        return timelineComments;
    }

    /**
     * 전체 코멘트중 부모글과 답글 정보를 재할당한다.
     * @param commitComments
     * @return
     */
    private List<CommitComment> computeCommitCommentReplies(
            List<CommitComment> commitComments) {
        return reAssignReplyComments(sameTopicCommentGroups(commitComments));
    }

    /**
     * 답글목록을 부모글의 필드로 재할당한다.
     *
     * commentGroup은 등록일순으로 오름차순 정렬되어 있는 상태이며
     * 목록의 첫번째 코멘트를 부모글로 판단한다.
     *
     * @param commentGroup
     * @return
     */
    private List<CommitComment> reAssignReplyComments(
            Map<String, List<CommitComment>> commentGroup) {
        List<CommitComment> parentCommitComments = new ArrayList<>();

        for (List<CommitComment> commitComments : commentGroup.values()) {
            CommitComment parentComment = commitComments.get(0);
            if (hasReply(commitComments)) {
                parentComment.replies = replies(commitComments);
            }
            parentCommitComments.add(parentComment);
        }
        return parentCommitComments;
    }

    /**
     * 답글 목록을 반환한다.
     * @param commitComments
     * @return
     */
    private List<CommitComment> replies(List<CommitComment> commitComments) {
        return commitComments.subList(1, commitComments.size());
    }

    /**
     * 답글 유무를 체크한다.
     * @param commitComments
     * @return
     */
    private boolean hasReply(List<CommitComment> commitComments) {
        return commitComments.size() > 1;
    }

    /**
     * groupKey를 통해 같은 코멘트그룹 목록을 반환한다.
     * (같은 커밋, 같은 파일, 같은 라인의 댓글들)
     * @param commitComments
     * @return
     */
    private Map<String, List<CommitComment>> sameTopicCommentGroups(
            List<CommitComment> commitComments) {
        Map<String, List<CommitComment>> commentGroup = new HashMap<>();
        for (CommitComment commitComment : commitComments) {
            commentGroup.put(
                    commitComment.groupKey(),
                    commitCommentsGroupByKey(commitComment.groupKey(),
                            commitComments));
        }
        return commentGroup;
    }

    /**
     * groupKey를 통해 같은 코멘트그룹을 반환한다.
     * @param groupKey
     * @param codeComments
     * @return
     */
    private List<CommitComment> commitCommentsGroupByKey(String groupKey,
            List<CommitComment> codeComments) {
        List<CommitComment> commitCommentGroups = new ArrayList<CommitComment>();
        for (CommitComment commitComment : codeComments) {
            if (commitComment.groupKey().equals(groupKey)) {
                commitCommentGroups.add(commitComment);
            }
        }
        return commitCommentGroups;
    }

    /**
     * 보낸 코드를 병합해보고 결과 정보를 반환한다.
     *
     * @param pullRequest
     * @return
     */
    public PullRequestMergeResult attemptMerge() {
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

                String mergedCommitIdFrom;
                MergeResult mergeResult;

                synchronized(this) {
                    mergedCommitIdFrom =
                            clonedRepository.getRef(org.eclipse.jgit.lib.Constants.HEAD).getObjectId().getName();

                    mergeResult = GitRepository.merge(clonedRepository, cloneAndFetch.getDestFromBranchName());
                }

                if (mergeResult.getMergeStatus() == MergeResult.MergeStatus.CONFLICTING) {
                    conflicts[0] = new GitConflicts(clonedRepository, mergeResult);
                } else if (mergeResult.getMergeStatus().isSuccessful()) {
                    pullRequest.mergedCommitIdFrom = mergedCommitIdFrom;
                    pullRequest.mergedCommitIdTo = mergeResult.getNewHead().getName();
                }
            }
        });

        PullRequestMergeResult pullRequestMergeResult = new PullRequestMergeResult();
        pullRequestMergeResult.setGitCommits(commits);
        pullRequestMergeResult.setGitConflicts(conflicts[0]);
        pullRequestMergeResult.setPullRequest(pullRequest);

        return pullRequestMergeResult;
    }

    public void startMerge() {
        isMerging = true;
    }

    public void endMerge() {
        this.isMerging = false;
    }

    public PullRequestMergeResult getPullRequestMergeResult() {
        PullRequestMergeResult mergeResult = null;
        if (!StringUtils.isEmpty(this.fromBranch) && !StringUtils.isEmpty(this.toBranch)) {
            mergeResult = this.attemptMerge();
            Map<String, String> suggestText = suggestTitleAndBodyFromDiffCommit(mergeResult.getGitCommits());
            this.title = suggestText.get("title");
            this.body = suggestText.get("body");
        }
        return mergeResult;
    }

    /**
     * diff commit message로 pull request의 title과 body를 채운다.<br>
     * <br>
     * case 1 : commit이 한개이고 message가 한줄일 경우 title에 추가한다.<br>
     * case 2 : commit이 한개이고 message가 여러줄일 경우 첫번째줄 mesage는 title 나머지 message는 body에 추가<br>
     * case 3 : commit이 여러개일 경우 각 commit의 첫번째줄 message들을 모아서 body에 추가 <br>
     *
     * @param commits
     * @return
     */
    private Map<String, String> suggestTitleAndBodyFromDiffCommit(List<GitCommit> commits) {
        Map<String, String> messageMap = new HashMap<>();

        String message;

        if (commits.isEmpty()) {
            return messageMap;

        } else if (commits.size() == 1) {
            message = commits.get(0).getMessage();
            String[] messages = message.split(Constants.NEW_LINE_DELIMETER);

            if (messages.length > 1) {
                String[] msgs = Arrays.copyOfRange(messages, 1, messages.length);
                messageMap.put("title", messages[0]);
                messageMap.put("body", StringUtils.join(msgs, Constants.NEW_LINE_DELIMETER));

            } else {
                messageMap.put("title", messages[0]);
                messageMap.put("body", StringUtils.EMPTY);
            }

        } else {
            String[] firstMessages = new String[commits.size()];
            for (int i = 0; i < commits.size(); i++) {
                String[] messages = commits.get(i).getMessage().split(Constants.NEW_LINE_DELIMETER);
                firstMessages[i] = messages[0];
            }
            messageMap.put("body", StringUtils.join(firstMessages, Constants.NEW_LINE_DELIMETER));

        }

        return messageMap;
    }

    /**
     * {@code fromProject}의 {@code fromBranch}에서 보낸 가장 최근 코드보내기 요청을 찾는다.
     *
     * fromProject와 toProject가 같은 수도 있다고 가정한다.
     * {@code fromProject}가 fork 프로젝트 일때는 upstream으로 보낸 코드보내기 요청을 포함하여 찾는다.
     *
     * when: 코드 메뉴의 브랜치 탭에서 브랜치 목록을 보여줄 때 특정 브랜치와 관련있는 코드보내기 요청을 찾을 때 사용한다.
     *
     * @param fromProject
     * @param fromBranch
     * @return
     */
    public static PullRequest findTheLatestOneFrom(Project fromProject, String fromBranch) {
        ExpressionList<PullRequest> el = finder.where()
                .eq("fromProject", fromProject)
                .eq("fromBranch", fromBranch);

        if(fromProject.isForkedFromOrigin()) {
            el.in("toProject", fromProject, fromProject.originalProject);
        } else {
            el.eq("toProject", fromProject);
        }

        return el
                .order().desc("number")
                .setMaxRows(1)
                .findUnique();
    }

    public static void changeStateToClosed() {
        List<PullRequest> rejectedPullRequests = PullRequest.finder.where()
                                    .eq("state", State.REJECTED).findList();
        for (PullRequest rejectedPullRequest : rejectedPullRequests) {
            rejectedPullRequest.state = State.CLOSED;
            rejectedPullRequest.received = JodaDateUtil.now();
            rejectedPullRequest.update();
        }
    }

    public void clearReviewers() {
        this.reviewers = new ArrayList<>();
        this.update();
    }

    public int getRequiredReviewPoint() {
        return this.toProject.defaultReviewPoint;
    }

    public void addReviewer(User user) {
        this.reviewers.add(user);
        this.update();
    }

    public void removeReviewer(User user) {
        this.reviewers.remove(user);
        this.update();
    }

    public boolean isReviewedBy(User user) {
        return this.reviewers.contains(user);
    }

    public boolean isReviewed() {
        return reviewers.size() >= toProject.defaultReviewPoint;
    }

    public int getLackingPoints() {
        return toProject.defaultReviewPoint - reviewers.size();
    }

}
