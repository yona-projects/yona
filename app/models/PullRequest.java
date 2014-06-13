/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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
package models;

import actors.RelatedPullRequestMergingActor;
import akka.actor.Props;
import com.avaje.ebean.*;
import controllers.PullRequestApp.SearchCondition;
import controllers.UserApp;
import models.enumeration.EventType;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.joda.time.Duration;

import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.Akka;
import playRepository.FileDiff;
import playRepository.GitCommit;
import playRepository.GitConflicts;
import playRepository.GitRepository;
import playRepository.GitRepository.AfterCloneAndFetchOperation;
import playRepository.GitRepository.CloneAndFetch;
import utils.Constants;
import utils.JodaDateUtil;

import javax.persistence.*;
import javax.persistence.OrderBy;
import javax.validation.constraints.Size;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.avaje.ebean.Expr.*;

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
    @OrderBy("created ASC")
    public List<PullRequestEvent> pullRequestEvents;

    public String lastCommitId;

    public String mergedCommitIdFrom;

    public String mergedCommitIdTo;

    public Long number;

    public String conflictFiles;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
        name = "pull_request_reviewers",
        joinColumns = @JoinColumn(name = "pull_request_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    public List<User> reviewers = new ArrayList<>();

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
        name = "pull_request_related_authors",
        joinColumns = @JoinColumn(name = "pull_request_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    public Set<User> relatedAuthors = new HashSet<>();

    @OneToMany(mappedBy = "pullRequest")
    public List<CommentThread> commentThreads = new ArrayList<>();

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

    public static void onStart() {
        regulateNumbers();
        changeStateToClosed();
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

    public boolean isAcceptable() {
        return !isConflict && isOpen() && !isMerging && (isReviewed() || !toProject.isUsingReviewerCount);
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

    public static List<PullRequest> findRecentlyReceivedOpen(Project project, int size) {
        return finder.where()
                .eq("toProject", project)
                .eq("state", State.OPEN)
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

    public void updateWith(PullRequest newPullRequest) {
        deleteIssueEvents();

        this.toBranch = newPullRequest.toBranch;
        this.fromBranch = newPullRequest.fromBranch;
        this.title = newPullRequest.title;
        this.body = newPullRequest.body;
        update();

        addNewIssueEvents();
    }

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
     * @see #lastCommitId
     */
    public void deleteFromBranch() {
        this.lastCommitId = GitRepository.deleteFromBranch(this);
        update();
    }

    public void restoreFromBranch() {
        GitRepository.restoreBranch(this);
    }

    public void merge(final PullRequestEventMessage message) {
        final PullRequest pullRequest = this;
        GitRepository.cloneAndFetch(pullRequest, new AfterCloneAndFetchOperation() {
            @Override
            public void invoke(CloneAndFetch cloneAndFetch) throws IOException, GitAPIException {
                Repository cloneRepository = cloneAndFetch.getRepository();
                String srcToBranchName = pullRequest.toBranch;
                String mergeBranchName = cloneAndFetch.getMergingBranchName();
                User sender = message.getSender();

                String mergedCommitIdFrom;
                MergeResult mergeResult;

                mergedCommitIdFrom =
                        cloneRepository.getRef(org.eclipse.jgit.lib.Constants.HEAD).getObjectId().getName();

                mergeResult = GitRepository.merge(cloneRepository, cloneAndFetch.getDestFromBranchName());

                if (mergeResult.getMergeStatus().isSuccessful()) {
                    RevCommit mergeCommit = writeMergeCommitMessage(cloneRepository, sender);
                    String mergedCommitIdTo = mergeCommit.getId().getName();
                    pullRequest.mergedCommitIdFrom = mergedCommitIdFrom;
                    pullRequest.mergedCommitIdTo = mergedCommitIdTo;

                    pullRequest.relatedAuthors = GitRepository.getRelatedAuthors(cloneRepository,
                            mergedCommitIdFrom, mergedCommitIdTo);

                    GitRepository.push(cloneRepository, GitRepository.getGitDirectoryURL(pullRequest.toProject), mergeBranchName, srcToBranchName);

                    pullRequest.state = State.MERGED;
                    pullRequest.received = JodaDateUtil.now();
                    pullRequest.receiver = sender;
                    pullRequest.update();

                    NotificationEvent.afterPullRequestUpdated(sender, pullRequest, State.OPEN, State.MERGED);
                    PullRequestEvent.addStateEvent(sender, pullRequest, State.MERGED);

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
        for (CommentThread thread : commentThreads) {
            for (ReviewComment c : thread.reviewComments) {
                User user = User.find.byId(c.author.id);
                if (user != null) {
                    actualWatchers.add(user);
                }
            }
        }

        return Watch.findActualWatchers(actualWatchers, asResource());
    }

    private RevCommit writeMergeCommitMessage(Repository cloneRepository, User user) throws GitAPIException {
        return new Git(cloneRepository).commit()
                .setAmend(true).setAuthor(user.name, user.email)
                .setMessage(makeMergeCommitMessage())
                .setCommitter(user.name, user.email)
                .call();
    }

    private String makeMergeCommitMessage() {
        return "Merge branch '" + this.fromBranch.replace("refs/heads/", "")
                + "' of " + fromProject.owner + "/" + fromProject.name + "\n\n"
            + "from pull request " + number;
    }

    private void changeState(State state) {
        this.state = state;
        this.received = JodaDateUtil.now();
        this.receiver = UserApp.currentUser();
        this.update();
    }

    public void reopen() {
        changeState(State.OPEN);
        PushedBranch.removeByPullRequestFrom(this);
    }

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

    public static Page<PullRequest> findPagingList(SearchCondition condition) {
        return createSearchExpressionList(condition)
                .order().desc(condition.category.order())
                .findPagingList(ITEMS_PER_PAGE)
                .getPage(condition.pageNum - 1);
    }

    public static int count(SearchCondition condition) {
        return createSearchExpressionList(condition).findRowCount();
    }

    private static ExpressionList<PullRequest> createSearchExpressionList(SearchCondition condition) {
        ExpressionList<PullRequest> el = finder.where();
        if (condition.project != null) {
            el.eq(condition.category.project(), condition.project);
        }
        Expression state = createStateSearchExpression(condition.category.states());
        if (state != null) {
            el.add(state);
        }
        if (condition.contributorId != null) {
            el.eq("contributor.id", condition.contributorId);
        }
        if (StringUtils.isNotBlank(condition.filter)) {
            Set<Object> ids = new HashSet<>();
            ids.addAll(el.query().copy().where()
                    .icontains("commentThreads.reviewComments.contents", condition.filter).findIds());
            ids.addAll(el.query().copy().where()
                    .eq("pullRequestCommits.state", PullRequestCommit.State.CURRENT)
                    .or(
                            icontains("pullRequestCommits.commitMessage", condition.filter),
                            icontains("pullRequestCommits.commitId", condition.filter))
                    .findIds());
            Junction<PullRequest> junction = el.disjunction();
            junction.icontains("title", condition.filter).icontains("body", condition.filter)
                    .icontains("mergedCommitIdTo", condition.filter);
            if (!ids.isEmpty()) {
                junction.in("id", ids);
            }
            junction.endJunction();
        }
        return el;
    }

    private static Expression createStateSearchExpression(State[] states) {
        int stateCount = ArrayUtils.getLength(states);
        switch (stateCount) {
            case 0:
                return null;
            case 1:
                return eq("state", states[0]);
            default:
                return in("state", states);
        }
    }

    private void addNewIssueEvents() {
        Set<Issue> referredIsseus = IssueEvent.findReferredIssue(this.title + this.body, this.toProject);
        String newValue = this.id.toString();
        for(Issue issue : referredIsseus) {
            IssueEvent issueEvent = new IssueEvent();
            issueEvent.issue = issue;
            issueEvent.senderLoginId = this.contributor.loginId;
            issueEvent.newValue = newValue;
            issueEvent.created = new Date();
            issueEvent.eventType = EventType.ISSUE_REFERRED_FROM_PULL_REQUEST;
            issueEvent.save();
        }
    }

    public void deleteIssueEvents() {
        String newValue = this.id.toString();

        List<IssueEvent> oldEvents = IssueEvent.find.where()
                .eq("newValue", newValue)
                .eq("senderLoginId", this.contributor.loginId)
                .eq("eventType", EventType.ISSUE_REFERRED_FROM_PULL_REQUEST)
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

    @Transient
    public List<CommitComment> getCommitComments() {
        return CommitComment.findByCommits(fromProject, pullRequestCommits);
    }

    @Transient
    public List<PullRequestCommit> getCurrentCommits() {
        return PullRequestCommit.getCurrentCommits(this);
    }

    public PullRequestMergeResult attemptMerge() {
        final GitConflicts[] conflicts = {null};
        final List<GitCommit> commits = new ArrayList<>();
        final PullRequest pullRequest = this;

        GitRepository.cloneAndFetch(pullRequest, new AfterCloneAndFetchOperation() {
            @Override
            public void invoke(CloneAndFetch cloneAndFetch) throws IOException, GitAPIException {
                Repository clonedRepository = cloneAndFetch.getRepository();

                List<GitCommit> commitList = GitRepository.diffCommits(clonedRepository,
                    cloneAndFetch.getDestFromBranchName(), cloneAndFetch.getMergingBranchName());

                for (GitCommit gitCommit : commitList) {
                    commits.add(gitCommit);
                }

                String mergedCommitIdFrom = clonedRepository
                        .getRef(org.eclipse.jgit.lib.Constants.HEAD).getObjectId().getName();
                MergeResult mergeResult = GitRepository.merge(clonedRepository,
                        cloneAndFetch.getDestFromBranchName());

                if (mergeResult.getMergeStatus() == MergeResult.MergeStatus.CONFLICTING) {
                    conflicts[0] = new GitConflicts(clonedRepository, mergeResult);
                } else if (mergeResult.getMergeStatus().isSuccessful()) {
                    String mergedCommitIdTo = mergeResult.getNewHead().getName();
                    pullRequest.mergedCommitIdFrom = mergedCommitIdFrom;
                    pullRequest.mergedCommitIdTo = mergedCommitIdTo;
                    pullRequest.relatedAuthors = GitRepository.getRelatedAuthors(clonedRepository,
                            mergedCommitIdFrom, mergedCommitIdTo);
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

    public int getRequiredReviewerCount() {
        return this.toProject.defaultReviewerCount;
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
        return reviewers.size() >= toProject.defaultReviewerCount;
    }

    public int getLackingReviewerCount() {
        return toProject.defaultReviewerCount - reviewers.size();
    }

    public List<CodeCommentThread> getCodeCommentThreadsForChanges(String commitId) throws
            IOException, GitAPIException {
        List<CodeCommentThread> result = new ArrayList<>();
        for(CommentThread commentThread : commentThreads) {
            // Include CodeCommentThread only
            if (!(commentThread instanceof CodeCommentThread)) {
                continue;
            }

            CodeCommentThread codeCommentThread = (CodeCommentThread) commentThread;

            if (commitId != null) {
                if (codeCommentThread.commitId.equals(commitId)) {
                    result.add(codeCommentThread);
                }
            } else {
                // Exclude threads on specific commit
                if (codeCommentThread.isCommitComment()) {
                    continue;
                }

                // Include threads which are not outdated certainly.
                if (mergedCommitIdFrom.equals(codeCommentThread.prevCommitId) && mergedCommitIdTo
                        .equals(codeCommentThread.commitId)) {
                    result.add(codeCommentThread);
                    continue;
                }

                // Include the other non-outdated threads
                Repository mergedRepository = getMergedRepository();
                if (noChangesBetween(mergedRepository,
                        mergedCommitIdFrom, mergedRepository, codeCommentThread.prevCommitId,
                        codeCommentThread.codeRange.path) && noChangesBetween(mergedRepository,
                        mergedCommitIdTo, mergedRepository, codeCommentThread.commitId,
                        codeCommentThread.codeRange.path)) {
                    result.add(codeCommentThread);
                }
            }
        }
        return result;
    }

    public List<CommentThread> getCommentThreadsByState(CommentThread.ThreadState state){
        List<CommentThread> result = new ArrayList<>();

        for (CommentThread commentThread : commentThreads) {
            if(commentThread.state == state){
                result.add(commentThread);
            }
        }

        return result;
    }

    public int countCommentThreadsByState(CommentThread.ThreadState state){
        Integer count = 0;

        for (CommentThread commentThread : commentThreads) {
            if(commentThread.state == state){
                count++;
            }
        }

        return count;
    }

    public List<FileDiff> getDiff(String commitId) throws IOException {
        if (commitId == null) {
            return getDiff();
        }
        return GitRepository.getDiff(getMergedRepository(), commitId);
    }

    public void removeCommentThread(CommentThread commentThread) {
        this.commentThreads.remove(commentThread);
        commentThread.pullRequest = null;
    }

    public void addCommentThread(CommentThread thread) {
        this.commentThreads.add(thread);
        thread.pullRequest = this;
    }

    static public boolean noChangesBetween(Repository repoA, String rev1,
                                           Repository repoB, String rev2,
                                           String path) throws IOException {
        ObjectId a = getBlobId(repoA, rev1, path);
        ObjectId b = getBlobId(repoB, rev2, path);
        return ObjectUtils.equals(a, b);
    }

    static private ObjectId getBlobId(Repository repo, String rev, String path) throws IOException {
        if (StringUtils.isEmpty(rev)) {
            throw new IllegalArgumentException("rev must not be empty");
        }
        RevTree tree = new RevWalk(repo).parseTree(repo.resolve(rev));
        TreeWalk tw = TreeWalk.forPath(repo, path, tree);
        if (tw == null) {
            return null;
        }
        return tw.getObjectId(0);
    }

    public String getMessageForDisabledAcceptButton() {
        if(this.isMerging) {
            return Messages.get("pullRequest.not.acceptable.because.is.merging");
        } else if(this.isConflict) {
            return Messages.get("pullRequest.not.acceptable.because.is.conflict");
        } else if(!this.isOpen()) {
            return Messages.get("pullRequest.not.acceptable.because.is.not.open");
        } else { // isOpen == false
            return Messages.get("pullRequest.not.acceptable.because.is.not.enough.review.point",
                    getLackingReviewerCount());
        }
    }

    public boolean isDiffable() {
        return this.isConflict == false &&
                this.mergedCommitIdFrom != null && this.mergedCommitIdTo != null;
    }

}
