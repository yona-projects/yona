---
id: gl-models
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

# 버킷 C — 공백 후보: `models` 영역 (1479개 심볼)

자동 분류 결과이며, 실제 공백인지는 사람이 개별 확인해야 한다(자동 승격 금지). 상세 방법론은 [[../methodology]] 참고.

| GL-ID | yona 파일:라인 | 선언 |
|---|---|---|
| GL-models_AuthInfo-001 | `app/models/AuthInfo.java:26` | `public class AuthInfo {` |
| GL-models_AuthInfo-002 | `app/models/AuthInfo.java:28` | `@Constraints.Required` |
| GL-models_AuthInfo-003 | `app/models/AuthInfo.java:31` | `@Constraints.Required` |
| GL-models_AuthInfo-004 | `app/models/AuthInfo.java:35` | `/**` |
| GL-models_Statistics-001 | `app/models/Statistics.java:4` | `public class Statistics {` |
| GL-models_Statistics-009 | `app/models/Statistics.java:21` | `private static final Statistics EMPTY = new Statistics();` |
| GL-models_Statistics-010 | `app/models/Statistics.java:24` | `public static Statistics empty() {` |
| GL-models_Statistics-011 | `app/models/Statistics.java:29` | `public Statistics() {` |
| GL-models_RecentProject-001 | `app/models/RecentProject.java:15` | `@Entity` |
| GL-models_RecentProject-004 | `app/models/RecentProject.java:24` | `public static Finder<Long, RecentProject> find = new Finder<>(Long.class, RecentProject.class);` |
| GL-models_RecentProject-005 | `app/models/RecentProject.java:27` | `@Id` |
| GL-models_RecentProject-010 | `app/models/RecentProject.java:40` | `public RecentProject(User user, Project project) {` |
| GL-models_RecentProject-012 | `app/models/RecentProject.java:66` | `public static void addNew(final User user, final Project project){` |
| GL-models_RecentProject-013 | `app/models/RecentProject.java:78` | `@Transactional` |
| GL-models_RecentProject-014 | `app/models/RecentProject.java:95` | `public static void deletePrevious(User user, Project project) {` |
| GL-models_RecentProject-015 | `app/models/RecentProject.java:106` | `private static void deleteOldestIfOverflow(User user) {` |
| GL-models_RecentProject-016 | `app/models/RecentProject.java:123` | `public static void deleteAll(User user) {` |
| GL-models_RecentProject-017 | `app/models/RecentProject.java:132` | `@Override` |
| GL-models_MailRecipient-001 | `app/models/MailRecipient.java:27` | `/**` |
| GL-models_MailRecipient-002 | `app/models/MailRecipient.java:32` | `@Nonnull` |
| GL-models_MailRecipient-003 | `app/models/MailRecipient.java:35` | `@Nullable` |
| GL-models_MailRecipient-004 | `app/models/MailRecipient.java:39` | `public MailRecipient(String email, String name) {` |
| GL-models_IssueLabel-001 | `app/models/IssueLabel.java:38` | `@Entity` |
| GL-models_IssueLabel-002 | `app/models/IssueLabel.java:42` | `static public class IssueLabelException extends Exception {` |
| GL-models_IssueLabel-004 | `app/models/IssueLabel.java:52` | `public static final Finder<Long, IssueLabel> finder = new Finder<>(Long.class, IssueLabel.class);` |
| GL-models_IssueLabel-005 | `app/models/IssueLabel.java:55` | `@Id` |
| GL-models_IssueLabel-006 | `app/models/IssueLabel.java:59` | `@Required` |
| GL-models_IssueLabel-007 | `app/models/IssueLabel.java:64` | `@Required(message="label.error.color.empty")` |
| GL-models_IssueLabel-008 | `app/models/IssueLabel.java:68` | `@Required(message="label.error.labelName.empty")` |
| GL-models_IssueLabel-009 | `app/models/IssueLabel.java:73` | `@ManyToOne` |
| GL-models_IssueLabel-010 | `app/models/IssueLabel.java:77` | `@ManyToMany(mappedBy="labels", fetch = FetchType.EAGER)` |
| GL-models_IssueLabel-011 | `app/models/IssueLabel.java:81` | `@ManyToMany(mappedBy="labels", fetch = FetchType.EAGER)` |
| GL-models_IssueLabel-012 | `app/models/IssueLabel.java:85` | `public static List<IssueLabel> findByProject(Project project) {` |
| GL-models_IssueLabel-013 | `app/models/IssueLabel.java:94` | `public static void copyIssueLabels(Project fromProject, Project toProject){` |
| GL-models_IssueLabel-014 | `app/models/IssueLabel.java:111` | `/**` |
| GL-models_IssueLabel-015 | `app/models/IssueLabel.java:129` | `/**` |
| GL-models_IssueLabel-016 | `app/models/IssueLabel.java:151` | `public String toString() {` |
| GL-models_IssueLabel-017 | `app/models/IssueLabel.java:156` | `@Transient` |
| GL-models_IssueLabel-018 | `app/models/IssueLabel.java:166` | `@Transient` |
| GL-models_IssueLabel-019 | `app/models/IssueLabel.java:181` | `@Transient` |
| GL-models_IssueLabel-020 | `app/models/IssueLabel.java:196` | `@Override` |
| GL-models_IssueLabel-021 | `app/models/IssueLabel.java:206` | `@Override` |
| GL-models_IssueLabel-022 | `app/models/IssueLabel.java:227` | `@Override` |
| GL-models_IssueLabel-023 | `app/models/IssueLabel.java:243` | `@Override` |
| GL-models_IssueMassUpdate-001 | `app/models/IssueMassUpdate.java:31` | `public class IssueMassUpdate {` |
| GL-models_IssueMassUpdate-006 | `app/models/IssueMassUpdate.java:41` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_IssueMassUpdate-008 | `app/models/IssueMassUpdate.java:47` | `@Constraints.Required` |
| GL-models_WebhookThread-001 | `app/models/WebhookThread.java:25` | `@Entity` |
| GL-models_WebhookThread-003 | `app/models/WebhookThread.java:30` | `public static Finder<Long, WebhookThread> find = new Finder<>(Long.class, WebhookThread.class);` |
| GL-models_WebhookThread-004 | `app/models/WebhookThread.java:33` | `@Id` |
| GL-models_WebhookThread-005 | `app/models/WebhookThread.java:37` | `@ManyToOne` |
| GL-models_WebhookThread-006 | `app/models/WebhookThread.java:41` | `@Required` |
| GL-models_WebhookThread-007 | `app/models/WebhookThread.java:46` | `@Required` |
| GL-models_WebhookThread-008 | `app/models/WebhookThread.java:50` | `@Required` |
| GL-models_WebhookThread-010 | `app/models/WebhookThread.java:58` | `public WebhookThread(Long webhookId, Resource resource, String threadId) {` |
| GL-models_WebhookThread-011 | `app/models/WebhookThread.java:67` | `public static WebhookThread create(Long webhookId, Resource resource, String threadId) {` |
| GL-models_WebhookThread-013 | `app/models/WebhookThread.java:84` | `@Override` |
| GL-models_PullRequestMergeResult-001 | `app/models/PullRequestMergeResult.java:30` | `public class PullRequestMergeResult {` |
| GL-models_PullRequestMergeResult-009 | `app/models/PullRequestMergeResult.java:55` | `public boolean hasDiffCommits() {` |
| GL-models_PullRequestMergeResult-010 | `app/models/PullRequestMergeResult.java:60` | `public boolean conflicts() {` |
| GL-models_PullRequestMergeResult-012 | `app/models/PullRequestMergeResult.java:70` | `public List<PullRequestCommit> findNewCommits() {` |
| GL-models_PullRequestMergeResult-013 | `app/models/PullRequestMergeResult.java:95` | `/**` |
| GL-models_PullRequestMergeResult-014 | `app/models/PullRequestMergeResult.java:106` | `public void saveCommits() {` |
| GL-models_PullRequestMergeResult-015 | `app/models/PullRequestMergeResult.java:113` | `public void saveNewCommits() {` |
| GL-models_PullRequestMergeResult-016 | `app/models/PullRequestMergeResult.java:120` | `public void updatePriorCommits() {` |
| GL-models_Search-001 | `app/models/Search.java:37` | `public class Search {` |
| GL-models_Search-004 | `app/models/Search.java:45` | `private static JunctionOperation<Issue> containsKeywordInIssue = new JunctionOperation<Issue>() {` |
| GL-models_Search-005 | `app/models/Search.java:53` | `private static JunctionOperation<Posting> containsKeywordInPosting = new JunctionOperation<Posting>(` |
| GL-models_Search-006 | `app/models/Search.java:61` | `private static JunctionOperation<Milestone> containsKeywordInMilestone = new JunctionOperation<Miles` |
| GL-models_Search-007 | `app/models/Search.java:69` | `private static JunctionOperation<IssueComment> containsKeywordInIssueComment = new JunctionOperation` |
| GL-models_Search-008 | `app/models/Search.java:77` | `private static JunctionOperation<PostingComment> containsKeywordInPostComment = new JunctionOperatio` |
| GL-models_Search-009 | `app/models/Search.java:85` | `private static JunctionOperation<ReviewComment> containsKeywordInReviewComment = new JunctionOperati` |
| GL-models_Search-010 | `app/models/Search.java:93` | `/**` |
| GL-models_Search-011 | `app/models/Search.java:113` | `/**` |
| GL-models_Search-012 | `app/models/Search.java:125` | `/**` |
| GL-models_Search-013 | `app/models/Search.java:144` | `/**` |
| GL-models_Search-014 | `app/models/Search.java:164` | `/**` |
| GL-models_Search-015 | `app/models/Search.java:178` | `private static ExpressionList<Issue> issuesEL(String keyword, User user, Project project) {` |
| GL-models_Search-016 | `app/models/Search.java:192` | `/**` |
| GL-models_Search-017 | `app/models/Search.java:211` | `/**` |
| GL-models_Search-018 | `app/models/Search.java:225` | `private static ExpressionList<Issue> issuesEL(String keyword, User user, Organization organization) ` |
| GL-models_Search-019 | `app/models/Search.java:238` | `/**` |
| GL-models_Search-020 | `app/models/Search.java:256` | `/**` |
| GL-models_Search-021 | `app/models/Search.java:268` | `private static ExpressionList<Posting> postsEL(String keyword, User user) {` |
| GL-models_Search-022 | `app/models/Search.java:279` | `/**` |
| GL-models_Search-023 | `app/models/Search.java:298` | `/**` |
| GL-models_Search-024 | `app/models/Search.java:311` | `private static ExpressionList<Posting> postsEL(String keyword, User user, Project project) {` |
| GL-models_Search-025 | `app/models/Search.java:323` | `/**` |
| GL-models_Search-026 | `app/models/Search.java:342` | `/**` |
| GL-models_Search-027 | `app/models/Search.java:356` | `private static ExpressionList<Posting> postsEL(String keyword, User user, Organization organization)` |
| GL-models_Search-028 | `app/models/Search.java:368` | `/**` |
| GL-models_Search-029 | `app/models/Search.java:380` | `/**` |
| GL-models_Search-030 | `app/models/Search.java:391` | `private static ExpressionList<User> usersEL(String keyword) {` |
| GL-models_Search-031 | `app/models/Search.java:403` | `/**` |
| GL-models_Search-032 | `app/models/Search.java:416` | `/**` |
| GL-models_Search-033 | `app/models/Search.java:429` | `private static ExpressionList<User> usersEL(String keyword, Project project) {` |
| GL-models_Search-034 | `app/models/Search.java:442` | `/**` |
| GL-models_Search-035 | `app/models/Search.java:455` | `/**` |
| GL-models_Search-036 | `app/models/Search.java:467` | `private static ExpressionList<User> usersEL(String keyword, Organization organization) {` |
| GL-models_Search-037 | `app/models/Search.java:480` | `/**` |
| GL-models_Search-038 | `app/models/Search.java:493` | `/**` |
| GL-models_Search-039 | `app/models/Search.java:511` | `/**` |
| GL-models_Search-040 | `app/models/Search.java:526` | `/**` |
| GL-models_Search-041 | `app/models/Search.java:539` | `private static ExpressionList<Project> projectsEL(String keyword, User user) {` |
| GL-models_Search-042 | `app/models/Search.java:570` | `public static Page<Milestone> findMilestones(String keyword, User user, PageParam pageParam) {` |
| GL-models_Search-043 | `app/models/Search.java:575` | `public static int countMilestones(String keyword, User user) {` |
| GL-models_Search-044 | `app/models/Search.java:580` | `private static ExpressionList<Milestone> milestonesEL(String keyword, User user) {` |
| GL-models_Search-045 | `app/models/Search.java:590` | `public static Page<Milestone> findMilestones(String keyword, User user, Project project, PageParam p` |
| GL-models_Search-047 | `app/models/Search.java:606` | `private static ExpressionList<Milestone> milestonesEL(String keyword, Project project) {` |
| GL-models_Search-048 | `app/models/Search.java:617` | `public static Page<Milestone> findMilestones(String keyword, User user, Organization organization, P` |
| GL-models_Search-049 | `app/models/Search.java:622` | `public static int countMilestones(String keyword, User user, Organization organization) {` |
| GL-models_Search-050 | `app/models/Search.java:627` | `private static ExpressionList<Milestone> milestonesEL(String keyword, User user, Organization organi` |
| GL-models_Search-051 | `app/models/Search.java:638` | `public static Page<IssueComment> findIssueComments(String keyword, User user, PageParam pageParam) {` |
| GL-models_Search-052 | `app/models/Search.java:643` | `public static int countIssueComments(String keyword, User user) {` |
| GL-models_Search-053 | `app/models/Search.java:648` | `private static ExpressionList<IssueComment> issueCommentsEL(String keyword, User user) {` |
| GL-models_Search-054 | `app/models/Search.java:659` | `public static Page<IssueComment> findIssueComments(String keyword, User user, Project project, PageP` |
| GL-models_Search-055 | `app/models/Search.java:664` | `public static int countIssueComments(String keyword, User user, Project project) {` |
| GL-models_Search-056 | `app/models/Search.java:669` | `private static ExpressionList<IssueComment> issueCommentsEL(String keyword, User user, Project proje` |
| GL-models_Search-057 | `app/models/Search.java:681` | `public static Page<IssueComment> findIssueComments(String keyword, User user, Organization organizat` |
| GL-models_Search-058 | `app/models/Search.java:686` | `public static int countIssueComments(String keyword, User user, Organization organization) {` |
| GL-models_Search-059 | `app/models/Search.java:691` | `private static ExpressionList<IssueComment> issueCommentsEL(String keyword, User user, Organization ` |
| GL-models_Search-060 | `app/models/Search.java:703` | `public static Page<PostingComment> findPostComments(String keyword, User user, PageParam pageParam) ` |
| GL-models_Search-063 | `app/models/Search.java:724` | `public static Page<PostingComment> findPostComments(String keyword, User user, Project project, Page` |
| GL-models_Search-064 | `app/models/Search.java:729` | `public static int countPostComments(String keyword, User user, Project project) {` |
| GL-models_Search-065 | `app/models/Search.java:734` | `private static ExpressionList<PostingComment> postCommentsEL(String keyword, User user, Project proj` |
| GL-models_Search-066 | `app/models/Search.java:747` | `public static Page<PostingComment> findPostComments(String keyword, User user, Organization organiza` |
| GL-models_Search-067 | `app/models/Search.java:752` | `public static int countPostComments(String keyword, User user, Organization organization) {` |
| GL-models_Search-068 | `app/models/Search.java:757` | `private static ExpressionList<PostingComment> postCommentsEL(String keyword, User user, Organization` |
| GL-models_Search-069 | `app/models/Search.java:769` | `public static Page<ReviewComment> findReviews(String keyword, User user, PageParam pageParam) {` |
| GL-models_Search-070 | `app/models/Search.java:774` | `public static int countReviews(String keyword, User user) {` |
| GL-models_Search-071 | `app/models/Search.java:779` | `private static ExpressionList<ReviewComment> reviewsEL(String keyword, User user) {` |
| GL-models_Search-072 | `app/models/Search.java:790` | `public static Page<ReviewComment> findReviews(String keyword, User user, Project project, PageParam ` |
| GL-models_Search-073 | `app/models/Search.java:795` | `public static int countReviews(String keyword, User user, Project project) {` |
| GL-models_Search-074 | `app/models/Search.java:800` | `private static ExpressionList<ReviewComment> reviewsEL(String keyword, User user, Project project) {` |
| GL-models_Search-075 | `app/models/Search.java:812` | `public static Page<ReviewComment> findReviews(String keyword, User user, Organization organization, ` |
| GL-models_Search-076 | `app/models/Search.java:817` | `public static int countReviews(String keyword, User user, Organization organization) {` |
| GL-models_Search-077 | `app/models/Search.java:822` | `private static ExpressionList<ReviewComment> reviewsEL(String keyword, User user, Organization organ` |
| GL-models_Search-078 | `app/models/Search.java:834` | `interface JunctionOperation<T> {` |
| GL-models_Search-079 | `app/models/Search.java:839` | `private static <T> void containsKeywordIn(String keyword, Junction<T> junction, String[] fields) {` |
| GL-models_Search-080 | `app/models/Search.java:848` | `private static <T> void inProjectsTemplate(String keyword, User user, Organization organization, Jun` |
| GL-models_Search-081 | `app/models/Search.java:877` | `private static <T> void inProjectsTemplate(String keyword, User user, Junction<T> junction, String p` |
| GL-models_Search-082 | `app/models/Search.java:904` | `private static <T> void equalsUserTemplate(String keyword, User user, Junction<T> junction, String p` |
| GL-models_Search-083 | `app/models/Search.java:917` | `private static <T> Page<T> emptyPage() {` |
| GL-models_LabelOwner-001 | `app/models/LabelOwner.java:28` | `/**` |
| GL-models_Comment-001 | `app/models/Comment.java:28` | `@MappedSuperclass` |
| GL-models_Comment-003 | `app/models/Comment.java:34` | `@Id` |
| GL-models_Comment-004 | `app/models/Comment.java:38` | `@Lob @Constraints.Required` |
| GL-models_Comment-005 | `app/models/Comment.java:42` | `@Constraints.Required` |
| GL-models_Comment-010 | `app/models/Comment.java:55` | `@Transient` |
| GL-models_Comment-011 | `app/models/Comment.java:59` | `@Transient` |
| GL-models_Comment-012 | `app/models/Comment.java:63` | `public Comment() {` |
| GL-models_Comment-013 | `app/models/Comment.java:68` | `public Comment(User author, String contents) {` |
| GL-models_Comment-014 | `app/models/Comment.java:75` | `public Duration ago() {` |
| GL-models_Comment-015 | `app/models/Comment.java:80` | `@Override` |
| GL-models_Comment-017 | `app/models/Comment.java:87` | `@Transient` |
| GL-models_Comment-018 | `app/models/Comment.java:95` | `@Transactional` |
| GL-models_Comment-019 | `app/models/Comment.java:103` | `@Transactional` |
| GL-models_Comment-020 | `app/models/Comment.java:110` | `protected void updateMention() {` |
| GL-models_Comment-021 | `app/models/Comment.java:115` | `public void delete() {` |
| GL-models_Comment-022 | `app/models/Comment.java:123` | `public static Comparator<Comment> comparator(){` |
| GL-models_Comment-028 | `app/models/Comment.java:147` | `@Override` |
| GL-models_Comment-029 | `app/models/Comment.java:171` | `@Override` |
| GL-models_Comment-031 | `app/models/Comment.java:190` | `@Override` |
| GL-models_NullUser-001 | `app/models/NullUser.java:33` | `public class NullUser extends User {` |
| GL-models_NullUser-003 | `app/models/NullUser.java:38` | `public NullUser(){` |
| GL-models_NullUser-004 | `app/models/NullUser.java:47` | `public List<Project> myProjects(){` |
| GL-models_NullUser-006 | `app/models/NullUser.java:57` | `@Override` |
| GL-models_NullUser-008 | `app/models/NullUser.java:78` | `@Override` |
| GL-models_Mention-001 | `app/models/Mention.java:26` | `@Entity` |
| GL-models_Mention-003 | `app/models/Mention.java:32` | `public static final Finder<Long, Mention> find = new Finder<>(Long.class, Mention.class);` |
| GL-models_SimpleCommentThread-001 | `app/models/SimpleCommentThread.java:27` | `/**` |
| GL-models_Issue-001 | `app/models/Issue.java:71` | `@Entity` |
| GL-models_Issue-003 | `app/models/Issue.java:78` | `public static final Finder<Long, Issue> finder = new Finder<>(Long.class, Issue.class);` |
| GL-models_Issue-006 | `app/models/Issue.java:85` | `public static final Pattern ISSUE_PATTERN = Pattern.compile("#\\d+");` |
| GL-models_Issue-008 | `app/models/Issue.java:91` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_Issue-009 | `app/models/Issue.java:95` | `public static final List<State> availableStates =` |
| GL-models_Issue-010 | `app/models/Issue.java:99` | `@ManyToOne` |
| GL-models_Issue-011 | `app/models/Issue.java:103` | `@ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)` |
| GL-models_Issue-012 | `app/models/Issue.java:107` | `@ManyToOne` |
| GL-models_Issue-013 | `app/models/Issue.java:111` | `@OneToMany(cascade = CascadeType.ALL, mappedBy="issue")` |
| GL-models_Issue-014 | `app/models/Issue.java:115` | `@OneToMany(cascade = CascadeType.ALL, mappedBy="issue")` |
| GL-models_Issue-015 | `app/models/Issue.java:119` | `@OneToMany(cascade = CascadeType.ALL, mappedBy = "issue")` |
| GL-models_Issue-016 | `app/models/Issue.java:123` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_Issue-017 | `app/models/Issue.java:132` | `@Transient` |
| GL-models_Issue-018 | `app/models/Issue.java:137` | `@Transient` |
| GL-models_Issue-019 | `app/models/Issue.java:142` | `public Issue(Project project, User author, String title, String body) {` |
| GL-models_Issue-020 | `app/models/Issue.java:148` | `@Transient` |
| GL-models_Issue-021 | `app/models/Issue.java:152` | `@Transient` |
| GL-models_Issue-022 | `app/models/Issue.java:156` | `@Transient` |
| GL-models_Issue-023 | `app/models/Issue.java:160` | `@OneToOne` |
| GL-models_Issue-026 | `app/models/Issue.java:170` | `public Issue() {` |
| GL-models_Issue-027 | `app/models/Issue.java:175` | `/**` |
| GL-models_Issue-028 | `app/models/Issue.java:183` | `/**` |
| GL-models_Issue-029 | `app/models/Issue.java:192` | `protected void fixLastNumber() {` |
| GL-models_Issue-030 | `app/models/Issue.java:197` | `public String assigneeName() {` |
| GL-models_Issue-031 | `app/models/Issue.java:202` | `public Long milestoneId() {` |
| GL-models_Issue-032 | `app/models/Issue.java:210` | `public boolean hasAssignee() {` |
| GL-models_Issue-033 | `app/models/Issue.java:215` | `/**` |
| GL-models_Issue-034 | `app/models/Issue.java:225` | `/**` |
| GL-models_Issue-035 | `app/models/Issue.java:235` | `public void checkLabels() throws IssueLabel.IssueLabelException {` |
| GL-models_Issue-036 | `app/models/Issue.java:251` | `@Override` |
| GL-models_Issue-037 | `app/models/Issue.java:268` | `/**` |
| GL-models_Issue-038 | `app/models/Issue.java:278` | `public static int countAllAssignedBy(User user) {` |
| GL-models_Issue-039 | `app/models/Issue.java:288` | `public static int countVoterOf(User user) {` |
| GL-models_Issue-040 | `app/models/Issue.java:300` | `public static int countAllCreatedBy(User user) {` |
| GL-models_Issue-041 | `app/models/Issue.java:305` | `public static int countIssues(Long projectId, State state) {` |
| GL-models_Issue-042 | `app/models/Issue.java:314` | `public static int countIssuesBy(Long projectId, SearchCondition cond) {` |
| GL-models_Issue-043 | `app/models/Issue.java:319` | `public static int countIssuesBy(SearchCondition cond) {` |
| GL-models_Issue-044 | `app/models/Issue.java:324` | `public static int countIssuesBy(Long projectId, Map<String, String> paramMap) {` |
| GL-models_Issue-045 | `app/models/Issue.java:332` | `public static int countIssuesBy(Organization organization, SearchCondition cond) {` |
| GL-models_Issue-046 | `app/models/Issue.java:337` | `/**` |
| GL-models_Issue-055 | `app/models/Issue.java:470` | `@Override` |
| GL-models_Issue-056 | `app/models/Issue.java:476` | `public Resource fieldAsResource(final ResourceType resourceType) {` |
| GL-models_Issue-057 | `app/models/Issue.java:501` | `public Resource stateAsResource() {` |
| GL-models_Issue-058 | `app/models/Issue.java:506` | `public Resource milestoneAsResource() {` |
| GL-models_Issue-059 | `app/models/Issue.java:511` | `public Resource assigneeAsResource() {` |
| GL-models_Issue-060 | `app/models/Issue.java:516` | `public static List<Issue> findRecentlyCreated(Project project, int size) {` |
| GL-models_Issue-062 | `app/models/Issue.java:534` | `public static Issue findByNumber(Project project, Long number) {` |
| GL-models_Issue-063 | `app/models/Issue.java:539` | `public static List<Issue> findByMilestone(Milestone milestone) {` |
| GL-models_Issue-064 | `app/models/Issue.java:544` | `public static List<Issue> findClosedIssuesByMilestone(Milestone milestone) {` |
| GL-models_Issue-065 | `app/models/Issue.java:549` | `public static List<Issue> findOpenIssuesByMilestone(Milestone milestone) {` |
| GL-models_Issue-066 | `app/models/Issue.java:554` | `@Transient` |
| GL-models_Issue-067 | `app/models/Issue.java:560` | `/**` |
| GL-models_Issue-068 | `app/models/Issue.java:577` | `public boolean assignedUserEquals(Assignee otherAssignee) {` |
| GL-models_Issue-069 | `app/models/Issue.java:588` | `/**` |
| GL-models_Issue-070 | `app/models/Issue.java:601` | `public static List<Issue> findByProject(Project project, String filter) {` |
| GL-models_Issue-071 | `app/models/Issue.java:611` | `public static List<Issue> findByProject(Project project, String filter, int limit) {` |
| GL-models_Issue-072 | `app/models/Issue.java:621` | `public static List<Issue> findParentIssueByProject(Project project, String filter, int limit) {` |
| GL-models_Issue-073 | `app/models/Issue.java:632` | `public static Page<Issue> findIssuesByState(int size, int pageNum, State state) {` |
| GL-models_Issue-074 | `app/models/Issue.java:639` | `public State previousState() {` |
| GL-models_Issue-076 | `app/models/Issue.java:654` | `public State nextState() {` |
| GL-models_Issue-078 | `app/models/Issue.java:669` | `public State toNextState(){` |
| GL-models_Issue-079 | `app/models/Issue.java:677` | `@Override` |
| GL-models_Issue-082 | `app/models/Issue.java:703` | `public boolean canBeDeleted() {` |
| GL-models_Issue-083 | `app/models/Issue.java:718` | `/**` |
| GL-models_Issue-084 | `app/models/Issue.java:730` | `/**` |
| GL-models_Issue-085 | `app/models/Issue.java:742` | `/**` |
| GL-models_Issue-088 | `app/models/Issue.java:767` | `public String until(){` |
| GL-models_Issue-089 | `app/models/Issue.java:784` | `public static int countOpenIssuesByLabel(Project project, IssueLabel label) {` |
| GL-models_Issue-090 | `app/models/Issue.java:793` | `public static int countOpenIssuesByAssignee(Project project, Assignee assignee) {` |
| GL-models_Issue-091 | `app/models/Issue.java:802` | `public static int countOpenIssuesByMilestone(Project project, Milestone milestone) {` |
| GL-models_Issue-092 | `app/models/Issue.java:811` | `public static List<Issue> findByParentIssueId(Long parentIssueId){` |
| GL-models_Issue-093 | `app/models/Issue.java:818` | `public boolean hasChildIssue(){` |
| GL-models_Issue-094 | `app/models/Issue.java:827` | `public boolean hasParentIssue(){` |
| GL-models_Issue-095 | `app/models/Issue.java:834` | `public static List<Issue> findByParentIssueIdAndState(Long parentIssueId, State state){` |
| GL-models_Issue-096 | `app/models/Issue.java:843` | `public static int countByParentIssueIdAndState(Long parentIssueId, State state){` |
| GL-models_Issue-097 | `app/models/Issue.java:851` | `public static int countOpenIssuesByUser(User user) {` |
| GL-models_Issue-098 | `app/models/Issue.java:859` | `public IssueSharer findSharerByUserId(Long id){` |
| GL-models_Issue-099 | `app/models/Issue.java:869` | `public IssueComment findCommentByCommentId(Long id) {` |
| GL-models_Issue-102 | `app/models/Issue.java:892` | `public static Issue from(Posting posting) {` |
| GL-models_NonRangedCodeCommentThread-001 | `app/models/NonRangedCodeCommentThread.java:29` | `/**` |
| GL-models_OrganizationUser-001 | `app/models/OrganizationUser.java:32` | `@Entity` |
| GL-models_OrganizationUser-003 | `app/models/OrganizationUser.java:39` | `public static final Finder<Long, OrganizationUser> find = new Finder<>(Long.class, OrganizationUser.` |
| GL-models_OrganizationUser-004 | `app/models/OrganizationUser.java:42` | `@Id` |
| GL-models_OrganizationUser-005 | `app/models/OrganizationUser.java:46` | `@ManyToOne` |
| GL-models_OrganizationUser-006 | `app/models/OrganizationUser.java:50` | `@ManyToOne` |
| GL-models_OrganizationUser-007 | `app/models/OrganizationUser.java:54` | `@ManyToOne` |
| GL-models_OrganizationUser-008 | `app/models/OrganizationUser.java:58` | `public static List<OrganizationUser> findAdminsOf(Organization organization) {` |
| GL-models_OrganizationUser-015 | `app/models/OrganizationUser.java:97` | `public static String roleTypeOf(User user, Organization organization) {` |
| GL-models_OrganizationUser-016 | `app/models/OrganizationUser.java:115` | `private static boolean contains(Organization organization, User user, RoleType roleType) {` |
| GL-models_OrganizationUser-017 | `app/models/OrganizationUser.java:126` | `private static boolean contains(Long organizationId, Long userId, RoleType roleType) {` |
| GL-models_OrganizationUser-018 | `app/models/OrganizationUser.java:135` | `public static void assignRole(Long userId, Long organizationId, Long roleId) {` |
| GL-models_OrganizationUser-019 | `app/models/OrganizationUser.java:151` | `public static OrganizationUser findByOrganizationIdAndUserId(Long organizationId, Long userId) {` |
| GL-models_OrganizationUser-020 | `app/models/OrganizationUser.java:158` | `public static void create(Long userId, Long organizationId, Long roleId) {` |
| GL-models_OrganizationUser-021 | `app/models/OrganizationUser.java:167` | `public static void delete(Long organizationId, Long userId) {` |
| GL-models_OrganizationUser-022 | `app/models/OrganizationUser.java:176` | `public static boolean exist(Long organizationId, Long userId) {` |
| GL-models_OrganizationUser-023 | `app/models/OrganizationUser.java:181` | `public static List<OrganizationUser> findByUser(User user, int size) {` |
| GL-models_CandidateUser-001 | `app/models/CandidateUser.java:14` | `// Simple DTO for automatic user creation` |
| GL-models_CandidateUser-007 | `app/models/CandidateUser.java:28` | `public CandidateUser(String name, String email) {` |
| GL-models_CandidateUser-008 | `app/models/CandidateUser.java:34` | `public CandidateUser(String name, String email, String loginId, String password, boolean isGuest) {` |
| GL-models_CandidateUser-018 | `app/models/CandidateUser.java:91` | `@Override` |
| GL-models_UserIdent-001 | `app/models/UserIdent.java:26` | `/**` |
| GL-models_UserIdent-005 | `app/models/UserIdent.java:42` | `public UserIdent(User author) {` |
| GL-models_NotificationMail-001 | `app/models/NotificationMail.java:58` | `@Entity` |
| GL-models_NotificationMail-006 | `app/models/NotificationMail.java:70` | `@Id` |
| GL-models_NotificationMail-007 | `app/models/NotificationMail.java:74` | `@OneToOne` |
| GL-models_NotificationMail-008 | `app/models/NotificationMail.java:78` | `public static final Finder<Long, NotificationMail> find = new Finder<>(Long.class,` |
| GL-models_NotificationMail-009 | `app/models/NotificationMail.java:82` | `public static void onStart() {` |
| GL-models_NotificationMail-010 | `app/models/NotificationMail.java:95` | `private static boolean notificationEnabled() {` |
| GL-models_NotificationMail-011 | `app/models/NotificationMail.java:102` | `/**` |
| GL-models_NotificationMail-012 | `app/models/NotificationMail.java:203` | `/**` |
| GL-models_NotificationMail-013 | `app/models/NotificationMail.java:322` | `/**` |
| GL-models_NotificationMail-014 | `app/models/NotificationMail.java:382` | `/**` |
| GL-models_NotificationMail-023 | `app/models/NotificationMail.java:530` | `private static void sendMail(INotificationEvent event, Set<MailRecipient> toList, Set<MailRecipient>` |
| GL-models_NotificationMail-024 | `app/models/NotificationMail.java:601` | `private static String removeHeadAnchor(String htmlText) {` |
| GL-models_NotificationMail-025 | `app/models/NotificationMail.java:606` | `@Nullable` |
| GL-models_NotificationMail-028 | `app/models/NotificationMail.java:684` | `/**` |
| GL-models_NotificationMail-029 | `app/models/NotificationMail.java:727` | `private static void handleImages(Document doc){` |
| GL-models_YobiUpdate-001 | `app/models/YobiUpdate.java:38` | `public class YobiUpdate {` |
| GL-models_YobiUpdate-003 | `app/models/YobiUpdate.java:43` | `private static final Long UPDATE_NOTIFICATION_INTERVAL_IN_MILLIS = Configuration.root()` |
| GL-models_YobiUpdate-004 | `app/models/YobiUpdate.java:46` | `private static final String UPDATE_REPOSITORY_URL = Configuration.root()` |
| GL-models_YobiUpdate-005 | `app/models/YobiUpdate.java:49` | `private static final String RELEASE_URL_FORMAT = Configuration.root()` |
| GL-models_YobiUpdate-008 | `app/models/YobiUpdate.java:60` | `public static void onStart() {` |
| GL-models_YobiUpdate-011 | `app/models/YobiUpdate.java:92` | `public static void refreshVersionToUpdate() throws GitAPIException {` |
| GL-models_YobiUpdate-012 | `app/models/YobiUpdate.java:97` | `/**` |
| GL-models_Milestone-001 | `app/models/Milestone.java:46` | `@Entity` |
| GL-models_Milestone-003 | `app/models/Milestone.java:53` | `public static final Finder<Long, Milestone> find = new Finder<>(Long.class, Milestone.class);` |
| GL-models_Milestone-006 | `app/models/Milestone.java:62` | `@Id` |
| GL-models_Milestone-007 | `app/models/Milestone.java:66` | `@Constraints.Required` |
| GL-models_Milestone-008 | `app/models/Milestone.java:70` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_Milestone-009 | `app/models/Milestone.java:74` | `@Lob` |
| GL-models_Milestone-010 | `app/models/Milestone.java:78` | `@Constraints.Required` |
| GL-models_Milestone-011 | `app/models/Milestone.java:82` | `@ManyToOne` |
| GL-models_Milestone-012 | `app/models/Milestone.java:86` | `@OneToMany(mappedBy = "milestone")` |
| GL-models_Milestone-013 | `app/models/Milestone.java:90` | `public void delete() {` |
| GL-models_Milestone-017 | `app/models/Milestone.java:117` | `public List<Issue> sortedByNumberOfIssue(){` |
| GL-models_Milestone-018 | `app/models/Milestone.java:129` | `public List<Issue> sortedByNumberOfOpenIssue(){` |
| GL-models_Milestone-019 | `app/models/Milestone.java:140` | `public List<Issue> sortedByNumberOfClosedIssue(){` |
| GL-models_Milestone-022 | `app/models/Milestone.java:161` | `public static Milestone findById(Long id) {` |
| GL-models_Milestone-023 | `app/models/Milestone.java:166` | `public static List<Milestone> findByProjectId(Long projectId) {` |
| GL-models_Milestone-024 | `app/models/Milestone.java:171` | `public static List<Milestone> findClosedMilestones(Long projectId) {` |
| GL-models_Milestone-025 | `app/models/Milestone.java:176` | `public static List<Milestone> findOpenMilestones(Long projectId) {` |
| GL-models_Milestone-026 | `app/models/Milestone.java:181` | `public static Milestone findMilestoneByTitle(@Nonnull Project project, String title) {` |
| GL-models_Milestone-030 | `app/models/Milestone.java:263` | `public void updateWith(Milestone newMilestone) {` |
| GL-models_Milestone-031 | `app/models/Milestone.java:272` | `/**` |
| GL-models_Milestone-033 | `app/models/Milestone.java:294` | `public String until() {` |
| GL-models_Milestone-035 | `app/models/Milestone.java:316` | `@Override` |
| GL-models_Milestone-036 | `app/models/Milestone.java:337` | `public void open() {` |
| GL-models_Milestone-037 | `app/models/Milestone.java:343` | `public void close() {` |
| GL-models_Milestone-039 | `app/models/Milestone.java:354` | `public static int countOpened(Project project) {` |
| GL-models_CommitComment-001 | `app/models/CommitComment.java:34` | `@Entity` |
| GL-models_CommitComment-003 | `app/models/CommitComment.java:39` | `public static final Finder<Long, CommitComment> find = new Finder<>(Long.class, CommitComment.class)` |
| GL-models_CommitComment-004 | `app/models/CommitComment.java:42` | `@Transient` |
| GL-models_CommitComment-006 | `app/models/CommitComment.java:49` | `public CommitComment() {` |
| GL-models_CommitComment-007 | `app/models/CommitComment.java:54` | `@Override` |
| GL-models_CommitComment-008 | `app/models/CommitComment.java:89` | `public static int count(Project project, String commitId, String path){` |
| GL-models_CommitComment-009 | `app/models/CommitComment.java:105` | `public static int countByCommits(Project project, List<PullRequestCommit> commits) {` |
| GL-models_CommitComment-010 | `app/models/CommitComment.java:117` | `public static List<CommitComment> findByCommits(Project project, List<PullRequestCommit> commits) {` |
| GL-models_CommitComment-011 | `app/models/CommitComment.java:126` | `public String groupKey() {` |
| GL-models_CommitComment-012 | `app/models/CommitComment.java:132` | `public boolean threadEquals(CommitComment other) {` |
| GL-models_CommitComment-014 | `app/models/CommitComment.java:145` | `public boolean hasLocation() {` |
| GL-models_FavoriteProject-001 | `app/models/FavoriteProject.java:19` | `@Entity` |
| GL-models_FavoriteProject-002 | `app/models/FavoriteProject.java:22` | `public static Finder<Long, FavoriteProject> finder = new Finder<>(Long.class, FavoriteProject.class)` |
| GL-models_FavoriteProject-003 | `app/models/FavoriteProject.java:25` | `@Id` |
| GL-models_FavoriteProject-004 | `app/models/FavoriteProject.java:29` | `@ManyToOne` |
| GL-models_FavoriteProject-005 | `app/models/FavoriteProject.java:33` | `@OneToOne` |
| GL-models_FavoriteProject-009 | `app/models/FavoriteProject.java:51` | `public static void updateFavoriteProject(@Nonnull Project project){` |
| GL-models_FavoriteProject-010 | `app/models/FavoriteProject.java:63` | `public static FavoriteProject findByProjectId(Long userId, Long projectId){` |
| GL-models_LinkedAccount-001 | `app/models/LinkedAccount.java:11` | `@Entity` |
| GL-models_LinkedAccount-003 | `app/models/LinkedAccount.java:18` | `@Id` |
| GL-models_LinkedAccount-004 | `app/models/LinkedAccount.java:22` | `@ManyToOne` |
| GL-models_LinkedAccount-007 | `app/models/LinkedAccount.java:31` | `public static final Finder<Long, LinkedAccount> find = new Finder<Long, LinkedAccount>(` |
| GL-models_LinkedAccount-008 | `app/models/LinkedAccount.java:35` | `public static LinkedAccount findByProviderKey(final UserCredential userCredential, String key) {` |
| GL-models_LinkedAccount-009 | `app/models/LinkedAccount.java:41` | `public static LinkedAccount create(final AuthUser authUser) {` |
| GL-models_LinkedAccount-010 | `app/models/LinkedAccount.java:48` | `public void update(final AuthUser authUser) {` |
| GL-models_LinkedAccount-011 | `app/models/LinkedAccount.java:54` | `public static LinkedAccount create(final LinkedAccount acc) {` |
| GL-models_ProjectUser-001 | `app/models/ProjectUser.java:34` | `@Entity` |
| GL-models_ProjectUser-003 | `app/models/ProjectUser.java:41` | `private static Finder<Long, ProjectUser> find = new Finder<>(Long.class, ProjectUser.class);` |
| GL-models_ProjectUser-004 | `app/models/ProjectUser.java:44` | `@Id` |
| GL-models_ProjectUser-005 | `app/models/ProjectUser.java:48` | `@ManyToOne` |
| GL-models_ProjectUser-006 | `app/models/ProjectUser.java:52` | `@ManyToOne` |
| GL-models_ProjectUser-007 | `app/models/ProjectUser.java:56` | `@ManyToOne` |
| GL-models_ProjectUser-008 | `app/models/ProjectUser.java:60` | `public ProjectUser(Long userId, Long projectId, Long roleId) {` |
| GL-models_ProjectUser-009 | `app/models/ProjectUser.java:67` | `public static void create(Long userId, Long projectId, Long roleId) {` |
| GL-models_ProjectUser-010 | `app/models/ProjectUser.java:73` | `public static void delete(Long userId, Long projectId) {` |
| GL-models_ProjectUser-011 | `app/models/ProjectUser.java:81` | `public static void assignRole(Long userId, Long projectId, Long roleId) {` |
| GL-models_ProjectUser-012 | `app/models/ProjectUser.java:92` | `/**` |
| GL-models_ProjectUser-013 | `app/models/ProjectUser.java:105` | `public static ProjectUser findByIds(Long userId, Long projectId) {` |
| GL-models_ProjectUser-014 | `app/models/ProjectUser.java:115` | `public static List<ProjectUser> findMemberListByProject(Long projectId) {` |
| GL-models_ProjectUser-015 | `app/models/ProjectUser.java:123` | `public static boolean checkOneMangerPerOneProject(Long userId, Long projectId) {` |
| GL-models_ProjectUser-018 | `app/models/ProjectUser.java:149` | `/**` |
| GL-models_ProjectUser-019 | `app/models/ProjectUser.java:164` | `public static ProjectUser findById(Long id) {` |
| GL-models_ProjectUser-020 | `app/models/ProjectUser.java:169` | `public static List<ProjectUser> findAll(){` |
| GL-models_ProjectUser-022 | `app/models/ProjectUser.java:185` | `public static String roleOf(String loginId, Project project) {` |
| GL-models_ProjectUser-023 | `app/models/ProjectUser.java:191` | `public static String roleOf(User user, Project project) {` |
| GL-models_Role-001 | `app/models/Role.java:34` | `@Entity` |
| GL-models_Role-003 | `app/models/Role.java:39` | `public static final Finder<Long, Role> find = new Finder<>(Long.class,` |
| GL-models_Role-004 | `app/models/Role.java:43` | `@Id` |
| GL-models_Role-007 | `app/models/Role.java:52` | `@OneToMany(mappedBy = "role", cascade = CascadeType.ALL)` |
| GL-models_Role-008 | `app/models/Role.java:56` | `@OneToMany(mappedBy = "role", cascade = CascadeType.ALL)` |
| GL-models_Role-009 | `app/models/Role.java:60` | `public static Role findById(Long id) {` |
| GL-models_Role-010 | `app/models/Role.java:65` | `public static Role findByRoleType(RoleType roleType) {` |
| GL-models_Role-011 | `app/models/Role.java:70` | `public static Role findByName(String name) {` |
| GL-models_Role-012 | `app/models/Role.java:75` | `public static Role findOrganizationRoleByIds(Long userId, Long organizationId) {` |
| GL-models_Role-013 | `app/models/Role.java:82` | `public static Role findRoleByIds(Long userId, Long projectId) {` |
| GL-models_Role-014 | `app/models/Role.java:89` | `public static List<Role> findProjectRoles() {` |
| GL-models_Role-015 | `app/models/Role.java:100` | `public static List<Role> findOrganizationRoles() {` |
| GL-models_PostReceiveMessage-001 | `app/models/PostReceiveMessage.java:28` | `/**` |
| GL-models_PostReceiveMessage-005 | `app/models/PostReceiveMessage.java:43` | `public PostReceiveMessage(Collection<ReceiveCommand> commands, Project project, User user) {` |
| GL-models_PullRequestEvent-001 | `app/models/PullRequestEvent.java:38` | `@Entity` |
| GL-models_PullRequestEvent-003 | `app/models/PullRequestEvent.java:44` | `public static final Finder<Long, PullRequestEvent> finder = new Finder<>(Long.class, PullRequestEven` |
| GL-models_PullRequestEvent-004 | `app/models/PullRequestEvent.java:47` | `@Id` |
| GL-models_PullRequestEvent-006 | `app/models/PullRequestEvent.java:53` | `@ManyToOne` |
| GL-models_PullRequestEvent-007 | `app/models/PullRequestEvent.java:57` | `@Enumerated(EnumType.STRING)` |
| GL-models_PullRequestEvent-009 | `app/models/PullRequestEvent.java:64` | `@Lob` |
| GL-models_PullRequestEvent-010 | `app/models/PullRequestEvent.java:67` | `@Lob` |
| GL-models_PullRequestEvent-011 | `app/models/PullRequestEvent.java:71` | `@Override` |
| GL-models_PullRequestEvent-012 | `app/models/PullRequestEvent.java:77` | `public static void addFromNotificationEvent(NotificationEvent notiEvent, PullRequest pullRequest) {` |
| GL-models_PullRequestEvent-013 | `app/models/PullRequestEvent.java:90` | `private static void add(PullRequestEvent event) {` |
| GL-models_PullRequestEvent-015 | `app/models/PullRequestEvent.java:112` | `private static boolean needToDeleteEvent(PullRequestEvent lastEvent, PullRequestEvent currentEvent) ` |
| GL-models_PullRequestEvent-016 | `app/models/PullRequestEvent.java:120` | `public static void addStateEvent(User sender, PullRequest pullRequest, State state) {` |
| GL-models_PullRequestEvent-017 | `app/models/PullRequestEvent.java:132` | `public static void addMergeEvent(User sender, EventType eventType, State state, PullRequest pullRequ` |
| GL-models_PullRequestEvent-018 | `app/models/PullRequestEvent.java:144` | `public static void addCommitEvents(User sender, PullRequest pullRequest,` |
| GL-models_PullRequestEvent-019 | `app/models/PullRequestEvent.java:166` | `public static List<PullRequestEvent> findByPullRequest(PullRequest pullRequest) {` |
| GL-models_PullRequestEvent-020 | `app/models/PullRequestEvent.java:171` | `@Transient` |
| GL-models_Label-001 | `app/models/Label.java:34` | `/**` |
| GL-models_Label-003 | `app/models/Label.java:44` | `public static final Finder<Long, Label> find = new Finder<>(Long.class, Label.class);` |
| GL-models_Label-004 | `app/models/Label.java:47` | `@Id` |
| GL-models_Label-005 | `app/models/Label.java:51` | `@Required` |
| GL-models_Label-006 | `app/models/Label.java:55` | `@Required` |
| GL-models_Label-007 | `app/models/Label.java:59` | `@ManyToMany(mappedBy="labels")` |
| GL-models_Label-008 | `app/models/Label.java:63` | `/**` |
| GL-models_Label-009 | `app/models/Label.java:78` | `/**` |
| GL-models_Label-010 | `app/models/Label.java:94` | `/**` |
| GL-models_Label-011 | `app/models/Label.java:106` | `/**` |
| GL-models_Label-012 | `app/models/Label.java:130` | `/**` |
| GL-models_Assignee-001 | `app/models/Assignee.java:34` | `@Entity` |
| GL-models_Assignee-003 | `app/models/Assignee.java:41` | `@Id` |
| GL-models_Assignee-004 | `app/models/Assignee.java:45` | `@ManyToOne` |
| GL-models_Assignee-005 | `app/models/Assignee.java:50` | `@ManyToOne` |
| GL-models_Assignee-006 | `app/models/Assignee.java:55` | `@OneToMany(mappedBy = "assignee")` |
| GL-models_Assignee-007 | `app/models/Assignee.java:59` | `public static final Model.Finder<Long, Assignee> finder = new Finder<>(Long.class, Assignee.class);` |
| GL-models_Assignee-008 | `app/models/Assignee.java:62` | `public Assignee(Long userId, Long projectId) {` |
| GL-models_Assignee-009 | `app/models/Assignee.java:68` | `public static Assignee add(Long userId, Long projectId) {` |
| GL-models_Property-001 | `app/models/Property.java:37` | `@Entity` |
| GL-models_Property-002 | `app/models/Property.java:40` | `public static final Finder<Long, Property> find = new Finder<>(Long.class, Property.class);` |
| GL-models_Property-004 | `app/models/Property.java:46` | `@Id` |
| GL-models_Property-005 | `app/models/Property.java:50` | `@Enumerated(EnumType.STRING)` |
| GL-models_Property-006 | `app/models/Property.java:55` | `@Constraints.MaxLength(4000)` |
| GL-models_Property-007 | `app/models/Property.java:59` | `public static String get(Name name) {` |
| GL-models_Property-009 | `app/models/Property.java:77` | `public static void set(Name name, String value) {` |
| GL-models_Property-010 | `app/models/Property.java:90` | `public static void set(Name name, Long value) {` |
| GL-models_Property-011 | `app/models/Property.java:95` | `public static enum Name {` |
| GL-models_Property-012 | `app/models/Property.java:104` | `public static void onStart() {` |
| GL-models_Unwatch-001 | `app/models/Unwatch.java:29` | `@Entity` |
| GL-models_Unwatch-003 | `app/models/Unwatch.java:35` | `public static final Finder<Long, Unwatch> find = new Finder<>(Long.class, Unwatch.class);` |
| GL-models_Unwatch-004 | `app/models/Unwatch.java:38` | `public static List<Unwatch> findBy(ResourceType resourceType, String resourceId) {` |
| GL-models_Unwatch-005 | `app/models/Unwatch.java:43` | `public static Unwatch findBy(User watcher, ResourceType resourceType, String resourceId) {` |
| GL-models_Unwatch-006 | `app/models/Unwatch.java:48` | `public static List<Unwatch> findBy(User user, ResourceType resourceType) {` |
| GL-models_IssueComment-001 | `app/models/IssueComment.java:43` | `@Entity` |
| GL-models_IssueComment-003 | `app/models/IssueComment.java:48` | `public static final Finder<Long, IssueComment> find = new Finder<>(Long.class, IssueComment.class);` |
| GL-models_IssueComment-004 | `app/models/IssueComment.java:51` | `@ManyToOne` |
| GL-models_IssueComment-005 | `app/models/IssueComment.java:55` | `@OneToOne` |
| GL-models_IssueComment-006 | `app/models/IssueComment.java:59` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_IssueComment-007 | `app/models/IssueComment.java:68` | `public IssueComment(Issue issue, User author, String contents) {` |
| GL-models_IssueComment-008 | `app/models/IssueComment.java:75` | `/**` |
| GL-models_IssueComment-009 | `app/models/IssueComment.java:83` | `@Override` |
| GL-models_IssueComment-010 | `app/models/IssueComment.java:89` | `@Override` |
| GL-models_IssueComment-011 | `app/models/IssueComment.java:95` | `@Override` |
| GL-models_IssueComment-012 | `app/models/IssueComment.java:108` | `@Override` |
| GL-models_IssueComment-013 | `app/models/IssueComment.java:117` | `/**` |
| GL-models_IssueComment-014 | `app/models/IssueComment.java:151` | `public void addVoter(User user) {` |
| GL-models_IssueComment-015 | `app/models/IssueComment.java:158` | `public void removeVoter(User user) {` |
| GL-models_IssueComment-016 | `app/models/IssueComment.java:165` | `public static IssueComment from(PostingComment postingComment, Issue issue) {` |
| GL-models_IssueComment-017 | `app/models/IssueComment.java:183` | `public static List<IssueComment> from(Collection<PostingComment> postingComments, Issue issue) {` |
| GL-models_IssueComment-018 | `app/models/IssueComment.java:193` | `public static int countAllCreatedBy(User user) {` |
| GL-models_IssueComment-019 | `app/models/IssueComment.java:198` | `public static int countVoterOf(User user) {` |
| GL-models_IssueComment-020 | `app/models/IssueComment.java:210` | `@Override` |
| GL-models_UserCredential-001 | `app/models/UserCredential.java:22` | `@Entity` |
| GL-models_UserCredential-003 | `app/models/UserCredential.java:28` | `@Id` |
| GL-models_UserCredential-004 | `app/models/UserCredential.java:32` | `@OneToOne` |
| GL-models_UserCredential-006 | `app/models/UserCredential.java:39` | `@Constraints.Email` |
| GL-models_UserCredential-010 | `app/models/UserCredential.java:55` | `@OneToMany(cascade = CascadeType.ALL)` |
| GL-models_UserCredential-011 | `app/models/UserCredential.java:59` | `public static final Finder<Long, UserCredential> find = new Finder<Long, UserCredential>(` |
| GL-models_UserCredential-012 | `app/models/UserCredential.java:63` | `public static boolean existsByAuthUserIdentity(` |
| GL-models_UserCredential-014 | `app/models/UserCredential.java:78` | `public static UserCredential findByAuthUserIdentity(final AuthUserIdentity identity) {` |
| GL-models_UserCredential-015 | `app/models/UserCredential.java:86` | `public void merge(final UserCredential otherUser) {` |
| GL-models_UserCredential-016 | `app/models/UserCredential.java:98` | `public static UserCredential create(final AuthUser authUser) {` |
| GL-models_UserCredential-017 | `app/models/UserCredential.java:126` | `public static void merge(final AuthUser oldUser, final AuthUser newUser) {` |
| GL-models_UserCredential-019 | `app/models/UserCredential.java:142` | `public static void addLinkedAccount(final AuthUser oldUser,` |
| GL-models_UserCredential-020 | `app/models/UserCredential.java:150` | `public static UserCredential findByEmail(final String email) {` |
| GL-models_UserCredential-023 | `app/models/UserCredential.java:165` | `public static List<UserCredential> findByUserId(Long id){` |
| GL-models_UserCredential-024 | `app/models/UserCredential.java:170` | `@Override` |
| GL-models_PullRequestEventMessage-001 | `app/models/PullRequestEventMessage.java:27` | `public class PullRequestEventMessage {` |
| GL-models_PullRequestEventMessage-008 | `app/models/PullRequestEventMessage.java:42` | `public PullRequestEventMessage(User sender, Request request, Project project, String branch) {` |
| GL-models_PullRequestEventMessage-009 | `app/models/PullRequestEventMessage.java:50` | `public PullRequestEventMessage(User sender, Request request, PullRequest pullRequest) {` |
| GL-models_PullRequestEventMessage-010 | `app/models/PullRequestEventMessage.java:57` | `public PullRequestEventMessage(User sender, Request request, PullRequest pullRequest, EventType even` |
| GL-models_User-001 | `app/models/User.java:46` | `@Table(name = "n4user")` |
| GL-models_User-003 | `app/models/User.java:53` | `public static final Model.Finder<Long, User> find = new Finder<>(Long.class, User.class);` |
| GL-models_User-004 | `app/models/User.java:56` | `public static final Comparator<User> USER_NAME_COMPARATOR = new Comparator<User>() {` |
| GL-models_User-005 | `app/models/User.java:64` | `/**` |
| GL-models_User-007 | `app/models/User.java:73` | `public static final String LOGIN_ID_PATTERN = "[a-zA-Z0-9가-힣-]+([_.][a-z_.A-Z0-9가-힣-]+)*";` |
| GL-models_User-008 | `app/models/User.java:75` | `public static final String LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH = "[a-zA-Z0-9-/]+([_.][a-z_.A-Z0-9-/` |
| GL-models_User-009 | `app/models/User.java:78` | `public static final User anonymous = new NullUser();` |
| GL-models_User-010 | `app/models/User.java:81` | `@Id` |
| GL-models_User-011 | `app/models/User.java:85` | `/**` |
| GL-models_User-013 | `app/models/User.java:93` | `@Pattern(value = "^" + LOGIN_ID_PATTERN + "$", message = "user.wrongloginId.alert")` |
| GL-models_User-014 | `app/models/User.java:99` | `/**` |
| GL-models_User-017 | `app/models/User.java:109` | `@Constraints.Email(message = "user.wrongEmail.alert")` |
| GL-models_User-019 | `app/models/User.java:115` | `@Transient` |
| GL-models_User-020 | `app/models/User.java:119` | `@Transient` |
| GL-models_User-021 | `app/models/User.java:123` | `@Transient` |
| GL-models_User-022 | `app/models/User.java:127` | `@Transient` |
| GL-models_User-023 | `app/models/User.java:131` | `/**` |
| GL-models_User-024 | `app/models/User.java:139` | `@Enumerated(EnumType.STRING)` |
| GL-models_User-025 | `app/models/User.java:143` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_User-026 | `app/models/User.java:147` | `/**` |
| GL-models_User-027 | `app/models/User.java:154` | `/**` |
| GL-models_User-028 | `app/models/User.java:163` | `@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)` |
| GL-models_User-029 | `app/models/User.java:167` | `@OneToMany(mappedBy = "user")` |
| GL-models_User-030 | `app/models/User.java:171` | `/**` |
| GL-models_User-031 | `app/models/User.java:179` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_User-032 | `app/models/User.java:184` | `@ManyToMany(mappedBy = "receivers")` |
| GL-models_User-033 | `app/models/User.java:189` | `/**` |
| GL-models_User-034 | `app/models/User.java:203` | `@OneToMany(mappedBy = "user")` |
| GL-models_User-035 | `app/models/User.java:207` | `@OneToMany(mappedBy = "user")` |
| GL-models_User-036 | `app/models/User.java:211` | `@OneToMany(mappedBy = "user")` |
| GL-models_User-037 | `app/models/User.java:215` | `/**` |
| GL-models_User-038 | `app/models/User.java:222` | `@Transient` |
| GL-models_User-039 | `app/models/User.java:226` | `@Transient` |
| GL-models_User-040 | `app/models/User.java:231` | `@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)` |
| GL-models_User-042 | `app/models/User.java:238` | `public User() {` |
| GL-models_User-043 | `app/models/User.java:242` | `public User(Long id) {` |
| GL-models_User-048 | `app/models/User.java:288` | `/**` |
| GL-models_User-049 | `app/models/User.java:301` | `public List<Project> myProjects(String orderString) {` |
| GL-models_User-050 | `app/models/User.java:306` | `public List<Project> ownProjects() {` |
| GL-models_User-051 | `app/models/User.java:311` | `/**` |
| GL-models_User-052 | `app/models/User.java:326` | `/**` |
| GL-models_User-053 | `app/models/User.java:346` | `public static User findByUserToken(String token){` |
| GL-models_User-054 | `app/models/User.java:360` | `public static User findUserIfTokenExist(User user){` |
| GL-models_User-055 | `app/models/User.java:373` | `public static String extractUserTokenFromRequestHeader(Http.Request request) {` |
| GL-models_User-056 | `app/models/User.java:383` | `/**` |
| GL-models_User-057 | `app/models/User.java:415` | `public static User findByLoginKey(String loginIdOrEmail) {` |
| GL-models_User-058 | `app/models/User.java:426` | `/**` |
| GL-models_User-059 | `app/models/User.java:438` | `/**` |
| GL-models_User-060 | `app/models/User.java:451` | `/**` |
| GL-models_User-062 | `app/models/User.java:497` | `/**` |
| GL-models_User-063 | `app/models/User.java:510` | `public static List<User> findUsersByProjectAndOrganization(Project project) {` |
| GL-models_User-064 | `app/models/User.java:544` | `@Transient` |
| GL-models_User-065 | `app/models/User.java:556` | `/**` |
| GL-models_User-066 | `app/models/User.java:570` | `/**` |
| GL-models_User-067 | `app/models/User.java:578` | `/**` |
| GL-models_User-070 | `app/models/User.java:605` | `@Override` |
| GL-models_User-071 | `app/models/User.java:621` | `public Resource avatarAsResource() {` |
| GL-models_User-077 | `app/models/User.java:676` | `@Transactional` |
| GL-models_User-082 | `app/models/User.java:711` | `/**` |
| GL-models_User-083 | `app/models/User.java:723` | `public void enroll(Organization organization) {` |
| GL-models_User-084 | `app/models/User.java:730` | `/**` |
| GL-models_User-085 | `app/models/User.java:742` | `public void cancelEnroll(Organization organization) {` |
| GL-models_User-086 | `app/models/User.java:749` | `/**` |
| GL-models_User-087 | `app/models/User.java:764` | `public static boolean enrolled(Organization organization) {` |
| GL-models_User-088 | `app/models/User.java:773` | `@Override` |
| GL-models_User-089 | `app/models/User.java:783` | `public void changeState(UserState state) {` |
| GL-models_User-090 | `app/models/User.java:812` | `public String avatarUrl() {` |
| GL-models_User-091 | `app/models/User.java:823` | `public String avatarUrl(int size) {` |
| GL-models_User-092 | `app/models/User.java:834` | `/**` |
| GL-models_User-093 | `app/models/User.java:860` | `/**` |
| GL-models_User-094 | `app/models/User.java:883` | `/**` |
| GL-models_User-095 | `app/models/User.java:899` | `/**` |
| GL-models_User-096 | `app/models/User.java:912` | `public static List<User> findUsersByOrganization(Long organizationId, RoleType roleType) {` |
| GL-models_User-097 | `app/models/User.java:918` | `/**` |
| GL-models_User-098 | `app/models/User.java:929` | `/**` |
| GL-models_User-099 | `app/models/User.java:945` | `/**` |
| GL-models_User-100 | `app/models/User.java:956` | `public void visits(Project project) {` |
| GL-models_User-101 | `app/models/User.java:965` | `public void visits(Issue issue) {` |
| GL-models_User-102 | `app/models/User.java:975` | `public void visits(Posting posting) {` |
| GL-models_User-106 | `app/models/User.java:1017` | `public void createOrganization(Organization organization) {` |
| GL-models_User-107 | `app/models/User.java:1031` | `private void add(OrganizationUser ou) {` |
| GL-models_User-108 | `app/models/User.java:1036` | `public String toString() {` |
| GL-models_User-109 | `app/models/User.java:1045` | `@Override` |
| GL-models_User-110 | `app/models/User.java:1056` | `@Override` |
| GL-models_User-112 | `app/models/User.java:1087` | `public void updateFavoriteProject(@Nonnull Project project){` |
| GL-models_User-113 | `app/models/User.java:1096` | `public void updateFavoriteOrganization(@Nonnull Organization organization){` |
| GL-models_User-114 | `app/models/User.java:1105` | `public boolean toggleFavoriteProject(Long projectId) {` |
| GL-models_User-115 | `app/models/User.java:1122` | `public void removeFavoriteProject(Long projectId) {` |
| GL-models_User-117 | `app/models/User.java:1145` | `public boolean toggleFavoriteOrganization(Long organizationId) {` |
| GL-models_User-118 | `app/models/User.java:1161` | `private void removeFavoriteOrganization(Long organizationId) {` |
| GL-models_User-120 | `app/models/User.java:1184` | `public void updateFavoriteIssue(@Nonnull Issue issue){` |
| GL-models_User-121 | `app/models/User.java:1193` | `public boolean toggleFavoriteIssue(Long issueId) {` |
| GL-models_User-122 | `app/models/User.java:1209` | `public void removeFavoriteIssue(Long issueId) {` |
| GL-models_User-127 | `app/models/User.java:1293` | `public String extractDepartmentPart(){` |
| GL-models_IssueEvent-001 | `app/models/IssueEvent.java:23` | `@Entity` |
| GL-models_IssueEvent-003 | `app/models/IssueEvent.java:29` | `@Id` |
| GL-models_IssueEvent-007 | `app/models/IssueEvent.java:41` | `@ManyToOne` |
| GL-models_IssueEvent-008 | `app/models/IssueEvent.java:45` | `@Enumerated(EnumType.STRING)` |
| GL-models_IssueEvent-009 | `app/models/IssueEvent.java:49` | `@Lob` |
| GL-models_IssueEvent-010 | `app/models/IssueEvent.java:53` | `@Lob` |
| GL-models_IssueEvent-011 | `app/models/IssueEvent.java:57` | `private static final int DRAFT_TIME_IN_MILLIS = Configuration.root()` |
| GL-models_IssueEvent-012 | `app/models/IssueEvent.java:61` | `public static final Finder<Long, IssueEvent> find = new Finder<>(Long.class,` |
| GL-models_IssueEvent-013 | `app/models/IssueEvent.java:65` | `/**` |
| GL-models_IssueEvent-014 | `app/models/IssueEvent.java:113` | `/**` |
| GL-models_IssueEvent-017 | `app/models/IssueEvent.java:154` | `/**` |
| GL-models_IssueEvent-018 | `app/models/IssueEvent.java:176` | `public static void addFromNotificationEventWithoutSkipEvent(NotificationEvent notiEvent, Issue updat` |
| GL-models_IssueEvent-019 | `app/models/IssueEvent.java:189` | `@Override` |
| GL-models_IssueEvent-020 | `app/models/IssueEvent.java:195` | `public static Set<Issue> findReferredIssue(String message, Project project) {` |
| GL-models_CodeComment-001 | `app/models/CodeComment.java:36` | `@MappedSuperclass` |
| GL-models_CodeComment-003 | `app/models/CodeComment.java:41` | `public static final Finder<Long, CodeComment> find = new Finder<>(Long.class, CodeComment.class);` |
| GL-models_CodeComment-004 | `app/models/CodeComment.java:44` | `@Id` |
| GL-models_CodeComment-005 | `app/models/CodeComment.java:47` | `@ManyToOne` |
| GL-models_CodeComment-008 | `app/models/CodeComment.java:54` | `@Enumerated(EnumType.STRING)` |
| GL-models_CodeComment-009 | `app/models/CodeComment.java:57` | `@Lob @Constraints.Required` |
| GL-models_CodeComment-010 | `app/models/CodeComment.java:60` | `@Constraints.Required` |
| GL-models_CodeComment-014 | `app/models/CodeComment.java:70` | `public CodeComment() {` |
| GL-models_CodeComment-015 | `app/models/CodeComment.java:76` | `@Transient` |
| GL-models_CodeComment-016 | `app/models/CodeComment.java:84` | `@Override` |
| GL-models_CodeComment-017 | `app/models/CodeComment.java:90` | `public Duration ago() {` |
| GL-models_CodeComment-018 | `app/models/CodeComment.java:95` | `abstract public Resource asResource();` |
| GL-models_UserVerification-001 | `app/models/UserVerification.java:19` | `@Entity` |
| GL-models_UserVerification-003 | `app/models/UserVerification.java:25` | `public static final Model.Finder<Long, UserVerification> find = new Finder<>(Long.class, UserVerific` |
| GL-models_UserVerification-004 | `app/models/UserVerification.java:28` | `@Id` |
| GL-models_UserVerification-005 | `app/models/UserVerification.java:32` | `@OneToOne` |
| GL-models_UserVerification-009 | `app/models/UserVerification.java:45` | `public static synchronized UserVerification newVerification(User user) {` |
| GL-models_UserVerification-010 | `app/models/UserVerification.java:56` | `public static UserVerification findbyUser(User user) {` |
| GL-models_UserVerification-011 | `app/models/UserVerification.java:66` | `public static UserVerification findbyLoginIdAndVerificationCode(String loginId, String verificationC` |
| GL-models_UserVerification-013 | `app/models/UserVerification.java:88` | `public void invalidate(){` |
| GL-models_UserVerification-014 | `app/models/UserVerification.java:93` | `@Override` |
| GL-models_PullRequestCommit-001 | `app/models/PullRequestCommit.java:34` | `@Entity` |
| GL-models_PullRequestCommit-003 | `app/models/PullRequestCommit.java:41` | `public static final Finder<Long, PullRequestCommit> find = new Finder<>(Long.class, PullRequestCommi` |
| GL-models_PullRequestCommit-004 | `app/models/PullRequestCommit.java:44` | `@Id` |
| GL-models_PullRequestCommit-005 | `app/models/PullRequestCommit.java:48` | `@ManyToOne` |
| GL-models_PullRequestCommit-009 | `app/models/PullRequestCommit.java:58` | `@Lob` |
| GL-models_PullRequestCommit-012 | `app/models/PullRequestCommit.java:66` | `@Enumerated(EnumType.STRING)` |
| GL-models_PullRequestCommit-019 | `app/models/PullRequestCommit.java:114` | `@Transient` |
| GL-models_PullRequestCommit-020 | `app/models/PullRequestCommit.java:121` | `/**` |
| GL-models_PullRequestCommit-022 | `app/models/PullRequestCommit.java:143` | `public static PullRequestCommit findById(String id) {` |
| GL-models_PullRequestCommit-025 | `app/models/PullRequestCommit.java:162` | `public static PullRequestCommit bindPullRequestCommit(GitCommit commit, PullRequest pullRequest) {` |
| GL-models_PullRequestCommit-026 | `app/models/PullRequestCommit.java:176` | `public enum State {` |
| GL-models_Email-001 | `app/models/Email.java:40` | `@Entity` |
| GL-models_Email-003 | `app/models/Email.java:47` | `public static final Finder<Long, Email> find = new Finder<>(Long.class, Email.class);` |
| GL-models_Email-004 | `app/models/Email.java:50` | `/**` |
| GL-models_Email-005 | `app/models/Email.java:57` | `/**` |
| GL-models_Email-006 | `app/models/Email.java:64` | `/**` |
| GL-models_Email-007 | `app/models/Email.java:72` | `/**` |
| GL-models_Email-009 | `app/models/Email.java:81` | `@Transient` |
| GL-models_Email-010 | `app/models/Email.java:85` | `public static boolean exists(String newEmail, boolean valid) {` |
| GL-models_Email-011 | `app/models/Email.java:98` | `public boolean validate(String token) {` |
| GL-models_Email-012 | `app/models/Email.java:110` | `public static void deleteOtherInvalidEmails(String emailAddress) {` |
| GL-models_Email-013 | `app/models/Email.java:118` | `public void sendValidationEmail() {` |
| GL-models_Email-014 | `app/models/Email.java:126` | `public static Email findByEmail(String email, boolean isValid) {` |
| GL-models_Email-015 | `app/models/Email.java:131` | `private static ExpressionList<Email> findByEmailAndIsValid(String email, boolean isValid) {` |
| GL-models_IssueSharer-001 | `app/models/IssueSharer.java:17` | `@Entity` |
| GL-models_IssueSharer-003 | `app/models/IssueSharer.java:23` | `@Id` |
| GL-models_IssueSharer-006 | `app/models/IssueSharer.java:33` | `@OneToOne` |
| GL-models_IssueSharer-007 | `app/models/IssueSharer.java:37` | `@OneToOne` |
| GL-models_IssueSharer-010 | `app/models/IssueSharer.java:46` | `public static final Finder<Long, IssueSharer> find = new Finder<>(Long.class,` |
| GL-models_IssueSharer-011 | `app/models/IssueSharer.java:50` | `public static IssueSharer createSharer(String loginId, Issue issue) {` |
| GL-models_ProjectTransfer-001 | `app/models/ProjectTransfer.java:37` | `@Entity` |
| GL-models_ProjectTransfer-003 | `app/models/ProjectTransfer.java:44` | `public static final Finder<Long, ProjectTransfer> find = new Finder<>(Long.class, ProjectTransfer.cl` |
| GL-models_ProjectTransfer-004 | `app/models/ProjectTransfer.java:47` | `@Id` |
| GL-models_ProjectTransfer-005 | `app/models/ProjectTransfer.java:51` | `// who requested this transfer.` |
| GL-models_ProjectTransfer-006 | `app/models/ProjectTransfer.java:56` | `/**` |
| GL-models_ProjectTransfer-007 | `app/models/ProjectTransfer.java:64` | `@ManyToOne` |
| GL-models_ProjectTransfer-008 | `app/models/ProjectTransfer.java:68` | `@Temporal(TemporalType.TIMESTAMP)` |
| GL-models_ProjectTransfer-012 | `app/models/ProjectTransfer.java:81` | `public static ProjectTransfer requestNewTransfer(Project project, User sender, String destination) {` |
| GL-models_ProjectTransfer-014 | `app/models/ProjectTransfer.java:112` | `public static ProjectTransfer findValidOne(Long id) {` |
| GL-models_ProjectTransfer-015 | `app/models/ProjectTransfer.java:124` | `public static void deleteExisting(Project project, User sender, String destination) {` |
| GL-models_ProjectTransfer-016 | `app/models/ProjectTransfer.java:137` | `public Resource asResource() {` |
| GL-models_ProjectTransfer-017 | `app/models/ProjectTransfer.java:157` | `public static List<ProjectTransfer> findByProject(Project project) {` |
| GL-models_TimelineItem-001 | `app/models/TimelineItem.java:27` | `public interface TimelineItem {` |
| GL-models_TimelineItem-002 | `app/models/TimelineItem.java:29` | `/**` |
| GL-models_TimelineItem-003 | `app/models/TimelineItem.java:40` | `/**` |
| GL-models_ProjectMenuSetting-001 | `app/models/ProjectMenuSetting.java:30` | `@Entity` |
| GL-models_ProjectMenuSetting-003 | `app/models/ProjectMenuSetting.java:35` | `public static Finder<Long, ProjectMenuSetting> finder = new Finder<>(Long.class, ProjectMenuSetting.` |
| GL-models_ProjectMenuSetting-004 | `app/models/ProjectMenuSetting.java:38` | `@Id` |
| GL-models_ProjectMenuSetting-005 | `app/models/ProjectMenuSetting.java:41` | `@OneToOne` |
| GL-models_ProjectMenuSetting-012 | `app/models/ProjectMenuSetting.java:57` | `public ProjectMenuSetting() {}` |
| GL-models_ProjectMenuSetting-013 | `app/models/ProjectMenuSetting.java:60` | `public ProjectMenuSetting(ProjectMenuSetting projectMenuSetting) {` |
| GL-models_ProjectMenuSetting-014 | `app/models/ProjectMenuSetting.java:70` | `public void updateMenuSetting(ProjectMenuSetting setting){` |
| GL-models_ProjectMenuSetting-015 | `app/models/ProjectMenuSetting.java:81` | `@Override` |
| GL-models_Organization-001 | `app/models/Organization.java:34` | `@Entity` |
| GL-models_Organization-003 | `app/models/Organization.java:41` | `public static final Finder<Long, Organization> find = new Finder<>(Long.class, Organization.class);` |
| GL-models_Organization-004 | `app/models/Organization.java:44` | `@Id` |
| GL-models_Organization-005 | `app/models/Organization.java:48` | `@Constraints.Pattern(value = "^" + User.LOGIN_ID_PATTERN + "$", message = "user.wrongloginId.alert")` |
| GL-models_Organization-006 | `app/models/Organization.java:54` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_Organization-007 | `app/models/Organization.java:58` | `@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)` |
| GL-models_Organization-008 | `app/models/Organization.java:62` | `@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)` |
| GL-models_Organization-009 | `app/models/Organization.java:66` | `@ManyToMany(mappedBy = "enrolledOrganizations")` |
| GL-models_Organization-011 | `app/models/Organization.java:73` | `public void add(OrganizationUser ou) {` |
| GL-models_Organization-012 | `app/models/Organization.java:78` | `public static Organization findByName(String name) {` |
| GL-models_Organization-013 | `app/models/Organization.java:83` | `public static PagingList<Organization> findByNameLike(String name) {` |
| GL-models_Organization-017 | `app/models/Organization.java:113` | `@Transactional` |
| GL-models_Organization-019 | `app/models/Organization.java:162` | `/**` |
| GL-models_Organization-020 | `app/models/Organization.java:175` | `public static List<Organization> findAllOrganizations() {` |
| GL-models_Organization-021 | `app/models/Organization.java:187` | `public static List<Organization> findAllOrganizations(String loginId) {` |
| GL-models_Organization-022 | `app/models/Organization.java:214` | `/**` |
| GL-models_Organization-024 | `app/models/Organization.java:242` | `public void updateWith(Organization modifiedOrganization) throws IOException, ServletException {` |
| GL-models_Organization-025 | `app/models/Organization.java:250` | `private void updateProjects(String newOwner) throws IOException, ServletException {` |
| GL-models_SiteAdmin-001 | `app/models/SiteAdmin.java:21` | `@Entity` |
| GL-models_SiteAdmin-003 | `app/models/SiteAdmin.java:27` | `@Id` |
| GL-models_SiteAdmin-004 | `app/models/SiteAdmin.java:31` | `@OneToOne` |
| GL-models_SiteAdmin-006 | `app/models/SiteAdmin.java:37` | `public static final Model.Finder<Long, SiteAdmin> find = new Finder<>(Long.class, SiteAdmin.class);` |
| GL-models_SiteAdmin-007 | `app/models/SiteAdmin.java:40` | `public static boolean exists(User user) {` |
| GL-models_SiteAdmin-008 | `app/models/SiteAdmin.java:45` | `public static SiteAdmin findByUserLoginId(String userLoginId) {` |
| GL-models_SiteAdmin-009 | `app/models/SiteAdmin.java:50` | `public static User updateDefaultSiteAdmin(User user) {` |
| GL-models_UserAction-001 | `app/models/UserAction.java:16` | `@MappedSuperclass` |
| GL-models_UserAction-003 | `app/models/UserAction.java:21` | `@Id` |
| GL-models_UserAction-004 | `app/models/UserAction.java:25` | `@ManyToOne` |
| GL-models_UserAction-005 | `app/models/UserAction.java:29` | `@Enumerated(EnumType.STRING)` |
| GL-models_UserAction-007 | `app/models/UserAction.java:36` | `public static <T extends UserAction> List<T> findBy(Finder<Long, T> finder,` |
| GL-models_UserAction-008 | `app/models/UserAction.java:44` | `public static <T extends UserAction> T findBy(Finder<Long, T> finder, User subject,` |
| GL-models_UserAction-009 | `app/models/UserAction.java:58` | `public static <T extends UserAction> List<T> findBy(Finder<Long, T> finder, User subject,` |
| GL-models_UserAction-010 | `app/models/UserAction.java:66` | `public static <T extends UserAction> int countBy(Finder<Long, T> finder,` |
| GL-models_UserSetting-001 | `app/models/UserSetting.java:16` | `@Entity` |
| GL-models_UserSetting-003 | `app/models/UserSetting.java:22` | `public static final Model.Finder<Long, UserSetting> find = new Finder<>(Long.class, UserSetting.clas` |
| GL-models_UserSetting-004 | `app/models/UserSetting.java:25` | `@Id` |
| GL-models_UserSetting-005 | `app/models/UserSetting.java:29` | `@OneToOne` |
| GL-models_UserSetting-007 | `app/models/UserSetting.java:36` | `public UserSetting(User user) {` |
| GL-models_UserSetting-008 | `app/models/UserSetting.java:41` | `public static UserSetting findByUser(Long id){` |
| GL-models_AbstractPosting-001 | `app/models/AbstractPosting.java:29` | `@MappedSuperclass` |
| GL-models_AbstractPosting-002 | `app/models/AbstractPosting.java:32` | `public static final Finder<Long, AbstractPosting> finder = new Finder<>(Long.class, AbstractPosting.` |
| GL-models_AbstractPosting-006 | `app/models/AbstractPosting.java:42` | `@Id` |
| GL-models_AbstractPosting-007 | `app/models/AbstractPosting.java:46` | `@Constraints.Required` |
| GL-models_AbstractPosting-008 | `app/models/AbstractPosting.java:51` | `@Lob` |
| GL-models_AbstractPosting-009 | `app/models/AbstractPosting.java:55` | `@Lob` |
| GL-models_AbstractPosting-010 | `app/models/AbstractPosting.java:59` | `@Constraints.Required` |
| GL-models_AbstractPosting-011 | `app/models/AbstractPosting.java:64` | `@Constraints.Required` |
| GL-models_AbstractPosting-016 | `app/models/AbstractPosting.java:79` | `@Transient` |
| GL-models_AbstractPosting-017 | `app/models/AbstractPosting.java:83` | `@ManyToOne` |
| GL-models_AbstractPosting-019 | `app/models/AbstractPosting.java:90` | `// This field is only for ordering. This field should be persistent because` |
| GL-models_AbstractPosting-020 | `app/models/AbstractPosting.java:95` | `@Transient` |
| GL-models_AbstractPosting-021 | `app/models/AbstractPosting.java:99` | `abstract public int computeNumOfComments();` |
| GL-models_AbstractPosting-022 | `app/models/AbstractPosting.java:102` | `public AbstractPosting() {` |
| GL-models_AbstractPosting-023 | `app/models/AbstractPosting.java:108` | `public AbstractPosting(Project project, User author, String title, String body) {` |
| GL-models_AbstractPosting-024 | `app/models/AbstractPosting.java:117` | `/**` |
| GL-models_AbstractPosting-025 | `app/models/AbstractPosting.java:124` | `protected abstract void fixLastNumber();` |
| GL-models_AbstractPosting-028 | `app/models/AbstractPosting.java:137` | `/**` |
| GL-models_AbstractPosting-029 | `app/models/AbstractPosting.java:170` | `@Transactional` |
| GL-models_AbstractPosting-030 | `app/models/AbstractPosting.java:179` | `@Transactional` |
| GL-models_AbstractPosting-031 | `app/models/AbstractPosting.java:193` | `/**` |
| GL-models_AbstractPosting-032 | `app/models/AbstractPosting.java:203` | `public void updateNumber() {` |
| GL-models_AbstractPosting-033 | `app/models/AbstractPosting.java:209` | `public static <T> T findByNumber(Finder<Long, T> finder, Project project, Long number) {` |
| GL-models_AbstractPosting-034 | `app/models/AbstractPosting.java:214` | `public static <T> List<T> findByProject(Finder<Long, T> finder, Project project) {` |
| GL-models_AbstractPosting-035 | `app/models/AbstractPosting.java:219` | `public Duration ago() {` |
| GL-models_AbstractPosting-036 | `app/models/AbstractPosting.java:224` | `public Resource asResource(final ResourceType type) {` |
| GL-models_AbstractPosting-037 | `app/models/AbstractPosting.java:249` | `@Transient` |
| GL-models_AbstractPosting-038 | `app/models/AbstractPosting.java:257` | `@Transient` |
| GL-models_AbstractPosting-040 | `app/models/AbstractPosting.java:266` | `public void delete() {` |
| GL-models_AbstractPosting-041 | `app/models/AbstractPosting.java:277` | `public void deleteOnly() {` |
| GL-models_AbstractPosting-042 | `app/models/AbstractPosting.java:282` | `public void updateProperties() {` |
| GL-models_AbstractPosting-043 | `app/models/AbstractPosting.java:287` | `@Transient` |
| GL-models_AbstractPosting-044 | `app/models/AbstractPosting.java:293` | `/**` |
| GL-models_AbstractPosting-045 | `app/models/AbstractPosting.java:303` | `/**` |
| GL-models_AbstractPosting-046 | `app/models/AbstractPosting.java:319` | `protected void updateMention() {` |
| GL-models_AbstractPosting-047 | `app/models/AbstractPosting.java:326` | `public abstract void checkLabels() throws IssueLabel.IssueLabelException;` |
| GL-models_PageParam-001 | `app/models/PageParam.java:24` | `/**` |
| GL-models_PageParam-002 | `app/models/PageParam.java:33` | `// start from 0` |
| GL-models_PageParam-003 | `app/models/PageParam.java:37` | `// size of one page` |
| GL-models_PageParam-004 | `app/models/PageParam.java:41` | `public PageParam(int page, int size) {` |
| GL-models_IssueLabelCategory-001 | `app/models/IssueLabelCategory.java:35` | `@Entity` |
| GL-models_IssueLabelCategory-003 | `app/models/IssueLabelCategory.java:42` | `public static final Finder<Long, IssueLabelCategory> find = new Finder<>(Long.class, IssueLabelCateg` |
| GL-models_IssueLabelCategory-004 | `app/models/IssueLabelCategory.java:45` | `@Id` |
| GL-models_IssueLabelCategory-005 | `app/models/IssueLabelCategory.java:49` | `@Required` |
| GL-models_IssueLabelCategory-006 | `app/models/IssueLabelCategory.java:54` | `@Required(message="label.error.categoryName.empty")` |
| GL-models_IssueLabelCategory-007 | `app/models/IssueLabelCategory.java:59` | `@OneToMany(mappedBy="category", cascade = CascadeType.ALL)` |
| GL-models_IssueLabelCategory-008 | `app/models/IssueLabelCategory.java:63` | `/**` |
| GL-models_IssueLabelCategory-009 | `app/models/IssueLabelCategory.java:70` | `@Transient` |
| GL-models_IssueLabelCategory-010 | `app/models/IssueLabelCategory.java:79` | `public static IssueLabelCategory findByName(String name, Project project) {` |
| GL-models_IssueLabelCategory-011 | `app/models/IssueLabelCategory.java:87` | `public static IssueLabelCategory findBy(IssueLabelCategory instance) {` |
| GL-models_IssueLabelCategory-012 | `app/models/IssueLabelCategory.java:95` | `public static List<IssueLabelCategory> findByProject(Project project) {` |
| GL-models_IssueLabelCategory-013 | `app/models/IssueLabelCategory.java:103` | `@Override` |
| GL-models_IssueLabelCategory-014 | `app/models/IssueLabelCategory.java:124` | `@Override` |
| GL-models_UserProjectNotification-001 | `app/models/UserProjectNotification.java:16` | `/**` |
| GL-models_UserProjectNotification-003 | `app/models/UserProjectNotification.java:27` | `public static final Finder<Long, UserProjectNotification> find = new Finder<>(Long.class, UserProjec` |
| GL-models_UserProjectNotification-004 | `app/models/UserProjectNotification.java:30` | `@Id` |
| GL-models_UserProjectNotification-005 | `app/models/UserProjectNotification.java:34` | `@ManyToOne` |
| GL-models_UserProjectNotification-006 | `app/models/UserProjectNotification.java:38` | `@ManyToOne` |
| GL-models_UserProjectNotification-007 | `app/models/UserProjectNotification.java:42` | `@Enumerated(EnumType.STRING)` |
| GL-models_UserProjectNotification-010 | `app/models/UserProjectNotification.java:65` | `/**` |
| GL-models_UserProjectNotification-013 | `app/models/UserProjectNotification.java:102` | `public static UserProjectNotification findOne(User user, Project project, EventType notificationType` |
| GL-models_UserProjectNotification-014 | `app/models/UserProjectNotification.java:111` | `public void toggle(EventType notificationType) {` |
| GL-models_UserProjectNotification-015 | `app/models/UserProjectNotification.java:121` | `public static void unwatchExplictly(User user, Project project, EventType notiType) {` |
| GL-models_UserProjectNotification-016 | `app/models/UserProjectNotification.java:131` | `public static void watchExplictly(User user, Project project, EventType notiType) {` |
| GL-models_UserProjectNotification-017 | `app/models/UserProjectNotification.java:141` | `/**` |
| GL-models_UserProjectNotification-019 | `app/models/UserProjectNotification.java:167` | `public static Set<User> findEventWatchersByEventType(Long projectId, EventType eventType) {` |
| GL-models_UserProjectNotification-020 | `app/models/UserProjectNotification.java:172` | `public static Set<User> findEventUnwatchersByEventType(Long projectId, EventType eventType) {` |
| GL-models_UserProjectNotification-021 | `app/models/UserProjectNotification.java:177` | `private static Set<User> findByEventTypeAndOption(Long projectId, EventType eventType, boolean isAll` |
| GL-models_UserProjectNotification-022 | `app/models/UserProjectNotification.java:191` | `public static void deleteUnwatchedProjectNotifications(User user, Project project){` |
| GL-models_CodeRange-001 | `app/models/CodeRange.java:33` | `/**` |
| GL-models_CodeRange-003 | `app/models/CodeRange.java:54` | `public boolean endsWith(DiffLine line) {` |
| GL-models_CodeRange-004 | `app/models/CodeRange.java:60` | `public enum Side {` |
| GL-models_CodeRange-006 | `app/models/CodeRange.java:69` | `@Enumerated(EnumType.STRING)` |
| GL-models_CodeRange-007 | `app/models/CodeRange.java:73` | `@Constraints.Required` |
| GL-models_CodeRange-008 | `app/models/CodeRange.java:77` | `@Constraints.Required` |
| GL-models_CodeRange-009 | `app/models/CodeRange.java:81` | `@Enumerated(EnumType.STRING)` |
| GL-models_CodeRange-010 | `app/models/CodeRange.java:85` | `@Constraints.Required` |
| GL-models_CodeRange-011 | `app/models/CodeRange.java:89` | `@Constraints.Required` |
| GL-models_Attachment-001 | `app/models/Attachment.java:42` | `@Entity` |
| GL-models_Attachment-003 | `app/models/Attachment.java:47` | `public static final Finder<Long, Attachment> find = new Finder<>(Long.class, Attachment.class);` |
| GL-models_Attachment-006 | `app/models/Attachment.java:53` | `@Id` |
| GL-models_Attachment-007 | `app/models/Attachment.java:57` | `@Constraints.Required` |
| GL-models_Attachment-008 | `app/models/Attachment.java:61` | `@Constraints.Required` |
| GL-models_Attachment-009 | `app/models/Attachment.java:65` | `@Enumerated(EnumType.STRING)` |
| GL-models_Attachment-016 | `app/models/Attachment.java:104` | `/**` |
| GL-models_Attachment-017 | `app/models/Attachment.java:113` | `/**` |
| GL-models_Attachment-018 | `app/models/Attachment.java:135` | `/**` |
| GL-models_Attachment-019 | `app/models/Attachment.java:153` | `/**` |
| GL-models_Attachment-020 | `app/models/Attachment.java:164` | `/**` |
| GL-models_Attachment-021 | `app/models/Attachment.java:184` | `/**` |
| GL-models_Attachment-022 | `app/models/Attachment.java:210` | `/**` |
| GL-models_Attachment-023 | `app/models/Attachment.java:222` | `/**` |
| GL-models_Attachment-024 | `app/models/Attachment.java:249` | `private static File moveFileIntoUploadDirectory(File file, String hash)` |
| GL-models_Attachment-025 | `app/models/Attachment.java:264` | `/**` |
| GL-models_Attachment-026 | `app/models/Attachment.java:291` | `/**` |
| GL-models_Attachment-028 | `app/models/Attachment.java:309` | `/**` |
| GL-models_Attachment-029 | `app/models/Attachment.java:321` | `/**` |
| GL-models_Attachment-030 | `app/models/Attachment.java:334` | `/**` |
| GL-models_Attachment-031 | `app/models/Attachment.java:367` | `/**` |
| GL-models_Attachment-032 | `app/models/Attachment.java:379` | `/**` |
| GL-models_Attachment-033 | `app/models/Attachment.java:395` | `private String messageForLosingProject() {` |
| GL-models_Attachment-034 | `app/models/Attachment.java:400` | `/**` |
| GL-models_Attachment-036 | `app/models/Attachment.java:516` | `public static void onStart() {` |
| GL-models_Attachment-037 | `app/models/Attachment.java:521` | `@Override` |
| GL-models_Attachment-038 | `app/models/Attachment.java:536` | `public boolean store(InputStream inputStream, @Nullable String fileName,` |
| GL-models_Attachment-041 | `app/models/Attachment.java:626` | `private static String toHex(byte[] bytes) {` |
| GL-models_Attachment-042 | `app/models/Attachment.java:637` | `// Create the upload directory if it doesn't exist.` |
| GL-models_Attachment-043 | `app/models/Attachment.java:649` | `public static Attachment copyAs(Attachment other) {` |
| GL-models_Posting-001 | `app/models/Posting.java:28` | `@Entity` |
| GL-models_Posting-003 | `app/models/Posting.java:35` | `public static final Finder<Long, Posting> finder = new Finder<>(Long.class, Posting.class);` |
| GL-models_Posting-008 | `app/models/Posting.java:52` | `//ToDo: Sperate it from posting for online commit` |
| GL-models_Posting-009 | `app/models/Posting.java:57` | `//ToDo: Sperate it from posting for online commit` |
| GL-models_Posting-010 | `app/models/Posting.java:62` | `@OneToMany(cascade = CascadeType.ALL)` |
| GL-models_Posting-011 | `app/models/Posting.java:66` | `@ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)` |
| GL-models_Posting-013 | `app/models/Posting.java:81` | `public Posting(Project project, User author, String title, String body) {` |
| GL-models_Posting-014 | `app/models/Posting.java:86` | `/**` |
| GL-models_Posting-015 | `app/models/Posting.java:95` | `protected void fixLastNumber() {` |
| GL-models_Posting-016 | `app/models/Posting.java:100` | `/**` |
| GL-models_Posting-017 | `app/models/Posting.java:108` | `@OneToOne` |
| GL-models_Posting-018 | `app/models/Posting.java:112` | `public Posting() {` |
| GL-models_Posting-019 | `app/models/Posting.java:117` | `@Override` |
| GL-models_Posting-020 | `app/models/Posting.java:123` | `public static List<Posting> findNotices(Project project) {` |
| GL-models_Posting-021 | `app/models/Posting.java:132` | `public static List<Posting> findRecentlyCreated(Project project, int size) {` |
| GL-models_Posting-022 | `app/models/Posting.java:141` | `public static List<Posting> findRecentlyCreatedByDaysAgo(Project project, int days) {` |
| GL-models_Posting-023 | `app/models/Posting.java:148` | `/**` |
| GL-models_Posting-024 | `app/models/Posting.java:158` | `@Override` |
| GL-models_Posting-025 | `app/models/Posting.java:164` | `public static Posting findByNumber(Project project, long number) {` |
| GL-models_Posting-026 | `app/models/Posting.java:169` | `public static int countAllCreatedBy(User user) {` |
| GL-models_Posting-027 | `app/models/Posting.java:174` | `public static int countPostings(Project project) {` |
| GL-models_Posting-028 | `app/models/Posting.java:179` | `/**` |
| GL-models_Posting-029 | `app/models/Posting.java:188` | `public static Posting findREADMEPosting(Project project) {` |
| GL-models_Posting-030 | `app/models/Posting.java:196` | `public PostingComment findCommentByCommentId(Long id) {` |
| GL-models_Posting-031 | `app/models/Posting.java:206` | `public static Posting from(Issue issue) {` |
| GL-models_RecentIssue-001 | `app/models/RecentIssue.java:13` | `@Entity` |
| GL-models_RecentIssue-004 | `app/models/RecentIssue.java:21` | `public static Finder<Long, RecentIssue> find = new Finder<>(Long.class, RecentIssue.class);` |
| GL-models_RecentIssue-005 | `app/models/RecentIssue.java:24` | `@Id` |
| GL-models_RecentIssue-012 | `app/models/RecentIssue.java:41` | `public RecentIssue(User user, String title, Issue issue, Posting posting) {` |
| GL-models_RecentIssue-014 | `app/models/RecentIssue.java:61` | `public static void addNewIssue(final User user, final Issue issue){` |
| GL-models_RecentIssue-015 | `app/models/RecentIssue.java:73` | `public static void addNewPosting(final User user, final Posting posting){` |
| GL-models_RecentIssue-016 | `app/models/RecentIssue.java:86` | `@Transactional` |
| GL-models_RecentIssue-017 | `app/models/RecentIssue.java:101` | `@Transactional` |
| GL-models_RecentIssue-018 | `app/models/RecentIssue.java:116` | `public static void deletePreviousIssue(User user, Long issueId) {` |
| GL-models_RecentIssue-019 | `app/models/RecentIssue.java:134` | `public static void deletePreviousPosting(User user, Long postingId) {` |
| GL-models_RecentIssue-020 | `app/models/RecentIssue.java:145` | `private static void deleteOldestIfOverflow(User user) {` |
| GL-models_RecentIssue-021 | `app/models/RecentIssue.java:162` | `public static void deleteAll(User user) {` |
| GL-models_RecentIssue-023 | `app/models/RecentIssue.java:178` | `@Override` |
| GL-models_FavoriteIssue-001 | `app/models/FavoriteIssue.java:20` | `@Entity` |
| GL-models_FavoriteIssue-002 | `app/models/FavoriteIssue.java:23` | `public static Finder<Long, FavoriteIssue> find = new Finder<>(Long.class, FavoriteIssue.class);` |
| GL-models_FavoriteIssue-003 | `app/models/FavoriteIssue.java:26` | `@Id` |
| GL-models_FavoriteIssue-004 | `app/models/FavoriteIssue.java:30` | `@ManyToOne` |
| GL-models_FavoriteIssue-005 | `app/models/FavoriteIssue.java:34` | `@OneToOne` |
| GL-models_FavoriteIssue-006 | `app/models/FavoriteIssue.java:38` | `public FavoriteIssue(User user, Issue issue) {` |
| GL-models_FavoriteIssue-007 | `app/models/FavoriteIssue.java:44` | `public static void updateFavoriteIssue(@Nonnull Issue issue){` |
| GL-models_FavoriteIssue-008 | `app/models/FavoriteIssue.java:54` | `public static FavoriteIssue findByIssueId(Long userId, Long issueId){` |
| GL-models_Webhook-001 | `app/models/Webhook.java:51` | `/**` |
| GL-models_Webhook-003 | `app/models/Webhook.java:60` | `public static final Finder<Long, Webhook> find = new Finder<>(Long.class, Webhook.class);` |
| GL-models_Webhook-004 | `app/models/Webhook.java:63` | `/**` |
| GL-models_Webhook-005 | `app/models/Webhook.java:70` | `/**` |
| GL-models_Webhook-007 | `app/models/Webhook.java:85` | `/**` |
| GL-models_Webhook-008 | `app/models/Webhook.java:92` | `/**` |
| GL-models_Webhook-011 | `app/models/Webhook.java:105` | `/**` |
| GL-models_Webhook-012 | `app/models/Webhook.java:126` | `/**` |
| GL-models_Webhook-013 | `app/models/Webhook.java:150` | `public static List<Webhook> findByProject(Long projectId) {` |
| GL-models_Webhook-014 | `app/models/Webhook.java:155` | `public static void create(Long projectId, String payloadUrl, String secret, Boolean gitPush, Webhook` |
| GL-models_Webhook-015 | `app/models/Webhook.java:164` | `public static void delete(Long webhookId, Long projectId) {` |
| GL-models_Webhook-016 | `app/models/Webhook.java:169` | `/**` |
| GL-models_Webhook-020 | `app/models/Webhook.java:202` | `private String buildRequestMessage(String url, String message) {` |
| GL-models_Webhook-021 | `app/models/Webhook.java:215` | `// Issue` |
| GL-models_Webhook-022 | `app/models/Webhook.java:238` | `private String buildRequestBody(EventType eventType, User sender, Issue eventIssue) {` |
| GL-models_Webhook-023 | `app/models/Webhook.java:271` | `// Issue transfer` |
| GL-models_Webhook-026 | `app/models/Webhook.java:326` | `// Posting` |
| GL-models_Webhook-027 | `app/models/Webhook.java:346` | `private String buildRequestBody(EventType eventType, User sender, Posting eventPost) {` |
| GL-models_Webhook-028 | `app/models/Webhook.java:365` | `// Comment` |
| GL-models_Webhook-029 | `app/models/Webhook.java:388` | `private String buildRequestBody(EventType eventType, User sender, Comment eventComment) {` |
| GL-models_Webhook-030 | `app/models/Webhook.java:406` | `// Comment Detail (Slack)` |
| GL-models_Webhook-031 | `app/models/Webhook.java:417` | `// Pull Request` |
| GL-models_Webhook-032 | `app/models/Webhook.java:440` | `private String buildRequestBody(EventType eventType, User sender, PullRequest eventPullRequest) {` |
| GL-models_Webhook-033 | `app/models/Webhook.java:464` | `// Pull Request Review` |
| GL-models_Webhook-034 | `app/models/Webhook.java:487` | `private String buildRequestBody(EventType eventType, User sender, PullRequest eventPullRequest, Pull` |
| GL-models_Webhook-035 | `app/models/Webhook.java:506` | `// Pull Request Comment` |
| GL-models_Webhook-036 | `app/models/Webhook.java:529` | `private String buildRequestBody(EventType eventType, User sender, PullRequest eventPullRequest, Revi` |
| GL-models_Webhook-037 | `app/models/Webhook.java:539` | `// Pull Request Detail (Slack)` |
| GL-models_Webhook-038 | `app/models/Webhook.java:555` | `private String buildTextPropertyOnlyJSON(String requestMessage) {` |
| GL-models_Webhook-039 | `app/models/Webhook.java:562` | `private String buildRequestJsonWithAttachments(String requestMessage, ArrayNode attachments) {` |
| GL-models_Webhook-040 | `app/models/Webhook.java:570` | `private String buildRequestJsonWithThread(String requestMessage, ObjectNode thread) {` |
| GL-models_Webhook-041 | `app/models/Webhook.java:578` | `private ObjectNode buildTitleValueJSON(String title, String value, Boolean shorten) {` |
| GL-models_Webhook-042 | `app/models/Webhook.java:587` | `private ObjectNode buildAttachmentJSON(String text, ArrayNode detailFields, EventType eventType) {` |
| GL-models_Webhook-043 | `app/models/Webhook.java:597` | `private ObjectNode buildSenderJSON(User sender) {` |
| GL-models_Webhook-044 | `app/models/Webhook.java:608` | `private ObjectNode buildPusherJSON(User sender) {` |
| GL-models_Webhook-045 | `app/models/Webhook.java:616` | `private ObjectNode buildRepositoryJSON() {` |
| GL-models_Webhook-048 | `app/models/Webhook.java:670` | `private void sendRequest(String payload, Long webhookId, Resource resource) {` |
| GL-models_Webhook-049 | `app/models/Webhook.java:709` | `// Commit (message)` |
| GL-models_Webhook-050 | `app/models/Webhook.java:718` | `private String buildRequestBody(List<RevCommit> commits, List<String> refNames, User sender, String ` |
| GL-models_Webhook-051 | `app/models/Webhook.java:725` | `// Commit (json)` |
| GL-models_Webhook-052 | `app/models/Webhook.java:732` | `private String buildRequestBody(List<RevCommit> commits, List<String> refNames, User sender) {` |
| GL-models_Webhook-053 | `app/models/Webhook.java:758` | `private ObjectNode buildJSONFromCommit(Project project, RevCommit commit) {` |
| GL-models_Webhook-054 | `app/models/Webhook.java:785` | `@Override` |
| GL-models_OriginalEmail-001 | `app/models/OriginalEmail.java:32` | `@Entity` |
| GL-models_OriginalEmail-002 | `app/models/OriginalEmail.java:36` | `public static final Finder<Long, OriginalEmail> finder = new Finder<>(Long.class,` |
| GL-models_OriginalEmail-004 | `app/models/OriginalEmail.java:43` | `@Id` |
| GL-models_OriginalEmail-005 | `app/models/OriginalEmail.java:47` | `@Constraints.Required` |
| GL-models_OriginalEmail-006 | `app/models/OriginalEmail.java:52` | `@Constraints.Required` |
| GL-models_OriginalEmail-007 | `app/models/OriginalEmail.java:57` | `@Constraints.Required` |
| GL-models_OriginalEmail-008 | `app/models/OriginalEmail.java:61` | `@Constraints.Required` |
| GL-models_OriginalEmail-009 | `app/models/OriginalEmail.java:65` | `public static OriginalEmail findBy(Resource resource) {` |
| GL-models_OriginalEmail-010 | `app/models/OriginalEmail.java:73` | `public static boolean exists(Resource resource) {` |
| GL-models_OriginalEmail-011 | `app/models/OriginalEmail.java:78` | `public OriginalEmail(String messageId, Resource resource) {` |
| GL-models_OriginalEmail-012 | `app/models/OriginalEmail.java:85` | `@Override` |
| GL-models_PushedBranch-001 | `app/models/PushedBranch.java:36` | `/**` |
| GL-models_PushedBranch-003 | `app/models/PushedBranch.java:45` | `public static final Finder<Long, PushedBranch> find = new Finder<>(Long.class, PushedBranch.class);` |
| GL-models_PushedBranch-004 | `app/models/PushedBranch.java:47` | `public PushedBranch() {` |
| GL-models_PushedBranch-005 | `app/models/PushedBranch.java:51` | `public PushedBranch(Date pushedDate, String branch, Project project) {` |
| GL-models_PushedBranch-006 | `app/models/PushedBranch.java:58` | `@Id` |
| GL-models_PushedBranch-009 | `app/models/PushedBranch.java:66` | `@ManyToOne` |
| GL-models_PushedBranch-011 | `app/models/PushedBranch.java:75` | `public static void removeByPullRequestFrom(PullRequest pullRequest) {` |
| GL-models_PushedBranch-012 | `app/models/PushedBranch.java:83` | `public static List<PushedBranch> findByOwnerAndOriginalProject(User owner, Project originalProject) ` |
| GL-models_TitleHead-001 | `app/models/TitleHead.java:16` | `@Entity` |
| GL-models_TitleHead-003 | `app/models/TitleHead.java:23` | `public static final Finder<Long, TitleHead> finder = new Finder<>(Long.class, TitleHead.class);` |
| GL-models_TitleHead-004 | `app/models/TitleHead.java:26` | `@Id` |
| GL-models_TitleHead-005 | `app/models/TitleHead.java:30` | `@ManyToOne` |
| GL-models_TitleHead-008 | `app/models/TitleHead.java:40` | `public static List<TitleHead> findByProject(Project project, String query) {` |
| GL-models_TitleHead-009 | `app/models/TitleHead.java:48` | `public static TitleHead findByHeadKeyword(Project project, String headKeyword) {` |
| GL-models_TitleHead-010 | `app/models/TitleHead.java:60` | `public static void newHeadKeyword(Project project, String headKeyword) {` |
| GL-models_TitleHead-011 | `app/models/TitleHead.java:75` | `public static void reduceHeadKeyword(Project project, String headKeyword) {` |
| GL-models_TitleHead-012 | `app/models/TitleHead.java:88` | `public static void saveTitleHeadKeyword(Project project, String title) {` |
| GL-models_TitleHead-013 | `app/models/TitleHead.java:100` | `private static String removeBracket(String trimmed) {` |
| GL-models_TitleHead-015 | `app/models/TitleHead.java:112` | `public static void deleteTitleHeadKeyword(Project project, String title) {` |
| GL-models_CodeCommentThread-001 | `app/models/CodeCommentThread.java:39` | `/**` |
| GL-models_CodeCommentThread-003 | `app/models/CodeCommentThread.java:49` | `public static final Finder<Long, CodeCommentThread> find = new Finder<>(Long.class, CodeCommentThrea` |
| GL-models_CodeCommentThread-004 | `app/models/CodeCommentThread.java:52` | `@Embedded` |
| GL-models_CodeCommentThread-007 | `app/models/CodeCommentThread.java:61` | `@Transient` |
| GL-models_CodeCommentThread-008 | `app/models/CodeCommentThread.java:65` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_CodeCommentThread-010 | `app/models/CodeCommentThread.java:74` | `private String unexpectedSideMessage(Side side) {` |
| GL-models_SearchResult-001 | `app/models/SearchResult.java:32` | `public class SearchResult {` |
| GL-models_SearchResult-020 | `app/models/SearchResult.java:74` | `public List<String> makeSnippets(String contents, int threshold) {` |
| GL-models_SearchResult-021 | `app/models/SearchResult.java:111` | `private List<Integer> findIndexes(String contents, String keyword) {` |
| GL-models_SearchResult-022 | `app/models/SearchResult.java:122` | `private int beginIndex(int index, int threshold) {` |
| GL-models_SearchResult-023 | `app/models/SearchResult.java:127` | `private int endIndex(int keywordEndIndex, int contentLength, int threshold) {` |
| GL-models_SearchResult-024 | `app/models/SearchResult.java:133` | `public void updateSearchType() {` |
| GL-models_SearchResult-025 | `app/models/SearchResult.java:182` | `private class BeginAndEnd {` |
| GL-models_Watch-001 | `app/models/Watch.java:40` | `@Entity` |
| GL-models_Watch-003 | `app/models/Watch.java:46` | `public static final Finder<Long, Watch> find = new Finder<>(Long.class, Watch.class);` |
| GL-models_Watch-004 | `app/models/Watch.java:49` | `public static List<Watch> findBy(ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-005 | `app/models/Watch.java:54` | `public static Watch findBy(User watcher, ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-006 | `app/models/Watch.java:59` | `public static List<Watch> findBy(User user, ResourceType resourceType) {` |
| GL-models_Watch-007 | `app/models/Watch.java:64` | `public static int countBy(ResourceType type, String id) {` |
| GL-models_Watch-008 | `app/models/Watch.java:69` | `public static void watch(Resource resource) {` |
| GL-models_Watch-009 | `app/models/Watch.java:74` | `@Transactional` |
| GL-models_Watch-010 | `app/models/Watch.java:80` | `public static void watch(User user, ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-011 | `app/models/Watch.java:97` | `public static void unwatch(Resource resource) {` |
| GL-models_Watch-012 | `app/models/Watch.java:102` | `public static void unwatch(User user, Resource resource) {` |
| GL-models_Watch-013 | `app/models/Watch.java:107` | `public static void unwatch(User user, ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-014 | `app/models/Watch.java:124` | `public static Set<User> findWatchers(Resource target) {` |
| GL-models_Watch-015 | `app/models/Watch.java:129` | `public static Set<User> findWatchers(ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-016 | `app/models/Watch.java:138` | `public static Set<User> findUnwatchers(Resource target) {` |
| GL-models_Watch-017 | `app/models/Watch.java:143` | `public static Set<User> findUnwatchers(ResourceType resourceType, String resourceId) {` |
| GL-models_Watch-018 | `app/models/Watch.java:152` | `public static List<String> findWatchedResourceIds(User user, ResourceType resourceType) {` |
| GL-models_Watch-022 | `app/models/Watch.java:182` | `public static Set<User> findActualWatchers(` |
| GL-models_ReviewComment-001 | `app/models/ReviewComment.java:34` | `/**` |
| GL-models_ReviewComment-003 | `app/models/ReviewComment.java:42` | `public static final Finder<Long, ReviewComment> find = new Finder<>(Long.class, ReviewComment.class)` |
| GL-models_ReviewComment-004 | `app/models/ReviewComment.java:45` | `@Id` |
| GL-models_ReviewComment-005 | `app/models/ReviewComment.java:49` | `@Lob` |
| GL-models_ReviewComment-006 | `app/models/ReviewComment.java:54` | `@Constraints.Required` |
| GL-models_ReviewComment-007 | `app/models/ReviewComment.java:58` | `@Embedded` |
| GL-models_ReviewComment-008 | `app/models/ReviewComment.java:67` | `@ManyToOne(cascade = CascadeType.ALL)` |
| GL-models_ReviewComment-011 | `app/models/ReviewComment.java:81` | `public ReviewComment() {` |
| GL-models_ReviewComment-012 | `app/models/ReviewComment.java:86` | `public static List<ReviewComment> findByThread(Long threadId) {` |
| GL-models_ReviewComment-013 | `app/models/ReviewComment.java:94` | `public Resource asResource() {` |
| GL-models_ReviewComment-014 | `app/models/ReviewComment.java:130` | `@Override` |
| GL-models_History-001 | `app/models/History.java:30` | `public class History {` |
| GL-models_History-029 | `app/models/History.java:149` | `public static List<History> makeHistory(String userName, Project project,` |
| GL-models_History-030 | `app/models/History.java:164` | `private static void buildPullRequestsHistory(String userName, Project project, List<PullRequest> pul` |
| GL-models_History-031 | `app/models/History.java:181` | `private static void sort(List<History> histories) {` |
| GL-models_History-032 | `app/models/History.java:191` | `private static void buildPostingHistory(String userName, Project project, List<Posting> postings, Li` |
| GL-models_History-033 | `app/models/History.java:208` | `private static void buildIssueHistory(String userName, Project project, List<Issue> issues, List<His` |
| GL-models_History-034 | `app/models/History.java:225` | `private static void buildCommitHistory(String userName, Project project, List<Commit> commits, List<` |
| GL-models_NotificationEvent-001 | `app/models/NotificationEvent.java:55` | `@Entity` |
| GL-models_NotificationEvent-003 | `app/models/NotificationEvent.java:61` | `@Id` |
| GL-models_NotificationEvent-004 | `app/models/NotificationEvent.java:65` | `public static final Finder<Long, NotificationEvent> find = new Finder<>(Long.class, NotificationEven` |
| GL-models_NotificationEvent-007 | `app/models/NotificationEvent.java:74` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_NotificationEvent-008 | `app/models/NotificationEvent.java:78` | `@Temporal(TemporalType.TIMESTAMP)` |
| GL-models_NotificationEvent-009 | `app/models/NotificationEvent.java:82` | `@Enumerated(EnumType.STRING)` |
| GL-models_NotificationEvent-011 | `app/models/NotificationEvent.java:89` | `@Enumerated(EnumType.STRING)` |
| GL-models_NotificationEvent-012 | `app/models/NotificationEvent.java:93` | `@Lob @Basic(fetch=FetchType.EAGER)` |
| GL-models_NotificationEvent-013 | `app/models/NotificationEvent.java:97` | `@Lob @Basic(fetch=FetchType.EAGER)` |
| GL-models_NotificationEvent-014 | `app/models/NotificationEvent.java:101` | `@OneToOne(mappedBy="notificationEvent", cascade = CascadeType.ALL)` |
| GL-models_NotificationEvent-015 | `app/models/NotificationEvent.java:105` | `/**` |
| GL-models_NotificationEvent-016 | `app/models/NotificationEvent.java:121` | `@Override` |
| GL-models_NotificationEvent-019 | `app/models/NotificationEvent.java:137` | `@Transient` |
| GL-models_NotificationEvent-020 | `app/models/NotificationEvent.java:143` | `@Transient` |
| GL-models_NotificationEvent-021 | `app/models/NotificationEvent.java:254` | `@Transient` |
| GL-models_NotificationEvent-022 | `app/models/NotificationEvent.java:260` | `@Transient` |
| GL-models_NotificationEvent-023 | `app/models/NotificationEvent.java:272` | `/**` |
| GL-models_NotificationEvent-028 | `app/models/NotificationEvent.java:424` | `public boolean resourceExists() {` |
| GL-models_NotificationEvent-029 | `app/models/NotificationEvent.java:429` | `public static void add(NotificationEvent event) {` |
| GL-models_NotificationEvent-030 | `app/models/NotificationEvent.java:467` | `public static void addWithoutSkipEvent(NotificationEvent event) {` |
| GL-models_NotificationEvent-034 | `app/models/NotificationEvent.java:520` | `private static void filterReceivers(final NotificationEvent event) {` |
| GL-models_NotificationEvent-035 | `app/models/NotificationEvent.java:548` | `public static void deleteBy(Resource resource) {` |
| GL-models_NotificationEvent-036 | `app/models/NotificationEvent.java:556` | `/**` |
| GL-models_NotificationEvent-047 | `app/models/NotificationEvent.java:688` | `private static void webhookRequest(EventType eventTypes, Posting post) {` |
| GL-models_NotificationEvent-048 | `app/models/NotificationEvent.java:699` | `private static void webhookRequest(EventType eventTypes, Comment comment) {` |
| GL-models_NotificationEvent-049 | `app/models/NotificationEvent.java:710` | `private static void webhookRequest(EventType eventTypes, PullRequest pullRequest, ReviewComment revi` |
| GL-models_NotificationEvent-050 | `app/models/NotificationEvent.java:721` | `private static void webhookRequest(Project project, List<RevCommit> commits, List<String> refNames, ` |
| GL-models_NotificationEvent-051 | `app/models/NotificationEvent.java:734` | `/**` |
| GL-models_NotificationEvent-052 | `app/models/NotificationEvent.java:755` | `public static NotificationEvent afterPullRequestCommitChanged(User sender, PullRequest pullRequest) ` |
| GL-models_NotificationEvent-053 | `app/models/NotificationEvent.java:770` | `private static String newPullRequestCommitChangedMessage(PullRequest pullRequest) {` |
| GL-models_NotificationEvent-054 | `app/models/NotificationEvent.java:788` | `/**` |
| GL-models_NotificationEvent-055 | `app/models/NotificationEvent.java:802` | `/**` |
| GL-models_NotificationEvent-056 | `app/models/NotificationEvent.java:811` | `public static NotificationEvent forNewComment(User sender, PullRequest pullRequest, ReviewComment ne` |
| GL-models_NotificationEvent-057 | `app/models/NotificationEvent.java:827` | `public static NotificationEvent afterNewPullRequest(PullRequest pullRequest) {` |
| GL-models_NotificationEvent-058 | `app/models/NotificationEvent.java:833` | `public static NotificationEvent afterPullRequestUpdated(PullRequest pullRequest, State oldState, Sta` |
| GL-models_NotificationEvent-059 | `app/models/NotificationEvent.java:838` | `public static void afterNewComment(Comment comment) {` |
| GL-models_NotificationEvent-060 | `app/models/NotificationEvent.java:844` | `public static NotificationEvent forComment(Comment comment, User author, EventType eventType) {` |
| GL-models_NotificationEvent-061 | `app/models/NotificationEvent.java:859` | `public static NotificationEvent forUpdatedComment(Comment comment, User author) {` |
| GL-models_NotificationEvent-062 | `app/models/NotificationEvent.java:864` | `public static NotificationEvent forNewComment(Comment comment, User author) {` |
| GL-models_NotificationEvent-063 | `app/models/NotificationEvent.java:869` | `public static void afterNewCommentWithState(Comment comment, State state) {` |
| GL-models_NotificationEvent-064 | `app/models/NotificationEvent.java:888` | `public static NotificationEvent afterStateChanged(State oldState, Issue issue) {` |
| GL-models_NotificationEvent-065 | `app/models/NotificationEvent.java:902` | `public static NotificationEvent afterStateChanged(` |
| GL-models_NotificationEvent-066 | `app/models/NotificationEvent.java:938` | `public static NotificationEvent afterAssigneeChanged(User oldAssignee, Issue issue) {` |
| GL-models_NotificationEvent-068 | `app/models/NotificationEvent.java:973` | `public static NotificationEvent afterNewIssue(Issue issue) {` |
| GL-models_NotificationEvent-069 | `app/models/NotificationEvent.java:981` | `public static NotificationEvent forNewIssue(Issue issue, User author) {` |
| GL-models_NotificationEvent-070 | `app/models/NotificationEvent.java:992` | `public static NotificationEvent afterResourceDeleted(AbstractPosting item, User reuqestedUser) {` |
| GL-models_NotificationEvent-071 | `app/models/NotificationEvent.java:1008` | `public static NotificationEvent afterIssueBodyChanged(String oldBody, Issue issue) {` |
| GL-models_NotificationEvent-072 | `app/models/NotificationEvent.java:1024` | `public static NotificationEvent afterIssueMoved(Project previous, Issue issue, Supplier<Set<User>> g` |
| GL-models_NotificationEvent-073 | `app/models/NotificationEvent.java:1040` | `public static NotificationEvent afterIssueSharerChanged(Issue issue, String sharerLoginId, String ac` |
| GL-models_NotificationEvent-074 | `app/models/NotificationEvent.java:1059` | `private static Set<User> findSharer(String sharerLoginId) {` |
| GL-models_NotificationEvent-075 | `app/models/NotificationEvent.java:1066` | `public static NotificationEvent afterIssueLabelChanged(String addedLabels, String deletedLabels, Iss` |
| GL-models_NotificationEvent-076 | `app/models/NotificationEvent.java:1079` | `public static NotificationEvent afterMilestoneChanged(Long oldMilestoneId, Issue issue) {` |
| GL-models_NotificationEvent-078 | `app/models/NotificationEvent.java:1123` | `private static User findCurrentUserToBeExcluded(Long authorId) {` |
| GL-models_NotificationEvent-081 | `app/models/NotificationEvent.java:1186` | `private static Set<User> filterInactiveUsers(Set<User> receivers) {` |
| GL-models_NotificationEvent-083 | `app/models/NotificationEvent.java:1208` | `private static Set<User> findMembersOnlyFromWatchers(Project project) {` |
| GL-models_NotificationEvent-084 | `app/models/NotificationEvent.java:1220` | `private static Set<User> extractMembers(Project project) {` |
| GL-models_NotificationEvent-086 | `app/models/NotificationEvent.java:1237` | `public static void afterNewPost(Posting post) {` |
| GL-models_NotificationEvent-087 | `app/models/NotificationEvent.java:1243` | `public static void afterUpdatePosting(String oldValue, Posting post) {` |
| GL-models_NotificationEvent-088 | `app/models/NotificationEvent.java:1248` | `public static NotificationEvent forNewPosting(Posting post, User author) {` |
| GL-models_NotificationEvent-089 | `app/models/NotificationEvent.java:1259` | `public static NotificationEvent forUpdatePosting(String oldValue, Posting post, User author) {` |
| GL-models_NotificationEvent-090 | `app/models/NotificationEvent.java:1270` | `public static void afterNewCommitComment(Project project, ReviewComment comment,` |
| GL-models_NotificationEvent-091 | `app/models/NotificationEvent.java:1278` | `public static NotificationEvent forNewCommitComment(` |
| GL-models_NotificationEvent-092 | `app/models/NotificationEvent.java:1296` | `public static void afterNewSVNCommitComment(Project project, CommitComment codeComment)` |
| GL-models_NotificationEvent-093 | `app/models/NotificationEvent.java:1302` | `private static NotificationEvent forNewSVNCommitComment(` |
| GL-models_NotificationEvent-094 | `app/models/NotificationEvent.java:1320` | `public static void afterMemberRequest(Project project, User user, RequestState state) {` |
| GL-models_NotificationEvent-095 | `app/models/NotificationEvent.java:1352` | `public static void afterOrganizationMemberRequest(Organization organization, User user, RequestState` |
| GL-models_NotificationEvent-097 | `app/models/NotificationEvent.java:1399` | `public static NotificationEvent afterReviewed(PullRequest pullRequest, PullRequestReviewAction revie` |
| GL-models_NotificationEvent-099 | `app/models/NotificationEvent.java:1459` | `private static NotificationEvent createFrom(User sender, ResourceConvertible rc) {` |
| GL-models_NotificationEvent-103 | `app/models/NotificationEvent.java:1491` | `private static void includeAssigneeIfExist(Comment comment, Set<User> receivers) {` |
| GL-models_NotificationEvent-105 | `app/models/NotificationEvent.java:1510` | `private static String formatReplyTitle(AbstractPosting posting) {` |
| GL-models_NotificationEvent-106 | `app/models/NotificationEvent.java:1516` | `private static String formatNewTitle(AbstractPosting posting) {` |
| GL-models_NotificationEvent-111 | `app/models/NotificationEvent.java:1570` | `private static String formatNewTitle(PullRequest pullRequest) {` |
| GL-models_NotificationEvent-112 | `app/models/NotificationEvent.java:1576` | `private static String formatReplyTitle(PullRequest pullRequest) {` |
| GL-models_NotificationEvent-115 | `app/models/NotificationEvent.java:1603` | `private static String formatMemberRequestTitle(Project project, User user) {` |
| GL-models_NotificationEvent-116 | `app/models/NotificationEvent.java:1608` | `private static String formatMemberRequestCancelTitle(Project project, User user) {` |
| GL-models_NotificationEvent-117 | `app/models/NotificationEvent.java:1613` | `private static String formatMemberRequestCancelTitle(Organization organization, User user) {` |
| GL-models_NotificationEvent-118 | `app/models/NotificationEvent.java:1618` | `private static String formatMemberRequestTitle(Organization organization, User user) {` |
| GL-models_NotificationEvent-119 | `app/models/NotificationEvent.java:1623` | `private static String formatMemberAcceptTitle(Project project, User user) {` |
| GL-models_NotificationEvent-120 | `app/models/NotificationEvent.java:1628` | `private static String formatMemberAcceptTitle(Organization organization, User user) {` |
| GL-models_NotificationEvent-121 | `app/models/NotificationEvent.java:1633` | `/**` |
| GL-models_NotificationEvent-122 | `app/models/NotificationEvent.java:1653` | `/**` |
| GL-models_NotificationEvent-123 | `app/models/NotificationEvent.java:1673` | `private static Set<User> findOrganizationMembers(String mentionWord) {` |
| GL-models_NotificationEvent-124 | `app/models/NotificationEvent.java:1685` | `private static Set<User> findProjectMembers(String mentionWord) {` |
| GL-models_NotificationEvent-125 | `app/models/NotificationEvent.java:1702` | `public static void scheduleDeleteOldNotifications() {` |
| GL-models_NotificationEvent-126 | `app/models/NotificationEvent.java:1725` | `public static void onStart() {` |
| GL-models_NotificationEvent-127 | `app/models/NotificationEvent.java:1730` | `/**` |
| GL-models_NotificationEvent-129 | `app/models/NotificationEvent.java:1768` | `public static void afterCommentUpdated(Comment comment) {` |
| GL-models_NotificationEvent-130 | `app/models/NotificationEvent.java:1774` | `@Override` |
| GL-models_FavoriteOrganization-001 | `app/models/FavoriteOrganization.java:18` | `@Entity` |
| GL-models_FavoriteOrganization-002 | `app/models/FavoriteOrganization.java:21` | `public static Finder<Long, FavoriteOrganization> finder = new Finder<>(Long.class, FavoriteOrganizat` |
| GL-models_FavoriteOrganization-003 | `app/models/FavoriteOrganization.java:24` | `@Id` |
| GL-models_FavoriteOrganization-004 | `app/models/FavoriteOrganization.java:28` | `@ManyToOne` |
| GL-models_FavoriteOrganization-005 | `app/models/FavoriteOrganization.java:32` | `@OneToOne` |
| GL-models_FavoriteOrganization-008 | `app/models/FavoriteOrganization.java:47` | `public static void updateFavoriteOrganization(Organization organization) {` |
| GL-models_CommentThread-001 | `app/models/CommentThread.java:38` | `/**` |
| GL-models_CommentThread-003 | `app/models/CommentThread.java:48` | `public static final Finder<Long, CommentThread> find = new Finder<>(Long.class, CommentThread.class)` |
| GL-models_CommentThread-004 | `app/models/CommentThread.java:51` | `@Id` |
| GL-models_CommentThread-005 | `app/models/CommentThread.java:55` | `@Embedded` |
| GL-models_CommentThread-006 | `app/models/CommentThread.java:64` | `@OneToMany(mappedBy = "thread", cascade = CascadeType.REMOVE)` |
| GL-models_CommentThread-007 | `app/models/CommentThread.java:68` | `@Enumerated(EnumType.STRING)` |
| GL-models_CommentThread-008 | `app/models/CommentThread.java:72` | `@Constraints.Required` |
| GL-models_CommentThread-009 | `app/models/CommentThread.java:77` | `@ManyToOne` |
| GL-models_CommentThread-011 | `app/models/CommentThread.java:86` | `public static List<CommentThread> findByCommitId(String commitId) {` |
| GL-models_CommentThread-012 | `app/models/CommentThread.java:94` | `public static <T extends CommentThread> List<T> findByCommitId(Finder<Long, T> find,` |
| GL-models_CommentThread-013 | `app/models/CommentThread.java:105` | `public static List<CommentThread> findByCommitIdAndState(String commitId, ThreadState state) {` |
| GL-models_CommentThread-014 | `app/models/CommentThread.java:114` | `@Override` |
| GL-models_CommentThread-015 | `app/models/CommentThread.java:127` | `@ManyToOne` |
| GL-models_CommentThread-016 | `app/models/CommentThread.java:131` | `public Resource asResource() {` |
| GL-models_CommentThread-017 | `app/models/CommentThread.java:156` | `public void removeComment(ReviewComment reviewComment) {` |
| GL-models_CommentThread-018 | `app/models/CommentThread.java:162` | `public enum ThreadState {` |
| GL-models_CommentThread-019 | `app/models/CommentThread.java:167` | `public void addComment(ReviewComment reviewComment) {` |
| GL-models_CommentThread-021 | `app/models/CommentThread.java:183` | `/**` |
| GL-models_CommentThread-022 | `app/models/CommentThread.java:201` | `public static int count(PullRequest pullRequest, String commitId, String path) {` |
| GL-models_CommentThread-023 | `app/models/CommentThread.java:221` | `public static int countOnCommit(Project project, String commitId, String path) {` |
| GL-models_CommentThread-025 | `app/models/CommentThread.java:253` | `public boolean hasChildComments(){` |
| GL-models_CommentThread-026 | `app/models/CommentThread.java:262` | `public static void deleteByPullRequest(PullRequest pullRequest) {` |
| GL-models_Project-001 | `app/models/Project.java:46` | `@Entity` |
| GL-models_Project-003 | `app/models/Project.java:51` | `public static final play.db.ebean.Model.Finder <Long, Project> find = new Finder<>(Long.class, Proje` |
| GL-models_Project-005 | `app/models/Project.java:57` | `@Id` |
| GL-models_Project-006 | `app/models/Project.java:61` | `@Constraints.Required` |
| GL-models_Project-012 | `app/models/Project.java:79` | `@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-013 | `app/models/Project.java:83` | `@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-014 | `app/models/Project.java:87` | `@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-015 | `app/models/Project.java:91` | `@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-016 | `app/models/Project.java:95` | `/** Project Notification */` |
| GL-models_Project-020 | `app/models/Project.java:109` | `@ManyToMany` |
| GL-models_Project-021 | `app/models/Project.java:113` | `@ManyToOne` |
| GL-models_Project-022 | `app/models/Project.java:117` | `@OneToMany(mappedBy = "originalProject")` |
| GL-models_Project-023 | `app/models/Project.java:121` | `@OneToMany(mappedBy = "project")` |
| GL-models_Project-024 | `app/models/Project.java:125` | `@OneToMany(mappedBy = "project")` |
| GL-models_Project-027 | `app/models/Project.java:136` | `@ManyToMany(mappedBy = "enrolledProjects")` |
| GL-models_Project-028 | `app/models/Project.java:140` | `@OneToMany(cascade = CascadeType.REMOVE)` |
| GL-models_Project-029 | `app/models/Project.java:144` | `@OneToMany(cascade = CascadeType.REMOVE)` |
| GL-models_Project-032 | `app/models/Project.java:154` | `@ManyToOne` |
| GL-models_Project-033 | `app/models/Project.java:158` | `@Enumerated(EnumType.STRING)` |
| GL-models_Project-034 | `app/models/Project.java:162` | `@OneToOne(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-038 | `app/models/Project.java:173` | `@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)` |
| GL-models_Project-039 | `app/models/Project.java:177` | `/**` |
| GL-models_Project-042 | `app/models/Project.java:221` | `public static List<Project> findByOwner(String loginId) {` |
| GL-models_Project-043 | `app/models/Project.java:226` | `public Set<User> findAuthors() {` |
| GL-models_Project-044 | `app/models/Project.java:236` | `public Set<User> findAuthorsAndWatchers() {` |
| GL-models_Project-049 | `app/models/Project.java:269` | `public boolean hasMember(User user) {` |
| GL-models_Project-050 | `app/models/Project.java:280` | `public static boolean exists(String loginId, String projectName) {` |
| GL-models_Project-051 | `app/models/Project.java:287` | `public static boolean projectNameChangeable(Long id, String userName,` |
| GL-models_Project-052 | `app/models/Project.java:295` | `/**` |
| GL-models_Project-053 | `app/models/Project.java:313` | `public static List<Project> findProjectsByMember(Long userId) {` |
| GL-models_Project-054 | `app/models/Project.java:318` | `public static List<Project> findProjectsJustMemberAndNotOwner(User user) {` |
| GL-models_Project-055 | `app/models/Project.java:323` | `public static List<Project> findProjectsJustMemberAndNotOwner(User user, String orderString) {` |
| GL-models_Project-056 | `app/models/Project.java:336` | `public static List<Project> findProjectsByMemberWithFilter(Long userId, String orderString) {` |
| GL-models_Project-057 | `app/models/Project.java:346` | `public static List<Project> findProjectsCreatedByUser(String loginId, String orderString) {` |
| GL-models_Project-058 | `app/models/Project.java:356` | `public static List<Project> findProjectsCreatedByUserAndScope(String loginId, ProjectScope projectSc` |
| GL-models_Project-059 | `app/models/Project.java:363` | `public Date lastUpdateDate() {` |
| GL-models_Project-060 | `app/models/Project.java:390` | `public String defaultBranch() {` |
| GL-models_Project-061 | `app/models/Project.java:399` | `public Duration ago() {` |
| GL-models_Project-062 | `app/models/Project.java:404` | `public Duration lastPushedDateAgo(){` |
| GL-models_Project-063 | `app/models/Project.java:412` | `public String readme() {` |
| GL-models_Project-065 | `app/models/Project.java:434` | `/**` |
| GL-models_Project-072 | `app/models/Project.java:514` | `/**` |
| GL-models_Project-073 | `app/models/Project.java:530` | `public static void fixLastIssueNumber(Long projectId) {` |
| GL-models_Project-074 | `app/models/Project.java:538` | `public static Long increaseLastPostingNumber(Long projectId) {` |
| GL-models_Project-075 | `app/models/Project.java:547` | `public static void fixLastPostingNumber(Long projectId) {` |
| GL-models_Project-076 | `app/models/Project.java:555` | `public Resource labelsAsResource() {` |
| GL-models_Project-077 | `app/models/Project.java:577` | `@Override` |
| GL-models_Project-079 | `app/models/Project.java:605` | `public Boolean attachLabel(Label label) {` |
| GL-models_Project-080 | `app/models/Project.java:619` | `public void detachLabel(Label label) {` |
| GL-models_Project-082 | `app/models/Project.java:634` | `public String toString() {` |
| GL-models_Project-089 | `app/models/Project.java:682` | `public boolean hasForks() {` |
| GL-models_Project-091 | `app/models/Project.java:695` | `public void addFork(Project forkProject) {` |
| GL-models_Project-092 | `app/models/Project.java:701` | `public static List<Project> findByOwnerAndOriginalProject(String loginId, Project originalProject) {` |
| GL-models_Project-093 | `app/models/Project.java:709` | `public void deleteFork() {` |
| GL-models_Project-094 | `app/models/Project.java:716` | `private void deleteFork(Project project) {` |
| GL-models_Project-095 | `app/models/Project.java:722` | `public void fixInvalidForkData() {` |
| GL-models_Project-096 | `app/models/Project.java:734` | `/**` |
| GL-models_Project-097 | `app/models/Project.java:755` | `public void changeVCS() throws Exception {` |
| GL-models_Project-099 | `app/models/Project.java:775` | `/**` |
| GL-models_Project-101 | `app/models/Project.java:794` | `private static boolean hasPassed24hoursFrom(Long time) {` |
| GL-models_Project-102 | `app/models/Project.java:799` | `public enum State {` |
| GL-models_Project-103 | `app/models/Project.java:804` | `/**` |
| GL-models_Project-104 | `app/models/Project.java:859` | `private void deleteProjectTransfer() {` |
| GL-models_Project-105 | `app/models/Project.java:867` | `private void deleteOriginal() {` |
| GL-models_Project-106 | `app/models/Project.java:872` | `private void deletePullRequests() {` |
| GL-models_Project-107 | `app/models/Project.java:887` | `private void deleteCommentThreads() {` |
| GL-models_Project-108 | `app/models/Project.java:894` | `public static String newProjectName(String loginId, String projectName) {` |
| GL-models_Project-109 | `app/models/Project.java:910` | `/**` |
| GL-models_Project-110 | `app/models/Project.java:928` | `@Override` |
| GL-models_Project-111 | `app/models/Project.java:934` | `public static int countProjectsJustMemberAndNotOwner(String loginId) {` |
| GL-models_Project-112 | `app/models/Project.java:940` | `public static int countProjectsCreatedByUser(String loginId) {` |
| GL-models_Project-119 | `app/models/Project.java:994` | `public boolean hasGroup() {` |
| GL-models_Project-123 | `app/models/Project.java:1014` | `public String nextVCS() {` |
| GL-models_Project-124 | `app/models/Project.java:1023` | `/**` |
| GL-models_Project-125 | `app/models/Project.java:1049` | `public boolean hasOldPlace(){` |
| GL-models_PostingComment-001 | `app/models/PostingComment.java:20` | `@Entity` |
| GL-models_PostingComment-003 | `app/models/PostingComment.java:25` | `public static final Finder<Long, PostingComment> find = new Finder<>(Long.class, PostingComment.clas` |
| GL-models_PostingComment-004 | `app/models/PostingComment.java:28` | `@ManyToOne` |
| GL-models_PostingComment-005 | `app/models/PostingComment.java:32` | `@OneToOne` |
| GL-models_PostingComment-006 | `app/models/PostingComment.java:36` | `public PostingComment(Posting posting, User author, String contents) {` |
| GL-models_PostingComment-007 | `app/models/PostingComment.java:43` | `/**` |
| GL-models_PostingComment-008 | `app/models/PostingComment.java:51` | `@Override` |
| GL-models_PostingComment-009 | `app/models/PostingComment.java:57` | `@Override` |
| GL-models_PostingComment-010 | `app/models/PostingComment.java:63` | `@Override` |
| GL-models_PostingComment-011 | `app/models/PostingComment.java:76` | `@Override` |
| GL-models_PostingComment-012 | `app/models/PostingComment.java:85` | `/**` |
| GL-models_PostingComment-013 | `app/models/PostingComment.java:119` | `public static List<PostingComment> findAllBy(Posting posting) {` |
| GL-models_PostingComment-014 | `app/models/PostingComment.java:126` | `public static int countAllCreatedBy(User user) {` |
| GL-models_PullRequest-001 | `app/models/PullRequest.java:60` | `@Entity` |
| GL-models_PullRequest-004 | `app/models/PullRequest.java:69` | `public static final Finder<Long, PullRequest> finder = new Finder<>(Long.class, PullRequest.class);` |
| GL-models_PullRequest-006 | `app/models/PullRequest.java:75` | `@Id` |
| GL-models_PullRequest-007 | `app/models/PullRequest.java:79` | `@Constraints.Required` |
| GL-models_PullRequest-008 | `app/models/PullRequest.java:84` | `@Lob` |
| GL-models_PullRequest-009 | `app/models/PullRequest.java:88` | `@Transient` |
| GL-models_PullRequest-010 | `app/models/PullRequest.java:91` | `@Transient` |
| GL-models_PullRequest-011 | `app/models/PullRequest.java:95` | `@ManyToOne` |
| GL-models_PullRequest-012 | `app/models/PullRequest.java:99` | `@ManyToOne` |
| GL-models_PullRequest-013 | `app/models/PullRequest.java:103` | `@Constraints.Required` |
| GL-models_PullRequest-014 | `app/models/PullRequest.java:108` | `@Constraints.Required` |
| GL-models_PullRequest-015 | `app/models/PullRequest.java:113` | `@ManyToOne` |
| GL-models_PullRequest-016 | `app/models/PullRequest.java:117` | `@ManyToOne` |
| GL-models_PullRequest-017 | `app/models/PullRequest.java:121` | `@Temporal(TemporalType.TIMESTAMP)` |
| GL-models_PullRequest-018 | `app/models/PullRequest.java:125` | `@Temporal(TemporalType.TIMESTAMP)` |
| GL-models_PullRequest-019 | `app/models/PullRequest.java:129` | `@Temporal(TemporalType.TIMESTAMP)` |
| GL-models_PullRequest-023 | `app/models/PullRequest.java:141` | `@OneToMany(cascade = CascadeType.ALL)` |
| GL-models_PullRequest-024 | `app/models/PullRequest.java:145` | `@OneToMany(cascade = CascadeType.ALL)` |
| GL-models_PullRequest-029 | `app/models/PullRequest.java:162` | `@ManyToMany(cascade = CascadeType.ALL)` |
| GL-models_PullRequest-030 | `app/models/PullRequest.java:172` | `@OneToMany(mappedBy = "pullRequest")` |
| GL-models_PullRequest-031 | `app/models/PullRequest.java:176` | `@Transient` |
| GL-models_PullRequest-032 | `app/models/PullRequest.java:180` | `public static PullRequest createNewPullRequest(Project fromProject, Project toProject, String fromBr` |
| GL-models_PullRequest-033 | `app/models/PullRequest.java:190` | `@Override` |
| GL-models_PullRequest-034 | `app/models/PullRequest.java:210` | `public static void onStart() {` |
| GL-models_PullRequest-035 | `app/models/PullRequest.java:216` | `public Duration createdAgo() {` |
| GL-models_PullRequest-039 | `app/models/PullRequest.java:236` | `public static PullRequest findById(long id) {` |
| GL-models_PullRequest-040 | `app/models/PullRequest.java:241` | `public static PullRequest findDuplicatedPullRequest(PullRequest pullRequest) {` |
| GL-models_PullRequest-041 | `app/models/PullRequest.java:252` | `public static List<PullRequest> findOpendPullRequests(Project project) {` |
| GL-models_PullRequest-042 | `app/models/PullRequest.java:261` | `public static List<PullRequest> findOpendPullRequestsByDaysAgo(User user, int days) {` |
| GL-models_PullRequest-043 | `app/models/PullRequest.java:270` | `public static List<PullRequest> findClosedPullRequests(Project project) {` |
| GL-models_PullRequest-044 | `app/models/PullRequest.java:279` | `public static List<PullRequest> findSentPullRequests(Project project) {` |
| GL-models_PullRequest-045 | `app/models/PullRequest.java:287` | `public static List<PullRequest> findAcceptedPullRequests(Project project) {` |
| GL-models_PullRequest-046 | `app/models/PullRequest.java:296` | `public static List<PullRequest> allReceivedRequests(Project project) {` |
| GL-models_PullRequest-047 | `app/models/PullRequest.java:304` | `public static List<PullRequest> findRecentlyReceived(Project project, int size) {` |
| GL-models_PullRequest-048 | `app/models/PullRequest.java:313` | `public static List<PullRequest> findRecentlyReceivedOpen(Project project, int size) {` |
| GL-models_PullRequest-049 | `app/models/PullRequest.java:323` | `public static int countOpenedPullRequests(Project project) {` |
| GL-models_PullRequest-050 | `app/models/PullRequest.java:331` | `public static List<PullRequest> findRelatedPullRequests(Project project, String branch) {` |
| GL-models_PullRequest-051 | `app/models/PullRequest.java:346` | `@Override` |
| GL-models_PullRequest-052 | `app/models/PullRequest.java:372` | `public void updateWith(PullRequest newPullRequest) {` |
| GL-models_PullRequest-053 | `app/models/PullRequest.java:385` | `public boolean hasSameBranchesWith(PullRequest pullRequest) {` |
| GL-models_PullRequest-056 | `app/models/PullRequest.java:400` | `/**` |
| GL-models_PullRequest-057 | `app/models/PullRequest.java:409` | `public void restoreFromBranch() {` |
| GL-models_PullRequest-058 | `app/models/PullRequest.java:414` | `public class Merger {` |
| GL-models_PullRequest-059 | `app/models/PullRequest.java:592` | `public void merge(final PullRequestEventMessage message) throws IOException, GitAPIException, PullRe` |
| GL-models_PullRequest-060 | `app/models/PullRequest.java:614` | `public String fetchSourceBranch() throws IOException, GitAPIException {` |
| GL-models_PullRequest-061 | `app/models/PullRequest.java:621` | `public void updateMergedCommitId(Merger.MergeResult mergeResult) {` |
| GL-models_PullRequest-065 | `app/models/PullRequest.java:656` | `/**` |
| GL-models_PullRequest-066 | `app/models/PullRequest.java:699` | `private void addReviewers(StringBuilder builder) {` |
| GL-models_PullRequest-068 | `app/models/PullRequest.java:717` | `private void addCommitMessages(List<GitCommit> commits, StringBuilder builder) {` |
| GL-models_PullRequest-069 | `app/models/PullRequest.java:726` | `private void changeState(State state) {` |
| GL-models_PullRequest-070 | `app/models/PullRequest.java:731` | `private void changeState(State state, User updater) {` |
| GL-models_PullRequest-071 | `app/models/PullRequest.java:738` | `public void reopen() {` |
| GL-models_PullRequest-072 | `app/models/PullRequest.java:744` | `public void close() {` |
| GL-models_PullRequest-073 | `app/models/PullRequest.java:749` | `public static List<PullRequest> findByToProject(Project project) {` |
| GL-models_PullRequest-074 | `app/models/PullRequest.java:754` | `public static List<PullRequest> findByFromProjectAndBranch(Project fromProject, String fromBranch) {` |
| GL-models_PullRequest-075 | `app/models/PullRequest.java:760` | `@Transactional` |
| GL-models_PullRequest-076 | `app/models/PullRequest.java:769` | `public static long nextPullRequestNumber(Project project) {` |
| GL-models_PullRequest-077 | `app/models/PullRequest.java:783` | `public static PullRequest findOne(Project toProject, long number) {` |
| GL-models_PullRequest-078 | `app/models/PullRequest.java:791` | `@Transactional` |
| GL-models_PullRequest-081 | `app/models/PullRequest.java:827` | `@Transient` |
| GL-models_PullRequest-082 | `app/models/PullRequest.java:834` | `public static Page<PullRequest> findPagingList(SearchCondition condition) {` |
| GL-models_PullRequest-083 | `app/models/PullRequest.java:842` | `public static int count(SearchCondition condition) {` |
| GL-models_PullRequest-084 | `app/models/PullRequest.java:847` | `private static ExpressionList<PullRequest> createSearchExpressionList(SearchCondition condition) {` |
| GL-models_PullRequest-085 | `app/models/PullRequest.java:890` | `private static Expression createStateSearchExpression(State[] states) {` |
| GL-models_PullRequest-086 | `app/models/PullRequest.java:903` | `private void addNewIssueEvents() {` |
| GL-models_PullRequest-087 | `app/models/PullRequest.java:918` | `public void deleteIssueEvents() {` |
| GL-models_PullRequest-088 | `app/models/PullRequest.java:933` | `@Override` |
| GL-models_PullRequest-089 | `app/models/PullRequest.java:940` | `@Transient` |
| GL-models_PullRequest-090 | `app/models/PullRequest.java:946` | `@Transient` |
| GL-models_PullRequest-091 | `app/models/PullRequest.java:952` | `private FetchResult fetchSourceBranchTo(String destination) throws IOException,` |
| GL-models_PullRequest-092 | `app/models/PullRequest.java:964` | `public PullRequestMergeResult updateMerge() throws IOException, GitAPIException, PullRequestExceptio` |
| GL-models_PullRequest-095 | `app/models/PullRequest.java:1008` | `public String fetchSourceTemporarilly() throws IOException, GitAPIException {` |
| GL-models_PullRequest-096 | `app/models/PullRequest.java:1017` | `// locking this repository is required because of fetch and update` |
| GL-models_PullRequest-097 | `app/models/PullRequest.java:1048` | `public void startMerge() {` |
| GL-models_PullRequest-098 | `app/models/PullRequest.java:1053` | `public void endMerge() {` |
| GL-models_PullRequest-101 | `app/models/PullRequest.java:1106` | `public static PullRequest findTheLatestOneFrom(Project fromProject, String fromBranch) {` |
| GL-models_PullRequest-102 | `app/models/PullRequest.java:1124` | `public static void changeStateToClosed() {` |
| GL-models_PullRequest-103 | `app/models/PullRequest.java:1135` | `public void clearReviewers() {` |
| GL-models_PullRequest-105 | `app/models/PullRequest.java:1146` | `public void addReviewer(User user) {` |
| GL-models_PullRequest-106 | `app/models/PullRequest.java:1153` | `public void removeReviewer(User user) {` |
| GL-models_PullRequest-112 | `app/models/PullRequest.java:1230` | `public int countCommentThreadsByState(CommentThread.ThreadState state){` |
| GL-models_PullRequest-114 | `app/models/PullRequest.java:1251` | `public void removeCommentThread(CommentThread commentThread) {` |
| GL-models_PullRequest-115 | `app/models/PullRequest.java:1257` | `public void addCommentThread(CommentThread thread) {` |
| GL-models_PullRequest-116 | `app/models/PullRequest.java:1263` | `static public boolean noChangesBetween(Repository repoA, String rev1,` |
| GL-models_resource_Resource-001 | `app/models/resource/Resource.java:34` | `public abstract class Resource {` |
| GL-models_resource_Resource-002 | `app/models/resource/Resource.java:36` | `public static boolean exists(ResourceType type, String id) {` |
| GL-models_resource_Resource-004 | `app/models/resource/Resource.java:114` | `public static Resource get(ResourceType resourceType, String resourceId) {` |
| GL-models_resource_Resource-005 | `app/models/resource/Resource.java:175` | `public ResourceParam asParameter() {` |
| GL-models_resource_Resource-012 | `app/models/resource/Resource.java:192` | `public void delete() { throw new UnsupportedOperationException(); }` |
| GL-models_resource_Resource-014 | `app/models/resource/Resource.java:207` | `@Override` |
| GL-models_resource_Resource-015 | `app/models/resource/Resource.java:221` | `@Override` |
| GL-models_resource_Resource-016 | `app/models/resource/Resource.java:230` | `@Override` |
| GL-models_resource_Resource-018 | `app/models/resource/Resource.java:241` | `/**` |
| GL-models_resource_Resource-019 | `app/models/resource/Resource.java:268` | `/**` |
| GL-models_resource_GlobalResource-001 | `app/models/resource/GlobalResource.java:26` | `abstract public class GlobalResource extends Resource {` |
| GL-models_resource_GlobalResource-002 | `app/models/resource/GlobalResource.java:28` | `@Override` |
| GL-models_resource_ResourceParam-001 | `app/models/resource/ResourceParam.java:30` | `public class ResourceParam implements QueryStringBindable<ResourceParam> {` |
| GL-models_resource_ResourceParam-003 | `app/models/resource/ResourceParam.java:36` | `public static ResourceParam get(Resource resource) {` |
| GL-models_resource_ResourceParam-004 | `app/models/resource/ResourceParam.java:43` | `@Override` |
| GL-models_resource_ResourceParam-005 | `app/models/resource/ResourceParam.java:56` | `@Override` |
| GL-models_resource_ResourceParam-006 | `app/models/resource/ResourceParam.java:63` | `@Override` |
| GL-models_resource_ResourcePersistAdapter-001 | `app/models/resource/ResourcePersistAdapter.java:32` | `/**` |
| GL-models_resource_ResourcePersistAdapter-002 | `app/models/resource/ResourcePersistAdapter.java:38` | `/**` |
| GL-models_resource_ResourcePersistAdapter-003 | `app/models/resource/ResourcePersistAdapter.java:47` | `/**` |
| GL-models_resource_ResourcePersistAdapter-004 | `app/models/resource/ResourcePersistAdapter.java:63` | `private void deleteRelatedWatch(Resource resource, EbeanServer server, Transaction transaction) {` |
| GL-models_resource_ResourcePersistAdapter-005 | `app/models/resource/ResourcePersistAdapter.java:70` | `private void deleteRelatedUnwatch(Resource resource, EbeanServer server, Transaction transaction) {` |
| GL-models_resource_ResourceConvertible-001 | `app/models/resource/ResourceConvertible.java:24` | `/**` |
| GL-models_resource_ResourceConvertible-002 | `app/models/resource/ResourceConvertible.java:29` | `/**` |
| GL-models_enumeration_Direction-001 | `app/models/enumeration/Direction.java:24` | `public enum Direction {` |
| GL-models_enumeration_Direction-002 | `app/models/enumeration/Direction.java:28` | `ASC("asc"), DESC("desc");` |
| GL-models_enumeration_Direction-003 | `app/models/enumeration/Direction.java:28` | `ASC("asc"), DESC("desc");` |
| GL-models_enumeration_Direction-005 | `app/models/enumeration/Direction.java:34` | `Direction(String direction) {` |
| GL-models_enumeration_Direction-006 | `app/models/enumeration/Direction.java:39` | `public String direction() {` |
| GL-models_enumeration_IssueFilterType-001 | `app/models/enumeration/IssueFilterType.java:4` | `public enum IssueFilterType {` |
| GL-models_enumeration_IssueFilterType-002 | `app/models/enumeration/IssueFilterType.java:6` | `ASSIGNED("assigned"),` |
| GL-models_enumeration_IssueFilterType-003 | `app/models/enumeration/IssueFilterType.java:8` | `CREATED("created"),` |
| GL-models_enumeration_IssueFilterType-004 | `app/models/enumeration/IssueFilterType.java:10` | `MENTIONED("mentioned"),` |
| GL-models_enumeration_IssueFilterType-005 | `app/models/enumeration/IssueFilterType.java:12` | `FAVORITE("favorite"),` |
| GL-models_enumeration_IssueFilterType-006 | `app/models/enumeration/IssueFilterType.java:14` | `ALL("all");` |
| GL-models_enumeration_IssueFilterType-008 | `app/models/enumeration/IssueFilterType.java:20` | `IssueFilterType(String issueFilter) {` |
| GL-models_enumeration_UserState-001 | `app/models/enumeration/UserState.java:24` | `public enum UserState {` |
| GL-models_enumeration_UserState-002 | `app/models/enumeration/UserState.java:30` | `ACTIVE("ACTIVE"), LOCKED("LOCKED"), DELETED("DELETED"), GUEST("GUEST"), SITE_ADMIN("SITE_ADMIN");` |
| GL-models_enumeration_UserState-003 | `app/models/enumeration/UserState.java:30` | `ACTIVE("ACTIVE"), LOCKED("LOCKED"), DELETED("DELETED"), GUEST("GUEST"), SITE_ADMIN("SITE_ADMIN");` |
| GL-models_enumeration_UserState-004 | `app/models/enumeration/UserState.java:30` | `ACTIVE("ACTIVE"), LOCKED("LOCKED"), DELETED("DELETED"), GUEST("GUEST"), SITE_ADMIN("SITE_ADMIN");` |
| GL-models_enumeration_UserState-005 | `app/models/enumeration/UserState.java:30` | `ACTIVE("ACTIVE"), LOCKED("LOCKED"), DELETED("DELETED"), GUEST("GUEST"), SITE_ADMIN("SITE_ADMIN");` |
| GL-models_enumeration_UserState-006 | `app/models/enumeration/UserState.java:30` | `ACTIVE("ACTIVE"), LOCKED("LOCKED"), DELETED("DELETED"), GUEST("GUEST"), SITE_ADMIN("SITE_ADMIN");` |
| GL-models_enumeration_UserState-008 | `app/models/enumeration/UserState.java:36` | `UserState(String state) {` |
| GL-models_enumeration_UserState-009 | `app/models/enumeration/UserState.java:41` | `public String state() {` |
| GL-models_enumeration_UserState-010 | `app/models/enumeration/UserState.java:46` | `public static UserState of(String value) {` |
| GL-models_enumeration_ResourceType-001 | `app/models/enumeration/ResourceType.java:27` | `public enum ResourceType {` |
| GL-models_enumeration_ResourceType-002 | `app/models/enumeration/ResourceType.java:29` | `ISSUE_POST("issue_post"),` |
| GL-models_enumeration_ResourceType-003 | `app/models/enumeration/ResourceType.java:31` | `ISSUE_ASSIGNEE("issue_assignee"),` |
| GL-models_enumeration_ResourceType-004 | `app/models/enumeration/ResourceType.java:33` | `ISSUE_STATE("issue_state"),` |
| GL-models_enumeration_ResourceType-005 | `app/models/enumeration/ResourceType.java:35` | `ISSUE_CATEGORY("issue_category"),` |
| GL-models_enumeration_ResourceType-006 | `app/models/enumeration/ResourceType.java:37` | `ISSUE_MILESTONE("issue_milestone"),` |
| GL-models_enumeration_ResourceType-007 | `app/models/enumeration/ResourceType.java:40` | `ISSUE_LABEL("issue_label"),` |
| GL-models_enumeration_ResourceType-008 | `app/models/enumeration/ResourceType.java:42` | `BOARD_POST("board_post"),` |
| GL-models_enumeration_ResourceType-009 | `app/models/enumeration/ResourceType.java:44` | `BOARD_CATEGORY("board_category"),` |
| GL-models_enumeration_ResourceType-010 | `app/models/enumeration/ResourceType.java:46` | `BOARD_NOTICE("board_notice"),` |
| GL-models_enumeration_ResourceType-011 | `app/models/enumeration/ResourceType.java:48` | `CODE("code"),` |
| GL-models_enumeration_ResourceType-012 | `app/models/enumeration/ResourceType.java:50` | `MILESTONE("milestone"),` |
| GL-models_enumeration_ResourceType-013 | `app/models/enumeration/ResourceType.java:52` | `WIKI_PAGE("wiki_page"),` |
| GL-models_enumeration_ResourceType-014 | `app/models/enumeration/ResourceType.java:54` | `PROJECT_SETTING("project_setting"),` |
| GL-models_enumeration_ResourceType-015 | `app/models/enumeration/ResourceType.java:56` | `SITE_SETTING("site_setting"),` |
| GL-models_enumeration_ResourceType-016 | `app/models/enumeration/ResourceType.java:58` | `USER("user"),` |
| GL-models_enumeration_ResourceType-017 | `app/models/enumeration/ResourceType.java:60` | `USER_AVATAR("user_avatar"),` |
| GL-models_enumeration_ResourceType-018 | `app/models/enumeration/ResourceType.java:62` | `PROJECT("project"),` |
| GL-models_enumeration_ResourceType-019 | `app/models/enumeration/ResourceType.java:64` | `ATTACHMENT("attachment"),` |
| GL-models_enumeration_ResourceType-020 | `app/models/enumeration/ResourceType.java:66` | `ISSUE_COMMENT("issue_comment"),` |
| GL-models_enumeration_ResourceType-021 | `app/models/enumeration/ResourceType.java:68` | `NONISSUE_COMMENT("nonissue_comment"),` |
| GL-models_enumeration_ResourceType-022 | `app/models/enumeration/ResourceType.java:70` | `LABEL("label"),` |
| GL-models_enumeration_ResourceType-023 | `app/models/enumeration/ResourceType.java:72` | `PROJECT_LABELS("project_labels"),` |
| GL-models_enumeration_ResourceType-024 | `app/models/enumeration/ResourceType.java:74` | `FORK("fork"),` |
| GL-models_enumeration_ResourceType-025 | `app/models/enumeration/ResourceType.java:76` | `COMMIT_COMMENT("code_comment"),` |
| GL-models_enumeration_ResourceType-026 | `app/models/enumeration/ResourceType.java:78` | `PULL_REQUEST("pull_request"),` |
| GL-models_enumeration_ResourceType-027 | `app/models/enumeration/ResourceType.java:80` | `COMMIT("commit"),` |
| GL-models_enumeration_ResourceType-028 | `app/models/enumeration/ResourceType.java:82` | `COMMENT_THREAD("comment_thread"),` |
| GL-models_enumeration_ResourceType-029 | `app/models/enumeration/ResourceType.java:84` | `REVIEW_COMMENT("review_comment"),` |
| GL-models_enumeration_ResourceType-030 | `app/models/enumeration/ResourceType.java:86` | `ORGANIZATION("organization"),` |
| GL-models_enumeration_ResourceType-031 | `app/models/enumeration/ResourceType.java:88` | `PROJECT_TRANSFER("project_transfer"),` |
| GL-models_enumeration_ResourceType-032 | `app/models/enumeration/ResourceType.java:90` | `ISSUE_LABEL_CATEGORY("issue_label_category"),` |
| GL-models_enumeration_ResourceType-033 | `app/models/enumeration/ResourceType.java:92` | `WEBHOOK("webhook"),` |
| GL-models_enumeration_ResourceType-034 | `app/models/enumeration/ResourceType.java:94` | `NOT_A_RESOURCE("");` |
| GL-models_enumeration_ResourceType-036 | `app/models/enumeration/ResourceType.java:100` | `ResourceType(String resource) {` |
| GL-models_enumeration_ResourceType-037 | `app/models/enumeration/ResourceType.java:105` | `public String resource() {` |
| GL-models_enumeration_ResourceType-039 | `app/models/enumeration/ResourceType.java:120` | `public String asPathSegment() {` |
| GL-models_enumeration_RoleType-001 | `app/models/enumeration/RoleType.java:24` | `public enum RoleType {` |
| GL-models_enumeration_RoleType-002 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-003 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-004 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-005 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-006 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-007 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-008 | `app/models/enumeration/RoleType.java:32` | `MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);` |
| GL-models_enumeration_RoleType-010 | `app/models/enumeration/RoleType.java:38` | `RoleType(Long roleType) {` |
| GL-models_enumeration_RoleType-011 | `app/models/enumeration/RoleType.java:43` | `public Long roleType() {` |
| GL-models_enumeration_State-001 | `app/models/enumeration/State.java:25` | `public enum State {` |
| GL-models_enumeration_State-002 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-003 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-004 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-005 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-006 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-007 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-008 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-009 | `app/models/enumeration/State.java:34` | `ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("re` |
| GL-models_enumeration_State-011 | `app/models/enumeration/State.java:39` | `State(String state) {` |
| GL-models_enumeration_State-012 | `app/models/enumeration/State.java:44` | `public String state() {` |
| GL-models_enumeration_EventType-001 | `app/models/enumeration/EventType.java:17` | `public enum EventType {` |
| GL-models_enumeration_EventType-002 | `app/models/enumeration/EventType.java:20` | `NEW_ISSUE("notification.type.new.issue", 1),` |
| GL-models_enumeration_EventType-003 | `app/models/enumeration/EventType.java:22` | `NEW_POSTING("notification.type.new.posting", 2),` |
| GL-models_enumeration_EventType-004 | `app/models/enumeration/EventType.java:24` | `NEW_PULL_REQUEST("notification.type.new.pullrequest", 3),` |
| GL-models_enumeration_EventType-005 | `app/models/enumeration/EventType.java:26` | `ISSUE_STATE_CHANGED("notification.type.issue.state.changed", 4),` |
| GL-models_enumeration_EventType-006 | `app/models/enumeration/EventType.java:28` | `ISSUE_ASSIGNEE_CHANGED("notification.type.issue.assignee.changed", 5),` |
| GL-models_enumeration_EventType-007 | `app/models/enumeration/EventType.java:30` | `PULL_REQUEST_STATE_CHANGED("notification.type.pullrequest.state.changed", 6),` |
| GL-models_enumeration_EventType-008 | `app/models/enumeration/EventType.java:32` | `NEW_COMMENT("notification.type.new.comment", 7),` |
| GL-models_enumeration_EventType-009 | `app/models/enumeration/EventType.java:34` | `NEW_REVIEW_COMMENT("notification.type.new.simple.comment", 8),` |
| GL-models_enumeration_EventType-010 | `app/models/enumeration/EventType.java:36` | `MEMBER_ENROLL_REQUEST("notification.type.member.enroll", 9),` |
| GL-models_enumeration_EventType-011 | `app/models/enumeration/EventType.java:38` | `PULL_REQUEST_MERGED("notification.type.pullrequest.merged", 10),` |
| GL-models_enumeration_EventType-012 | `app/models/enumeration/EventType.java:40` | `ISSUE_REFERRED_FROM_COMMIT("notification.type.issue.referred.from.commit", 11),` |
| GL-models_enumeration_EventType-013 | `app/models/enumeration/EventType.java:42` | `PULL_REQUEST_COMMIT_CHANGED("notification.type.pullrequest.commit.changed", 12),` |
| GL-models_enumeration_EventType-014 | `app/models/enumeration/EventType.java:44` | `NEW_COMMIT("notification.type.new.commit", 13),` |
| GL-models_enumeration_EventType-015 | `app/models/enumeration/EventType.java:46` | `PULL_REQUEST_REVIEW_STATE_CHANGED("notification.type.pullrequest.review.action.changed",14),` |
| GL-models_enumeration_EventType-016 | `app/models/enumeration/EventType.java:48` | `ISSUE_BODY_CHANGED("notification.type.issue.body.changed", 17),` |
| GL-models_enumeration_EventType-017 | `app/models/enumeration/EventType.java:50` | `ISSUE_REFERRED_FROM_PULL_REQUEST("notification.type.issue.referred.from.pullrequest", 16),` |
| GL-models_enumeration_EventType-018 | `app/models/enumeration/EventType.java:52` | `REVIEW_THREAD_STATE_CHANGED("notification.type.review.state.changed", 18),` |
| GL-models_enumeration_EventType-019 | `app/models/enumeration/EventType.java:54` | `ORGANIZATION_MEMBER_ENROLL_REQUEST("notification.organization.type.member.enroll",19),` |
| GL-models_enumeration_EventType-020 | `app/models/enumeration/EventType.java:56` | `COMMENT_UPDATED("notification.type.comment.updated", 20),` |
| GL-models_enumeration_EventType-021 | `app/models/enumeration/EventType.java:58` | `ISSUE_MOVED("notification.type.issue.is.moved", 21),` |
| GL-models_enumeration_EventType-022 | `app/models/enumeration/EventType.java:60` | `ISSUE_SHARER_CHANGED("notification.type.issue.sharer.changed", 22),` |
| GL-models_enumeration_EventType-023 | `app/models/enumeration/EventType.java:62` | `ISSUE_LABEL_CHANGED("notification.type.issue.label.changed", 23),` |
| GL-models_enumeration_EventType-024 | `app/models/enumeration/EventType.java:64` | `ISSUE_MILESTONE_CHANGED("notification.type.milestone.changed", 24),` |
| GL-models_enumeration_EventType-025 | `app/models/enumeration/EventType.java:66` | `POSTING_BODY_CHANGED("notification.type.posting.body.changed", 25),` |
| GL-models_enumeration_EventType-026 | `app/models/enumeration/EventType.java:68` | `RESOURCE_DELETED("notification.type.resource.deleted", 26),` |
| GL-models_enumeration_EventType-027 | `app/models/enumeration/EventType.java:70` | `MEMBER_ENROLL_ACCEPT("notification.member.enroll.accept", 27),` |
| GL-models_enumeration_EventType-028 | `app/models/enumeration/EventType.java:72` | `ORGANIZATION_MEMBER_ENROLL_ACCEPT("notification.member.enroll.accept", 28);` |
| GL-models_enumeration_EventType-032 | `app/models/enumeration/EventType.java:84` | `EventType(String messageKey, int order) {` |
| GL-models_enumeration_EventType-037 | `app/models/enumeration/EventType.java:128` | `@Override` |
| GL-models_enumeration_SearchType-001 | `app/models/enumeration/SearchType.java:4` | `/**` |
| GL-models_enumeration_SearchType-002 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-003 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-004 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-005 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-006 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-007 | `app/models/enumeration/SearchType.java:15` | `AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),` |
| GL-models_enumeration_SearchType-008 | `app/models/enumeration/SearchType.java:20` | `MILESTONE("milestone"), ISSUE_COMMENT("issue_comment"), POST_COMMENT("post_comment"), REVIEW("review` |
| GL-models_enumeration_SearchType-009 | `app/models/enumeration/SearchType.java:20` | `MILESTONE("milestone"), ISSUE_COMMENT("issue_comment"), POST_COMMENT("post_comment"), REVIEW("review` |
| GL-models_enumeration_SearchType-010 | `app/models/enumeration/SearchType.java:20` | `MILESTONE("milestone"), ISSUE_COMMENT("issue_comment"), POST_COMMENT("post_comment"), REVIEW("review` |
| GL-models_enumeration_SearchType-011 | `app/models/enumeration/SearchType.java:20` | `MILESTONE("milestone"), ISSUE_COMMENT("issue_comment"), POST_COMMENT("post_comment"), REVIEW("review` |
| GL-models_enumeration_SearchType-013 | `app/models/enumeration/SearchType.java:26` | `SearchType(String value) {` |
| GL-models_enumeration_Operation-001 | `app/models/enumeration/Operation.java:24` | `public enum Operation {` |
| GL-models_enumeration_Operation-002 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-003 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-004 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-005 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-006 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-007 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-008 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-009 | `app/models/enumeration/Operation.java:33` | `READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), ` |
| GL-models_enumeration_Operation-010 | `app/models/enumeration/Operation.java:35` | `// this operation means an action which assign an issue to him or her self.` |
| GL-models_enumeration_Operation-012 | `app/models/enumeration/Operation.java:42` | `Operation(String operation) {` |
| GL-models_enumeration_Operation-013 | `app/models/enumeration/Operation.java:47` | `public String operation() {` |
| GL-models_enumeration_WebhookType-001 | `app/models/enumeration/WebhookType.java:10` | `public enum WebhookType {` |
| GL-models_enumeration_WebhookType-002 | `app/models/enumeration/WebhookType.java:15` | `SIMPLE(0), DETAIL_SLACK(1), DETAIL_HANGOUT_CHAT(2), JSON(3);` |
| GL-models_enumeration_WebhookType-003 | `app/models/enumeration/WebhookType.java:15` | `SIMPLE(0), DETAIL_SLACK(1), DETAIL_HANGOUT_CHAT(2), JSON(3);` |
| GL-models_enumeration_WebhookType-004 | `app/models/enumeration/WebhookType.java:15` | `SIMPLE(0), DETAIL_SLACK(1), DETAIL_HANGOUT_CHAT(2), JSON(3);` |
| GL-models_enumeration_WebhookType-005 | `app/models/enumeration/WebhookType.java:15` | `SIMPLE(0), DETAIL_SLACK(1), DETAIL_HANGOUT_CHAT(2), JSON(3);` |
| GL-models_enumeration_WebhookType-007 | `app/models/enumeration/WebhookType.java:21` | `WebhookType(int type) {` |
| GL-models_enumeration_RequestState-001 | `app/models/enumeration/RequestState.java:24` | `public enum RequestState {` |
| GL-models_enumeration_RequestState-002 | `app/models/enumeration/RequestState.java:29` | `REQUEST, CANCEL, ACCEPT, REJECT` |
| GL-models_enumeration_RequestState-003 | `app/models/enumeration/RequestState.java:29` | `REQUEST, CANCEL, ACCEPT, REJECT` |
| GL-models_enumeration_RequestState-004 | `app/models/enumeration/RequestState.java:29` | `REQUEST, CANCEL, ACCEPT, REJECT` |
| GL-models_enumeration_RequestState-005 | `app/models/enumeration/RequestState.java:29` | `REQUEST, CANCEL, ACCEPT, REJECT` |
| GL-models_enumeration_Matching-001 | `app/models/enumeration/Matching.java:24` | `public enum Matching {` |
| GL-models_enumeration_Matching-002 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-003 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-004 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-005 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-006 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-007 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-008 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_Matching-009 | `app/models/enumeration/Matching.java:34` | `EQUALS, CONTAINS, GT, GE, LT, LE, NOT_EQUALS, IN` |
| GL-models_enumeration_ProjectScope-001 | `app/models/enumeration/ProjectScope.java:24` | `public enum ProjectScope {` |
| GL-models_enumeration_PullRequestReviewAction-001 | `app/models/enumeration/PullRequestReviewAction.java:24` | `public enum PullRequestReviewAction {` |
| GL-models_support_ModelLock-001 | `app/models/support/ModelLock.java:29` | `public class ModelLock<T extends Model> {` |
| GL-models_support_ModelLock-002 | `app/models/support/ModelLock.java:31` | `private final Map<T, Object> locks = new MapMaker().weakValues().makeMap();` |
| GL-models_support_ModelLock-003 | `app/models/support/ModelLock.java:34` | `public Object get(T model) {` |
| GL-models_support_UserComparator-001 | `app/models/support/UserComparator.java:28` | `public class UserComparator implements Comparator<User> {` |
| GL-models_support_UserComparator-002 | `app/models/support/UserComparator.java:30` | `@Override` |
| GL-models_support_LdapUser-001 | `app/models/support/LdapUser.java:23` | `public class LdapUser {` |
| GL-models_support_LdapUser-007 | `app/models/support/LdapUser.java:36` | `public LdapUser(Attribute displayName, Attribute email, Attribute userLoginId,` |
| GL-models_support_LdapUser-016 | `app/models/support/LdapUser.java:117` | `@Override` |
| GL-models_support_ReviewSearchCondition-001 | `app/models/support/ReviewSearchCondition.java:36` | `/**` |
| GL-models_support_ReviewSearchCondition-005 | `app/models/support/ReviewSearchCondition.java:48` | `public ReviewSearchCondition() {` |
| GL-models_support_ReviewSearchCondition-006 | `app/models/support/ReviewSearchCondition.java:54` | `/**` |
| GL-models_support_ReviewSearchCondition-007 | `app/models/support/ReviewSearchCondition.java:94` | `public ReviewSearchCondition clone() {` |
| GL-models_support_FinderTemplate-001 | `app/models/support/FinderTemplate.java:31` | `public class FinderTemplate {` |
| GL-models_support_FinderTemplate-002 | `app/models/support/FinderTemplate.java:34` | `private static <K, T> ExpressionList<T> makeExpressionList(OrderParams mop,` |
| GL-models_support_FinderTemplate-003 | `app/models/support/FinderTemplate.java:97` | `public static <K, T> List<T> findBy(OrderParams mop,` |
| GL-models_support_OrderParam-001 | `app/models/support/OrderParam.java:26` | `public class OrderParam {` |
| GL-models_support_OrderParam-004 | `app/models/support/OrderParam.java:35` | `public OrderParam(String sort, Direction direction) {` |
| GL-models_support_SearchCondition-001 | `app/models/support/SearchCondition.java:25` | `public class SearchCondition extends AbstractPostingApp.SearchCondition implements Cloneable {` |
| GL-models_support_SearchCondition-005 | `app/models/support/SearchCondition.java:34` | `public Set<Long> labelIds = new HashSet<>();` |
| GL-models_support_SearchCondition-015 | `app/models/support/SearchCondition.java:59` | `@Formats.DateTime(pattern = "yyyy-MM-dd")` |
| GL-models_support_SearchCondition-016 | `app/models/support/SearchCondition.java:63` | `private User byUser = UserApp.currentUser();` |
| GL-models_support_SearchCondition-017 | `app/models/support/SearchCondition.java:66` | `/**` |
| GL-models_support_SearchCondition-025 | `app/models/support/SearchCondition.java:134` | `public ExpressionList<Issue> asExpressionList(@Nonnull Organization organization) {` |
| GL-models_support_SearchCondition-034 | `app/models/support/SearchCondition.java:255` | `public SearchCondition() {` |
| GL-models_support_SearchCondition-035 | `app/models/support/SearchCondition.java:264` | `public ExpressionList<Issue> asExpressionList() {` |
| GL-models_support_SearchCondition-039 | `app/models/support/SearchCondition.java:323` | `private void updateElWhenIdsEmpty(ExpressionList<Issue> el, List<Long> ids) {` |
| GL-models_support_SearchCondition-044 | `app/models/support/SearchCondition.java:391` | `public ExpressionList<Issue> asExpressionList(Project project) {` |
| GL-models_support_SearchCondition-046 | `app/models/support/SearchCondition.java:484` | `private Set<Long> extractIssueIds(List<Issue> issues) {` |
| GL-models_support_SearchCondition-047 | `app/models/support/SearchCondition.java:493` | `private List<Issue> findIssueByLabel(List<Issue> issues, IssueLabel label) {` |
| GL-models_support_SearchCondition-049 | `app/models/support/SearchCondition.java:513` | `public boolean hasCondition(){` |
| GL-models_support_SearchCondition-050 | `app/models/support/SearchCondition.java:523` | `@Override` |
| GL-models_support_Options-001 | `app/models/support/Options.java:26` | `public class Options extends LinkedHashMap<String, String> {` |
| GL-models_support_Options-003 | `app/models/support/Options.java:31` | `public Options(String... args) {` |
| GL-models_support_SearchParams-001 | `app/models/support/SearchParams.java:29` | `public class SearchParams {` |
| GL-models_support_SearchParams-003 | `app/models/support/SearchParams.java:35` | `public SearchParams() {` |
| GL-models_support_SearchParams-004 | `app/models/support/SearchParams.java:40` | `public SearchParams add(String field, Object value, Matching matching) {` |
| GL-models_support_SearchParams-006 | `app/models/support/SearchParams.java:51` | `public List<SearchParam> clean() {` |
| GL-models_support_IssueLabelAggregate-001 | `app/models/support/IssueLabelAggregate.java:15` | `@Entity` |
| GL-models_support_OrderParams-001 | `app/models/support/OrderParams.java:30` | `public class OrderParams {` |
| GL-models_support_OrderParams-003 | `app/models/support/OrderParams.java:36` | `public OrderParams() {` |
| GL-models_support_OrderParams-004 | `app/models/support/OrderParams.java:41` | `public OrderParams add(String field, Direction direction) {` |
| GL-models_support_OrderParams-006 | `app/models/support/OrderParams.java:52` | `public List<OrderParam> clean() {` |
| GL-models_support_IssueSearchCondition-001 | `app/models/support/IssueSearchCondition.java:13` | `public class IssueSearchCondition  extends AbstractPostingApp.SearchCondition {` |
| GL-models_support_IssueSearchCondition-007 | `app/models/support/IssueSearchCondition.java:53` | `private ExpressionList<Issue> asExpressionList() {` |
| GL-models_support_IssueSearchCondition-008 | `app/models/support/IssueSearchCondition.java:66` | `private ExpressionList<Issue> asExpressionListForAll() {` |
| GL-models_support_IssueSearchCondition-013 | `app/models/support/IssueSearchCondition.java:113` | `private void updateElWhenIdsEmpty(ExpressionList<Issue> el, List<Long> ids) {` |
| GL-models_support_SearchParam-001 | `app/models/support/SearchParam.java:26` | `public class SearchParam {` |
| GL-models_support_SearchParam-005 | `app/models/support/SearchParam.java:38` | `public SearchParam(String field, Object value, Matching matching) {` |
