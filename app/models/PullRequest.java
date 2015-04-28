/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
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
import errors.PullRequestException;
import models.enumeration.EventType;
import models.enumeration.ResourceType;
import models.enumeration.State;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.joda.time.Duration;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import play.i18n.Messages;
import play.libs.Akka;
import playRepository.FileDiff;
import playRepository.GitCommit;
import playRepository.GitRepository;
import utils.Constants;
import utils.JodaDateUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.*;
import javax.persistence.OrderBy;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

import static com.avaje.ebean.Expr.*;

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

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
        name = "pull_request_reviewers",
        joinColumns = @JoinColumn(name = "pull_request_id", unique = false),
        inverseJoinColumns = @JoinColumn(name = "user_id", unique = false),
        uniqueConstraints = @UniqueConstraint(columnNames = {"pull_request_id", "user_id"})
    )
    public Set<User> reviewers = new HashSet<>();

    @OneToMany(mappedBy = "pullRequest")
    public List<CommentThread> commentThreads = new ArrayList<>();

    @Transient
    private Repository repository;

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

    public class Merger {
        private ThreeWayMerger merger;
        private String leftRef;
        private String rightRef;

        public Merger(String leftRef, String rightRef) throws IOException {
            this.leftRef = Objects.requireNonNull(leftRef);
            this.rightRef = Objects.requireNonNull(rightRef);
        }

        public MergeResult merge() throws IOException {
            merger = MergeStrategy.RECURSIVE.newMerger(getRepository(), true);
            String refNotExistMessageFormat = "Ref '%s' does not exist in Git repository '%s'";
            ObjectId leftParent = Objects.requireNonNull(getRepository().resolve(leftRef),
                    String.format(refNotExistMessageFormat, leftRef, getRepository()));
            ObjectId rightParent = Objects.requireNonNull(getRepository().resolve(rightRef),
                    String.format(refNotExistMessageFormat, rightRef, getRepository()));

            if (merger.merge(leftParent, rightParent)) {
                return new Success(merger.getResultTreeId(), leftParent, rightParent);
            } else {
                return new Conflict(leftParent, rightParent);
            }
        }

        public class Conflict extends MergeResult {
            private Conflict(ObjectId leftParent, ObjectId rightParent) {
                this.leftParent = Objects.requireNonNull(leftParent);
                this.rightParent = Objects.requireNonNull(rightParent);
            }

            @Override
            public MergeRefUpdate createCommit() throws IOException, GitAPIException {
                throw new UnsupportedOperationException();
            }

            @Override
            public MergeRefUpdate createCommit(PersonIdent whoMerges) throws IOException, GitAPIException {
                throw new UnsupportedOperationException();
            }

            @Nullable
            @Override
            public ObjectId getMergeCommitId() {
                throw new UnsupportedOperationException();
            }
        }

        public class Success extends MergeResult {
            private ObjectId mergeCommitId;
            protected ObjectId treeId;

            private Success(
                    ObjectId treeId, ObjectId leftParent, ObjectId rightParent) {
                this.treeId = Objects.requireNonNull(treeId);
                this.leftParent = Objects.requireNonNull(leftParent);
                this.rightParent = Objects.requireNonNull(rightParent);
            }
                        public MergeRefUpdate createCommit() throws IOException, GitAPIException {
                return createCommit(new PersonIdent(utils.Config.getSiteName(),
                        utils.Config.getSystemEmailAddress()));
            }

            private ObjectId getMergedTreeIfReusable() {
                String refName = getNameOfRefToMerged();
                RevCommit commit = null;
                try {
                    ObjectId objectId = getRepository().getRef(refName).getObjectId();
                    commit = new RevWalk(getRepository()).parseCommit(objectId);
                } catch (Exception e) {
                    play.Logger.info("Failed to get the merged branch", e);
                }

                if (commit != null
                        && commit.getParentCount() == 2
                        && commit.getParent(0).equals(leftParent)
                        && commit.getParent(1).equals(rightParent)) {
                    return commit.getTree().toObjectId();
                }

                return null;
            }

            public MergeRefUpdate createCommit(PersonIdent whoMerges) throws IOException,
                    GitAPIException {
                // creates merge commit
                CommitBuilder mergeCommit = new CommitBuilder();
                ObjectId reusableMergedTreeId = getMergedTreeIfReusable();
                if (reusableMergedTreeId != null) {
                    mergeCommit.setTreeId(reusableMergedTreeId);
                } else {
                    mergeCommit.setTreeId(treeId);
                }
                mergeCommit.setParentIds(leftParent, rightParent);
                mergeCommit.setAuthor(whoMerges);
                mergeCommit.setCommitter(whoMerges);
                List<GitCommit> commitList = GitRepository.diffCommits(
                        getRepository(), leftParent, rightParent);
                mergeCommit.setMessage(makeMergeCommitMessage(commitList));

                // insertObject and got mergeCommit Object Id
                ObjectInserter inserter = getRepository().newObjectInserter();
                mergeCommitId = inserter.insert(mergeCommit);
                inserter.flush();
                inserter.release();

                return new MergeRefUpdate(mergeCommitId, whoMerges);
            }

            @Nullable
            public ObjectId getMergeCommitId() {
                return mergeCommitId;
            }
        }

        public abstract class MergeResult {
            protected ObjectId leftParent;
            protected ObjectId rightParent;

            abstract public MergeRefUpdate createCommit() throws IOException, GitAPIException;

            abstract public MergeRefUpdate createCommit(PersonIdent whoMerges) throws IOException,
                    GitAPIException;

            @Nullable
            abstract public ObjectId getMergeCommitId();

            public ObjectId getLeftParentId() {
                return leftParent;
            }

            public ObjectId getRightParentId() {
                return rightParent;
            }

            boolean conflicts() {
                return this instanceof Conflict;
            }
        }

        public class MergeRefUpdate {
            private ObjectId mergeCommitId;
            private PersonIdent whoMerges;

            private MergeRefUpdate(ObjectId mergeCommitId, PersonIdent whoMerges) {
                this.mergeCommitId = Objects.requireNonNull(mergeCommitId);
                this.whoMerges = Objects.requireNonNull(whoMerges);
            }

            public void updateRef(String ref) throws
                    IOException, ConcurrentRefUpdateException, PullRequestException {
                RefUpdate refUpdate = getRepository().updateRef(ref);
                refUpdate.setNewObjectId(mergeCommitId);
                refUpdate.setForceUpdate(true);
                refUpdate.setRefLogIdent(whoMerges);
                refUpdate.setRefLogMessage("merged", true);
                RefUpdate.Result rc = refUpdate.update();
                switch (rc) {
                    case NEW:
                    case FAST_FORWARD:
                    case FORCED:
                        return;
                    case REJECTED:
                    case LOCK_FAILURE:
                        throw new ConcurrentRefUpdateException(
                                "Could not lock '" + refUpdate.getRef() + "'",
                                refUpdate.getRef(), rc);
                    default:
                        throw new PullRequestException(MessageFormat.format(
                                JGitText.get().updatingRefFailed, refUpdate.getRef(),
                                mergeCommitId, rc));
                }

            }
        }
    }

    public void merge(final PullRequestEventMessage message) throws IOException, GitAPIException, PullRequestException {
        Merger.MergeResult result =
                new Merger(toBranch, fetchSourceBranch()).merge();

        if (!result.conflicts()) {
            User sender = message.getSender();
            result.createCommit(new PersonIdent(sender.name,
                    sender.email)).updateRef(toBranch);

            // Update the pull request
            updateMergedCommitId(result);
            changeState(State.MERGED, sender);

            // Add event
            NotificationEvent.afterPullRequestUpdated(sender, this, State.OPEN, State.MERGED);
            PullRequestEvent.addStateEvent(sender, this, State.MERGED);

            Akka.system().actorOf(Props.create(RelatedPullRequestMergingActor.class)).tell(message, null);
        }
    }

    public String fetchSourceBranch() throws IOException, GitAPIException {
        String destination = getRefNameToFetchedSource();
        fetchSourceBranchTo(destination);
        return destination;
    }

    public void updateMergedCommitId(Merger.MergeResult mergeResult) {
        mergedCommitIdFrom = mergeResult.getLeftParentId().getName();
        mergedCommitIdTo = mergeResult.getMergeCommitId().getName();
        update();
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

    /**
     * Make merge commit message e.g.
     *
     * Merge branch 'dev' of dlab/hive into 'next'
     *
     * from pull-request 10
     *
     * @param commits
     * @return
     * @throws IOException
     */
    private String makeMergeCommitMessage(List<GitCommit> commits) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("Merge branch ");
        builder.append("\'");
        builder.append(Repository.shortenRefName(fromBranch));
        builder.append("\'");

        if (!fromProject.equals(toProject)) {
            builder.append(" of ");
            builder.append(fromProject.owner);
            builder.append("/");
            builder.append(fromProject.name);
        }

        if (toBranch.equals("refs/heads/master")) {
            builder.append("\n\n");
        } else {
            builder.append(" into ");
            builder.append("\'");
            builder.append(Repository.shortenRefName(toBranch));
            builder.append("\'");
            builder.append("\n\n");
        }
        builder.append("from pull-request ");
        builder.append(number);
        builder.append("\n\n");
        addCommitMessages(commits, builder);
        addReviewers(builder);
        return builder.toString();
    }

    private void addReviewers(StringBuilder builder) {
        for(User user : reviewers) {
            builder.append(String.format("Reviewed-by: %s <%s>\n", user.name, user.email));
        }
    }

    public List<String> getReviewerNames(){
        List<String> names = new ArrayList<>();

        for(User user : reviewers){
            names.add(user.name);
        }

        return names;
    }

    private void addCommitMessages(List<GitCommit> commits, StringBuilder builder) {
        builder.append(String.format("* %s:\n", Repository.shortenRefName(this.fromBranch)));
        for(GitCommit gitCommit : commits) {
            builder.append(String.format("  %s\n", gitCommit.getShortMessage()));
        }
        builder.append("\n");
    }

    private void changeState(State state) {
        changeState(state, UserApp.currentUser());
    }

    private void changeState(State state, User updater) {
        this.state = state;
        this.received = JodaDateUtil.now();
        this.receiver = updater;
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

    public Repository getRepository() throws IOException {
        if (repository == null) {
            repository = new GitRepository(toProject).getRepository();
        }

        return repository;
    }

    @Transient
    public List<FileDiff> getDiff(String revA, String revB) throws IOException {
        Repository repository = getRepository();
        return GitRepository.getDiff(repository, revA, repository, revB);
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
    public List<CommitComment> getCommitComments() {
        return CommitComment.findByCommits(fromProject, pullRequestCommits);
    }

    @Transient
    public List<PullRequestCommit> getCurrentCommits() {
        return PullRequestCommit.getCurrentCommits(this);
    }

    private FetchResult fetchSourceBranchTo(String destination) throws IOException,
            GitAPIException {
        return new Git(getRepository()).fetch()
                .setRemote(GitRepository.getGitDirectoryURL(fromProject))
                .setRefSpecs(new RefSpec()
                        .setSource(fromBranch)
                        .setDestination(destination)
                        .setForceUpdate(true))
                .call();
    }

    public PullRequestMergeResult updateMerge() throws IOException, GitAPIException, PullRequestException {
        if (id == null) {
            throw new IllegalStateException("id must not be null");
        }

        // merge
        Merger.MergeResult mergeResult = new Merger(toBranch,
                fetchSourceBranch()).merge();

        // Make a PullRequestMergeResult to return
        PullRequestMergeResult pullRequestMergeResult = new PullRequestMergeResult();
        pullRequestMergeResult.setPullRequest(this);

        if (mergeResult instanceof Merger.Conflict) {
            pullRequestMergeResult.setConflictStateOfPullRequest();
        } else {
            // Commit and update the ref to merge commit of this pullrequest
            mergeResult.createCommit().updateRef(getNameOfRefToMerged());

            pullRequestMergeResult.setResolvedStateOfPullRequest();

            // Update the pullrequest
            updateMergedCommitId(mergeResult);
        }

        pullRequestMergeResult.setGitCommits(GitRepository.diffCommits(
                getRepository(),
                mergeResult.getLeftParentId(),
                mergeResult.getRightParentId()));

        return pullRequestMergeResult;
    }

    public String getRefNameToFetchedSource() {
        return "refs/yobi/pull/" + id + "/head";
    }

    public String getNameOfRefToMerged() {
        return "refs/yobi/pull/" + id + "/merged";
    }

    public String fetchSourceTemporarilly() throws IOException, GitAPIException {
        String tempBranchToCheckConflict = String.format(
                "refs/yobi/pull-check/%s/%s/%s",
                fromProject.owner, fromProject.name, fromBranch);
        fetchSourceBranchTo(tempBranchToCheckConflict);
        return tempBranchToCheckConflict;
    }

    // locking this repository is required because of fetch and update
    public PullRequestMergeResult attemptMerge() throws IOException, GitAPIException {
        // fetch the branch to merge
        String tempBranchToCheckConflict = fetchSourceTemporarilly();

        // merge
        Merger.MergeResult mergeResult = new Merger(toBranch,
                tempBranchToCheckConflict).merge();

        // Make a PullRequestMergeResult to return
        PullRequestMergeResult pullRequestMergeResult = new PullRequestMergeResult();
        pullRequestMergeResult.setPullRequest(this);
        if (mergeResult.conflicts()) {
            pullRequestMergeResult.setConflictStateOfPullRequest();
        } else {
            pullRequestMergeResult.setResolvedStateOfPullRequest();
        }
        pullRequestMergeResult.setGitCommits(GitRepository.diffCommits(
                getRepository(),
                mergeResult.getLeftParentId(),
                mergeResult.getRightParentId()));

        // Clean Up: Delete the temporary branch
        RefUpdate refUpdate = getRepository().updateRef(tempBranchToCheckConflict);
        refUpdate.setForceUpdate(true);
        refUpdate.delete();

        return pullRequestMergeResult;
    }

    public void startMerge() {
        isMerging = true;
    }

    public void endMerge() {
        this.isMerging = false;
    }

    public PullRequestMergeResult getPullRequestMergeResult() throws IOException, GitAPIException {
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
                messageMap.put("body", StringUtils.join(msgs, Constants.NEW_LINE_DELIMETER).trim());

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
        this.reviewers = new HashSet<>();
        this.update();
    }

    public int getRequiredReviewerCount() {
        return this.toProject.defaultReviewerCount;
    }

    public void addReviewer(User user) {
        if(this.reviewers.add(user)) {
            this.update();
        }
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
                Repository repository = getRepository();
                if (noChangesBetween(repository,
                        mergedCommitIdFrom, repository, codeCommentThread.prevCommitId,
                        codeCommentThread.codeRange.path) && noChangesBetween(repository,
                        mergedCommitIdTo, repository, codeCommentThread.commitId,
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
        return GitRepository.getDiff(getRepository(), commitId);
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
