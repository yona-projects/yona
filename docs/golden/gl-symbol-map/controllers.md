---
id: gl-controllers
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

# 버킷 C — 공백 후보: `controllers` 영역 (544개 심볼)

자동 분류 결과이며, 실제 공백인지는 사람이 개별 확인해야 한다(자동 승격 금지). 상세 방법론은 [[../methodology]] 참고.

| GL-ID | yona 파일:라인 | 선언 |
|---|---|---|
| GL-controllers_IssueApp-001 | `app/controllers/IssueApp.java:45` | `@AnonymousCheck` |
| GL-controllers_IssueApp-004 | `app/controllers/IssueApp.java:53` | `@AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)` |
| GL-controllers_IssueApp-005 | `app/controllers/IssueApp.java:73` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_IssueApp-006 | `app/controllers/IssueApp.java:80` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_IssueApp-007 | `app/controllers/IssueApp.java:120` | `@Transactional` |
| GL-controllers_IssueApp-008 | `app/controllers/IssueApp.java:127` | `@IsAllowed(Operation.READ)` |
| GL-controllers_IssueApp-009 | `app/controllers/IssueApp.java:139` | `@Transactional` |
| GL-controllers_IssueApp-011 | `app/controllers/IssueApp.java:191` | `private static Result issuesAsHTML(Project project, Page<Issue> issues, models.support.SearchConditi` |
| GL-controllers_IssueApp-012 | `app/controllers/IssueApp.java:202` | `private static Result issuesAsExcel(Project project, ExpressionList<Issue> el) throws WriteException` |
| GL-controllers_IssueApp-013 | `app/controllers/IssueApp.java:214` | `private static Result issuesAsPjax(Project project, Page<Issue> issues, models.support.SearchConditi` |
| GL-controllers_IssueApp-014 | `app/controllers/IssueApp.java:225` | `private static Result issuesAsJson(Project project, Page<Issue> issues) {` |
| GL-controllers_IssueApp-015 | `app/controllers/IssueApp.java:263` | `@Transactional` |
| GL-controllers_IssueApp-016 | `app/controllers/IssueApp.java:315` | `@IsAllowed(resourceType = ResourceType.ISSUE_POST, value = Operation.READ)` |
| GL-controllers_IssueApp-017 | `app/controllers/IssueApp.java:328` | `public static Result newDirectIssueForm(Long commentId) {` |
| GL-controllers_IssueApp-018 | `app/controllers/IssueApp.java:351` | `public static Result newDirectMyIssueForm() {` |
| GL-controllers_IssueApp-019 | `app/controllers/IssueApp.java:385` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_IssueApp-020 | `app/controllers/IssueApp.java:394` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_IssueApp-021 | `app/controllers/IssueApp.java:410` | `@Transactional` |
| GL-controllers_IssueApp-022 | `app/controllers/IssueApp.java:484` | `private static void updateLabelIfChanged(List<Long> attachingLabelIds, List<Long> detachingLabelIds,` |
| GL-controllers_IssueApp-023 | `app/controllers/IssueApp.java:518` | `private static void updateMilestoneIfChanged(Milestone newMilestone, Issue issue) {` |
| GL-controllers_IssueApp-025 | `app/controllers/IssueApp.java:549` | `private static void updateStateIfChanged(State newState, Issue issue) {` |
| GL-controllers_IssueApp-026 | `app/controllers/IssueApp.java:564` | `private static void updateAssigneeIfChanged(User assignee, Project project, Issue issue) {` |
| GL-controllers_IssueApp-027 | `app/controllers/IssueApp.java:589` | `@Transactional` |
| GL-controllers_IssueApp-028 | `app/controllers/IssueApp.java:676` | `private static void removeAnonymousAssignee(Issue issue) {` |
| GL-controllers_IssueApp-030 | `app/controllers/IssueApp.java:688` | `private static boolean hasAssignee(Issue issue) {` |
| GL-controllers_IssueApp-031 | `app/controllers/IssueApp.java:693` | `@With(NullProjectCheckAction.class)` |
| GL-controllers_IssueApp-032 | `app/controllers/IssueApp.java:712` | `@Transactional` |
| GL-controllers_IssueApp-033 | `app/controllers/IssueApp.java:731` | `private static void addAssigneeChangedNotification(Issue modifiedIssue, Issue originalIssue) {` |
| GL-controllers_IssueApp-034 | `app/controllers/IssueApp.java:743` | `private static void addStateChangedNotification(Issue modifiedIssue, Issue originalIssue) {` |
| GL-controllers_IssueApp-035 | `app/controllers/IssueApp.java:751` | `private static void addBodyChangedNotification(Issue modifiedIssue, Issue originalIssue) {` |
| GL-controllers_IssueApp-036 | `app/controllers/IssueApp.java:759` | `private static void addIssueMovedNotification(Project previous, Issue originalIssue, Issue issue, Se` |
| GL-controllers_IssueApp-037 | `app/controllers/IssueApp.java:772` | `@With(NullProjectCheckAction.class)` |
| GL-controllers_IssueApp-038 | `app/controllers/IssueApp.java:879` | `private static boolean hasTargetProject(Issue issue) {` |
| GL-controllers_IssueApp-040 | `app/controllers/IssueApp.java:889` | `private static void moveIssueToOtherProject(Issue originalIssue, Project toOtherProject) {` |
| GL-controllers_IssueApp-041 | `app/controllers/IssueApp.java:896` | `private static void moveSubtaskToOtherProject(Issue originalIssue, Project toOtherProject) {` |
| GL-controllers_IssueApp-042 | `app/controllers/IssueApp.java:904` | `private static void updateIssueToOtherProject(Issue issue, Project toOtherProject) {` |
| GL-controllers_IssueApp-043 | `app/controllers/IssueApp.java:923` | `private static void transferLabels(Issue originalIssue, Project toProject) {` |
| GL-controllers_IssueApp-045 | `app/controllers/IssueApp.java:951` | `private static void updateSubtaskRelation(Issue issue, Issue originalIssue) {` |
| GL-controllers_IssueApp-048 | `app/controllers/IssueApp.java:983` | `/**` |
| GL-controllers_IssueApp-051 | `app/controllers/IssueApp.java:1072` | `private static void AddPreviousContent(Issue issue, IssueComment comment) {` |
| GL-controllers_IssueApp-053 | `app/controllers/IssueApp.java:1117` | `// Just made for compatibility. No meanings.` |
| GL-controllers_IssueApp-054 | `app/controllers/IssueApp.java:1123` | `private static Comment saveComment(Project project, Issue issue, IssueComment comment) {` |
| GL-controllers_IssueApp-056 | `app/controllers/IssueApp.java:1151` | `private static void toNextState(Long number, Project project) {` |
| GL-controllers_IssueApp-057 | `app/controllers/IssueApp.java:1157` | `private static boolean containsStateTransitionRequest() {` |
| GL-controllers_IssueApp-060 | `app/controllers/IssueApp.java:1177` | `private static Html commentFormValidationResult(Project project, Form<IssueComment> commentForm) {` |
| GL-controllers_IssueApp-061 | `app/controllers/IssueApp.java:1188` | `/**` |
| GL-controllers_IssueApp-062 | `app/controllers/IssueApp.java:1204` | `private static void addLabels(Issue issue, Http.Request request) {` |
| GL-controllers_UserApp-001 | `app/controllers/UserApp.java:58` | `public class UserApp extends Controller {` |
| GL-controllers_UserApp-010 | `app/controllers/UserApp.java:76` | `public static final String DEFAULT_AVATAR_URL` |
| GL-controllers_UserApp-012 | `app/controllers/UserApp.java:81` | `public static final int MAX_FETCH_USERS = 10;  //Match value to Typeahead deafult value at yobi.ui.T` |
| GL-controllers_UserApp-021 | `app/controllers/UserApp.java:100` | `public static final boolean useSocialLoginOnly = play.Configuration.root()` |
| GL-controllers_UserApp-024 | `app/controllers/UserApp.java:107` | `private static boolean usingEmailVerification = play.Configuration.root()` |
| GL-controllers_UserApp-025 | `app/controllers/UserApp.java:111` | `@AnonymousCheck` |
| GL-controllers_UserApp-026 | `app/controllers/UserApp.java:151` | `public static void noCache(final Http.Response response) {` |
| GL-controllers_UserApp-027 | `app/controllers/UserApp.java:159` | `public static Result loginForm() {` |
| GL-controllers_UserApp-028 | `app/controllers/UserApp.java:182` | `public static Result logout() {` |
| GL-controllers_UserApp-029 | `app/controllers/UserApp.java:190` | `public static Result login() {` |
| GL-controllers_UserApp-030 | `app/controllers/UserApp.java:205` | `/**` |
| GL-controllers_UserApp-031 | `app/controllers/UserApp.java:288` | `private static String encodedPath(String path){` |
| GL-controllers_UserApp-032 | `app/controllers/UserApp.java:301` | `/**` |
| GL-controllers_UserApp-033 | `app/controllers/UserApp.java:360` | `/**` |
| GL-controllers_UserApp-034 | `app/controllers/UserApp.java:370` | `/**` |
| GL-controllers_UserApp-035 | `app/controllers/UserApp.java:383` | `/**` |
| GL-controllers_UserApp-036 | `app/controllers/UserApp.java:397` | `public static User authenticateWithHashedPassword(String loginId, String password) {` |
| GL-controllers_UserApp-037 | `app/controllers/UserApp.java:402` | `public static User authenticateWithPlainPassword(String loginId, String password) {` |
| GL-controllers_UserApp-038 | `app/controllers/UserApp.java:407` | `public static Result signupForm() {` |
| GL-controllers_UserApp-039 | `app/controllers/UserApp.java:416` | `@Transactional` |
| GL-controllers_UserApp-040 | `app/controllers/UserApp.java:447` | `private static String newLoginIdWithoutDup(final String candidate, int num) {` |
| GL-controllers_UserApp-041 | `app/controllers/UserApp.java:458` | `public static User createLocalUserWithOAuth(UserCredential userCredential){` |
| GL-controllers_UserApp-042 | `app/controllers/UserApp.java:492` | `private static void forceOAuthLogout() {` |
| GL-controllers_UserApp-043 | `app/controllers/UserApp.java:497` | `private static User createUserDelegate(CandidateUser candidateUser) {` |
| GL-controllers_UserApp-044 | `app/controllers/UserApp.java:522` | `public static Result verifyUser(String loginId, String verificationCode){` |
| GL-controllers_UserApp-045 | `app/controllers/UserApp.java:541` | `private static void sendMailAfterUserCreation(User created) {` |
| GL-controllers_UserApp-050 | `app/controllers/UserApp.java:617` | `private static String generateLoginId(User user, String loginIdCandidate) {` |
| GL-controllers_UserApp-051 | `app/controllers/UserApp.java:632` | `@Transactional` |
| GL-controllers_UserApp-052 | `app/controllers/UserApp.java:661` | `public static Result resetUserVisitedList() {` |
| GL-controllers_UserApp-054 | `app/controllers/UserApp.java:674` | `@Transactional` |
| GL-controllers_UserApp-055 | `app/controllers/UserApp.java:681` | `@Transactional` |
| GL-controllers_UserApp-058 | `app/controllers/UserApp.java:733` | `public static void initTokenUser() {` |
| GL-controllers_UserApp-061 | `app/controllers/UserApp.java:765` | `private static User invalidToken() {` |
| GL-controllers_UserApp-062 | `app/controllers/UserApp.java:771` | `@AnonymousCheck` |
| GL-controllers_UserApp-063 | `app/controllers/UserApp.java:787` | `@AnonymousCheck` |
| GL-controllers_UserApp-067 | `app/controllers/UserApp.java:897` | `private static void sortIssues(List<Issue> issues) {` |
| GL-controllers_UserApp-068 | `app/controllers/UserApp.java:907` | `private static void sortPullRequests(List<PullRequest> pullRequests) {` |
| GL-controllers_UserApp-069 | `app/controllers/UserApp.java:917` | `private static List<Project> collectProjects(User user, Map<Long, Boolean> projectAcl) {` |
| GL-controllers_UserApp-070 | `app/controllers/UserApp.java:924` | `private static void addProjectNotDupped(List<Project> target, List<Project> foundProjects,` |
| GL-controllers_UserApp-071 | `app/controllers/UserApp.java:947` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_UserApp-072 | `app/controllers/UserApp.java:956` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_UserApp-074 | `app/controllers/UserApp.java:990` | `private enum UserInfoFormTabType {` |
| GL-controllers_UserApp-076 | `app/controllers/UserApp.java:1068` | `@Transactional` |
| GL-controllers_UserApp-077 | `app/controllers/UserApp.java:1075` | `/**` |
| GL-controllers_UserApp-078 | `app/controllers/UserApp.java:1093` | `@BodyParser.Of(BodyParser.Json.class)` |
| GL-controllers_UserApp-081 | `app/controllers/UserApp.java:1146` | `@Transactional` |
| GL-controllers_UserApp-082 | `app/controllers/UserApp.java:1164` | `@Transactional` |
| GL-controllers_UserApp-083 | `app/controllers/UserApp.java:1184` | `@Transactional` |
| GL-controllers_UserApp-084 | `app/controllers/UserApp.java:1201` | `@Transactional` |
| GL-controllers_UserApp-085 | `app/controllers/UserApp.java:1229` | `private static User authenticate(String loginId, String password, boolean hashed) {` |
| GL-controllers_UserApp-086 | `app/controllers/UserApp.java:1242` | `public static User authenticateWithLdap(String loginIdOrEmail, String password) {` |
| GL-controllers_UserApp-087 | `app/controllers/UserApp.java:1289` | `private static User createNewUser(String password, LdapUser ldapUser) {` |
| GL-controllers_UserApp-089 | `app/controllers/UserApp.java:1316` | `public static void setupRememberMe(User user) {` |
| GL-controllers_UserApp-090 | `app/controllers/UserApp.java:1322` | `private static void processLogout() {` |
| GL-controllers_UserApp-091 | `app/controllers/UserApp.java:1328` | `private static void validate(Form<User> newUserForm) {` |
| GL-controllers_UserApp-092 | `app/controllers/UserApp.java:1352` | `public static User createNewUser(User user) {` |
| GL-controllers_UserApp-093 | `app/controllers/UserApp.java:1371` | `public static void addUserInfoToSession(User user) {` |
| GL-controllers_UserApp-094 | `app/controllers/UserApp.java:1385` | `public static boolean linkWithExistedOrCreateLocalUser() {` |
| GL-controllers_UserApp-095 | `app/controllers/UserApp.java:1409` | `public static void updatePreferredLanguage() {` |
| GL-controllers_UserApp-096 | `app/controllers/UserApp.java:1434` | `public static Result resetUserPasswordBySiteManager(String loginId){` |
| GL-controllers_UserApp-098 | `app/controllers/UserApp.java:1470` | `@AnonymousCheck` |
| GL-controllers_UserApp-099 | `app/controllers/UserApp.java:1482` | `public static Result usermenuTabContentList(){` |
| GL-controllers_VoteApp-001 | `app/controllers/VoteApp.java:39` | `/**` |
| GL-controllers_VoteApp-002 | `app/controllers/VoteApp.java:46` | `/**` |
| GL-controllers_VoteApp-003 | `app/controllers/VoteApp.java:71` | `/**` |
| GL-controllers_VoteApp-004 | `app/controllers/VoteApp.java:95` | `@Transactional` |
| GL-controllers_VoteApp-005 | `app/controllers/VoteApp.java:109` | `@Transactional` |
| GL-controllers_VoteApp-009 | `app/controllers/VoteApp.java:143` | `/**` |
| GL-controllers_IssueLabelApp-001 | `app/controllers/IssueLabelApp.java:52` | `@AnonymousCheck` |
| GL-controllers_IssueLabelApp-002 | `app/controllers/IssueLabelApp.java:55` | `/**` |
| GL-controllers_IssueLabelApp-003 | `app/controllers/IssueLabelApp.java:78` | `/**` |
| GL-controllers_IssueLabelApp-004 | `app/controllers/IssueLabelApp.java:116` | `private static Result labelsAsPjax(String ownerName, String projectName){` |
| GL-controllers_IssueLabelApp-005 | `app/controllers/IssueLabelApp.java:126` | `@IsAllowed(Operation.UPDATE)` |
| GL-controllers_IssueLabelApp-006 | `app/controllers/IssueLabelApp.java:135` | `public static class NewLabel {` |
| GL-controllers_IssueLabelApp-007 | `app/controllers/IssueLabelApp.java:168` | `/**` |
| GL-controllers_IssueLabelApp-008 | `app/controllers/IssueLabelApp.java:238` | `/**` |
| GL-controllers_IssueLabelApp-009 | `app/controllers/IssueLabelApp.java:284` | `@IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.ISSUE_LABEL)` |
| GL-controllers_IssueLabelApp-010 | `app/controllers/IssueLabelApp.java:302` | `/**` |
| GL-controllers_IssueLabelApp-011 | `app/controllers/IssueLabelApp.java:332` | `/**` |
| GL-controllers_IssueLabelApp-012 | `app/controllers/IssueLabelApp.java:372` | `/**` |
| GL-controllers_IssueLabelApp-013 | `app/controllers/IssueLabelApp.java:401` | `@IsAllowed(value = Operation.UPDATE,` |
| GL-controllers_IssueLabelApp-014 | `app/controllers/IssueLabelApp.java:429` | `/**` |
| GL-controllers_IssueLabelApp-015 | `app/controllers/IssueLabelApp.java:484` | `@Transactional` |
| GL-controllers_IssueLabelApp-016 | `app/controllers/IssueLabelApp.java:492` | `private static Map<String, String> toMap(IssueLabelCategory category) {` |
| GL-controllers_IssueLabelApp-017 | `app/controllers/IssueLabelApp.java:501` | `@IsCreatable(ResourceType.ISSUE_LABEL)` |
| GL-controllers_PlayDAVConfig-001 | `app/controllers/PlayDAVConfig.java:28` | `public class PlayDAVConfig extends DAVConfig {` |
| GL-controllers_PlayDAVConfig-002 | `app/controllers/PlayDAVConfig.java:30` | `public PlayDAVConfig() {` |
| GL-controllers_CodeHistoryApp-001 | `app/controllers/CodeHistoryApp.java:61` | `@AnonymousCheck` |
| GL-controllers_CodeHistoryApp-003 | `app/controllers/CodeHistoryApp.java:69` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeHistoryApp-004 | `app/controllers/CodeHistoryApp.java:77` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeHistoryApp-005 | `app/controllers/CodeHistoryApp.java:106` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeHistoryApp-006 | `app/controllers/CodeHistoryApp.java:159` | `@With(NullProjectCheckAction.class)` |
| GL-controllers_CodeHistoryApp-007 | `app/controllers/CodeHistoryApp.java:197` | `@IsCreatable(ResourceType.COMMIT_COMMENT)` |
| GL-controllers_CodeHistoryApp-008 | `app/controllers/CodeHistoryApp.java:258` | `@With(DefaultProjectCheckAction.class)` |
| GL-controllers_NotificationApp-001 | `app/controllers/NotificationApp.java:28` | `@AnonymousCheck` |
| GL-controllers_NotificationApp-002 | `app/controllers/NotificationApp.java:31` | `public static Result notifications(int from, int size) {` |
| GL-controllers_EnrollProjectApp-001 | `app/controllers/EnrollProjectApp.java:36` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_EnrollProjectApp-002 | `app/controllers/EnrollProjectApp.java:40` | `@Transactional` |
| GL-controllers_WatchApp-001 | `app/controllers/WatchApp.java:39` | `public class WatchApp extends Controller {` |
| GL-controllers_WatchApp-002 | `app/controllers/WatchApp.java:41` | `public static Result watch(ResourceParam resourceParam) {` |
| GL-controllers_WatchApp-003 | `app/controllers/WatchApp.java:59` | `@Transactional` |
| GL-controllers_OrganizationApp-001 | `app/controllers/OrganizationApp.java:48` | `/**` |
| GL-controllers_OrganizationApp-002 | `app/controllers/OrganizationApp.java:55` | `@AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)` |
| GL-controllers_OrganizationApp-003 | `app/controllers/OrganizationApp.java:75` | `@AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)` |
| GL-controllers_OrganizationApp-004 | `app/controllers/OrganizationApp.java:81` | `/**` |
| GL-controllers_OrganizationApp-006 | `app/controllers/OrganizationApp.java:121` | `private static void validate(Form<Organization> newOrgForm) {` |
| GL-controllers_OrganizationApp-007 | `app/controllers/OrganizationApp.java:140` | `/**` |
| GL-controllers_OrganizationApp-008 | `app/controllers/OrganizationApp.java:154` | `@Transactional` |
| GL-controllers_OrganizationApp-009 | `app/controllers/OrganizationApp.java:172` | `private static Result validateForAddMember(Form<User> addMemberForm, String organizationName) {` |
| GL-controllers_OrganizationApp-010 | `app/controllers/OrganizationApp.java:207` | `@Transactional` |
| GL-controllers_OrganizationApp-011 | `app/controllers/OrganizationApp.java:225` | `private static Result validateForDeleteMember(String organizationName, Long userId) {` |
| GL-controllers_OrganizationApp-012 | `app/controllers/OrganizationApp.java:252` | `@Transactional` |
| GL-controllers_OrganizationApp-013 | `app/controllers/OrganizationApp.java:267` | `private static Result validateForEditMember(Form<Role> roleForm, String organizationName, Long userI` |
| GL-controllers_OrganizationApp-015 | `app/controllers/OrganizationApp.java:312` | `public static ValidationResult validateForLeave(String organizationName) {` |
| GL-controllers_OrganizationApp-017 | `app/controllers/OrganizationApp.java:336` | `public static Result members(String organizationName) {` |
| GL-controllers_OrganizationApp-018 | `app/controllers/OrganizationApp.java:348` | `private static Result validateForSetting(String organizationName) {` |
| GL-controllers_OrganizationApp-019 | `app/controllers/OrganizationApp.java:363` | `public static Result settingForm(String organizationName) {` |
| GL-controllers_OrganizationApp-020 | `app/controllers/OrganizationApp.java:375` | `private static Result okWithLocation(String location) {` |
| GL-controllers_OrganizationApp-021 | `app/controllers/OrganizationApp.java:383` | `/**` |
| GL-controllers_OrganizationApp-025 | `app/controllers/OrganizationApp.java:471` | `public static Result deleteForm(String organizationName) {` |
| GL-controllers_OrganizationApp-026 | `app/controllers/OrganizationApp.java:483` | `@Transactional` |
| GL-controllers_OrganizationApp-027 | `app/controllers/OrganizationApp.java:499` | `private static ValidationResult validateForDelete(Organization organization) {` |
| GL-controllers_OrganizationApp-028 | `app/controllers/OrganizationApp.java:514` | `@GuestProhibit` |
| GL-controllers_ProjectApp-001 | `app/controllers/ProjectApp.java:65` | `@AnonymousCheck` |
| GL-controllers_ProjectApp-012 | `app/controllers/ProjectApp.java:99` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_ProjectApp-013 | `app/controllers/ProjectApp.java:116` | `@IsAllowed(Operation.READ)` |
| GL-controllers_ProjectApp-019 | `app/controllers/ProjectApp.java:220` | `private static boolean validateWhenNew(Form<Project> newProjectForm) {` |
| GL-controllers_ProjectApp-020 | `app/controllers/ProjectApp.java:255` | `@Transactional` |
| GL-controllers_ProjectApp-021 | `app/controllers/ProjectApp.java:301` | `public static void saveProjectMenuSetting(Project project) {` |
| GL-controllers_ProjectApp-022 | `app/controllers/ProjectApp.java:316` | `private static boolean validateWhenUpdate(String loginId, Form<Project> updateProjectForm) {` |
| GL-controllers_ProjectApp-023 | `app/controllers/ProjectApp.java:354` | `@IsAllowed(Operation.DELETE)` |
| GL-controllers_ProjectApp-024 | `app/controllers/ProjectApp.java:362` | `@Transactional` |
| GL-controllers_ProjectApp-025 | `app/controllers/ProjectApp.java:383` | `@Transactional` |
| GL-controllers_ProjectApp-026 | `app/controllers/ProjectApp.java:394` | `@Transactional` |
| GL-controllers_ProjectApp-027 | `app/controllers/ProjectApp.java:411` | `@AnonymousCheck` |
| GL-controllers_ProjectApp-030 | `app/controllers/ProjectApp.java:466` | `private static void addProjectNameToMentionList(List<Map<String, String>> users, Project project) {` |
| GL-controllers_ProjectApp-031 | `app/controllers/ProjectApp.java:487` | `private static void addOrganizationNameToMentionList(List<Map<String, String>> users, Project projec` |
| GL-controllers_ProjectApp-032 | `app/controllers/ProjectApp.java:503` | `private static void collectedIssuesToMap(List<Map<String, String>> mentionList,` |
| GL-controllers_ProjectApp-034 | `app/controllers/ProjectApp.java:533` | `@IsAllowed(Operation.READ)` |
| GL-controllers_ProjectApp-035 | `app/controllers/ProjectApp.java:576` | `@IsAllowed(Operation.READ)` |
| GL-controllers_ProjectApp-036 | `app/controllers/ProjectApp.java:619` | `private static void addCommentAuthors(Long pullRequestId, List<User> userList) {` |
| GL-controllers_ProjectApp-037 | `app/controllers/ProjectApp.java:634` | `@IsAllowed(Operation.DELETE)` |
| GL-controllers_ProjectApp-038 | `app/controllers/ProjectApp.java:643` | `@Transactional` |
| GL-controllers_ProjectApp-039 | `app/controllers/ProjectApp.java:686` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_ProjectApp-040 | `app/controllers/ProjectApp.java:745` | `private static void disableProjectTransferLink(ProjectTransfer pt, Project project, String newProjec` |
| GL-controllers_ProjectApp-041 | `app/controllers/ProjectApp.java:755` | `@IsAllowed(Operation.UPDATE)` |
| GL-controllers_ProjectApp-042 | `app/controllers/ProjectApp.java:763` | `@IsAllowed(Operation.UPDATE)` |
| GL-controllers_ProjectApp-043 | `app/controllers/ProjectApp.java:785` | `private static void sendTransferRequestMail(ProjectTransfer pt) {` |
| GL-controllers_ProjectApp-044 | `app/controllers/ProjectApp.java:828` | `private static void addCodeCommenters(String commitId, Long projectId, List<User> userList) {` |
| GL-controllers_ProjectApp-045 | `app/controllers/ProjectApp.java:863` | `private static void addCommitAuthor(Commit commit, List<User> userList) {` |
| GL-controllers_ProjectApp-046 | `app/controllers/ProjectApp.java:878` | `private static void collectAuthorAndCommenter(Project project, Long number, List<User> userList, Str` |
| GL-controllers_ProjectApp-047 | `app/controllers/ProjectApp.java:908` | `private static void collectedUsersToMentionList(List<Map<String, String>> users, List<User> userList` |
| GL-controllers_ProjectApp-048 | `app/controllers/ProjectApp.java:922` | `private static void addSearchedUsers(String query, List<User> userList) {` |
| GL-controllers_ProjectApp-049 | `app/controllers/ProjectApp.java:931` | `private static void addProjectMemberList(Project project, List<User> userList) {` |
| GL-controllers_ProjectApp-050 | `app/controllers/ProjectApp.java:940` | `private static void addGroupMemberList(Project project, List<User> userList) {` |
| GL-controllers_ProjectApp-051 | `app/controllers/ProjectApp.java:953` | `private static void addProjectAuthorsAndWatchersList(Project project, List<User> userList) {` |
| GL-controllers_ProjectApp-052 | `app/controllers/ProjectApp.java:966` | `private static void addSharers(Project project, Long number, List<User> userList, String resourceTyp` |
| GL-controllers_ProjectApp-053 | `app/controllers/ProjectApp.java:990` | `@Transactional` |
| GL-controllers_ProjectApp-055 | `app/controllers/ProjectApp.java:1037` | `/**` |
| GL-controllers_ProjectApp-056 | `app/controllers/ProjectApp.java:1054` | `/**` |
| GL-controllers_ProjectApp-057 | `app/controllers/ProjectApp.java:1089` | `/**` |
| GL-controllers_ProjectApp-058 | `app/controllers/ProjectApp.java:1110` | `/**` |
| GL-controllers_ProjectApp-062 | `app/controllers/ProjectApp.java:1177` | `private static ExpressionList<Project> createProjectSearchExpressionList(String query) {` |
| GL-controllers_ProjectApp-063 | `app/controllers/ProjectApp.java:1201` | `/**` |
| GL-controllers_ProjectApp-064 | `app/controllers/ProjectApp.java:1223` | `/**` |
| GL-controllers_ProjectApp-065 | `app/controllers/ProjectApp.java:1236` | `/**` |
| GL-controllers_ProjectApp-066 | `app/controllers/ProjectApp.java:1297` | `/**` |
| GL-controllers_ProjectApp-067 | `app/controllers/ProjectApp.java:1331` | `/**` |
| GL-controllers_ProjectApp-068 | `app/controllers/ProjectApp.java:1351` | `@Transactional` |
| GL-controllers_ProjectApp-069 | `app/controllers/ProjectApp.java:1374` | `private static void createWebhook(Project project, Form<Webhook> forms) {` |
| GL-controllers_ProjectApp-070 | `app/controllers/ProjectApp.java:1383` | `@Transactional` |
| GL-controllers_ProjectApp-071 | `app/controllers/ProjectApp.java:1396` | `@Transactional` |
| GL-controllers_ProjectApp-072 | `app/controllers/ProjectApp.java:1408` | `@IsAllowed(Operation.READ)` |
| GL-controllers_ImportApp-001 | `app/controllers/ImportApp.java:50` | `@AnonymousCheck` |
| GL-controllers_ImportApp-002 | `app/controllers/ImportApp.java:54` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_ImportApp-003 | `app/controllers/ImportApp.java:63` | `@Transactional` |
| GL-controllers_ImportApp-004 | `app/controllers/ImportApp.java:123` | `private static void saveProjectMenuSetting(Project project) {` |
| GL-controllers_ImportApp-005 | `app/controllers/ImportApp.java:139` | `/**` |
| GL-controllers_ImportApp-006 | `app/controllers/ImportApp.java:173` | `private static ValidationResult validateForm(Form<Project> newProjectForm, Organization organization` |
| GL-controllers_Secured-001 | `app/controllers/Secured.java:10` | `public class Secured extends Security.Authenticator {` |
| GL-controllers_Secured-002 | `app/controllers/Secured.java:13` | `@Override` |
| GL-controllers_Secured-003 | `app/controllers/Secured.java:25` | `@Override` |
| GL-controllers_SvnApp-001 | `app/controllers/SvnApp.java:40` | `public class SvnApp extends Controller {` |
| GL-controllers_SvnApp-002 | `app/controllers/SvnApp.java:42` | `private static final String[] WEBDAV_METHODS = {` |
| GL-controllers_SvnApp-004 | `app/controllers/SvnApp.java:71` | `@With(BasicAuthAction.class)` |
| GL-controllers_SvnApp-005 | `app/controllers/SvnApp.java:78` | `@With(BasicAuthAction.class)` |
| GL-controllers_SvnApp-006 | `app/controllers/SvnApp.java:151` | `private static PlayServletResponse startDavService(final String ownerName, String pathInfo) throws I` |
| GL-controllers_SvnApp-007 | `app/controllers/SvnApp.java:177` | `private static Result sendResponse(String requestMethod, int statusCode,` |
| GL-controllers_StatisticsApp-001 | `app/controllers/StatisticsApp.java:32` | `@AnonymousCheck` |
| GL-controllers_StatisticsApp-002 | `app/controllers/StatisticsApp.java:36` | `@With(DefaultProjectCheckAction.class)` |
| GL-controllers_CompareApp-001 | `app/controllers/CompareApp.java:41` | `@AnonymousCheck` |
| GL-controllers_CompareApp-002 | `app/controllers/CompareApp.java:44` | `@IsAllowed(Operation.READ)` |
| GL-controllers_MarkdownApp-001 | `app/controllers/MarkdownApp.java:30` | `public class MarkdownApp extends Controller {` |
| GL-controllers_MarkdownApp-002 | `app/controllers/MarkdownApp.java:32` | `public static Result render(String ownerName, String projectName) {` |
| GL-controllers_CodeApp-001 | `app/controllers/CodeApp.java:46` | `@AnonymousCheck` |
| GL-controllers_CodeApp-003 | `app/controllers/CodeApp.java:52` | `@IsAllowed(Operation.READ)` |
| GL-controllers_CodeApp-004 | `app/controllers/CodeApp.java:89` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeApp-005 | `app/controllers/CodeApp.java:127` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeApp-007 | `app/controllers/CodeApp.java:174` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeApp-008 | `app/controllers/CodeApp.java:191` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeApp-009 | `app/controllers/CodeApp.java:212` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_CodeApp-010 | `app/controllers/CodeApp.java:222` | `private static Tika tika = new Tika();` |
| GL-controllers_CodeApp-014 | `app/controllers/CodeApp.java:256` | `@IsAllowed(Operation.READ)` |
| GL-controllers_AbstractPostingApp-001 | `app/controllers/AbstractPostingApp.java:34` | `@AnonymousCheck` |
| GL-controllers_AbstractPostingApp-004 | `app/controllers/AbstractPostingApp.java:42` | `public static class SearchCondition {` |
| GL-controllers_AbstractPostingApp-005 | `app/controllers/AbstractPostingApp.java:57` | `public static Comment saveComment(final Comment comment, Runnable containerUpdater) {` |
| GL-controllers_AbstractPostingApp-006 | `app/controllers/AbstractPostingApp.java:74` | `protected static Result delete(Model target, Resource resource, Call redirectTo) {` |
| GL-controllers_AbstractPostingApp-007 | `app/controllers/AbstractPostingApp.java:90` | `protected static Result editPosting(AbstractPosting original, AbstractPosting posting, Form<? extend` |
| GL-controllers_AbstractPostingApp-008 | `app/controllers/AbstractPostingApp.java:141` | `private static String addToHistory(AbstractPosting original, AbstractPosting posting) {` |
| GL-controllers_AbstractPostingApp-011 | `app/controllers/AbstractPostingApp.java:232` | `public static void attachUploadFilesToPost(Resource resource) {` |
| GL-controllers_AbstractPostingApp-012 | `app/controllers/AbstractPostingApp.java:245` | `public static void attachUploadFilesToPost(JsonNode files, Resource resource) {` |
| GL-controllers_WatchProjectApp-001 | `app/controllers/WatchProjectApp.java:28` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_WatchProjectApp-002 | `app/controllers/WatchProjectApp.java:32` | `@IsAllowed(Operation.READ)` |
| GL-controllers_WatchProjectApp-003 | `app/controllers/WatchProjectApp.java:41` | `@IsAllowed(Operation.READ)` |
| GL-controllers_WatchProjectApp-004 | `app/controllers/WatchProjectApp.java:53` | `public static Result toggle(Long projectId, String notificationType) {` |
| GL-controllers_HelpApp-001 | `app/controllers/HelpApp.java:29` | `@AnonymousCheck` |
| GL-controllers_HelpApp-002 | `app/controllers/HelpApp.java:32` | `public static Result help() {` |
| GL-controllers_BoardApp-001 | `app/controllers/BoardApp.java:48` | `public class BoardApp extends AbstractPostingApp {` |
| GL-controllers_BoardApp-002 | `app/controllers/BoardApp.java:50` | `public static class SearchCondition extends AbstractPostingApp.SearchCondition {` |
| GL-controllers_BoardApp-003 | `app/controllers/BoardApp.java:122` | `@AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)` |
| GL-controllers_BoardApp-004 | `app/controllers/BoardApp.java:143` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.PROJECT)` |
| GL-controllers_BoardApp-005 | `app/controllers/BoardApp.java:165` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_BoardApp-006 | `app/controllers/BoardApp.java:196` | `private static boolean projectHasReadme(Project project) {` |
| GL-controllers_BoardApp-007 | `app/controllers/BoardApp.java:201` | `private static boolean readmeEditRequested() {` |
| GL-controllers_BoardApp-008 | `app/controllers/BoardApp.java:206` | `private static boolean issueTemplateEditRequested() {` |
| GL-controllers_BoardApp-011 | `app/controllers/BoardApp.java:221` | `@Transactional` |
| GL-controllers_BoardApp-012 | `app/controllers/BoardApp.java:275` | `private static void commitReadmeFile(Project project, Posting post){` |
| GL-controllers_BoardApp-013 | `app/controllers/BoardApp.java:287` | `private static void commitIssueTemplateFile(Project project, Posting post){` |
| GL-controllers_BoardApp-014 | `app/controllers/BoardApp.java:299` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)` |
| GL-controllers_BoardApp-015 | `app/controllers/BoardApp.java:329` | `@With(NullProjectCheckAction.class)` |
| GL-controllers_BoardApp-016 | `app/controllers/BoardApp.java:348` | `/**` |
| GL-controllers_BoardApp-017 | `app/controllers/BoardApp.java:385` | `private static void unmarkAnotherReadmePostingIfExists(Project project, Long postingNumber) {` |
| GL-controllers_BoardApp-018 | `app/controllers/BoardApp.java:394` | `/**` |
| GL-controllers_BoardApp-020 | `app/controllers/BoardApp.java:449` | `private static void AddPreviousContent(Posting posting, PostingComment comment) {` |
| GL-controllers_BoardApp-022 | `app/controllers/BoardApp.java:474` | `// Just made for compatibility. No meanings.` |
| GL-controllers_BoardApp-023 | `app/controllers/BoardApp.java:480` | `private static Comment saveComment(Project project, Posting posting, PostingComment comment) {` |
| GL-controllers_BoardApp-025 | `app/controllers/BoardApp.java:508` | `/**` |
| GL-controllers_MigrationApp-001 | `app/controllers/MigrationApp.java:43` | `@AnonymousCheck` |
| GL-controllers_MigrationApp-002 | `app/controllers/MigrationApp.java:47` | `static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");` |
| GL-controllers_MigrationApp-004 | `app/controllers/MigrationApp.java:54` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-006 | `app/controllers/MigrationApp.java:99` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-007 | `app/controllers/MigrationApp.java:120` | `private static List<Project> sortProjectsByOwnerAndName(Set<Project> projects) {` |
| GL-controllers_MigrationApp-008 | `app/controllers/MigrationApp.java:129` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-011 | `app/controllers/MigrationApp.java:172` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-012 | `app/controllers/MigrationApp.java:179` | `public static ObjectNode composeIssueLabelPairJson(String owner, String projectName) {` |
| GL-controllers_MigrationApp-013 | `app/controllers/MigrationApp.java:198` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-014 | `app/controllers/MigrationApp.java:218` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-015 | `app/controllers/MigrationApp.java:231` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-016 | `app/controllers/MigrationApp.java:244` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_MigrationApp-017 | `app/controllers/MigrationApp.java:257` | `public static ObjectNode composeMilestoneJson(Milestone m) {` |
| GL-controllers_MigrationApp-019 | `app/controllers/MigrationApp.java:277` | `private static String addOriginalAuthorName(String bodyText, String authorLoginId,` |
| GL-controllers_MigrationApp-020 | `app/controllers/MigrationApp.java:284` | `private static String relativeLinksToAbsolutePath(String text){` |
| GL-controllers_MigrationApp-021 | `app/controllers/MigrationApp.java:292` | `private static String relativeLinksToWikiCommitPath(String text){` |
| GL-controllers_MigrationApp-022 | `app/controllers/MigrationApp.java:300` | `private static StringBuilder addAttachmentsString(@NotNull StringBuilder sb, ResourceType type, Stri` |
| GL-controllers_MigrationApp-023 | `app/controllers/MigrationApp.java:316` | `private static void addListHeader(@NotNull StringBuilder sb) {` |
| GL-controllers_MigrationApp-024 | `app/controllers/MigrationApp.java:321` | `private static StringBuilder addAttachmentsStringUsingWikiCommit(@NotNull StringBuilder sb, Resource` |
| GL-controllers_MigrationApp-025 | `app/controllers/MigrationApp.java:338` | `private static ObjectNode composePostJson(Posting posting) {` |
| GL-controllers_MigrationApp-026 | `app/controllers/MigrationApp.java:370` | `private static boolean usingWikiCommitForAttachment() {` |
| GL-controllers_MigrationApp-027 | `app/controllers/MigrationApp.java:377` | `private static ObjectNode composeIssueJson(Issue issue) {` |
| GL-controllers_MigrationApp-028 | `app/controllers/MigrationApp.java:414` | `public static List<ObjectNode> composeCommentsJson(AbstractPosting posting, String orgLink, Resource` |
| GL-controllers_MigrationApp-029 | `app/controllers/MigrationApp.java:440` | `public static List<ObjectNode> composePlainCommentsJson(AbstractPosting posting, ResourceType type) ` |
| GL-controllers_MigrationApp-030 | `app/controllers/MigrationApp.java:461` | `private static void gatheringUserProjects(Set<Project> targetProjects) {` |
| GL-controllers_MigrationApp-031 | `app/controllers/MigrationApp.java:469` | `private static void getheringOrgProjects(Set<Project> targetProjects) {` |
| GL-controllers_GitApp-001 | `app/controllers/GitApp.java:43` | `public class GitApp extends Controller {` |
| GL-controllers_GitApp-004 | `app/controllers/GitApp.java:72` | `/**` |
| GL-controllers_GitApp-006 | `app/controllers/GitApp.java:150` | `@With(BasicAuthAction.class)` |
| GL-controllers_GitApp-007 | `app/controllers/GitApp.java:162` | `@With(BasicAuthAction.class)` |
| GL-controllers_PasswordResetApp-001 | `app/controllers/PasswordResetApp.java:47` | `public class PasswordResetApp extends Controller {` |
| GL-controllers_PasswordResetApp-002 | `app/controllers/PasswordResetApp.java:50` | `public static Result lostPassword(){` |
| GL-controllers_PasswordResetApp-003 | `app/controllers/PasswordResetApp.java:56` | `public static Result requestResetPasswordEmail(){` |
| GL-controllers_PasswordResetApp-004 | `app/controllers/PasswordResetApp.java:79` | `private static boolean sendPasswordResetMail(User user, String hashString) {` |
| GL-controllers_PasswordResetApp-006 | `app/controllers/PasswordResetApp.java:105` | `public static Result resetPasswordForm(String hashString){` |
| GL-controllers_PasswordResetApp-007 | `app/controllers/PasswordResetApp.java:110` | `public static Result resetPassword(){` |
| GL-controllers_PullRequestApp-001 | `app/controllers/PullRequestApp.java:69` | `@IsOnlyGitAvailable` |
| GL-controllers_PullRequestApp-002 | `app/controllers/PullRequestApp.java:74` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-003 | `app/controllers/PullRequestApp.java:86` | `private static String findDestination(String forkOwner) {` |
| GL-controllers_PullRequestApp-004 | `app/controllers/PullRequestApp.java:95` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-005 | `app/controllers/PullRequestApp.java:118` | `@Transactional` |
| GL-controllers_PullRequestApp-006 | `app/controllers/PullRequestApp.java:169` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-008 | `app/controllers/PullRequestApp.java:217` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-009 | `app/controllers/PullRequestApp.java:245` | `static class PullRequestCreationResult {` |
| GL-controllers_PullRequestApp-010 | `app/controllers/PullRequestApp.java:263` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-011 | `app/controllers/PullRequestApp.java:272` | `@Transactional` |
| GL-controllers_PullRequestApp-012 | `app/controllers/PullRequestApp.java:330` | `private static void validateForm(Form<PullRequest> form) {` |
| GL-controllers_PullRequestApp-013 | `app/controllers/PullRequestApp.java:338` | `@IsAllowed(Operation.READ)` |
| GL-controllers_PullRequestApp-014 | `app/controllers/PullRequestApp.java:344` | `@IsAllowed(Operation.READ)` |
| GL-controllers_PullRequestApp-015 | `app/controllers/PullRequestApp.java:350` | `@IsAllowed(Operation.READ)` |
| GL-controllers_PullRequestApp-016 | `app/controllers/PullRequestApp.java:356` | `@Transactional` |
| GL-controllers_PullRequestApp-017 | `app/controllers/PullRequestApp.java:377` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)` |
| GL-controllers_PullRequestApp-018 | `app/controllers/PullRequestApp.java:395` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)` |
| GL-controllers_PullRequestApp-019 | `app/controllers/PullRequestApp.java:425` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)` |
| GL-controllers_PullRequestApp-020 | `app/controllers/PullRequestApp.java:432` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.PULL_REQUEST)` |
| GL-controllers_PullRequestApp-021 | `app/controllers/PullRequestApp.java:441` | `@Transactional` |
| GL-controllers_PullRequestApp-022 | `app/controllers/PullRequestApp.java:483` | `private static void addNotification(PullRequest pullRequest, State from, State to) {` |
| GL-controllers_PullRequestApp-023 | `app/controllers/PullRequestApp.java:489` | `@Transactional` |
| GL-controllers_PullRequestApp-024 | `app/controllers/PullRequestApp.java:507` | `@Transactional` |
| GL-controllers_PullRequestApp-025 | `app/controllers/PullRequestApp.java:533` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_PullRequestApp-026 | `app/controllers/PullRequestApp.java:548` | `@Transactional` |
| GL-controllers_PullRequestApp-027 | `app/controllers/PullRequestApp.java:583` | `@Transactional` |
| GL-controllers_PullRequestApp-028 | `app/controllers/PullRequestApp.java:596` | `@Transactional` |
| GL-controllers_PullRequestApp-029 | `app/controllers/PullRequestApp.java:609` | `private static ValidationResult validateBeforePullRequest(Project project) {` |
| GL-controllers_PullRequestApp-030 | `app/controllers/PullRequestApp.java:622` | `@IsCreatable(ResourceType.REVIEW_COMMENT)` |
| GL-controllers_PullRequestApp-031 | `app/controllers/PullRequestApp.java:707` | `static class ValidationResult {` |
| GL-controllers_PullRequestApp-032 | `app/controllers/PullRequestApp.java:726` | `public static class SearchCondition implements Cloneable {` |
| GL-controllers_PullRequestApp-033 | `app/controllers/PullRequestApp.java:795` | `public enum Category {` |
| GL-controllers_ReviewThreadApp-001 | `app/controllers/ReviewThreadApp.java:36` | `@AnonymousCheck` |
| GL-controllers_ReviewThreadApp-003 | `app/controllers/ReviewThreadApp.java:43` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_ReviewThreadApp-004 | `app/controllers/ReviewThreadApp.java:58` | `private static Result reviewThreadsDownload(Project project, ExpressionList<CommentThread> el) {` |
| GL-controllers_ReviewThreadApp-005 | `app/controllers/ReviewThreadApp.java:79` | `public static byte[] excelFrom(List<CommentThread> commentThreads) throws WriteException, IOExceptio` |
| GL-controllers_SiteApp-001 | `app/controllers/SiteApp.java:72` | `/**` |
| GL-controllers_SiteApp-005 | `app/controllers/SiteApp.java:87` | `/**` |
| GL-controllers_SiteApp-006 | `app/controllers/SiteApp.java:111` | `public static Result writeMail(String errorMessage, boolean sended) {` |
| GL-controllers_SiteApp-007 | `app/controllers/SiteApp.java:128` | `public static Result massMail() {` |
| GL-controllers_SiteApp-008 | `app/controllers/SiteApp.java:133` | `/**` |
| GL-controllers_SiteApp-009 | `app/controllers/SiteApp.java:147` | `/**` |
| GL-controllers_SiteApp-010 | `app/controllers/SiteApp.java:157` | `/**` |
| GL-controllers_SiteApp-011 | `app/controllers/SiteApp.java:169` | `/**` |
| GL-controllers_SiteApp-012 | `app/controllers/SiteApp.java:196` | `@Transactional` |
| GL-controllers_SiteApp-013 | `app/controllers/SiteApp.java:221` | `/**` |
| GL-controllers_SiteApp-014 | `app/controllers/SiteApp.java:233` | `/**` |
| GL-controllers_SiteApp-015 | `app/controllers/SiteApp.java:248` | `/**` |
| GL-controllers_SiteApp-016 | `app/controllers/SiteApp.java:275` | `public static Result toggleGuestMode(String loginId, String state, String query){` |
| GL-controllers_SiteApp-017 | `app/controllers/SiteApp.java:294` | `public static Result mailList() {` |
| GL-controllers_SiteApp-018 | `app/controllers/SiteApp.java:333` | `/**` |
| GL-controllers_SiteApp-019 | `app/controllers/SiteApp.java:342` | `/**` |
| GL-controllers_SiteApp-020 | `app/controllers/SiteApp.java:364` | `/**` |
| GL-controllers_SiteApp-021 | `app/controllers/SiteApp.java:373` | `public static Result data() {` |
| GL-controllers_SiteApp-022 | `app/controllers/SiteApp.java:378` | `public static Result exportData() throws JsonProcessingException {` |
| GL-controllers_SiteApp-023 | `app/controllers/SiteApp.java:391` | `public static Result importData() throws IOException {` |
| GL-controllers_SiteApp-024 | `app/controllers/SiteApp.java:408` | `public static Result noAvatarUsers() {` |
| GL-controllers_SiteApp-025 | `app/controllers/SiteApp.java:425` | `private static ObjectNode composeUserNode(User user) {` |
| GL-controllers_BranchApp-001 | `app/controllers/BranchApp.java:46` | `/**` |
| GL-controllers_BranchApp-002 | `app/controllers/BranchApp.java:54` | `@With(CodeAccessCheckAction.class)` |
| GL-controllers_BranchApp-003 | `app/controllers/BranchApp.java:74` | `@IsAllowed(Operation.DELETE)` |
| GL-controllers_BranchApp-004 | `app/controllers/BranchApp.java:84` | `@IsAllowed(Operation.UPDATE)` |
| GL-controllers_MilestoneApp-001 | `app/controllers/MilestoneApp.java:49` | `@AnonymousCheck` |
| GL-controllers_MilestoneApp-004 | `app/controllers/MilestoneApp.java:83` | `/**` |
| GL-controllers_MilestoneApp-005 | `app/controllers/MilestoneApp.java:94` | `/**` |
| GL-controllers_MilestoneApp-007 | `app/controllers/MilestoneApp.java:129` | `private static void validateDueDate(Form<Milestone> milestoneForm) {` |
| GL-controllers_MilestoneApp-008 | `app/controllers/MilestoneApp.java:136` | `/**` |
| GL-controllers_MilestoneApp-009 | `app/controllers/MilestoneApp.java:150` | `/**` |
| GL-controllers_MilestoneApp-010 | `app/controllers/MilestoneApp.java:183` | `/**` |
| GL-controllers_MilestoneApp-011 | `app/controllers/MilestoneApp.java:206` | `@Transactional` |
| GL-controllers_MilestoneApp-012 | `app/controllers/MilestoneApp.java:215` | `@Transactional` |
| GL-controllers_MilestoneApp-013 | `app/controllers/MilestoneApp.java:224` | `/**` |
| GL-controllers_CommentThreadApp-001 | `app/controllers/CommentThreadApp.java:36` | `@AnonymousCheck` |
| GL-controllers_CommentThreadApp-002 | `app/controllers/CommentThreadApp.java:40` | `@Transactional` |
| GL-controllers_CommentThreadApp-003 | `app/controllers/CommentThreadApp.java:80` | `public static Result open(Long id) {` |
| GL-controllers_CommentThreadApp-004 | `app/controllers/CommentThreadApp.java:85` | `public static Result close(Long id) {` |
| GL-controllers_ReviewApp-001 | `app/controllers/ReviewApp.java:38` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_ReviewApp-002 | `app/controllers/ReviewApp.java:42` | `@Transactional` |
| GL-controllers_ReviewApp-003 | `app/controllers/ReviewApp.java:57` | `@Transactional` |
| GL-controllers_ReviewApp-004 | `app/controllers/ReviewApp.java:72` | `private static void addNotification(PullRequest pullRequest, PullRequestReviewAction reviewAction) {` |
| GL-controllers_LabelApp-001 | `app/controllers/LabelApp.java:43` | `@AnonymousCheck` |
| GL-controllers_LabelApp-003 | `app/controllers/LabelApp.java:49` | `/**` |
| GL-controllers_LabelApp-004 | `app/controllers/LabelApp.java:83` | `public static Result categories(String query, Integer limit) {` |
| GL-controllers_AttachmentApp-001 | `app/controllers/AttachmentApp.java:37` | `@AnonymousCheck` |
| GL-controllers_AttachmentApp-003 | `app/controllers/AttachmentApp.java:43` | `public static final long TEMPORARYFILES_KEEPUP_TIME_MILLIS = Configuration.root()` |
| GL-controllers_AttachmentApp-004 | `app/controllers/AttachmentApp.java:47` | `private static User findUploader(Map<String,String[]> formUrlEncoded) {` |
| GL-controllers_AttachmentApp-005 | `app/controllers/AttachmentApp.java:66` | `public static Result uploadFile() throws NoSuchAlgorithmException, IOException {` |
| GL-controllers_AttachmentApp-007 | `app/controllers/AttachmentApp.java:181` | `public static Result deleteFile(Long id) {` |
| GL-controllers_AttachmentApp-008 | `app/controllers/AttachmentApp.java:216` | `private static void logIfOriginFileIsNotValid(String hash) {` |
| GL-controllers_AttachmentApp-009 | `app/controllers/AttachmentApp.java:231` | `private static Map<String, String> extractFileMetaDataFromAttachementAsMap(Attachment attach) {` |
| GL-controllers_AttachmentApp-013 | `app/controllers/AttachmentApp.java:289` | `private static class PermissionDeniedException extends Exception {` |
| GL-controllers_SearchApp-001 | `app/controllers/SearchApp.java:35` | `@AnonymousCheck` |
| GL-controllers_SearchApp-002 | `app/controllers/SearchApp.java:39` | `private static final PageParam DEFAULT_PAGE = new PageParam(0, 20);` |
| GL-controllers_SearchApp-003 | `app/controllers/SearchApp.java:42` | `/**` |
| GL-controllers_SearchApp-005 | `app/controllers/SearchApp.java:112` | `/**` |
| GL-controllers_SearchApp-007 | `app/controllers/SearchApp.java:194` | `/**` |
| GL-controllers_CommentApp-001 | `app/controllers/CommentApp.java:35` | `@AnonymousCheck` |
| GL-controllers_CommentApp-002 | `app/controllers/CommentApp.java:38` | `@Transactional` |
| GL-controllers_Restricted-001 | `app/controllers/Restricted.java:10` | `@Security.Authenticated(Secured.class)` |
| GL-controllers_Restricted-002 | `app/controllers/Restricted.java:14` | `public static Result index() {` |
| GL-controllers_Application-001 | `app/controllers/Application.java:30` | `public class Application extends Controller {` |
| GL-controllers_Application-016 | `app/controllers/Application.java:61` | `@AnonymousCheck` |
| GL-controllers_Application-017 | `app/controllers/Application.java:72` | `@AnonymousCheck` |
| GL-controllers_Application-018 | `app/controllers/Application.java:78` | `@AnonymousCheck` |
| GL-controllers_Application-019 | `app/controllers/Application.java:84` | `public static Result oAuth(final String provider) {` |
| GL-controllers_Application-020 | `app/controllers/Application.java:89` | `public static Result oAuthLogout() {` |
| GL-controllers_Application-021 | `app/controllers/Application.java:96` | `public static Result oAuthDenied(final String providerKey) {` |
| GL-controllers_Application-024 | `app/controllers/Application.java:118` | `public static Result removeTrailer(String paths){` |
| GL-controllers_Application-025 | `app/controllers/Application.java:131` | `public static Result init() {` |
| GL-controllers_Application-026 | `app/controllers/Application.java:137` | `static final JsMessages messages = JsMessages.create(play.Play.application());` |
| GL-controllers_Application-027 | `app/controllers/Application.java:140` | `public static Result jsMessages() {` |
| GL-controllers_Application-028 | `app/controllers/Application.java:145` | `private static void makeTestRepository() {` |
| GL-controllers_Application-029 | `app/controllers/Application.java:157` | `public static Result navi() {` |
| GL-controllers_Application-030 | `app/controllers/Application.java:162` | `public static Result UIKit(){` |
| GL-controllers_Application-031 | `app/controllers/Application.java:167` | `public static Result fake() {` |
| GL-controllers_Application-032 | `app/controllers/Application.java:173` | `public static Result returnToReferer() {` |
| GL-controllers_EnrollOrganizationApp-001 | `app/controllers/EnrollOrganizationApp.java:38` | `@AnonymousCheck` |
| GL-controllers_EnrollOrganizationApp-002 | `app/controllers/EnrollOrganizationApp.java:42` | `@Transactional` |
| GL-controllers_EnrollOrganizationApp-003 | `app/controllers/EnrollOrganizationApp.java:64` | `private static ValidationResult validateForEnroll(String organizationName) {` |
| GL-controllers_EnrollOrganizationApp-004 | `app/controllers/EnrollOrganizationApp.java:79` | `@Transactional` |
| GL-controllers_api_UserApi-001 | `app/controllers/api/UserApi.java:55` | `public class UserApi extends Controller {` |
| GL-controllers_api_UserApi-006 | `app/controllers/api/UserApi.java:67` | `@Transactional` |
| GL-controllers_api_UserApi-007 | `app/controllers/api/UserApi.java:80` | `@Transactional` |
| GL-controllers_api_UserApi-008 | `app/controllers/api/UserApi.java:99` | `@Transactional` |
| GL-controllers_api_UserApi-009 | `app/controllers/api/UserApi.java:119` | `@Transactional` |
| GL-controllers_api_UserApi-010 | `app/controllers/api/UserApi.java:138` | `@Transactional` |
| GL-controllers_api_UserApi-011 | `app/controllers/api/UserApi.java:158` | `private static Result issuesAsJson(Page<Issue> issues) {` |
| GL-controllers_api_UserApi-012 | `app/controllers/api/UserApi.java:202` | `@Transactional` |
| GL-controllers_api_UserApi-013 | `app/controllers/api/UserApi.java:215` | `@Transactional` |
| GL-controllers_api_UserApi-016 | `app/controllers/api/UserApi.java:284` | `@AnonymousCheck(requiresLogin = true)` |
| GL-controllers_api_UserApi-022 | `app/controllers/api/UserApi.java:395` | `private static UserState findUserState(JsonNode json) {` |
| GL-controllers_api_UserApi-027 | `app/controllers/api/UserApi.java:464` | `private static JsonNode successfullyCreatedUserNode(User created) {` |
| GL-controllers_api_UserApi-028 | `app/controllers/api/UserApi.java:474` | `private static JsonNode notAllowedDomainEmailUser(JsonNode userNode) {` |
| GL-controllers_api_UserApi-029 | `app/controllers/api/UserApi.java:487` | `private static JsonNode alreadyExistedUser(JsonNode userNode) {` |
| GL-controllers_api_UserApi-030 | `app/controllers/api/UserApi.java:501` | `private static void loggingUser(JsonNode userNode, String message) {` |
| GL-controllers_api_BoardApi-001 | `app/controllers/api/BoardApi.java:40` | `public class BoardApi extends AbstractPostingApp {` |
| GL-controllers_api_BoardApi-002 | `app/controllers/api/BoardApi.java:43` | `@Transactional` |
| GL-controllers_api_BoardApi-003 | `app/controllers/api/BoardApi.java:68` | `@IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)` |
| GL-controllers_api_BoardApi-004 | `app/controllers/api/BoardApi.java:81` | `@Transactional` |
| GL-controllers_api_BoardApi-005 | `app/controllers/api/BoardApi.java:106` | `private static JsonNode createPostingNode(JsonNode json, Project project) {` |
| GL-controllers_api_BoardApi-007 | `app/controllers/api/BoardApi.java:169` | `@Transactional` |
| GL-controllers_api_GlobalApi-001 | `app/controllers/api/GlobalApi.java:16` | `public class GlobalApi extends Controller {` |
| GL-controllers_api_GlobalApi-002 | `app/controllers/api/GlobalApi.java:18` | `public static Result hello() {` |
| GL-controllers_api_WatcherApi-001 | `app/controllers/api/WatcherApi.java:25` | `public class WatcherApi extends Controller {` |
| GL-controllers_api_MilestoneApi-004 | `app/controllers/api/MilestoneApi.java:74` | `private static State parseMilestoneState(JsonNode json) {` |
| GL-controllers_api_MilestoneApi-005 | `app/controllers/api/MilestoneApi.java:86` | `private static Date parseDuedate(JsonNode json) {` |
| GL-controllers_api_MilestoneApi-006 | `app/controllers/api/MilestoneApi.java:96` | `private static String parseMilestoneTitle(JsonNode json) {` |
| GL-controllers_api_MilestoneApi-007 | `app/controllers/api/MilestoneApi.java:105` | `private static String parseMilestoneContents(JsonNode json) {` |
| GL-controllers_api_ProjectApi-001 | `app/controllers/api/ProjectApi.java:45` | `public class ProjectApi extends Controller {` |
| GL-controllers_api_ProjectApi-013 | `app/controllers/api/ProjectApi.java:244` | `private static void saveMenuSettingsToDefault(Project project) {` |
| GL-controllers_api_ProjectApi-014 | `app/controllers/api/ProjectApi.java:260` | `@IsAllowed(Operation.READ)` |
| GL-controllers_api_ProjectApi-015 | `app/controllers/api/ProjectApi.java:272` | `/**` |
| GL-controllers_api_ProjectApi-019 | `app/controllers/api/ProjectApi.java:347` | `private static JsonNode composeAuthorJson(User user) {` |
| GL-controllers_api_ProjectApi-027 | `app/controllers/api/ProjectApi.java:480` | `@Transactional` |
| GL-controllers_api_ProjectApi-028 | `app/controllers/api/ProjectApi.java:507` | `private static JsonNode createLabelNode(JsonNode json, Project project) {` |
| GL-controllers_api_ProjectApi-029 | `app/controllers/api/ProjectApi.java:544` | `private static JsonNode existedLabel(JsonNode labelNode) {` |
| GL-controllers_api_ProjectApi-030 | `app/controllers/api/ProjectApi.java:557` | `@Transactional` |
| GL-controllers_api_ProjectApi-031 | `app/controllers/api/ProjectApi.java:576` | `private static List<ObjectNode> getherProjectLabels(Project project) {` |
| GL-controllers_api_ProjectApi-032 | `app/controllers/api/ProjectApi.java:585` | `private static List<ObjectNode> getherTitleHeads(Project project, String query) {` |
| GL-controllers_api_IssueApi-001 | `app/controllers/api/IssueApi.java:51` | `public class IssueApi extends AbstractPostingApp {` |
| GL-controllers_api_IssueApi-007 | `app/controllers/api/IssueApi.java:64` | `@Transactional` |
| GL-controllers_api_IssueApi-008 | `app/controllers/api/IssueApi.java:99` | `private static void copyAttachmentsToIssue(Posting from, Issue to) {` |
| GL-controllers_api_IssueApi-009 | `app/controllers/api/IssueApi.java:111` | `private static void copyAttachmentsToIssueComments(Map<String, String> postingCommentIdToIssueCommen` |
| GL-controllers_api_IssueApi-010 | `app/controllers/api/IssueApi.java:128` | `private static void removePosting(Posting posting) {` |
| GL-controllers_api_IssueApi-011 | `app/controllers/api/IssueApi.java:133` | `private static Map<String, String> copyCommentsToIssue(Collection<PostingComment> postingComments, I` |
| GL-controllers_api_IssueApi-012 | `app/controllers/api/IssueApi.java:174` | `@Transactional` |
| GL-controllers_api_IssueApi-013 | `app/controllers/api/IssueApi.java:199` | `@Transactional` |
| GL-controllers_api_IssueApi-014 | `app/controllers/api/IssueApi.java:221` | `private static ObjectNode addIssueEvents(Issue issue, ObjectNode json) {` |
| GL-controllers_api_IssueApi-017 | `app/controllers/api/IssueApi.java:261` | `@Transactional` |
| GL-controllers_api_IssueApi-018 | `app/controllers/api/IssueApi.java:288` | `@Transactional` |
| GL-controllers_api_IssueApi-019 | `app/controllers/api/IssueApi.java:310` | `@Transactional` |
| GL-controllers_api_IssueApi-021 | `app/controllers/api/IssueApi.java:374` | `private static Result updateIssueNode(JsonNode json, Project project, Issue issue, User user) {` |
| GL-controllers_api_IssueApi-022 | `app/controllers/api/IssueApi.java:404` | `private static void addNewIssueEvent(Issue issue, User user, EventType eventType, String oldValue, S` |
| GL-controllers_api_IssueApi-023 | `app/controllers/api/IssueApi.java:416` | `private static JsonNode createIssuesNode(JsonNode json, Project project, boolean sendNotification) {` |
| GL-controllers_api_IssueApi-024 | `app/controllers/api/IssueApi.java:452` | `private static void updateLabels(JsonNode json, Issue issue, Project project) {` |
| GL-controllers_api_IssueApi-025 | `app/controllers/api/IssueApi.java:471` | `private static Milestone findMilestone(JsonNode milestoneTitle, Project project) {` |
| GL-controllers_api_IssueApi-026 | `app/controllers/api/IssueApi.java:479` | `private static Date findDueDate(JsonNode dueDateNode) {` |
| GL-controllers_api_IssueApi-027 | `app/controllers/api/IssueApi.java:492` | `private static State findIssueState(JsonNode json){` |
| GL-controllers_api_IssueApi-028 | `app/controllers/api/IssueApi.java:505` | `public static Result commentNotiRecivers(String ownerName, String projectName, Long number) {` |
| GL-controllers_api_IssueApi-034 | `app/controllers/api/IssueApi.java:671` | `private static Result createCommentByUser(Project project, Issue issue, JsonNode json) {` |
| GL-controllers_api_IssueApi-035 | `app/controllers/api/IssueApi.java:692` | `private static Result createCommentUsingToken(Issue issue, User user, String comment) {` |
| GL-controllers_api_IssueApi-036 | `app/controllers/api/IssueApi.java:699` | `private static IssueComment createComment(Issue issue, User user, String comment, JsonNode dateNode)` |
| GL-controllers_api_IssueApi-039 | `app/controllers/api/IssueApi.java:733` | `public static User findAuthor(JsonNode authorNode){` |
| GL-controllers_api_IssueApi-040 | `app/controllers/api/IssueApi.java:753` | `private static Assignee findAssginee(JsonNode assigneesNode, @Nonnull Project project) {` |
| GL-controllers_api_IssueApi-041 | `app/controllers/api/IssueApi.java:765` | `public static Date parseDateString(JsonNode dateStringNode){` |
| GL-controllers_api_IssueApi-042 | `app/controllers/api/IssueApi.java:780` | `@IsAllowed(Operation.READ)` |
| GL-controllers_api_IssueApi-043 | `app/controllers/api/IssueApi.java:813` | `private static void gatheringUsersFromExpressionList(Project project, List<ObjectNode> users, Expres` |
| GL-controllers_api_IssueApi-044 | `app/controllers/api/IssueApi.java:827` | `@IsAllowed(Operation.READ)` |
| GL-controllers_api_IssueApi-047 | `app/controllers/api/IssueApi.java:900` | `private static void addAuthorIfNotMe(Issue issue, List<ObjectNode> users, User issueAuthor) {` |
| GL-controllers_api_IssueApi-048 | `app/controllers/api/IssueApi.java:907` | `private static void addAuthorIfNotMeAndNotAssginee(Issue issue, List<ObjectNode> users, User issueAu` |
| GL-controllers_api_IssueApi-049 | `app/controllers/api/IssueApi.java:915` | `private static void addMyself(Issue issue, List<ObjectNode> users) {` |
| GL-controllers_api_IssueApi-050 | `app/controllers/api/IssueApi.java:922` | `static void addUserToUsers(User user, List<ObjectNode> users) {` |
| GL-controllers_api_IssueApi-051 | `app/controllers/api/IssueApi.java:936` | `static void addProjectToProjects(Project project, List<ObjectNode> projects) {` |
| GL-controllers_api_IssueApi-052 | `app/controllers/api/IssueApi.java:949` | `private static void addUserToUsersWithCustomName(User user, List<ObjectNode> users, String name) {` |
| GL-controllers_api_IssueApi-053 | `app/controllers/api/IssueApi.java:961` | `public static Result updateAssginees(String owner, String projectName, Long number){` |
| GL-controllers_api_IssueApi-054 | `app/controllers/api/IssueApi.java:1010` | `private static void composeResultJson(ObjectNode result, User assigneeUser) {` |
| GL-controllers_api_IssueApi-056 | `app/controllers/api/IssueApi.java:1033` | `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)` |
| GL-controllers_api_IssueApi-057 | `app/controllers/api/IssueApi.java:1073` | `private static F.Promise<WSResponse> translate(String text, WSRequestHolder translator) {` |
| GL-controllers_api_IssueApi-058 | `app/controllers/api/IssueApi.java:1082` | `private static Supplier<WSRequestHolder> translatorWsRequestHolderSupplier = () -> WS.url(TRANSLATIO` |
| GL-controllers_api_IssueApi-059 | `app/controllers/api/IssueApi.java:1088` | `private static List<String> merge(List<String> texts) {` |
| GL-controllers_api_IssueApi-062 | `app/controllers/api/IssueApi.java:1147` | `@AnonymousCheck` |
| GL-controllers_api_IssueApi-063 | `app/controllers/api/IssueApi.java:1167` | `private static void sortListByAddedDate(List<IssueSharer> list) {` |
| GL-controllers_api_IssueApi-066 | `app/controllers/api/IssueApi.java:1217` | `public static Result updateSharer(String owner, String projectName, Long number){` |
| GL-controllers_api_IssueApi-067 | `app/controllers/api/IssueApi.java:1243` | `public static Result upvoteWeight(String owner, String projectName, Long number){` |
| GL-controllers_api_IssueApi-068 | `app/controllers/api/IssueApi.java:1262` | `public static Result downvoteWeight(String owner, String projectName, Long number){` |
| GL-controllers_api_IssueApi-069 | `app/controllers/api/IssueApi.java:1282` | `private static ObjectNode changeSharer(JsonNode sharer, Issue issue, String action) {` |
| GL-controllers_api_IssueApi-070 | `app/controllers/api/IssueApi.java:1297` | `private static void changeSharerByUser(String loginId, Issue issue, String action, ObjectNode result` |
| GL-controllers_api_IssueApi-071 | `app/controllers/api/IssueApi.java:1312` | `private static void changeSharerByProject(Long projectId, Issue issue, String action, ObjectNode res` |
| GL-controllers_api_IssueApi-073 | `app/controllers/api/IssueApi.java:1343` | `private static void sendNotification(List<String> users, Issue issue, String action) {` |
| GL-controllers_api_IssueApi-074 | `app/controllers/api/IssueApi.java:1357` | `private static void addSharerChangedNotification(Issue issue, String sharerLoginId, String action) {` |
| GL-controllers_api_IssueApi-075 | `app/controllers/api/IssueApi.java:1363` | `private static boolean noSharer(JsonNode sharers) {` |
| GL-controllers_api_IssueApi-076 | `app/controllers/api/IssueApi.java:1368` | `private static void addSharer(Issue issue, String loginId) {` |
| GL-controllers_api_IssueApi-077 | `app/controllers/api/IssueApi.java:1380` | `private static void removeSharer(Issue issue, String loginId) {` |
| GL-controllers_annotation_GuestProhibit-001 | `app/controllers/annotation/GuestProhibit.java:18` | `@With(GuestProhibitAction.class)` |
| GL-controllers_annotation_GuestProhibit-002 | `app/controllers/annotation/GuestProhibit.java:23` | `boolean displaysFlashMessage() default true;` |
| GL-controllers_annotation_IsAllowed-001 | `app/controllers/annotation/IsAllowed.java:34` | `/**` |
| GL-controllers_annotation_IsAllowed-002 | `app/controllers/annotation/IsAllowed.java:44` | `Operation value();` |
| GL-controllers_annotation_IsAllowed-003 | `app/controllers/annotation/IsAllowed.java:46` | `ResourceType resourceType() default ResourceType.PROJECT;` |
| GL-controllers_annotation_AnonymousCheck-001 | `app/controllers/annotation/AnonymousCheck.java:12` | `/**` |
| GL-controllers_annotation_AnonymousCheck-002 | `app/controllers/annotation/AnonymousCheck.java:20` | `boolean requiresLogin() default false;` |
| GL-controllers_annotation_AnonymousCheck-003 | `app/controllers/annotation/AnonymousCheck.java:22` | `boolean displaysFlashMessage() default false;` |
| GL-controllers_annotation_IsCreatable-001 | `app/controllers/annotation/IsCreatable.java:33` | `/**` |
| GL-controllers_annotation_IsCreatable-002 | `app/controllers/annotation/IsCreatable.java:43` | `ResourceType value();` |
| GL-controllers_annotation_IsOnlyGitAvailable-001 | `app/controllers/annotation/IsOnlyGitAvailable.java:32` | `/**` |
