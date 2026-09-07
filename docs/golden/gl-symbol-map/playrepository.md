---
id: gl-playrepository
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

# 버킷 C — 공백 후보: `playRepository` 영역 (241개 심볼)

자동 분류 결과이며, 실제 공백인지는 사람이 개별 확인해야 한다(자동 승격 금지). 상세 방법론은 [[../methodology]] 참고.

| GL-ID | yona 파일:라인 | 선언 |
|---|---|---|
| GL-playRepository_GitRepository-001 | `app/playRepository/GitRepository.java:84` | `public class GitRepository implements PlayRepository {` |
| GL-playRepository_GitRepository-002 | `app/playRepository/GitRepository.java:86` | `private static final ModelLock<Project> PROJECT_LOCK = new ModelLock<>();` |
| GL-playRepository_GitRepository-008 | `app/playRepository/GitRepository.java:100` | `/**` |
| GL-playRepository_GitRepository-009 | `app/playRepository/GitRepository.java:106` | `/**` |
| GL-playRepository_GitRepository-016 | `app/playRepository/GitRepository.java:134` | `/**` |
| GL-playRepository_GitRepository-017 | `app/playRepository/GitRepository.java:144` | `public GitRepository(String ownerName, String projectName) {` |
| GL-playRepository_GitRepository-018 | `app/playRepository/GitRepository.java:149` | `/**` |
| GL-playRepository_GitRepository-019 | `app/playRepository/GitRepository.java:157` | `public static Repository buildGitRepository(String ownerName, String projectName,` |
| GL-playRepository_GitRepository-020 | `app/playRepository/GitRepository.java:175` | `public static Repository buildGitRepository(String ownerName, String projectName) {` |
| GL-playRepository_GitRepository-021 | `app/playRepository/GitRepository.java:180` | `/**` |
| GL-playRepository_GitRepository-022 | `app/playRepository/GitRepository.java:188` | `public static Repository buildGitRepository(Project project, boolean alternatesMergeRepo) {` |
| GL-playRepository_GitRepository-023 | `app/playRepository/GitRepository.java:193` | `public static void cloneLocalRepository(Project originalProject, Project forkProject)` |
| GL-playRepository_GitRepository-024 | `app/playRepository/GitRepository.java:206` | `/**` |
| GL-playRepository_GitRepository-025 | `app/playRepository/GitRepository.java:215` | `/**` |
| GL-playRepository_GitRepository-026 | `app/playRepository/GitRepository.java:224` | `/**` |
| GL-playRepository_GitRepository-029 | `app/playRepository/GitRepository.java:303` | `@Override` |
| GL-playRepository_GitRepository-030 | `app/playRepository/GitRepository.java:344` | `/**` |
| GL-playRepository_GitRepository-032 | `app/playRepository/GitRepository.java:399` | `/**` |
| GL-playRepository_GitRepository-033 | `app/playRepository/GitRepository.java:424` | `public class ObjectFinder {` |
| GL-playRepository_GitRepository-034 | `app/playRepository/GitRepository.java:648` | `public static interface TreeWalkHandler {` |
| GL-playRepository_GitRepository-035 | `app/playRepository/GitRepository.java:653` | `/**` |
| GL-playRepository_GitRepository-036 | `app/playRepository/GitRepository.java:675` | `/**` |
| GL-playRepository_GitRepository-037 | `app/playRepository/GitRepository.java:691` | `/**` |
| GL-playRepository_GitRepository-038 | `app/playRepository/GitRepository.java:720` | `@Override` |
| GL-playRepository_GitRepository-039 | `app/playRepository/GitRepository.java:733` | `/*` |
| GL-playRepository_GitRepository-040 | `app/playRepository/GitRepository.java:751` | `private void addTree(TreeWalk treeWalk, RevCommit commit) throws IOException {` |
| GL-playRepository_GitRepository-041 | `app/playRepository/GitRepository.java:760` | `/**` |
| GL-playRepository_GitRepository-047 | `app/playRepository/GitRepository.java:826` | `/**` |
| GL-playRepository_GitRepository-048 | `app/playRepository/GitRepository.java:869` | `@Override` |
| GL-playRepository_GitRepository-049 | `app/playRepository/GitRepository.java:881` | `/**` |
| GL-playRepository_GitRepository-050 | `app/playRepository/GitRepository.java:895` | `/**` |
| GL-playRepository_GitRepository-053 | `app/playRepository/GitRepository.java:948` | `@Override` |
| GL-playRepository_GitRepository-054 | `app/playRepository/GitRepository.java:970` | `@Override` |
| GL-playRepository_GitRepository-055 | `app/playRepository/GitRepository.java:976` | `/**` |
| GL-playRepository_GitRepository-056 | `app/playRepository/GitRepository.java:991` | `/**` |
| GL-playRepository_GitRepository-057 | `app/playRepository/GitRepository.java:1006` | `/**` |
| GL-playRepository_GitRepository-060 | `app/playRepository/GitRepository.java:1031` | `/**` |
| GL-playRepository_GitRepository-061 | `app/playRepository/GitRepository.java:1053` | `public static void cloneRepository(String gitUrl, Project forkingProject, String authId, String auth` |
| GL-playRepository_GitRepository-062 | `app/playRepository/GitRepository.java:1064` | `/**` |
| GL-playRepository_GitRepository-063 | `app/playRepository/GitRepository.java:1079` | `/**` |
| GL-playRepository_GitRepository-065 | `app/playRepository/GitRepository.java:1098` | `@SuppressWarnings("unchecked")` |
| GL-playRepository_GitRepository-066 | `app/playRepository/GitRepository.java:1105` | `public static List<GitCommit> diffCommits(Repository repository, ObjectId from, ObjectId to) throws ` |
| GL-playRepository_GitRepository-067 | `app/playRepository/GitRepository.java:1110` | `public static List<GitCommit> wrapInGitCommits(List<RevCommit> revCommits) throws IOException, GitAP` |
| GL-playRepository_GitRepository-068 | `app/playRepository/GitRepository.java:1119` | `/**` |
| GL-playRepository_GitRepository-069 | `app/playRepository/GitRepository.java:1171` | `/**` |
| GL-playRepository_GitRepository-070 | `app/playRepository/GitRepository.java:1193` | `/**` |
| GL-playRepository_GitRepository-071 | `app/playRepository/GitRepository.java:1205` | `/**` |
| GL-playRepository_GitRepository-072 | `app/playRepository/GitRepository.java:1233` | `/**` |
| GL-playRepository_GitRepository-073 | `app/playRepository/GitRepository.java:1254` | `/**` |
| GL-playRepository_GitRepository-074 | `app/playRepository/GitRepository.java:1284` | `/**` |
| GL-playRepository_GitRepository-075 | `app/playRepository/GitRepository.java:1298` | `/**` |
| GL-playRepository_GitRepository-076 | `app/playRepository/GitRepository.java:1313` | `public static Repository buildMergingRepository(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-077 | `app/playRepository/GitRepository.java:1318` | `public static Repository buildMergingRepository(Project project) {` |
| GL-playRepository_GitRepository-078 | `app/playRepository/GitRepository.java:1334` | `private static Git cloneRepository(Project project, File workingDirectory) throws GitAPIException, I` |
| GL-playRepository_GitRepository-079 | `app/playRepository/GitRepository.java:1342` | `public static boolean canDeleteFromBranch(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-080 | `app/playRepository/GitRepository.java:1379` | `public static String deleteFromBranch(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-081 | `app/playRepository/GitRepository.java:1408` | `public static void restoreBranch(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-082 | `app/playRepository/GitRepository.java:1430` | `public static boolean canRestoreBranch(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-083 | `app/playRepository/GitRepository.java:1450` | `public static List<GitCommit> diffCommits(PullRequest pullRequest) {` |
| GL-playRepository_GitRepository-087 | `app/playRepository/GitRepository.java:1542` | `@Override` |
| GL-playRepository_GitRepository-090 | `app/playRepository/GitRepository.java:1820` | `/**` |
| GL-playRepository_GitRepository-092 | `app/playRepository/GitRepository.java:1889` | `public static class CloneAndFetch {` |
| GL-playRepository_GitRepository-093 | `app/playRepository/GitRepository.java:1924` | `public static interface AfterCloneAndFetchOperation {` |
| GL-playRepository_GitRepository-094 | `app/playRepository/GitRepository.java:1929` | `public void close() {` |
| GL-playRepository_GitRepository-095 | `app/playRepository/GitRepository.java:1934` | `/**` |
| GL-playRepository_GitRepository-096 | `app/playRepository/GitRepository.java:1943` | `@Override` |
| GL-playRepository_GitRepository-097 | `app/playRepository/GitRepository.java:1949` | `@Override` |
| GL-playRepository_GitRepository-098 | `app/playRepository/GitRepository.java:1963` | `@Override` |
| GL-playRepository_GitRepository-099 | `app/playRepository/GitRepository.java:1981` | `@Override` |
| GL-playRepository_GitRepository-100 | `app/playRepository/GitRepository.java:1987` | `/*` |
| GL-playRepository_GitRepository-101 | `app/playRepository/GitRepository.java:1999` | `/*` |
| GL-playRepository_GitRepository-102 | `app/playRepository/GitRepository.java:2008` | `/*` |
| GL-playRepository_GitRepository-103 | `app/playRepository/GitRepository.java:2020` | `public boolean move(String srcProjectOwner, String srcProjectName, String desrProjectOwner, String d` |
| GL-playRepository_GitRepository-104 | `app/playRepository/GitRepository.java:2056` | `@Override` |
| GL-playRepository_RepositoryService-001 | `app/playRepository/RepositoryService.java:48` | `public class RepositoryService {` |
| GL-playRepository_RepositoryService-004 | `app/playRepository/RepositoryService.java:55` | `public static Map<String, String> vcsTypes() {` |
| GL-playRepository_RepositoryService-005 | `app/playRepository/RepositoryService.java:63` | `/**` |
| GL-playRepository_RepositoryService-006 | `app/playRepository/RepositoryService.java:73` | `/**` |
| GL-playRepository_RepositoryService-008 | `app/playRepository/RepositoryService.java:117` | `/**` |
| GL-playRepository_RepositoryService-012 | `app/playRepository/RepositoryService.java:157` | `public static DAVServlet createDavServlet(final String userName) throws ServletException {` |
| GL-playRepository_RepositoryService-013 | `app/playRepository/RepositoryService.java:192` | `/**` |
| GL-playRepository_RepositoryService-014 | `app/playRepository/RepositoryService.java:222` | `/**` |
| GL-playRepository_RepositoryService-015 | `app/playRepository/RepositoryService.java:276` | `private static PreReceiveHook createPreReceiveHook() {` |
| GL-playRepository_RepositoryService-016 | `app/playRepository/RepositoryService.java:283` | `private static PostReceiveHook createPostReceiveHook(` |
| GL-playRepository_RepositoryService-017 | `app/playRepository/RepositoryService.java:295` | `private static void receivePack(final InputStream input, Repository repository,` |
| GL-playRepository_RepositoryService-018 | `app/playRepository/RepositoryService.java:318` | `private static void uploadPack(final InputStream input, Repository repository,` |
| GL-playRepository_RepositoryService-019 | `app/playRepository/RepositoryService.java:337` | `private static void closeStreams(String serviceName, InputStream input, OutputStream output) {` |
| GL-playRepository_DiffLineType-001 | `app/playRepository/DiffLineType.java:24` | `public enum DiffLineType {` |
| GL-playRepository_DiffLineType-002 | `app/playRepository/DiffLineType.java:28` | `CONTEXT, ADD, REMOVE` |
| GL-playRepository_DiffLineType-003 | `app/playRepository/DiffLineType.java:28` | `CONTEXT, ADD, REMOVE` |
| GL-playRepository_DiffLineType-004 | `app/playRepository/DiffLineType.java:28` | `CONTEXT, ADD, REMOVE` |
| GL-playRepository_FileDiff-001 | `app/playRepository/FileDiff.java:34` | `/**` |
| GL-playRepository_FileDiff-004 | `app/playRepository/FileDiff.java:44` | `private Set<Error> errors = new HashSet<>();` |
| GL-playRepository_FileDiff-009 | `app/playRepository/FileDiff.java:69` | `public enum Error {A_SIZE_EXCEEDED, B_SIZE_EXCEEDED, DIFF_SIZE_EXCEEDED, OTHERS_SIZE_EXCEEDED }` |
| GL-playRepository_FileDiff-026 | `app/playRepository/FileDiff.java:104` | `public static class Hunks extends ArrayList<Hunk> {` |
| GL-playRepository_FileDiff-027 | `app/playRepository/FileDiff.java:111` | `public static class SizeExceededHunks extends Hunks {` |
| GL-playRepository_FileDiff-030 | `app/playRepository/FileDiff.java:130` | `/**` |
| GL-playRepository_FileDiff-031 | `app/playRepository/FileDiff.java:228` | `private int findCombinedEnd(final List<Edit> edits, final int i) {` |
| GL-playRepository_FileDiff-032 | `app/playRepository/FileDiff.java:237` | `private boolean combineA(final List<Edit> e, final int i) {` |
| GL-playRepository_FileDiff-033 | `app/playRepository/FileDiff.java:242` | `private boolean combineB(final List<Edit> e, final int i) {` |
| GL-playRepository_FileDiff-034 | `app/playRepository/FileDiff.java:247` | `private static boolean end(final Edit edit, final int a, final int b) {` |
| GL-playRepository_FileDiff-035 | `app/playRepository/FileDiff.java:252` | `private boolean checkEndOfLineMissing(final RawText text, final int line) {` |
| GL-playRepository_FileDiff-036 | `app/playRepository/FileDiff.java:257` | `/**` |
| GL-playRepository_FileDiff-038 | `app/playRepository/FileDiff.java:302` | `public void addError(Error error) {` |
| GL-playRepository_FileDiff-039 | `app/playRepository/FileDiff.java:307` | `public boolean hasAnyError(Error ... errors) {` |
| GL-playRepository_FileDiff-040 | `app/playRepository/FileDiff.java:320` | `private void refreshErrors() {` |
| GL-playRepository_FileDiff-041 | `app/playRepository/FileDiff.java:336` | `public boolean hasError(Error error) {` |
| GL-playRepository_FileDiff-042 | `app/playRepository/FileDiff.java:342` | `public boolean hasError() {` |
| GL-playRepository_FileDiff-043 | `app/playRepository/FileDiff.java:348` | `@Override` |
| GL-playRepository_FileDiff-044 | `app/playRepository/FileDiff.java:370` | `@Override` |
| GL-playRepository_FileDiff-045 | `app/playRepository/FileDiff.java:382` | `@Override` |
| GL-playRepository_GitRef-001 | `app/playRepository/GitRef.java:24` | `public class GitRef extends VCSRef {` |
| GL-playRepository_GitRef-002 | `app/playRepository/GitRef.java:26` | `public GitRef(String name) {` |
| GL-playRepository_GitRef-003 | `app/playRepository/GitRef.java:31` | `@Override` |
| GL-playRepository_SVNRepository-001 | `app/playRepository/SVNRepository.java:53` | `public class SVNRepository implements PlayRepository {` |
| GL-playRepository_SVNRepository-007 | `app/playRepository/SVNRepository.java:75` | `public SVNRepository(final String userName, String projectName) {` |
| GL-playRepository_SVNRepository-008 | `app/playRepository/SVNRepository.java:81` | `@Override` |
| GL-playRepository_SVNRepository-010 | `app/playRepository/SVNRepository.java:101` | `@Override` |
| GL-playRepository_SVNRepository-013 | `app/playRepository/SVNRepository.java:163` | `@Override` |
| GL-playRepository_SVNRepository-014 | `app/playRepository/SVNRepository.java:177` | `private ObjectNode fileAsJson(String path, org.tmatesoft.svn.core.io.SVNRepository repository) throw` |
| GL-playRepository_SVNRepository-015 | `app/playRepository/SVNRepository.java:225` | `@Override` |
| GL-playRepository_SVNRepository-016 | `app/playRepository/SVNRepository.java:231` | `@Override` |
| GL-playRepository_SVNRepository-017 | `app/playRepository/SVNRepository.java:237` | `@Override` |
| GL-playRepository_SVNRepository-018 | `app/playRepository/SVNRepository.java:244` | `@Override` |
| GL-playRepository_SVNRepository-020 | `app/playRepository/SVNRepository.java:268` | `@Override` |
| GL-playRepository_SVNRepository-021 | `app/playRepository/SVNRepository.java:274` | `@Override` |
| GL-playRepository_SVNRepository-022 | `app/playRepository/SVNRepository.java:280` | `@Override` |
| GL-playRepository_SVNRepository-023 | `app/playRepository/SVNRepository.java:315` | `@Override` |
| GL-playRepository_SVNRepository-024 | `app/playRepository/SVNRepository.java:330` | `@Override` |
| GL-playRepository_SVNRepository-025 | `app/playRepository/SVNRepository.java:338` | `@Override` |
| GL-playRepository_SVNRepository-028 | `app/playRepository/SVNRepository.java:372` | `@Override` |
| GL-playRepository_SVNRepository-029 | `app/playRepository/SVNRepository.java:378` | `@Override` |
| GL-playRepository_SVNRepository-030 | `app/playRepository/SVNRepository.java:385` | `@Override` |
| GL-playRepository_SVNRepository-031 | `app/playRepository/SVNRepository.java:391` | `@Override` |
| GL-playRepository_SVNRepository-032 | `app/playRepository/SVNRepository.java:397` | `@Override` |
| GL-playRepository_SVNRepository-033 | `app/playRepository/SVNRepository.java:402` | `@Override` |
| GL-playRepository_SVNRepository-034 | `app/playRepository/SVNRepository.java:413` | `@Override` |
| GL-playRepository_SVNRepository-035 | `app/playRepository/SVNRepository.java:431` | `public boolean move(String srcProjectOwner, String srcProjectName, String desrProjectOwner, String d` |
| GL-playRepository_SVNRepository-036 | `app/playRepository/SVNRepository.java:448` | `@Override` |
| GL-playRepository_SVNRepository-037 | `app/playRepository/SVNRepository.java:454` | `@Override` |
| GL-playRepository_GitCommit-001 | `app/playRepository/GitCommit.java:35` | `public class GitCommit extends Commit {` |
| GL-playRepository_GitCommit-007 | `app/playRepository/GitCommit.java:52` | `public GitCommit(RevCommit revCommit) {` |
| GL-playRepository_GitCommit-008 | `app/playRepository/GitCommit.java:57` | `@Override` |
| GL-playRepository_GitCommit-009 | `app/playRepository/GitCommit.java:63` | `// Imported from getFullMessage of` |
| GL-playRepository_GitCommit-010 | `app/playRepository/GitCommit.java:91` | `@Override` |
| GL-playRepository_GitCommit-011 | `app/playRepository/GitCommit.java:97` | `@Override` |
| GL-playRepository_GitCommit-012 | `app/playRepository/GitCommit.java:103` | `// Imported from` |
| GL-playRepository_GitCommit-013 | `app/playRepository/GitCommit.java:141` | `@Override` |
| GL-playRepository_GitCommit-014 | `app/playRepository/GitCommit.java:147` | `@Override` |
| GL-playRepository_GitCommit-015 | `app/playRepository/GitCommit.java:153` | `@Override` |
| GL-playRepository_GitCommit-016 | `app/playRepository/GitCommit.java:159` | `@Override` |
| GL-playRepository_GitCommit-017 | `app/playRepository/GitCommit.java:165` | `@Override` |
| GL-playRepository_GitCommit-018 | `app/playRepository/GitCommit.java:171` | `@Override` |
| GL-playRepository_GitCommit-020 | `app/playRepository/GitCommit.java:182` | `@Override` |
| GL-playRepository_GitCommit-021 | `app/playRepository/GitCommit.java:188` | `@Override` |
| GL-playRepository_GitCommit-022 | `app/playRepository/GitCommit.java:194` | `@Override` |
| GL-playRepository_GitCommit-024 | `app/playRepository/GitCommit.java:205` | `// Imported from` |
| GL-playRepository_GitCommit-025 | `app/playRepository/GitCommit.java:239` | `// Imported from` |
| GL-playRepository_GitCommit-026 | `app/playRepository/GitCommit.java:273` | `public static Charset parseEncoding(final byte[] b, Charset fallback) {` |
| GL-playRepository_GitCommit-027 | `app/playRepository/GitCommit.java:282` | `// Imported from` |
| GL-playRepository_GitCommit-028 | `app/playRepository/GitCommit.java:344` | `// Imported from` |
| GL-playRepository_GitCommit-029 | `app/playRepository/GitCommit.java:354` | `// Imported from` |
| GL-playRepository_Commit-001 | `app/playRepository/Commit.java:30` | `public abstract class Commit {` |
| GL-playRepository_Commit-021 | `app/playRepository/Commit.java:136` | `public Resource asResource(final Project project) {` |
| GL-playRepository_GitBranch-001 | `app/playRepository/GitBranch.java:29` | `/**` |
| GL-playRepository_GitBranch-007 | `app/playRepository/GitBranch.java:50` | `public GitBranch(String name, GitCommit headCommit) {` |
| GL-playRepository_DiffLine-001 | `app/playRepository/DiffLine.java:24` | `public class DiffLine {` |
| GL-playRepository_DiffLine-007 | `app/playRepository/DiffLine.java:37` | `public DiffLine(FileDiff file, DiffLineType type, Integer lineNumA, Integer lineNumB,` |
| GL-playRepository_DiffLine-008 | `app/playRepository/DiffLine.java:47` | `@Override` |
| GL-playRepository_DiffLine-009 | `app/playRepository/DiffLine.java:65` | `@Override` |
| GL-playRepository_BareRepository-001 | `app/playRepository/BareRepository.java:42` | `public class BareRepository {` |
| GL-playRepository_BareRepository-002 | `app/playRepository/BareRepository.java:44` | `/**` |
| GL-playRepository_BareRepository-007 | `app/playRepository/BareRepository.java:121` | `private static TreeFilter[] READMEFileNameFilter() {` |
| GL-playRepository_BareRepository-008 | `app/playRepository/BareRepository.java:131` | `public static EndingType findFileLineEnding(Repository repository, String fileNameWithPath) throws I` |
| GL-playRepository_VCSRef-001 | `app/playRepository/VCSRef.java:24` | `public class VCSRef {` |
| GL-playRepository_VCSRef-003 | `app/playRepository/VCSRef.java:29` | `public VCSRef(String name) {` |
| GL-playRepository_VCSRef-004 | `app/playRepository/VCSRef.java:34` | `public String name() {` |
| GL-playRepository_VCSRef-005 | `app/playRepository/VCSRef.java:39` | `public String canonicalName() {` |
| GL-playRepository_VCSRef-006 | `app/playRepository/VCSRef.java:44` | `@Override` |
| GL-playRepository_SvnCommit-001 | `app/playRepository/SvnCommit.java:31` | `public class SvnCommit extends Commit {` |
| GL-playRepository_SvnCommit-003 | `app/playRepository/SvnCommit.java:36` | `public SvnCommit(SVNLogEntry entry) {` |
| GL-playRepository_SvnCommit-005 | `app/playRepository/SvnCommit.java:46` | `@Override` |
| GL-playRepository_SvnCommit-006 | `app/playRepository/SvnCommit.java:52` | `@Override` |
| GL-playRepository_SvnCommit-007 | `app/playRepository/SvnCommit.java:58` | `@Override` |
| GL-playRepository_SvnCommit-008 | `app/playRepository/SvnCommit.java:64` | `@Override` |
| GL-playRepository_SvnCommit-009 | `app/playRepository/SvnCommit.java:70` | `@Override` |
| GL-playRepository_SvnCommit-010 | `app/playRepository/SvnCommit.java:82` | `@Override` |
| GL-playRepository_SvnCommit-011 | `app/playRepository/SvnCommit.java:88` | `@Override` |
| GL-playRepository_SvnCommit-012 | `app/playRepository/SvnCommit.java:94` | `@Override` |
| GL-playRepository_SvnCommit-013 | `app/playRepository/SvnCommit.java:100` | `@Override` |
| GL-playRepository_SvnCommit-014 | `app/playRepository/SvnCommit.java:106` | `@Override` |
| GL-playRepository_SvnCommit-015 | `app/playRepository/SvnCommit.java:112` | `@Override` |
| GL-playRepository_SvnCommit-016 | `app/playRepository/SvnCommit.java:118` | `@Override` |
| GL-playRepository_SvnCommit-017 | `app/playRepository/SvnCommit.java:124` | `@Override` |
| GL-playRepository_SvnCommit-018 | `app/playRepository/SvnCommit.java:134` | `@Override` |
| GL-playRepository_Hunk-001 | `app/playRepository/Hunk.java:29` | `public class Hunk {` |
| GL-playRepository_Hunk-006 | `app/playRepository/Hunk.java:39` | `public List<DiffLine> lines = new ArrayList<>();` |
| GL-playRepository_Hunk-007 | `app/playRepository/Hunk.java:42` | `public int size() {` |
| GL-playRepository_Hunk-008 | `app/playRepository/Hunk.java:51` | `@Override` |
| GL-playRepository_Hunk-009 | `app/playRepository/Hunk.java:68` | `@Override` |
| GL-playRepository_BareCommit-001 | `app/playRepository/BareCommit.java:54` | `public class BareCommit {` |
| GL-playRepository_BareCommit-009 | `app/playRepository/BareCommit.java:72` | `/**` |
| GL-playRepository_BareCommit-010 | `app/playRepository/BareCommit.java:86` | `/**` |
| GL-playRepository_BareCommit-011 | `app/playRepository/BareCommit.java:119` | `private boolean noHeadRef() {` |
| GL-playRepository_BareCommit-012 | `app/playRepository/BareCommit.java:127` | `private ObjectId createCommitWithNewTree(ObjectId targetTextFileObjectId) throws IOException {` |
| GL-playRepository_BareCommit-013 | `app/playRepository/BareCommit.java:132` | `private CommitBuilder buildCommitWith(String fileName, ObjectId fileObjectId) throws IOException {` |
| GL-playRepository_BareCommit-014 | `app/playRepository/BareCommit.java:145` | `private ObjectId createTreeWith(String fileName, ObjectId fileObjectId) throws IOException {` |
| GL-playRepository_BareCommit-015 | `app/playRepository/BareCommit.java:154` | `private TreeFormatter newTreeWith(String fileName, ObjectId fileObjectId) {` |
| GL-playRepository_BareCommit-016 | `app/playRepository/BareCommit.java:161` | `private TreeFormatter rebuildExistingTreeWith(String fileName, ObjectId fileObjectId) throws IOExcep` |
| GL-playRepository_BareCommit-018 | `app/playRepository/BareCommit.java:204` | `private ObjectId createGitObjectWithText(String contents) throws IOException {` |
| GL-playRepository_BareCommit-019 | `app/playRepository/BareCommit.java:210` | `private RefUpdate.Result refUpdate(ObjectId commitId, String refName) throws IOException {` |
| GL-playRepository_BareCommit-020 | `app/playRepository/BareCommit.java:226` | `private boolean hasOldCommit(String refName) throws IOException {` |
| GL-playRepository_BareCommit-030 | `app/playRepository/BareCommit.java:347` | `private static DirCache createTemporaryIndex(final Git git, final ObjectId headId, final String path` |
| GL-playRepository_PlayRepository-001 | `app/playRepository/PlayRepository.java:34` | `public interface PlayRepository {` |
| GL-playRepository_PlayRepository-003 | `app/playRepository/PlayRepository.java:41` | `public abstract void create() throws IOException, SVNException;` |
| GL-playRepository_PlayRepository-007 | `app/playRepository/PlayRepository.java:53` | `public abstract void delete() throws Exception;` |
| GL-playRepository_PlayRepository-016 | `app/playRepository/PlayRepository.java:80` | `public abstract Resource asResource();` |
| GL-playRepository_PlayRepository-019 | `app/playRepository/PlayRepository.java:89` | `public abstract boolean renameTo(String projectName);` |
| GL-playRepository_PlayRepository-024 | `app/playRepository/PlayRepository.java:104` | `boolean move(String srcProjectOwner, String srcProjectName, String desrProjectOwner, String destProj` |
| GL-playRepository_hooks_NotifyPushedCommits-001 | `app/playRepository/hooks/NotifyPushedCommits.java:39` | `public class NotifyPushedCommits implements PostReceiveHook {` |
| GL-playRepository_hooks_NotifyPushedCommits-004 | `app/playRepository/hooks/NotifyPushedCommits.java:46` | `public NotifyPushedCommits(Project project, User user) {` |
| GL-playRepository_hooks_NotifyPushedCommits-005 | `app/playRepository/hooks/NotifyPushedCommits.java:52` | `@Override` |
| GL-playRepository_hooks_UpdateLastPushedDate-001 | `app/playRepository/hooks/UpdateLastPushedDate.java:33` | `public class UpdateLastPushedDate implements PostReceiveHook {` |
| GL-playRepository_hooks_UpdateLastPushedDate-003 | `app/playRepository/hooks/UpdateLastPushedDate.java:38` | `public UpdateLastPushedDate(Project project) {` |
| GL-playRepository_hooks_UpdateLastPushedDate-004 | `app/playRepository/hooks/UpdateLastPushedDate.java:43` | `@Override` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-001 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:38` | `public class UpdateRecentlyPushedBranch implements PostReceiveHook {` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-003 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:43` | `public UpdateRecentlyPushedBranch(Project project) {` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-004 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:48` | `@Override` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-005 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:56` | `private void removeOldPushedBranches() {` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-006 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:64` | `private void saveRecentlyPushedBranch(Set<String> pushedBranches) {` |
| GL-playRepository_hooks_UpdateRecentlyPushedBranch-009 | `app/playRepository/hooks/UpdateRecentlyPushedBranch.java:92` | `private void deletePushedBranch(Set<String> deletedBranches) {` |
| GL-playRepository_hooks_IssueReferredFromCommitEvent-001 | `app/playRepository/hooks/IssueReferredFromCommitEvent.java:39` | `public class IssueReferredFromCommitEvent implements PostReceiveHook {` |
| GL-playRepository_hooks_IssueReferredFromCommitEvent-004 | `app/playRepository/hooks/IssueReferredFromCommitEvent.java:46` | `public IssueReferredFromCommitEvent(Project project, User user) {` |
| GL-playRepository_hooks_IssueReferredFromCommitEvent-005 | `app/playRepository/hooks/IssueReferredFromCommitEvent.java:52` | `@Override` |
| GL-playRepository_hooks_ReceiveCommandUtil-001 | `app/playRepository/hooks/ReceiveCommandUtil.java:32` | `public class ReceiveCommandUtil {` |
| GL-playRepository_hooks_PullRequestCheck-001 | `app/playRepository/hooks/PullRequestCheck.java:42` | `public class PullRequestCheck implements PostReceiveHook {` |
| GL-playRepository_hooks_PullRequestCheck-005 | `app/playRepository/hooks/PullRequestCheck.java:51` | `public PullRequestCheck(User user, Request request, Project project) {` |
| GL-playRepository_hooks_PullRequestCheck-006 | `app/playRepository/hooks/PullRequestCheck.java:58` | `@Override` |
| GL-playRepository_hooks_RejectPushToReservedRefs-001 | `app/playRepository/hooks/RejectPushToReservedRefs.java:10` | `public class RejectPushToReservedRefs implements PreReceiveHook {` |
| GL-playRepository_hooks_RejectPushToReservedRefs-002 | `app/playRepository/hooks/RejectPushToReservedRefs.java:12` | `public RejectPushToReservedRefs() {` |
| GL-playRepository_hooks_RejectPushToReservedRefs-003 | `app/playRepository/hooks/RejectPushToReservedRefs.java:16` | `@Override` |
