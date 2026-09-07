---
id: gl-mailbox
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

# 버킷 C — 공백 후보: `mailbox` 영역 (74개 심볼)

자동 분류 결과이며, 실제 공백인지는 사람이 개별 확인해야 한다(자동 승격 금지). 상세 방법론은 [[../methodology]] 참고.

| GL-ID | yona 파일:라인 | 선언 |
|---|---|---|
| GL-mailbox_IMAPMessageUtil-001 | `app/mailbox/IMAPMessageUtil.java:31` | `public class IMAPMessageUtil {` |
| GL-mailbox_IMAPMessageUtil-002 | `app/mailbox/IMAPMessageUtil.java:33` | `public static User extractSender(Message msg) throws MessagingException {` |
| GL-mailbox_IMAPMessageUtil-003 | `app/mailbox/IMAPMessageUtil.java:45` | `public static String asString(IMAPMessage msg) throws MessagingException {` |
| GL-mailbox_EmailAddressWithDetail-001 | `app/mailbox/EmailAddressWithDetail.java:29` | `/**` |
| GL-mailbox_EmailAddressWithDetail-002 | `app/mailbox/EmailAddressWithDetail.java:34` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-003 | `app/mailbox/EmailAddressWithDetail.java:38` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-004 | `app/mailbox/EmailAddressWithDetail.java:42` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-005 | `app/mailbox/EmailAddressWithDetail.java:46` | `public EmailAddressWithDetail(@Nonnull String address) {` |
| GL-mailbox_EmailAddressWithDetail-006 | `app/mailbox/EmailAddressWithDetail.java:62` | `/**` |
| GL-mailbox_EmailAddressWithDetail-007 | `app/mailbox/EmailAddressWithDetail.java:72` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-008 | `app/mailbox/EmailAddressWithDetail.java:78` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-009 | `app/mailbox/EmailAddressWithDetail.java:84` | `@Nonnull` |
| GL-mailbox_EmailAddressWithDetail-010 | `app/mailbox/EmailAddressWithDetail.java:90` | `/**` |
| GL-mailbox_EmailAddressWithDetail-011 | `app/mailbox/EmailAddressWithDetail.java:103` | `/**` |
| GL-mailbox_EmailAddressWithDetail-012 | `app/mailbox/EmailAddressWithDetail.java:113` | `public String toString() {` |
| GL-mailbox_EmailHandler-001 | `app/mailbox/EmailHandler.java:58` | `/**` |
| GL-mailbox_EmailHandler-002 | `app/mailbox/EmailHandler.java:65` | `/**` |
| GL-mailbox_EmailHandler-003 | `app/mailbox/EmailHandler.java:91` | `/**` |
| GL-mailbox_EmailHandler-004 | `app/mailbox/EmailHandler.java:102` | `private EmailHandler() {` |
| GL-mailbox_EmailHandler-005 | `app/mailbox/EmailHandler.java:108` | `private static List<String> parseMessageIds(String headerValue) {` |
| GL-mailbox_EmailHandler-006 | `app/mailbox/EmailHandler.java:133` | `private static void handleMessages(final IMAPFolder folder, List<Message> messages) {` |
| GL-mailbox_EmailHandler-007 | `app/mailbox/EmailHandler.java:167` | `private static void handleMessage(@Nonnull IMAPMessage msg) {` |
| GL-mailbox_EmailHandler-008 | `app/mailbox/EmailHandler.java:271` | `private static class MailHeader {` |
| GL-mailbox_EmailHandler-011 | `app/mailbox/EmailHandler.java:278` | `public MailHeader(@Nonnull IMAPMessage message, @Nonnull String name) {` |
| GL-mailbox_EmailHandler-012 | `app/mailbox/EmailHandler.java:284` | `public boolean containsIgnoreCase(@Nonnull String expectedValue) throws MessagingException {` |
| GL-mailbox_EmailHandler-013 | `app/mailbox/EmailHandler.java:306` | `/**` |
| GL-mailbox_EmailHandler-014 | `app/mailbox/EmailHandler.java:317` | `private static void createResources(IMAPMessage msg, User sender, List<String> errors)` |
| GL-mailbox_EmailHandler-016 | `app/mailbox/EmailHandler.java:389` | `/**` |
| GL-mailbox_EmailHandler-022 | `app/mailbox/EmailHandler.java:515` | `private static void reply(IMAPMessage origin, String username, String emailAddress,` |
| GL-mailbox_EmailHandler-023 | `app/mailbox/EmailHandler.java:545` | `private static void reply(IMAPMessage origin, User to, String msg) {` |
| GL-mailbox_EmailHandler-025 | `app/mailbox/EmailHandler.java:566` | `/**` |
| GL-mailbox_EmailHandler-026 | `app/mailbox/EmailHandler.java:592` | `/**` |
| GL-mailbox_MailboxService-001 | `app/mailbox/MailboxService.java:38` | `/**` |
| GL-mailbox_MailboxService-015 | `app/mailbox/MailboxService.java:89` | `/**` |
| GL-mailbox_MailboxService-016 | `app/mailbox/MailboxService.java:104` | `/**` |
| GL-mailbox_MailboxService-017 | `app/mailbox/MailboxService.java:135` | `/**` |
| GL-mailbox_MailboxService-018 | `app/mailbox/MailboxService.java:158` | `/**` |
| GL-mailbox_MailboxService-019 | `app/mailbox/MailboxService.java:210` | `private void handleNewMessagesAndStartListener() {` |
| GL-mailbox_MailboxService-020 | `app/mailbox/MailboxService.java:232` | `/**` |
| GL-mailbox_MailboxService-021 | `app/mailbox/MailboxService.java:251` | `/**` |
| GL-mailbox_MailboxService-022 | `app/mailbox/MailboxService.java:295` | `/**` |
| GL-mailbox_MailboxService-023 | `app/mailbox/MailboxService.java:362` | `/**` |
| GL-mailbox_CreationViaEmail-001 | `app/mailbox/CreationViaEmail.java:58` | `/**` |
| GL-mailbox_CreationViaEmail-002 | `app/mailbox/CreationViaEmail.java:63` | `/**` |
| GL-mailbox_CreationViaEmail-003 | `app/mailbox/CreationViaEmail.java:109` | `/**` |
| GL-mailbox_CreationViaEmail-004 | `app/mailbox/CreationViaEmail.java:127` | `private static Comment makeNewComment(Resource target, User sender, String body) throws IssueNotFoun` |
| GL-mailbox_CreationViaEmail-005 | `app/mailbox/CreationViaEmail.java:154` | `/**` |
| GL-mailbox_CreationViaEmail-006 | `app/mailbox/CreationViaEmail.java:182` | `@Transactional` |
| GL-mailbox_CreationViaEmail-007 | `app/mailbox/CreationViaEmail.java:212` | `/**` |
| GL-mailbox_CreationViaEmail-008 | `app/mailbox/CreationViaEmail.java:241` | `@Transactional` |
| GL-mailbox_CreationViaEmail-009 | `app/mailbox/CreationViaEmail.java:301` | `// You don't need to instantiate this class because this class is just` |
| GL-mailbox_CreationViaEmail-010 | `app/mailbox/CreationViaEmail.java:306` | `@Nonnull` |
| GL-mailbox_CreationViaEmail-011 | `app/mailbox/CreationViaEmail.java:312` | `@Nonnull` |
| GL-mailbox_CreationViaEmail-016 | `app/mailbox/CreationViaEmail.java:391` | `/**` |
| GL-mailbox_CreationViaEmail-018 | `app/mailbox/CreationViaEmail.java:424` | `/**` |
| GL-mailbox_CreationViaEmail-019 | `app/mailbox/CreationViaEmail.java:446` | `private static String cannotCreateMessage(User user, Project project,` |
| GL-mailbox_CreationViaEmail-020 | `app/mailbox/CreationViaEmail.java:455` | `private static void addEvent(NotificationEvent event, Address[] recipients,` |
| GL-mailbox_CreationViaEmail-021 | `app/mailbox/CreationViaEmail.java:468` | `private static String replaceCidWithAttachments(String html,` |
| GL-mailbox_CreationViaEmail-022 | `app/mailbox/CreationViaEmail.java:504` | `private static Attachment saveAttachment(Part partToAttach, Resource container)` |
| GL-mailbox_CreationViaEmail-023 | `app/mailbox/CreationViaEmail.java:519` | `private static Map<String, Attachment> saveAttachments(` |
| GL-mailbox_Content-001 | `app/mailbox/Content.java:10` | `public class Content {` |
| GL-mailbox_Content-003 | `app/mailbox/Content.java:14` | `public final List<MimePart> attachments = new ArrayList<>();` |
| GL-mailbox_Content-005 | `app/mailbox/Content.java:19` | `public Content() { }` |
| GL-mailbox_Content-006 | `app/mailbox/Content.java:22` | `public Content(MimePart attachment) {` |
| GL-mailbox_Content-007 | `app/mailbox/Content.java:27` | `public Content merge(Content that) {` |
| GL-mailbox_exceptions_MailHandlerException-001 | `app/mailbox/exceptions/MailHandlerException.java:24` | `public class MailHandlerException extends Exception{` |
| GL-mailbox_exceptions_MailHandlerException-003 | `app/mailbox/exceptions/MailHandlerException.java:29` | `MailHandlerException(String s) {` |
| GL-mailbox_exceptions_PostingNotFound-001 | `app/mailbox/exceptions/PostingNotFound.java:24` | `public class PostingNotFound extends MailHandlerException {` |
| GL-mailbox_exceptions_PostingNotFound-003 | `app/mailbox/exceptions/PostingNotFound.java:29` | `public PostingNotFound(Long number) {` |
| GL-mailbox_exceptions_IllegalDetailException-001 | `app/mailbox/exceptions/IllegalDetailException.java:24` | `public class IllegalDetailException extends Exception {` |
| GL-mailbox_exceptions_IssueNotFound-001 | `app/mailbox/exceptions/IssueNotFound.java:24` | `public class IssueNotFound extends MailHandlerException {` |
| GL-mailbox_exceptions_IssueNotFound-003 | `app/mailbox/exceptions/IssueNotFound.java:29` | `public IssueNotFound(Long number) {` |
| GL-mailbox_exceptions_PermissionDenied-001 | `app/mailbox/exceptions/PermissionDenied.java:24` | `public class PermissionDenied extends MailHandlerException {` |
| GL-mailbox_exceptions_PermissionDenied-003 | `app/mailbox/exceptions/PermissionDenied.java:29` | `public PermissionDenied(String s) {` |
