---
id: gl-utils
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

# 버킷 C — 공백 후보: `utils` 영역 (512개 심볼)

자동 분류 결과이며, 실제 공백인지는 사람이 개별 확인해야 한다(자동 승격 금지). 상세 방법론은 [[../methodology]] 참고.

| GL-ID | yona 파일:라인 | 선언 |
|---|---|---|
| GL-utils_GitUtil-001 | `app/utils/GitUtil.java:18` | `public class GitUtil {` |
| GL-utils_GitUtil-003 | `app/utils/GitUtil.java:31` | `public synchronized static void commitTextFile(Project project, String branchName, String path, Stri` |
| GL-utils_PasswordReset-001 | `app/utils/PasswordReset.java:33` | `public class PasswordReset {` |
| GL-utils_PasswordReset-002 | `app/utils/PasswordReset.java:35` | `/**` |
| GL-utils_PasswordReset-003 | `app/utils/PasswordReset.java:40` | `/**` |
| GL-utils_PasswordReset-004 | `app/utils/PasswordReset.java:45` | `/**` |
| GL-utils_PasswordReset-005 | `app/utils/PasswordReset.java:51` | `public static String generateResetHash(String loginId) {` |
| GL-utils_PasswordReset-006 | `app/utils/PasswordReset.java:56` | `public static void addHashToResetTable(String userId, String hashString) {` |
| GL-utils_PasswordReset-009 | `app/utils/PasswordReset.java:81` | `private static void removeResetHash(String hashString) {` |
| GL-utils_PasswordReset-011 | `app/utils/PasswordReset.java:98` | `public static boolean resetPassword(String hashString, String newPassword) {` |
| GL-utils_MalformedCredentialsException-001 | `app/utils/MalformedCredentialsException.java:24` | `public class MalformedCredentialsException extends Exception {` |
| GL-utils_MalformedCredentialsException-002 | `app/utils/MalformedCredentialsException.java:27` | `public MalformedCredentialsException() {` |
| GL-utils_MalformedCredentialsException-003 | `app/utils/MalformedCredentialsException.java:32` | `public MalformedCredentialsException(String message) {` |
| GL-utils_MalformedCredentialsException-004 | `app/utils/MalformedCredentialsException.java:37` | `public MalformedCredentialsException(String message, Exception cause) {` |
| GL-utils_MalformedCredentialsException-005 | `app/utils/MalformedCredentialsException.java:42` | `/**` |
| GL-utils_SimpleDiagnostic-001 | `app/utils/SimpleDiagnostic.java:28` | `abstract public class SimpleDiagnostic extends Diagnostic {` |
| GL-utils_SimpleDiagnostic-002 | `app/utils/SimpleDiagnostic.java:30` | `@Override` |
| GL-utils_SimpleDiagnostic-003 | `app/utils/SimpleDiagnostic.java:45` | `abstract public String checkOne();` |
| GL-utils_JodaDateUtil-001 | `app/utils/JodaDateUtil.java:17` | `public class JodaDateUtil {` |
| GL-utils_JodaDateUtil-005 | `app/utils/JodaDateUtil.java:37` | `public static Date today() {` |
| GL-utils_JodaDateUtil-006 | `app/utils/JodaDateUtil.java:42` | `public static Date now() {` |
| GL-utils_JodaDateUtil-007 | `app/utils/JodaDateUtil.java:47` | `public static Duration ago(DateTime time) {` |
| GL-utils_JodaDateUtil-008 | `app/utils/JodaDateUtil.java:52` | `public static Duration ago(Date time) {` |
| GL-utils_JodaDateUtil-009 | `app/utils/JodaDateUtil.java:57` | `public static Duration ago(Long time){` |
| GL-utils_JodaDateUtil-010 | `app/utils/JodaDateUtil.java:62` | `public static Date before(int days){` |
| GL-utils_JodaDateUtil-011 | `app/utils/JodaDateUtil.java:67` | `public static Date beforeByMillis(long millis){` |
| GL-utils_JodaDateUtil-012 | `app/utils/JodaDateUtil.java:72` | `public static String momentFromNow(Long time) {` |
| GL-utils_JodaDateUtil-013 | `app/utils/JodaDateUtil.java:77` | `public static String momentFromNow(Long time, String language) {` |
| GL-utils_JodaDateUtil-014 | `app/utils/JodaDateUtil.java:83` | `public static String momentFromNow(Date time) {` |
| GL-utils_JodaDateUtil-016 | `app/utils/JodaDateUtil.java:94` | `public static int localDaysBetween(Date from, Date to) {` |
| GL-utils_JodaDateUtil-017 | `app/utils/JodaDateUtil.java:99` | `/**` |
| GL-utils_JodaDateUtil-018 | `app/utils/JodaDateUtil.java:113` | `/**` |
| GL-utils_SHA256Util-001 | `app/utils/SHA256Util.java:7` | `public class SHA256Util {` |
| GL-utils_SHA256Util-002 | `app/utils/SHA256Util.java:9` | `public static String hashBasedNow() {` |
| GL-utils_RedirectUtil-001 | `app/utils/RedirectUtil.java:15` | `public class RedirectUtil {` |
| GL-utils_RedirectUtil-002 | `app/utils/RedirectUtil.java:17` | `public static Promise<Result> redirect(@Nonnull Project project) {` |
| GL-utils_diff_match_patch-001 | `app/utils/diff_match_patch.java:40` | `/*` |
| GL-utils_diff_match_patch-002 | `app/utils/diff_match_patch.java:55` | `// Defaults.` |
| GL-utils_diff_match_patch-003 | `app/utils/diff_match_patch.java:63` | `/**` |
| GL-utils_diff_match_patch-004 | `app/utils/diff_match_patch.java:68` | `/**` |
| GL-utils_diff_match_patch-005 | `app/utils/diff_match_patch.java:74` | `/**` |
| GL-utils_diff_match_patch-006 | `app/utils/diff_match_patch.java:79` | `/**` |
| GL-utils_diff_match_patch-007 | `app/utils/diff_match_patch.java:86` | `/**` |
| GL-utils_diff_match_patch-008 | `app/utils/diff_match_patch.java:94` | `/**` |
| GL-utils_diff_match_patch-009 | `app/utils/diff_match_patch.java:100` | `/**` |
| GL-utils_diff_match_patch-010 | `app/utils/diff_match_patch.java:106` | `/**` |
| GL-utils_diff_match_patch-011 | `app/utils/diff_match_patch.java:125` | `//  DIFF FUNCTIONS` |
| GL-utils_diff_match_patch-012 | `app/utils/diff_match_patch.java:140` | `/**` |
| GL-utils_diff_match_patch-013 | `app/utils/diff_match_patch.java:154` | `/**` |
| GL-utils_diff_match_patch-014 | `app/utils/diff_match_patch.java:203` | `/**` |
| GL-utils_diff_match_patch-015 | `app/utils/diff_match_patch.java:336` | `/**` |
| GL-utils_diff_match_patch-016 | `app/utils/diff_match_patch.java:362` | `/**` |
| GL-utils_diff_match_patch-017 | `app/utils/diff_match_patch.java:400` | `/**` |
| GL-utils_diff_match_patch-018 | `app/utils/diff_match_patch.java:420` | `/**` |
| GL-utils_diff_match_patch-019 | `app/utils/diff_match_patch.java:550` | `/**` |
| GL-utils_diff_match_patch-020 | `app/utils/diff_match_patch.java:604` | `/**` |
| GL-utils_diff_match_patch-021 | `app/utils/diff_match_patch.java:660` | `/**` |
| GL-utils_diff_match_patch-022 | `app/utils/diff_match_patch.java:678` | `/**` |
| GL-utils_diff_match_patch-023 | `app/utils/diff_match_patch.java:697` | `/**` |
| GL-utils_diff_match_patch-024 | `app/utils/diff_match_patch.java:718` | `/**` |
| GL-utils_diff_match_patch-025 | `app/utils/diff_match_patch.java:763` | `/**` |
| GL-utils_diff_match_patch-026 | `app/utils/diff_match_patch.java:804` | `/**` |
| GL-utils_diff_match_patch-027 | `app/utils/diff_match_patch.java:880` | `/**` |
| GL-utils_diff_match_patch-028 | `app/utils/diff_match_patch.java:967` | `/**` |
| GL-utils_diff_match_patch-029 | `app/utils/diff_match_patch.java:1012` | `private Pattern BLANKLINEEND` |
| GL-utils_diff_match_patch-030 | `app/utils/diff_match_patch.java:1015` | `private Pattern BLANKLINESTART` |
| GL-utils_diff_match_patch-031 | `app/utils/diff_match_patch.java:1020` | `/**` |
| GL-utils_diff_match_patch-032 | `app/utils/diff_match_patch.java:1129` | `/**` |
| GL-utils_diff_match_patch-033 | `app/utils/diff_match_patch.java:1281` | `/**` |
| GL-utils_diff_match_patch-034 | `app/utils/diff_match_patch.java:1322` | `/**` |
| GL-utils_diff_match_patch-035 | `app/utils/diff_match_patch.java:1356` | `/**` |
| GL-utils_diff_match_patch-036 | `app/utils/diff_match_patch.java:1373` | `/**` |
| GL-utils_diff_match_patch-037 | `app/utils/diff_match_patch.java:1390` | `/**` |
| GL-utils_diff_match_patch-038 | `app/utils/diff_match_patch.java:1422` | `/**` |
| GL-utils_diff_match_patch-039 | `app/utils/diff_match_patch.java:1462` | `/**` |
| GL-utils_diff_match_patch-040 | `app/utils/diff_match_patch.java:1542` | `//  MATCH FUNCTIONS` |
| GL-utils_diff_match_patch-041 | `app/utils/diff_match_patch.java:1573` | `/**` |
| GL-utils_diff_match_patch-042 | `app/utils/diff_match_patch.java:1678` | `/**` |
| GL-utils_diff_match_patch-043 | `app/utils/diff_match_patch.java:1698` | `/**` |
| GL-utils_diff_match_patch-044 | `app/utils/diff_match_patch.java:1719` | `//  PATCH FUNCTIONS` |
| GL-utils_diff_match_patch-045 | `app/utils/diff_match_patch.java:1769` | `/**` |
| GL-utils_diff_match_patch-046 | `app/utils/diff_match_patch.java:1788` | `/**` |
| GL-utils_diff_match_patch-047 | `app/utils/diff_match_patch.java:1802` | `/**` |
| GL-utils_diff_match_patch-048 | `app/utils/diff_match_patch.java:1819` | `/**` |
| GL-utils_diff_match_patch-049 | `app/utils/diff_match_patch.java:1904` | `/**` |
| GL-utils_diff_match_patch-050 | `app/utils/diff_match_patch.java:1928` | `/**` |
| GL-utils_diff_match_patch-051 | `app/utils/diff_match_patch.java:2041` | `/**` |
| GL-utils_diff_match_patch-052 | `app/utils/diff_match_patch.java:2104` | `/**` |
| GL-utils_diff_match_patch-053 | `app/utils/diff_match_patch.java:2210` | `/**` |
| GL-utils_diff_match_patch-054 | `app/utils/diff_match_patch.java:2225` | `/**` |
| GL-utils_diff_match_patch-055 | `app/utils/diff_match_patch.java:2322` | `/**` |
| GL-utils_diff_match_patch-056 | `app/utils/diff_match_patch.java:2379` | `/**` |
| GL-utils_diff_match_patch-057 | `app/utils/diff_match_patch.java:2450` | `/**` |
| GL-utils_HtmlUtil-001 | `app/utils/HtmlUtil.java:8` | `/**` |
| GL-utils_HtmlUtil-002 | `app/utils/HtmlUtil.java:30` | `/**` |
| GL-utils_HtmlUtil-003 | `app/utils/HtmlUtil.java:42` | `public static String boolToCheckedString(boolean bool){` |
| GL-utils_HtmlUtil-004 | `app/utils/HtmlUtil.java:51` | `public static String boolToCheckedString(String bool){` |
| GL-utils_EventConstants-001 | `app/utils/EventConstants.java:26` | `public class EventConstants {` |
| GL-utils_Diagnostic-001 | `app/utils/Diagnostic.java:31` | `abstract public class Diagnostic {` |
| GL-utils_Diagnostic-002 | `app/utils/Diagnostic.java:34` | `private final static List<Diagnostic> diagnostics = new CopyOnWriteArrayList<>();` |
| GL-utils_Diagnostic-003 | `app/utils/Diagnostic.java:37` | `/**` |
| GL-utils_Diagnostic-004 | `app/utils/Diagnostic.java:50` | `@Nonnull` |
| GL-utils_Diagnostic-005 | `app/utils/Diagnostic.java:67` | `@Nonnull` |
| GL-utils_AutoLinkRenderer-001 | `app/utils/AutoLinkRenderer.java:36` | `/**` |
| GL-utils_AutoLinkRenderer-005 | `app/utils/AutoLinkRenderer.java:62` | `private static final Pattern PATH_WITH_ISSUE_PATTERN = Pattern.compile("@?(" + PATH_PATTERN_STR + ")` |
| GL-utils_AutoLinkRenderer-006 | `app/utils/AutoLinkRenderer.java:64` | `private static final Pattern ISSUE_PATTERN = Pattern.compile("#(" + ISSUE_PATTERN_STR + ")");` |
| GL-utils_AutoLinkRenderer-007 | `app/utils/AutoLinkRenderer.java:67` | `private static final Pattern PATH_WITH_SHA_PATTERN = Pattern.compile("(" + PATH_PATTERN_STR + ")@?("` |
| GL-utils_AutoLinkRenderer-008 | `app/utils/AutoLinkRenderer.java:69` | `private static final Pattern SHA_PATTERN = Pattern.compile("@?(" + SHA_PATTERN_STR + ")");` |
| GL-utils_AutoLinkRenderer-009 | `app/utils/AutoLinkRenderer.java:72` | `private static final Pattern LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH_PATTERN = Pattern.compile("@(" + P` |
| GL-utils_AutoLinkRenderer-011 | `app/utils/AutoLinkRenderer.java:78` | `private static final Pattern WORD_PATTERN = Pattern.compile("\\w");` |
| GL-utils_AutoLinkRenderer-012 | `app/utils/AutoLinkRenderer.java:81` | `private static class Link {` |
| GL-utils_AutoLinkRenderer-013 | `app/utils/AutoLinkRenderer.java:116` | `private static interface ToLink {` |
| GL-utils_AutoLinkRenderer-016 | `app/utils/AutoLinkRenderer.java:126` | `public AutoLinkRenderer(String body, Project project) {` |
| GL-utils_AutoLinkRenderer-017 | `app/utils/AutoLinkRenderer.java:132` | `public String render(String lang) {` |
| GL-utils_AutoLinkRenderer-018 | `app/utils/AutoLinkRenderer.java:188` | `private AutoLinkRenderer parse(Pattern pattern, ToLink toLink) {` |
| GL-utils_AutoLinkRenderer-019 | `app/utils/AutoLinkRenderer.java:215` | `/**` |
| GL-utils_AutoLinkRenderer-020 | `app/utils/AutoLinkRenderer.java:243` | `/**` |
| GL-utils_AutoLinkRenderer-021 | `app/utils/AutoLinkRenderer.java:265` | `private Link toValidIssueLink(String prefix, Project project, String issueNumber) {` |
| GL-utils_AutoLinkRenderer-024 | `app/utils/AutoLinkRenderer.java:328` | `private static Link toValidUserLink(String userId, String lang) {` |
| GL-utils_AutoLinkRenderer-025 | `app/utils/AutoLinkRenderer.java:358` | `private static Link toValidProjectLink(String ownerName, String projectName) {` |
| GL-utils_AutoLinkRenderer-026 | `app/utils/AutoLinkRenderer.java:369` | `/**` |
| GL-utils_AutoLinkRenderer-027 | `app/utils/AutoLinkRenderer.java:379` | `/**` |
| GL-utils_ErrorViews-001 | `app/utils/ErrorViews.java:31` | `/**` |
| GL-utils_ErrorViews-002 | `app/utils/ErrorViews.java:36` | `Forbidden {` |
| GL-utils_ErrorViews-003 | `app/utils/ErrorViews.java:76` | `NotFound {` |
| GL-utils_ErrorViews-004 | `app/utils/ErrorViews.java:108` | `RequestTextEntityTooLarge {` |
| GL-utils_ErrorViews-005 | `app/utils/ErrorViews.java:138` | `BadRequest {` |
| GL-utils_ErrorViews-006 | `app/utils/ErrorViews.java:173` | `public abstract Html render();` |
| GL-utils_ErrorViews-007 | `app/utils/ErrorViews.java:176` | `public abstract Html render(String messageKey);` |
| GL-utils_ErrorViews-008 | `app/utils/ErrorViews.java:179` | `public abstract Html render(String messageKey, Project project);` |
| GL-utils_ErrorViews-009 | `app/utils/ErrorViews.java:182` | `public abstract Html render(String messageKey, Organization organization);` |
| GL-utils_ErrorViews-010 | `app/utils/ErrorViews.java:185` | `public abstract Html render(String messageKey, Project project, String target);` |
| GL-utils_ErrorViews-011 | `app/utils/ErrorViews.java:188` | `public abstract Html render(String messageKey, Project project, MenuType menuType);` |
| GL-utils_ErrorViews-012 | `app/utils/ErrorViews.java:191` | `public Html render(String messageKey, String returnUrl) {` |
| GL-utils_YamlUtil-001 | `app/utils/YamlUtil.java:31` | `public class YamlUtil {` |
| GL-utils_YamlUtil-002 | `app/utils/YamlUtil.java:33` | `public static void insertDataFromYaml(String yamlFileName, String[] entityNames) {` |
| GL-utils_MomentUtil-001 | `app/utils/MomentUtil.java:29` | `/**` |
| GL-utils_MomentUtil-002 | `app/utils/MomentUtil.java:39` | `private static ScriptEngine engine = buildEngine();` |
| GL-utils_MomentUtil-004 | `app/utils/MomentUtil.java:45` | `private static ScriptEngine buildEngine() {` |
| GL-utils_MomentUtil-005 | `app/utils/MomentUtil.java:70` | `public static JSInvocable newMoment(Long epoch) {` |
| GL-utils_MomentUtil-006 | `app/utils/MomentUtil.java:75` | `public static JSInvocable newMoment(Long epoch, String language) {` |
| GL-utils_JSInvocable-001 | `app/utils/JSInvocable.java:24` | `/**` |
| GL-utils_JSInvocable-004 | `app/utils/JSInvocable.java:41` | `public JSInvocable(Invocable invocable, Object object) {` |
| GL-utils_JSInvocable-005 | `app/utils/JSInvocable.java:47` | `public String invoke(String method, Object... args) {` |
| GL-utils_LineEnding-001 | `app/utils/LineEnding.java:12` | `public class LineEnding {` |
| GL-utils_LineEnding-003 | `app/utils/LineEnding.java:16` | `public enum EndingType {` |
| GL-utils_LineEnding-004 | `app/utils/LineEnding.java:26` | `public static String changeLineEnding(String contents, String to){` |
| GL-utils_LineEnding-005 | `app/utils/LineEnding.java:34` | `public static String changeLineEnding(String contents, EndingType to){` |
| GL-utils_LineEnding-006 | `app/utils/LineEnding.java:49` | `public static String addEOL(String contents){` |
| GL-utils_LineEnding-007 | `app/utils/LineEnding.java:63` | `public static EndingType findLineEnding(String contents){` |
| GL-utils_DiffUtil-001 | `app/utils/DiffUtil.java:16` | `public class DiffUtil {` |
| GL-utils_DiffUtil-007 | `app/utils/DiffUtil.java:113` | `private static String addHeadOfDiff(Diff diff) {` |
| GL-utils_DiffUtil-008 | `app/utils/DiffUtil.java:118` | `private static String addTailOfDiff(Diff diff) {` |
| GL-utils_DiffUtil-009 | `app/utils/DiffUtil.java:123` | `private static String addAllDiff(Diff diff) {` |
| GL-utils_DiffUtil-010 | `app/utils/DiffUtil.java:128` | `private static String addEllipsis() {` |
| GL-utils_DiffUtil-011 | `app/utils/DiffUtil.java:136` | `private static String addDiffStyle(Diff diff, String style) {` |
| GL-utils_DiffUtil-012 | `app/utils/DiffUtil.java:143` | `private static String addDiffText(Diff diff, String text) {` |
| GL-utils_DiffUtil-013 | `app/utils/DiffUtil.java:150` | `private static String addEllipsisText() {` |
| GL-utils_SecurityManager-001 | `app/utils/SecurityManager.java:4` | `/**` |
| GL-utils_AccessControl-001 | `app/utils/AccessControl.java:22` | `public class AccessControl {` |
| GL-utils_AccessControl-003 | `app/utils/AccessControl.java:27` | `/**` |
| GL-utils_AccessControl-004 | `app/utils/AccessControl.java:40` | `/**` |
| GL-utils_AccessControl-005 | `app/utils/AccessControl.java:88` | `/**` |
| GL-utils_AccessControl-010 | `app/utils/AccessControl.java:314` | `/**` |
| GL-utils_AccessControl-011 | `app/utils/AccessControl.java:347` | `public static void onStart() {` |
| GL-utils_AccessControl-012 | `app/utils/AccessControl.java:353` | `/**` |
| GL-utils_AccessControl-014 | `app/utils/AccessControl.java:400` | `/**` |
| GL-utils_PlayServletResponse-001 | `app/utils/PlayServletResponse.java:32` | `public class PlayServletResponse implements HttpServletResponse {` |
| GL-utils_PlayServletResponse-010 | `app/utils/PlayServletResponse.java:52` | `/**` |
| GL-utils_PlayServletResponse-012 | `app/utils/PlayServletResponse.java:73` | `class ChunkedOutputStream extends ServletOutputStream {` |
| GL-utils_PlayServletResponse-013 | `app/utils/PlayServletResponse.java:128` | `public PlayServletResponse(Response response) throws IOException {` |
| GL-utils_PlayServletResponse-014 | `app/utils/PlayServletResponse.java:139` | `@Override` |
| GL-utils_PlayServletResponse-015 | `app/utils/PlayServletResponse.java:145` | `@Override` |
| GL-utils_PlayServletResponse-016 | `app/utils/PlayServletResponse.java:151` | `@Override` |
| GL-utils_PlayServletResponse-017 | `app/utils/PlayServletResponse.java:162` | `@Override` |
| GL-utils_PlayServletResponse-018 | `app/utils/PlayServletResponse.java:168` | `@Override` |
| GL-utils_PlayServletResponse-020 | `app/utils/PlayServletResponse.java:179` | `@Override` |
| GL-utils_PlayServletResponse-021 | `app/utils/PlayServletResponse.java:185` | `@Override` |
| GL-utils_PlayServletResponse-022 | `app/utils/PlayServletResponse.java:191` | `@Override` |
| GL-utils_PlayServletResponse-023 | `app/utils/PlayServletResponse.java:197` | `@Override` |
| GL-utils_PlayServletResponse-024 | `app/utils/PlayServletResponse.java:203` | `@Override` |
| GL-utils_PlayServletResponse-025 | `app/utils/PlayServletResponse.java:215` | `@Override` |
| GL-utils_PlayServletResponse-026 | `app/utils/PlayServletResponse.java:221` | `@Override` |
| GL-utils_PlayServletResponse-027 | `app/utils/PlayServletResponse.java:227` | `@Override` |
| GL-utils_PlayServletResponse-029 | `app/utils/PlayServletResponse.java:238` | `@Override` |
| GL-utils_PlayServletResponse-030 | `app/utils/PlayServletResponse.java:245` | `@Override` |
| GL-utils_PlayServletResponse-031 | `app/utils/PlayServletResponse.java:251` | `@Override` |
| GL-utils_PlayServletResponse-032 | `app/utils/PlayServletResponse.java:257` | `@Override` |
| GL-utils_PlayServletResponse-033 | `app/utils/PlayServletResponse.java:263` | `@Override` |
| GL-utils_PlayServletResponse-034 | `app/utils/PlayServletResponse.java:276` | `@Override` |
| GL-utils_PlayServletResponse-035 | `app/utils/PlayServletResponse.java:282` | `@Override` |
| GL-utils_PlayServletResponse-036 | `app/utils/PlayServletResponse.java:288` | `@Override` |
| GL-utils_PlayServletResponse-037 | `app/utils/PlayServletResponse.java:294` | `/**` |
| GL-utils_PlayServletResponse-038 | `app/utils/PlayServletResponse.java:304` | `@Override` |
| GL-utils_PlayServletResponse-039 | `app/utils/PlayServletResponse.java:310` | `/**` |
| GL-utils_PlayServletResponse-040 | `app/utils/PlayServletResponse.java:320` | `@Override` |
| GL-utils_PlayServletResponse-041 | `app/utils/PlayServletResponse.java:332` | `@Override` |
| GL-utils_PlayServletResponse-042 | `app/utils/PlayServletResponse.java:338` | `@Override` |
| GL-utils_PlayServletResponse-043 | `app/utils/PlayServletResponse.java:344` | `@Override` |
| GL-utils_PlayServletResponse-044 | `app/utils/PlayServletResponse.java:350` | `@Override` |
| GL-utils_PlayServletResponse-045 | `app/utils/PlayServletResponse.java:357` | `@Override` |
| GL-utils_PlayServletResponse-046 | `app/utils/PlayServletResponse.java:373` | `@Override` |
| GL-utils_PlayServletResponse-047 | `app/utils/PlayServletResponse.java:381` | `@Override` |
| GL-utils_PlayServletResponse-048 | `app/utils/PlayServletResponse.java:387` | `@Override` |
| GL-utils_PlayServletResponse-049 | `app/utils/PlayServletResponse.java:401` | `@Override` |
| GL-utils_PlayServletResponse-050 | `app/utils/PlayServletResponse.java:407` | `@Override` |
| GL-utils_PlayServletResponse-051 | `app/utils/PlayServletResponse.java:413` | `/**` |
| GL-utils_PlayServletResponse-052 | `app/utils/PlayServletResponse.java:423` | `/**` |
| GL-utils_ValidationResult-001 | `app/utils/ValidationResult.java:26` | `public class ValidationResult {` |
| GL-utils_ValidationResult-004 | `app/utils/ValidationResult.java:33` | `public ValidationResult(Result result, boolean hasError) {` |
| GL-utils_ValidationResult-005 | `app/utils/ValidationResult.java:39` | `public boolean hasError(){` |
| GL-utils_PlayServletContext-001 | `app/utils/PlayServletContext.java:31` | `public class PlayServletContext implements ServletContext {` |
| GL-utils_PlayServletContext-002 | `app/utils/PlayServletContext.java:34` | `@Override` |
| GL-utils_PlayServletContext-003 | `app/utils/PlayServletContext.java:40` | `@Override` |
| GL-utils_PlayServletContext-004 | `app/utils/PlayServletContext.java:46` | `@Override` |
| GL-utils_PlayServletContext-005 | `app/utils/PlayServletContext.java:52` | `@Override` |
| GL-utils_PlayServletContext-006 | `app/utils/PlayServletContext.java:58` | `@Override` |
| GL-utils_PlayServletContext-007 | `app/utils/PlayServletContext.java:64` | `@Override` |
| GL-utils_PlayServletContext-008 | `app/utils/PlayServletContext.java:70` | `@Override` |
| GL-utils_PlayServletContext-009 | `app/utils/PlayServletContext.java:76` | `@Override` |
| GL-utils_PlayServletContext-010 | `app/utils/PlayServletContext.java:82` | `@Override` |
| GL-utils_PlayServletContext-011 | `app/utils/PlayServletContext.java:89` | `@Override` |
| GL-utils_PlayServletContext-012 | `app/utils/PlayServletContext.java:95` | `@Override` |
| GL-utils_PlayServletContext-013 | `app/utils/PlayServletContext.java:101` | `@Override` |
| GL-utils_PlayServletContext-014 | `app/utils/PlayServletContext.java:107` | `@Override` |
| GL-utils_PlayServletContext-015 | `app/utils/PlayServletContext.java:113` | `@Override` |
| GL-utils_PlayServletContext-016 | `app/utils/PlayServletContext.java:119` | `@Override` |
| GL-utils_PlayServletContext-017 | `app/utils/PlayServletContext.java:125` | `@Override` |
| GL-utils_PlayServletContext-018 | `app/utils/PlayServletContext.java:131` | `@Override` |
| GL-utils_PlayServletContext-019 | `app/utils/PlayServletContext.java:137` | `@Override` |
| GL-utils_PlayServletContext-020 | `app/utils/PlayServletContext.java:143` | `@Override` |
| GL-utils_PlayServletContext-021 | `app/utils/PlayServletContext.java:149` | `@Override` |
| GL-utils_PlayServletContext-022 | `app/utils/PlayServletContext.java:155` | `@Override` |
| GL-utils_PlayServletContext-023 | `app/utils/PlayServletContext.java:161` | `@Override` |
| GL-utils_PlayServletContext-024 | `app/utils/PlayServletContext.java:167` | `@Override` |
| GL-utils_PlayServletContext-025 | `app/utils/PlayServletContext.java:173` | `@Override` |
| GL-utils_PlayServletContext-026 | `app/utils/PlayServletContext.java:179` | `@Override` |
| GL-utils_PlayServletContext-027 | `app/utils/PlayServletContext.java:185` | `@Override` |
| GL-utils_PlayServletContext-028 | `app/utils/PlayServletContext.java:191` | `@Override` |
| GL-utils_PlayServletContext-029 | `app/utils/PlayServletContext.java:197` | `@Override` |
| GL-utils_PlayServletContext-030 | `app/utils/PlayServletContext.java:203` | `@Override` |
| GL-utils_PlayServletContext-031 | `app/utils/PlayServletContext.java:209` | `@Override` |
| GL-utils_PlayServletContext-032 | `app/utils/PlayServletContext.java:215` | `@Override` |
| GL-utils_PlayServletContext-033 | `app/utils/PlayServletContext.java:222` | `@Override` |
| GL-utils_PlayServletContext-034 | `app/utils/PlayServletContext.java:228` | `@Override` |
| GL-utils_PlayServletContext-035 | `app/utils/PlayServletContext.java:234` | `@Override` |
| GL-utils_PlayServletContext-036 | `app/utils/PlayServletContext.java:240` | `@Override` |
| GL-utils_PlayServletContext-037 | `app/utils/PlayServletContext.java:246` | `@Override` |
| GL-utils_PlayServletContext-038 | `app/utils/PlayServletContext.java:252` | `@Override` |
| GL-utils_PlayServletContext-039 | `app/utils/PlayServletContext.java:258` | `/**` |
| GL-utils_PlayServletContext-040 | `app/utils/PlayServletContext.java:268` | `@Override` |
| GL-utils_PlayServletContext-041 | `app/utils/PlayServletContext.java:274` | `/**` |
| GL-utils_PlayServletContext-042 | `app/utils/PlayServletContext.java:284` | `@Override` |
| GL-utils_PlayServletContext-043 | `app/utils/PlayServletContext.java:290` | `@Override` |
| GL-utils_PlayServletContext-044 | `app/utils/PlayServletContext.java:296` | `/**` |
| GL-utils_PlayServletContext-045 | `app/utils/PlayServletContext.java:306` | `@Override` |
| GL-utils_PlayServletContext-046 | `app/utils/PlayServletContext.java:312` | `@Override` |
| GL-utils_PlayServletContext-047 | `app/utils/PlayServletContext.java:318` | `/**` |
| GL-utils_PlayServletContext-048 | `app/utils/PlayServletContext.java:328` | `@Override` |
| GL-utils_PlayServletContext-049 | `app/utils/PlayServletContext.java:334` | `@Override` |
| GL-utils_PlayServletContext-050 | `app/utils/PlayServletContext.java:340` | `@Override` |
| GL-utils_PlayServletContext-051 | `app/utils/PlayServletContext.java:346` | `@Override` |
| GL-utils_PlayServletContext-052 | `app/utils/PlayServletContext.java:352` | `@Override` |
| GL-utils_MimeType-001 | `app/utils/MimeType.java:23` | `public class MimeType {` |
| GL-utils_ZipUtil-001 | `app/utils/ZipUtil.java:8` | `/**` |
| GL-utils_ZipUtil-002 | `app/utils/ZipUtil.java:15` | `public static byte[] compress(String text) {` |
| GL-utils_ZipUtil-003 | `app/utils/ZipUtil.java:28` | `public static String decompress(byte[] bytes) {` |
| GL-utils_FastHttpDateFormat-001 | `app/utils/FastHttpDateFormat.java:28` | `/**` |
| GL-utils_FastHttpDateFormat-002 | `app/utils/FastHttpDateFormat.java:37` | `// -------------------------------------------------------------- Variables` |
| GL-utils_FastHttpDateFormat-003 | `app/utils/FastHttpDateFormat.java:45` | `/**` |
| GL-utils_FastHttpDateFormat-004 | `app/utils/FastHttpDateFormat.java:52` | `private static final SimpleDateFormat format =` |
| GL-utils_FastHttpDateFormat-005 | `app/utils/FastHttpDateFormat.java:57` | `/**` |
| GL-utils_FastHttpDateFormat-007 | `app/utils/FastHttpDateFormat.java:86` | `/**` |
| GL-utils_FastHttpDateFormat-008 | `app/utils/FastHttpDateFormat.java:93` | `/**` |
| GL-utils_FastHttpDateFormat-009 | `app/utils/FastHttpDateFormat.java:100` | `/**` |
| GL-utils_FastHttpDateFormat-010 | `app/utils/FastHttpDateFormat.java:108` | `/**` |
| GL-utils_FastHttpDateFormat-011 | `app/utils/FastHttpDateFormat.java:116` | `// --------------------------------------------------------- Public Methods` |
| GL-utils_FastHttpDateFormat-012 | `app/utils/FastHttpDateFormat.java:139` | `/**` |
| GL-utils_FastHttpDateFormat-013 | `app/utils/FastHttpDateFormat.java:167` | `/**` |
| GL-utils_FastHttpDateFormat-014 | `app/utils/FastHttpDateFormat.java:195` | `/**` |
| GL-utils_FastHttpDateFormat-015 | `app/utils/FastHttpDateFormat.java:216` | `/**` |
| GL-utils_FastHttpDateFormat-016 | `app/utils/FastHttpDateFormat.java:231` | `/**` |
| GL-utils_PlayServletRequest-001 | `app/utils/PlayServletRequest.java:40` | `public class PlayServletRequest implements HttpServletRequest {` |
| GL-utils_PlayServletRequest-004 | `app/utils/PlayServletRequest.java:47` | `Map<String, Object> attributes = new HashMap<>();` |
| GL-utils_PlayServletRequest-007 | `app/utils/PlayServletRequest.java:54` | `public PlayServletRequest(Request request, String authenticatedUsername, String pathInfo) {` |
| GL-utils_PlayServletRequest-008 | `app/utils/PlayServletRequest.java:62` | `/**` |
| GL-utils_PlayServletRequest-010 | `app/utils/PlayServletRequest.java:76` | `@Override` |
| GL-utils_PlayServletRequest-011 | `app/utils/PlayServletRequest.java:82` | `@Override` |
| GL-utils_PlayServletRequest-012 | `app/utils/PlayServletRequest.java:88` | `@Override` |
| GL-utils_PlayServletRequest-013 | `app/utils/PlayServletRequest.java:94` | `@Override` |
| GL-utils_PlayServletRequest-014 | `app/utils/PlayServletRequest.java:101` | `@Override` |
| GL-utils_PlayServletRequest-016 | `app/utils/PlayServletRequest.java:124` | `@Override` |
| GL-utils_PlayServletRequest-017 | `app/utils/PlayServletRequest.java:130` | `@Override` |
| GL-utils_PlayServletRequest-018 | `app/utils/PlayServletRequest.java:136` | `@Override` |
| GL-utils_PlayServletRequest-019 | `app/utils/PlayServletRequest.java:200` | `@Override` |
| GL-utils_PlayServletRequest-020 | `app/utils/PlayServletRequest.java:206` | `@Override` |
| GL-utils_PlayServletRequest-021 | `app/utils/PlayServletRequest.java:212` | `@Override` |
| GL-utils_PlayServletRequest-022 | `app/utils/PlayServletRequest.java:218` | `@Override` |
| GL-utils_PlayServletRequest-023 | `app/utils/PlayServletRequest.java:229` | `@Override` |
| GL-utils_PlayServletRequest-024 | `app/utils/PlayServletRequest.java:239` | `@Override` |
| GL-utils_PlayServletRequest-025 | `app/utils/PlayServletRequest.java:252` | `@Override` |
| GL-utils_PlayServletRequest-026 | `app/utils/PlayServletRequest.java:258` | `@Override` |
| GL-utils_PlayServletRequest-027 | `app/utils/PlayServletRequest.java:264` | `@Override` |
| GL-utils_PlayServletRequest-028 | `app/utils/PlayServletRequest.java:270` | `@Override` |
| GL-utils_PlayServletRequest-029 | `app/utils/PlayServletRequest.java:276` | `@Override` |
| GL-utils_PlayServletRequest-030 | `app/utils/PlayServletRequest.java:282` | `/**` |
| GL-utils_PlayServletRequest-031 | `app/utils/PlayServletRequest.java:292` | `@Override` |
| GL-utils_PlayServletRequest-032 | `app/utils/PlayServletRequest.java:298` | `@Override` |
| GL-utils_PlayServletRequest-033 | `app/utils/PlayServletRequest.java:305` | `@Override` |
| GL-utils_PlayServletRequest-034 | `app/utils/PlayServletRequest.java:311` | `@Override` |
| GL-utils_PlayServletRequest-035 | `app/utils/PlayServletRequest.java:317` | `@Override` |
| GL-utils_PlayServletRequest-036 | `app/utils/PlayServletRequest.java:334` | `@Override` |
| GL-utils_PlayServletRequest-037 | `app/utils/PlayServletRequest.java:340` | `@Override` |
| GL-utils_PlayServletRequest-038 | `app/utils/PlayServletRequest.java:354` | `@Override` |
| GL-utils_PlayServletRequest-039 | `app/utils/PlayServletRequest.java:360` | `@Override` |
| GL-utils_PlayServletRequest-040 | `app/utils/PlayServletRequest.java:366` | `@Override` |
| GL-utils_PlayServletRequest-041 | `app/utils/PlayServletRequest.java:372` | `@Override` |
| GL-utils_PlayServletRequest-042 | `app/utils/PlayServletRequest.java:379` | `@Override` |
| GL-utils_PlayServletRequest-043 | `app/utils/PlayServletRequest.java:385` | `@Override` |
| GL-utils_PlayServletRequest-044 | `app/utils/PlayServletRequest.java:391` | `@Override` |
| GL-utils_PlayServletRequest-045 | `app/utils/PlayServletRequest.java:397` | `@Override` |
| GL-utils_PlayServletRequest-046 | `app/utils/PlayServletRequest.java:403` | `@Override` |
| GL-utils_PlayServletRequest-047 | `app/utils/PlayServletRequest.java:410` | `@Override` |
| GL-utils_PlayServletRequest-048 | `app/utils/PlayServletRequest.java:416` | `@Override` |
| GL-utils_PlayServletRequest-049 | `app/utils/PlayServletRequest.java:423` | `@Override` |
| GL-utils_PlayServletRequest-050 | `app/utils/PlayServletRequest.java:430` | `@Override` |
| GL-utils_PlayServletRequest-051 | `app/utils/PlayServletRequest.java:436` | `@Override` |
| GL-utils_PlayServletRequest-052 | `app/utils/PlayServletRequest.java:448` | `@Override` |
| GL-utils_PlayServletRequest-053 | `app/utils/PlayServletRequest.java:454` | `@Override` |
| GL-utils_PlayServletRequest-054 | `app/utils/PlayServletRequest.java:460` | `@Override` |
| GL-utils_PlayServletRequest-055 | `app/utils/PlayServletRequest.java:472` | `// same as org.apache.catalina.connector.Request.getHeaders` |
| GL-utils_PlayServletRequest-056 | `app/utils/PlayServletRequest.java:484` | `@Override` |
| GL-utils_PlayServletRequest-057 | `app/utils/PlayServletRequest.java:490` | `@Override` |
| GL-utils_PlayServletRequest-058 | `app/utils/PlayServletRequest.java:496` | `@Override` |
| GL-utils_PlayServletRequest-059 | `app/utils/PlayServletRequest.java:502` | `@Override` |
| GL-utils_PlayServletRequest-060 | `app/utils/PlayServletRequest.java:508` | `@Override` |
| GL-utils_PlayServletRequest-061 | `app/utils/PlayServletRequest.java:515` | `@Override` |
| GL-utils_PlayServletRequest-062 | `app/utils/PlayServletRequest.java:528` | `@Override` |
| GL-utils_PlayServletRequest-063 | `app/utils/PlayServletRequest.java:534` | `@Override` |
| GL-utils_PlayServletRequest-064 | `app/utils/PlayServletRequest.java:540` | `@Override` |
| GL-utils_PlayServletRequest-065 | `app/utils/PlayServletRequest.java:546` | `@Override` |
| GL-utils_PlayServletRequest-066 | `app/utils/PlayServletRequest.java:552` | `@Override` |
| GL-utils_PlayServletRequest-067 | `app/utils/PlayServletRequest.java:559` | `@Override` |
| GL-utils_PlayServletRequest-068 | `app/utils/PlayServletRequest.java:565` | `@Override` |
| GL-utils_PlayServletRequest-069 | `app/utils/PlayServletRequest.java:571` | `@Override` |
| GL-utils_PlayServletRequest-070 | `app/utils/PlayServletRequest.java:584` | `@Override` |
| GL-utils_PlayServletRequest-071 | `app/utils/PlayServletRequest.java:590` | `@Override` |
| GL-utils_PlayServletRequest-072 | `app/utils/PlayServletRequest.java:596` | `/**` |
| GL-utils_PlayServletRequest-073 | `app/utils/PlayServletRequest.java:606` | `@Override` |
| GL-utils_PlayServletRequest-074 | `app/utils/PlayServletRequest.java:612` | `@Override` |
| GL-utils_PlayServletRequest-075 | `app/utils/PlayServletRequest.java:618` | `@Override` |
| GL-utils_PlayServletRequest-076 | `app/utils/PlayServletRequest.java:624` | `@Override` |
| GL-utils_PlayServletRequest-077 | `app/utils/PlayServletRequest.java:630` | `public <T extends javax.servlet.http.HttpUpgradeHandler> T upgrade(java.lang.Class<T> httpUpgradeHan` |
| GL-utils_PlayServletRequest-078 | `app/utils/PlayServletRequest.java:637` | `public String changeSessionId() {` |
| GL-utils_Url-001 | `app/utils/Url.java:29` | `public class Url {` |
| GL-utils_Url-002 | `app/utils/Url.java:32` | `/**` |
| GL-utils_Url-003 | `app/utils/Url.java:44` | `public static String createWithContext(List<String> pathSegments) {` |
| GL-utils_Url-004 | `app/utils/Url.java:50` | `/**` |
| GL-utils_Url-005 | `app/utils/Url.java:66` | `/**` |
| GL-utils_Url-006 | `app/utils/Url.java:84` | `/**` |
| GL-utils_Url-007 | `app/utils/Url.java:96` | `/**` |
| GL-utils_Url-008 | `app/utils/Url.java:112` | `/**` |
| GL-utils_Url-009 | `app/utils/Url.java:132` | `private static String join(List<String> pathSegments) {` |
| GL-utils_Url-010 | `app/utils/Url.java:137` | `public static String removeFragment(String url) {` |
| GL-utils_AccessLogger-001 | `app/utils/AccessLogger.java:35` | `public class AccessLogger {` |
| GL-utils_AccessLogger-002 | `app/utils/AccessLogger.java:38` | `/**` |
| GL-utils_AccessLogger-003 | `app/utils/AccessLogger.java:53` | `/**` |
| GL-utils_AccessLogger-004 | `app/utils/AccessLogger.java:68` | `/**` |
| GL-utils_AccessLogger-005 | `app/utils/AccessLogger.java:106` | `/**` |
| GL-utils_AccessLogger-006 | `app/utils/AccessLogger.java:130` | `/**` |
| GL-utils_PathVariable-001 | `app/utils/PathVariable.java:15` | `public class PathVariable {` |
| GL-utils_PathVariable-005 | `app/utils/PathVariable.java:23` | `private Map<String, String> pathVariable = new HashMap<>();` |
| GL-utils_PathVariable-007 | `app/utils/PathVariable.java:28` | `public PathVariable(String url) {` |
| GL-utils_PathVariable-008 | `app/utils/PathVariable.java:41` | `/**` |
| GL-utils_PathVariable-010 | `app/utils/PathVariable.java:56` | `private void decomposeToPathVariable(String refinedUrl) {` |
| GL-utils_Timestamp-001 | `app/utils/Timestamp.java:11` | `public class Timestamp {` |
| GL-utils_Timestamp-003 | `app/utils/Timestamp.java:17` | `public Timestamp(String title) {` |
| GL-utils_Timestamp-004 | `app/utils/Timestamp.java:23` | `public void logElapsedTime(String message) {` |
| GL-utils_Config-001 | `app/utils/Config.java:21` | `public class Config {` |
| GL-utils_Config-003 | `app/utils/Config.java:25` | `private static final String YONA_DATA = "yona.data"; //property from java -Dyona.data option string` |
| GL-utils_Config-009 | `app/utils/Config.java:78` | `/**` |
| GL-utils_Config-010 | `app/utils/Config.java:98` | `/**` |
| GL-utils_Config-017 | `app/utils/Config.java:201` | `/**` |
| GL-utils_Config-018 | `app/utils/Config.java:223` | `/**` |
| GL-utils_Config-019 | `app/utils/Config.java:235` | `/**` |
| GL-utils_Config-020 | `app/utils/Config.java:262` | `/**` |
| GL-utils_Config-026 | `app/utils/Config.java:308` | `public static boolean displayPrivateRepositories() {` |
| GL-utils_ChunkedOutputStream-001 | `app/utils/ChunkedOutputStream.java:16` | `//` |
| GL-utils_ChunkedOutputStream-003 | `app/utils/ChunkedOutputStream.java:24` | `/**` |
| GL-utils_ChunkedOutputStream-004 | `app/utils/ChunkedOutputStream.java:30` | `/**` |
| GL-utils_ChunkedOutputStream-005 | `app/utils/ChunkedOutputStream.java:39` | `public ChunkedOutputStream(Chunks.Out<byte[]> out, int size) {` |
| GL-utils_ChunkedOutputStream-006 | `app/utils/ChunkedOutputStream.java:49` | `/**` |
| GL-utils_ChunkedOutputStream-007 | `app/utils/ChunkedOutputStream.java:64` | `public void write(byte b[]) throws IOException {` |
| GL-utils_ChunkedOutputStream-008 | `app/utils/ChunkedOutputStream.java:69` | `/**` |
| GL-utils_ChunkedOutputStream-009 | `app/utils/ChunkedOutputStream.java:103` | `private void flushBuffer() throws IOException {` |
| GL-utils_ChunkedOutputStream-010 | `app/utils/ChunkedOutputStream.java:110` | `@Override` |
| GL-utils_ChunkedOutputStream-011 | `app/utils/ChunkedOutputStream.java:119` | `private void chunkOut() {` |
| GL-utils_PullRequestCommit-001 | `app/utils/PullRequestCommit.java:24` | `public class PullRequestCommit {` |
| GL-utils_PullRequestCommit-006 | `app/utils/PullRequestCommit.java:39` | `public PullRequestCommit(String url) {` |
| GL-utils_MD5Util-001 | `app/utils/MD5Util.java:27` | `/**` |
| GL-utils_MD5Util-002 | `app/utils/MD5Util.java:32` | `public static String hex(byte[] array) {` |
| GL-utils_MD5Util-003 | `app/utils/MD5Util.java:41` | `public static String md5Hex (String message) {` |
| GL-utils_LogoUtil-001 | `app/utils/LogoUtil.java:26` | `public class LogoUtil {` |
| GL-utils_LogoUtil-002 | `app/utils/LogoUtil.java:28` | `public static final int LOGO_FILE_LIMIT_SIZE = 1024*1000*5; //5M` |
| GL-utils_CacheStore-001 | `app/utils/CacheStore.java:14` | `/**` |
| GL-utils_CacheStore-006 | `app/utils/CacheStore.java:36` | `public static Cache<Long, User> yonaUsers = CacheBuilder.newBuilder()` |
| GL-utils_CacheStore-008 | `app/utils/CacheStore.java:47` | `public static void refreshProjectMap(){` |
| GL-utils_BasicAuthAction-001 | `app/utils/BasicAuthAction.java:39` | `public class BasicAuthAction extends Action<Object> {` |
| GL-utils_BasicAuthAction-003 | `app/utils/BasicAuthAction.java:44` | `public static Result unauthorized(Response response) {` |
| GL-utils_BasicAuthAction-004 | `app/utils/BasicAuthAction.java:56` | `public static User parseCredentials(String credentials) throws MalformedCredentialsException, Unsupp` |
| GL-utils_BasicAuthAction-005 | `app/utils/BasicAuthAction.java:93` | `// !! Important !! For ldap, intentionally, user email is used for ldap authentication` |
| GL-utils_BasicAuthAction-006 | `app/utils/BasicAuthAction.java:119` | `@Override` |
| GL-utils_GravatarUtil-001 | `app/utils/GravatarUtil.java:16` | `public class GravatarUtil {` |
| GL-utils_PlayServletSession-001 | `app/utils/PlayServletSession.java:29` | `public class PlayServletSession implements HttpSession {` |
| GL-utils_PlayServletSession-003 | `app/utils/PlayServletSession.java:35` | `public PlayServletSession(ServletContext context) {` |
| GL-utils_PlayServletSession-004 | `app/utils/PlayServletSession.java:40` | `@Override` |
| GL-utils_PlayServletSession-005 | `app/utils/PlayServletSession.java:46` | `@Override` |
| GL-utils_PlayServletSession-006 | `app/utils/PlayServletSession.java:52` | `@Override` |
| GL-utils_PlayServletSession-007 | `app/utils/PlayServletSession.java:58` | `@Override` |
| GL-utils_PlayServletSession-008 | `app/utils/PlayServletSession.java:64` | `@Override` |
| GL-utils_PlayServletSession-009 | `app/utils/PlayServletSession.java:70` | `@Override` |
| GL-utils_PlayServletSession-010 | `app/utils/PlayServletSession.java:76` | `@Override` |
| GL-utils_PlayServletSession-011 | `app/utils/PlayServletSession.java:82` | `/**` |
| GL-utils_PlayServletSession-012 | `app/utils/PlayServletSession.java:92` | `/**` |
| GL-utils_PlayServletSession-013 | `app/utils/PlayServletSession.java:102` | `/**` |
| GL-utils_PlayServletSession-014 | `app/utils/PlayServletSession.java:112` | `@Override` |
| GL-utils_PlayServletSession-015 | `app/utils/PlayServletSession.java:118` | `@Override` |
| GL-utils_PlayServletSession-016 | `app/utils/PlayServletSession.java:124` | `/**` |
| GL-utils_PlayServletSession-017 | `app/utils/PlayServletSession.java:134` | `@Override` |
| GL-utils_PlayServletSession-018 | `app/utils/PlayServletSession.java:140` | `/**` |
| GL-utils_PlayServletSession-019 | `app/utils/PlayServletSession.java:150` | `@Override` |
| GL-utils_PlayServletSession-020 | `app/utils/PlayServletSession.java:156` | `@Override` |
| GL-utils_Markdown-001 | `app/utils/Markdown.java:37` | `public class Markdown {` |
| GL-utils_Markdown-005 | `app/utils/Markdown.java:46` | `private static ScriptEngine engine = buildEngine();` |
| GL-utils_Markdown-006 | `app/utils/Markdown.java:48` | `private static PolicyFactory sanitizerPolicy = Sanitizers.FORMATTING` |
| GL-utils_Markdown-007 | `app/utils/Markdown.java:66` | `private static ScriptEngine buildEngine() {` |
| GL-utils_Markdown-008 | `app/utils/Markdown.java:96` | `private static String removeJavascriptInHref(String source) {` |
| GL-utils_Markdown-014 | `app/utils/Markdown.java:287` | `/**` |
| GL-utils_Markdown-015 | `app/utils/Markdown.java:326` | `public static String render(@Nonnull String source) {` |
| GL-utils_Markdown-016 | `app/utils/Markdown.java:344` | `public static String render(@Nonnull String source, Project project, boolean breaks) {` |
| GL-utils_Markdown-020 | `app/utils/Markdown.java:366` | `public static String renderFileInCodeBrowser(@Nonnull String source, Project project) {` |
| GL-utils_Markdown-022 | `app/utils/Markdown.java:380` | `private static String replaceImageLinkPath(Project project, String text){` |
| GL-utils_Markdown-023 | `app/utils/Markdown.java:390` | `private static String replaceContentsLinkToCodeBrowerPath(Project project, String text){` |
| GL-utils_SiteManagerAuthAction-001 | `app/utils/SiteManagerAuthAction.java:30` | `/**` |
| GL-utils_SiteManagerAuthAction-002 | `app/utils/SiteManagerAuthAction.java:35` | `@Override` |
| GL-utils_HttpUtil-001 | `app/utils/HttpUtil.java:32` | `public class HttpUtil {` |
| GL-utils_HttpUtil-002 | `app/utils/HttpUtil.java:34` | `/**` |
| GL-utils_HttpUtil-003 | `app/utils/HttpUtil.java:58` | `/**` |
| GL-utils_HttpUtil-004 | `app/utils/HttpUtil.java:76` | `/**` |
| GL-utils_HttpUtil-005 | `app/utils/HttpUtil.java:96` | `/**` |
| GL-utils_HttpUtil-006 | `app/utils/HttpUtil.java:109` | `/**` |
| GL-utils_HttpUtil-007 | `app/utils/HttpUtil.java:131` | `/**` |
| GL-utils_HttpUtil-008 | `app/utils/HttpUtil.java:168` | `/**` |
| GL-utils_HttpUtil-009 | `app/utils/HttpUtil.java:184` | `/**` |
| GL-utils_HttpUtil-010 | `app/utils/HttpUtil.java:195` | `/**` |
| GL-utils_HttpUtil-011 | `app/utils/HttpUtil.java:214` | `public static String decodeUrlString(String str) {` |
| GL-utils_HttpUtil-012 | `app/utils/HttpUtil.java:226` | `public static String encodeUrlString(String str) {` |
| GL-utils_HttpUtil-013 | `app/utils/HttpUtil.java:238` | `// It is made for path which contains UTF8 chars` |
| GL-utils_RouteUtil-001 | `app/utils/RouteUtil.java:24` | `public class RouteUtil {` |
| GL-utils_RouteUtil-002 | `app/utils/RouteUtil.java:26` | `public static final DiffRenderer$ diffRenderer = new DiffRenderer$();` |
| GL-utils_LdapService-001 | `app/utils/LdapService.java:22` | `public class LdapService {` |
| GL-utils_LdapService-014 | `app/utils/LdapService.java:51` | `private static final String ENGLISH_NAME_PROPERTY = Play.application().configuration()` |
| GL-utils_LdapService-015 | `app/utils/LdapService.java:54` | `private static final int TIMEOUT = 5000; //ms` |
| GL-utils_LdapService-016 | `app/utils/LdapService.java:57` | `public LdapUser authenticate(String username, String password) throws NamingException {` |
| GL-utils_LdapService-017 | `app/utils/LdapService.java:81` | `private String guessedUser(String username) {` |
| GL-utils_LdapService-019 | `app/utils/LdapService.java:111` | `private String searchFilter(@Nonnull String username) {` |
| GL-utils_LdapService-021 | `app/utils/LdapService.java:129` | `private SearchResult findUser(DirContext ctx, String username, String filter) throws NamingException` |
| GL-utils_MenuType-001 | `app/utils/MenuType.java:24` | `public enum MenuType {` |
| GL-utils_MenuType-002 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-003 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-004 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-005 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-006 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-007 | `app/utils/MenuType.java:31` | `SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),` |
| GL-utils_MenuType-008 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-009 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-010 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-011 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-012 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-013 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-014 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-015 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-016 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-017 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-018 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-019 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-020 | `app/utils/MenuType.java:45` | `PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106` |
| GL-utils_MenuType-022 | `app/utils/MenuType.java:51` | `private MenuType(int type) {` |
| GL-utils_FileUtil-001 | `app/utils/FileUtil.java:35` | `public class FileUtil {` |
| GL-utils_FileUtil-002 | `app/utils/FileUtil.java:38` | `public static void rm_rf(File file) throws Exception {` |
| GL-utils_FileUtil-003 | `app/utils/FileUtil.java:54` | `static private String or(String a, String b) {` |
| GL-utils_FileUtil-004 | `app/utils/FileUtil.java:59` | `/**` |
| GL-utils_FileUtil-005 | `app/utils/FileUtil.java:86` | `/**` |
| GL-utils_FileUtil-006 | `app/utils/FileUtil.java:112` | `public static MediaType detectMediaType(File file, String name) throws IOException {` |
| GL-utils_FileUtil-010 | `app/utils/FileUtil.java:160` | `/**` |
| GL-utils_FileUtil-012 | `app/utils/FileUtil.java:183` | `/**` |
| GL-utils_Constants-001 | `app/utils/Constants.java:24` | `public class Constants {` |
| GL-utils_AttachmentCache-001 | `app/utils/AttachmentCache.java:11` | `/**` |
| GL-utils_AttachmentCache-002 | `app/utils/AttachmentCache.java:24` | `/**` |
| GL-utils_AttachmentCache-003 | `app/utils/AttachmentCache.java:32` | `/**` |
| GL-utils_AttachmentCache-004 | `app/utils/AttachmentCache.java:52` | `/**` |
| GL-utils_AttachmentCache-005 | `app/utils/AttachmentCache.java:63` | `/**` |
| GL-utils_AttachmentCache-006 | `app/utils/AttachmentCache.java:74` | `/**` |
| GL-utils_AttachmentCache-007 | `app/utils/AttachmentCache.java:92` | `private static String cacheKey(Resource container) {` |
| GL-utils_AttachmentCache-008 | `app/utils/AttachmentCache.java:97` | `/**` |
| GL-utils_AttachmentCache-009 | `app/utils/AttachmentCache.java:107` | `/**` |
| GL-utils_ReservedWordsValidator-001 | `app/utils/ReservedWordsValidator.java:39` | `/**` |
| GL-utils_ReservedWordsValidator-004 | `app/utils/ReservedWordsValidator.java:72` | `/**` |
| GL-utils_ReservedWordsValidator-005 | `app/utils/ReservedWordsValidator.java:84` | `/**` |
| GL-utils_ReservedWordsValidator-006 | `app/utils/ReservedWordsValidator.java:97` | `/**` |
| GL-utils_ValidationUtils-001 | `app/utils/ValidationUtils.java:26` | `public class ValidationUtils {` |
| GL-utils_ValidationUtils-002 | `app/utils/ValidationUtils.java:29` | `public static void rejectIfEmpty(Http.Flash flash, String value, String message) {` |
