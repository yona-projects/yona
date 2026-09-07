---
id: gl-data
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

# 버킷 C — 공백 후보: `data` 영역 (436개 심볼)

자동 분류 결과이며, 실제 공백인지는 사람이 개별 확인해야 한다(자동 승격 금지). 상세 방법론은 [[../methodology]] 참고.

| GL-ID | yona 파일:라인 | 선언 |
|---|---|---|
| GL-data_DataService-001 | `app/data/DataService.java:47` | `/**` |
| GL-data_DataService-003 | `app/data/DataService.java:57` | `private static final Comparator<Exchanger> COMPARATOR = new Comparator<Exchanger>() {` |
| GL-data_DataService-005 | `app/data/DataService.java:68` | `public DataService() {` |
| GL-data_DataService-006 | `app/data/DataService.java:122` | `public InputStream exportData() {` |
| GL-data_DataService-008 | `app/data/DataService.java:185` | `public void importData(File file) throws IOException {` |
| GL-data_DataService-010 | `app/data/DataService.java:238` | `private void enableReferentialIntegrity(String dbName, JdbcTemplate jdbcTemplate) {` |
| GL-data_DataService-011 | `app/data/DataService.java:248` | `private void disableReferentialIntegtiry(String dbName, JdbcTemplate jdbcTemplate) {` |
| GL-data_DefaultExchanger-001 | `app/data/DefaultExchanger.java:44` | `/**` |
| GL-data_DefaultExchanger-004 | `app/data/DefaultExchanger.java:55` | `protected Long timestamp(Timestamp timestamp) {` |
| GL-data_DefaultExchanger-005 | `app/data/DefaultExchanger.java:65` | `protected Long date(Date date) {` |
| GL-data_DefaultExchanger-006 | `app/data/DefaultExchanger.java:75` | `protected Timestamp timestamp(long time) {` |
| GL-data_DefaultExchanger-007 | `app/data/DefaultExchanger.java:84` | `protected Date date(long time) {` |
| GL-data_DefaultExchanger-009 | `app/data/DefaultExchanger.java:102` | `protected String clobString(@Nullable Clob clob) throws SQLException {` |
| GL-data_DefaultExchanger-011 | `app/data/DefaultExchanger.java:130` | `/**` |
| GL-data_DefaultExchanger-012 | `app/data/DefaultExchanger.java:146` | `protected void putLong(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws ` |
| GL-data_DefaultExchanger-013 | `app/data/DefaultExchanger.java:157` | `protected void putInt(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws S` |
| GL-data_DefaultExchanger-014 | `app/data/DefaultExchanger.java:168` | `protected void putString(JsonGenerator generator, String fieldName, ResultSet rs, short index) throw` |
| GL-data_DefaultExchanger-015 | `app/data/DefaultExchanger.java:179` | `protected void putBoolean(JsonGenerator generator, String fieldName, ResultSet rs, short index) thro` |
| GL-data_DefaultExchanger-016 | `app/data/DefaultExchanger.java:185` | `protected void putTimestamp(JsonGenerator generator, String fieldName, ResultSet rs, short index) th` |
| GL-data_DefaultExchanger-017 | `app/data/DefaultExchanger.java:196` | `protected void putDate(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws ` |
| GL-data_DefaultExchanger-018 | `app/data/DefaultExchanger.java:207` | `protected void putClob(JsonGenerator generator, String fieldName, ResultSet rs, short index) throws ` |
| GL-data_DefaultExchanger-019 | `app/data/DefaultExchanger.java:218` | `public void exportData(String dbName, String catalogName, final JsonGenerator generator, JdbcTemplat` |
| GL-data_DefaultExchanger-020 | `app/data/DefaultExchanger.java:256` | `public void importData(String dbName, JsonParser parser, JdbcTemplate jdbcTemplate) throws IOExcepti` |
| GL-data_DefaultExchanger-021 | `app/data/DefaultExchanger.java:295` | `private void importSequence(String dbName, JsonParser parser, JdbcTemplate jdbcTemplate) throws IOEx` |
| GL-data_DefaultExchanger-022 | `app/data/DefaultExchanger.java:317` | `private void importDataFromArray(JsonParser parser, JdbcTemplate jdbcTemplate, int batchSize) throws` |
| GL-data_DefaultExchanger-023 | `app/data/DefaultExchanger.java:335` | `private void truncateTable(JdbcTemplate jdbcTemplate) {` |
| GL-data_DefaultExchanger-024 | `app/data/DefaultExchanger.java:342` | `private int[] batchUpdate(JdbcTemplate jdbcTemplate, final List<JsonNode> nodes) {` |
| GL-data_DefaultExchanger-025 | `app/data/DefaultExchanger.java:358` | `/**` |
| GL-data_DefaultExchanger-026 | `app/data/DefaultExchanger.java:370` | `/**` |
| GL-data_DefaultExchanger-027 | `app/data/DefaultExchanger.java:383` | `/**` |
| GL-data_DefaultExchanger-028 | `app/data/DefaultExchanger.java:391` | `/**` |
| GL-data_DefaultExchanger-029 | `app/data/DefaultExchanger.java:399` | `protected boolean hasSequence() {` |
| GL-data_DefaultExchanger-030 | `app/data/DefaultExchanger.java:404` | `protected String sequenceName() {` |
| GL-data_Exchanger-001 | `app/data/Exchanger.java:30` | `/**` |
| GL-data_Exchanger-002 | `app/data/Exchanger.java:36` | `/**` |
| GL-data_Exchanger-003 | `app/data/Exchanger.java:45` | `/**` |
| GL-data_Exchanger-004 | `app/data/Exchanger.java:64` | `/**` |
| GL-data_exchangers_MilestoneDataExchanger-001 | `app/data/exchangers/MilestoneDataExchanger.java:31` | `/**` |
| GL-data_exchangers_MilestoneDataExchanger-002 | `app/data/exchangers/MilestoneDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_MilestoneDataExchanger-003 | `app/data/exchangers/MilestoneDataExchanger.java:39` | `private static final String TITLE = "title"; // VARCHAR(255)` |
| GL-data_exchangers_MilestoneDataExchanger-004 | `app/data/exchangers/MilestoneDataExchanger.java:41` | `private static final String DUE_DATE = "due_date"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_MilestoneDataExchanger-005 | `app/data/exchangers/MilestoneDataExchanger.java:43` | `private static final String CONTENTS = "contents"; // CLOB(2147483647)` |
| GL-data_exchangers_MilestoneDataExchanger-006 | `app/data/exchangers/MilestoneDataExchanger.java:45` | `private static final String STATE = "state"; // INTEGER(10)` |
| GL-data_exchangers_MilestoneDataExchanger-007 | `app/data/exchangers/MilestoneDataExchanger.java:47` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19)` |
| GL-data_exchangers_MilestoneDataExchanger-008 | `app/data/exchangers/MilestoneDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_MilestoneDataExchanger-009 | `app/data/exchangers/MilestoneDataExchanger.java:62` | `@Override` |
| GL-data_exchangers_MilestoneDataExchanger-010 | `app/data/exchangers/MilestoneDataExchanger.java:74` | `@Override` |
| GL-data_exchangers_MilestoneDataExchanger-011 | `app/data/exchangers/MilestoneDataExchanger.java:80` | `@Override` |
| GL-data_exchangers_MilestoneDataExchanger-012 | `app/data/exchangers/MilestoneDataExchanger.java:87` | `@Override` |
| GL-data_exchangers_ProjectTransferDataExchanger-001 | `app/data/exchangers/ProjectTransferDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectTransferDataExchanger-002 | `app/data/exchangers/ProjectTransferDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectTransferDataExchanger-003 | `app/data/exchangers/ProjectTransferDataExchanger.java:39` | `private static final String SENDER_ID = "sender_id"; // VARCHAR(255)` |
| GL-data_exchangers_ProjectTransferDataExchanger-004 | `app/data/exchangers/ProjectTransferDataExchanger.java:41` | `private static final String DESTINATION = "destination"; // VARCHAR(255)` |
| GL-data_exchangers_ProjectTransferDataExchanger-005 | `app/data/exchangers/ProjectTransferDataExchanger.java:43` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectTransferDataExchanger-006 | `app/data/exchangers/ProjectTransferDataExchanger.java:45` | `private static final String REQUESTED = "requested"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_ProjectTransferDataExchanger-007 | `app/data/exchangers/ProjectTransferDataExchanger.java:47` | `private static final String CONFIRM_KEY = "confirm_key"; // VARCHAR(50)` |
| GL-data_exchangers_ProjectTransferDataExchanger-008 | `app/data/exchangers/ProjectTransferDataExchanger.java:49` | `private static final String ACCEPTED = "accepted"; // BOOLEAN(1)` |
| GL-data_exchangers_ProjectTransferDataExchanger-009 | `app/data/exchangers/ProjectTransferDataExchanger.java:51` | `private static final String NEW_PROJECT_NAME = "new_project_name"; // VARCHAR(255)` |
| GL-data_exchangers_ProjectTransferDataExchanger-010 | `app/data/exchangers/ProjectTransferDataExchanger.java:54` | `@Override` |
| GL-data_exchangers_ProjectTransferDataExchanger-011 | `app/data/exchangers/ProjectTransferDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_ProjectTransferDataExchanger-012 | `app/data/exchangers/ProjectTransferDataExchanger.java:82` | `@Override` |
| GL-data_exchangers_ProjectTransferDataExchanger-013 | `app/data/exchangers/ProjectTransferDataExchanger.java:88` | `@Override` |
| GL-data_exchangers_ProjectTransferDataExchanger-014 | `app/data/exchangers/ProjectTransferDataExchanger.java:95` | `@Override` |
| GL-data_exchangers_OrganizationUserDataExchanger-001 | `app/data/exchangers/OrganizationUserDataExchanger.java:31` | `/**` |
| GL-data_exchangers_OrganizationUserDataExchanger-006 | `app/data/exchangers/OrganizationUserDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_OrganizationUserDataExchanger-007 | `app/data/exchangers/OrganizationUserDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_OrganizationUserDataExchanger-008 | `app/data/exchangers/OrganizationUserDataExchanger.java:66` | `@Override` |
| GL-data_exchangers_OrganizationUserDataExchanger-009 | `app/data/exchangers/OrganizationUserDataExchanger.java:72` | `@Override` |
| GL-data_exchangers_OrganizationUserDataExchanger-010 | `app/data/exchangers/OrganizationUserDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_OriginalEmailDataExchanger-001 | `app/data/exchangers/OriginalEmailDataExchanger.java:31` | `/**` |
| GL-data_exchangers_OriginalEmailDataExchanger-007 | `app/data/exchangers/OriginalEmailDataExchanger.java:47` | `@Override` |
| GL-data_exchangers_OriginalEmailDataExchanger-008 | `app/data/exchangers/OriginalEmailDataExchanger.java:53` | `@Override` |
| GL-data_exchangers_OriginalEmailDataExchanger-009 | `app/data/exchangers/OriginalEmailDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_OriginalEmailDataExchanger-010 | `app/data/exchangers/OriginalEmailDataExchanger.java:75` | `@Override` |
| GL-data_exchangers_OriginalEmailDataExchanger-011 | `app/data/exchangers/OriginalEmailDataExchanger.java:82` | `@Override` |
| GL-data_exchangers_IssueLabelDataExchanger-001 | `app/data/exchangers/IssueLabelDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueLabelDataExchanger-002 | `app/data/exchangers/IssueLabelDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueLabelDataExchanger-003 | `app/data/exchangers/IssueLabelDataExchanger.java:39` | `private static final String COLOR = "color"; // VARCHAR(255)` |
| GL-data_exchangers_IssueLabelDataExchanger-004 | `app/data/exchangers/IssueLabelDataExchanger.java:41` | `private static final String NAME = "name"; // VARCHAR(255)` |
| GL-data_exchangers_IssueLabelDataExchanger-005 | `app/data/exchangers/IssueLabelDataExchanger.java:43` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19)` |
| GL-data_exchangers_IssueLabelDataExchanger-006 | `app/data/exchangers/IssueLabelDataExchanger.java:45` | `private static final String CATEGORY_ID = "category_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueLabelDataExchanger-007 | `app/data/exchangers/IssueLabelDataExchanger.java:48` | `@Override` |
| GL-data_exchangers_IssueLabelDataExchanger-008 | `app/data/exchangers/IssueLabelDataExchanger.java:59` | `@Override` |
| GL-data_exchangers_IssueLabelDataExchanger-009 | `app/data/exchangers/IssueLabelDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_IssueLabelDataExchanger-010 | `app/data/exchangers/IssueLabelDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_IssueLabelDataExchanger-011 | `app/data/exchangers/IssueLabelDataExchanger.java:82` | `@Override` |
| GL-data_exchangers_MentionDataExchanger-001 | `app/data/exchangers/MentionDataExchanger.java:31` | `/**` |
| GL-data_exchangers_MentionDataExchanger-006 | `app/data/exchangers/MentionDataExchanger.java:45` | `@Override` |
| GL-data_exchangers_MentionDataExchanger-007 | `app/data/exchangers/MentionDataExchanger.java:55` | `@Override` |
| GL-data_exchangers_MentionDataExchanger-008 | `app/data/exchangers/MentionDataExchanger.java:65` | `@Override` |
| GL-data_exchangers_MentionDataExchanger-009 | `app/data/exchangers/MentionDataExchanger.java:71` | `@Override` |
| GL-data_exchangers_MentionDataExchanger-010 | `app/data/exchangers/MentionDataExchanger.java:77` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-001 | `app/data/exchangers/AssigneeDataExchanger.java:31` | `/**` |
| GL-data_exchangers_AssigneeDataExchanger-005 | `app/data/exchangers/AssigneeDataExchanger.java:44` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-006 | `app/data/exchangers/AssigneeDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-007 | `app/data/exchangers/AssigneeDataExchanger.java:59` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-008 | `app/data/exchangers/AssigneeDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-009 | `app/data/exchangers/AssigneeDataExchanger.java:74` | `@Override` |
| GL-data_exchangers_AssigneeDataExchanger-010 | `app/data/exchangers/AssigneeDataExchanger.java:80` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-001 | `app/data/exchangers/CommentThreadUserDataExchanger.java:31` | `/**` |
| GL-data_exchangers_CommentThreadUserDataExchanger-004 | `app/data/exchangers/CommentThreadUserDataExchanger.java:41` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-005 | `app/data/exchangers/CommentThreadUserDataExchanger.java:49` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-006 | `app/data/exchangers/CommentThreadUserDataExchanger.java:57` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-007 | `app/data/exchangers/CommentThreadUserDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-008 | `app/data/exchangers/CommentThreadUserDataExchanger.java:69` | `@Override` |
| GL-data_exchangers_CommentThreadUserDataExchanger-009 | `app/data/exchangers/CommentThreadUserDataExchanger.java:75` | `@Override` |
| GL-data_exchangers_PostingDataExchanger-001 | `app/data/exchangers/PostingDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PostingDataExchanger-002 | `app/data/exchangers/PostingDataExchanger.java:36` | `private static final String ID = "id";  //BIGINT  nullable? 0` |
| GL-data_exchangers_PostingDataExchanger-003 | `app/data/exchangers/PostingDataExchanger.java:38` | `private static final String TITLE = "title";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-004 | `app/data/exchangers/PostingDataExchanger.java:40` | `private static final String BODY = "body";  //CLOB  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-005 | `app/data/exchangers/PostingDataExchanger.java:42` | `private static final String CREATED_DATE = "created_date";  //TIMESTAMP  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-006 | `app/data/exchangers/PostingDataExchanger.java:44` | `private static final String NUM_OF_COMMENTS = "num_of_comments";  //INTEGER  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-007 | `app/data/exchangers/PostingDataExchanger.java:46` | `private static final String AUTHOR_ID = "author_id";  //BIGINT  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-008 | `app/data/exchangers/PostingDataExchanger.java:48` | `private static final String AUTHOR_LOGIN_ID = "author_login_id";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-009 | `app/data/exchangers/PostingDataExchanger.java:50` | `private static final String AUTHOR_NAME = "author_name";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-010 | `app/data/exchangers/PostingDataExchanger.java:52` | `private static final String PROJECT_ID = "project_id";  //BIGINT  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-011 | `app/data/exchangers/PostingDataExchanger.java:54` | `private static final String NUMBER = "number";  //BIGINT  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-012 | `app/data/exchangers/PostingDataExchanger.java:56` | `private static final String NOTICE = "notice";  //BOOLEAN  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-013 | `app/data/exchangers/PostingDataExchanger.java:58` | `private static final String UPDATED_DATE = "updated_date";  //TIMESTAMP  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-014 | `app/data/exchangers/PostingDataExchanger.java:60` | `private static final String README = "readme";  //BOOLEAN  nullable? 1` |
| GL-data_exchangers_PostingDataExchanger-015 | `app/data/exchangers/PostingDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_PostingDataExchanger-016 | `app/data/exchangers/PostingDataExchanger.java:82` | `@Override` |
| GL-data_exchangers_PostingDataExchanger-017 | `app/data/exchangers/PostingDataExchanger.java:101` | `@Override` |
| GL-data_exchangers_PostingDataExchanger-018 | `app/data/exchangers/PostingDataExchanger.java:107` | `@Override` |
| GL-data_exchangers_PostingDataExchanger-019 | `app/data/exchangers/PostingDataExchanger.java:114` | `@Override` |
| GL-data_exchangers_ProjectUserDataExchanger-001 | `app/data/exchangers/ProjectUserDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectUserDataExchanger-006 | `app/data/exchangers/ProjectUserDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_ProjectUserDataExchanger-007 | `app/data/exchangers/ProjectUserDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_ProjectUserDataExchanger-008 | `app/data/exchangers/ProjectUserDataExchanger.java:67` | `@Override` |
| GL-data_exchangers_ProjectUserDataExchanger-009 | `app/data/exchangers/ProjectUserDataExchanger.java:73` | `@Override` |
| GL-data_exchangers_ProjectUserDataExchanger-010 | `app/data/exchangers/ProjectUserDataExchanger.java:79` | `@Override` |
| GL-data_exchangers_LabelDataExchanger-001 | `app/data/exchangers/LabelDataExchanger.java:31` | `/**` |
| GL-data_exchangers_LabelDataExchanger-005 | `app/data/exchangers/LabelDataExchanger.java:44` | `@Override` |
| GL-data_exchangers_LabelDataExchanger-006 | `app/data/exchangers/LabelDataExchanger.java:53` | `@Override` |
| GL-data_exchangers_LabelDataExchanger-007 | `app/data/exchangers/LabelDataExchanger.java:62` | `@Override` |
| GL-data_exchangers_LabelDataExchanger-008 | `app/data/exchangers/LabelDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_LabelDataExchanger-009 | `app/data/exchangers/LabelDataExchanger.java:74` | `@Override` |
| GL-data_exchangers_IssueDataExchanger-001 | `app/data/exchangers/IssueDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueDataExchanger-017 | `app/data/exchangers/IssueDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_IssueDataExchanger-018 | `app/data/exchangers/IssueDataExchanger.java:89` | `@Override` |
| GL-data_exchangers_IssueDataExchanger-019 | `app/data/exchangers/IssueDataExchanger.java:110` | `@Override` |
| GL-data_exchangers_IssueDataExchanger-020 | `app/data/exchangers/IssueDataExchanger.java:116` | `@Override` |
| GL-data_exchangers_IssueDataExchanger-021 | `app/data/exchangers/IssueDataExchanger.java:124` | `@Override` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-001 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:31` | `/**` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-002 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-003 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:39` | `private static final String USER_ID = "user_id"; // BIGINT(19)` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-004 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:41` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19)` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-005 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:43` | `private static final String NOTIFICATION_TYPE = "notification_type"; // VARCHAR(255)` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-006 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:45` | `private static final String ALLOWED = "allowed"; // BOOLEAN(1)` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-007 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:48` | `@Override` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-008 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:59` | `@Override` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-009 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-010 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_UserProjectNotificationDataExchanger-011 | `app/data/exchangers/UserProjectNotificationDataExchanger.java:83` | `@Override` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-001 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-002 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-003 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:39` | `private static final String PUSHED_DATE = "pushed_date"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-004 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:41` | `private static final String NAME = "name"; // VARCHAR(255)` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-005 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:43` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-006 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-007 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:52` | `@Override` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-008 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:62` | `@Override` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-009 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:72` | `@Override` |
| GL-data_exchangers_ProjectPushedBranchDataExchanger-010 | `app/data/exchangers/ProjectPushedBranchDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_IssueCommentDataExchanger-001 | `app/data/exchangers/IssueCommentDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueCommentDataExchanger-009 | `app/data/exchangers/IssueCommentDataExchanger.java:52` | `@Override` |
| GL-data_exchangers_IssueCommentDataExchanger-010 | `app/data/exchangers/IssueCommentDataExchanger.java:65` | `@Override` |
| GL-data_exchangers_IssueCommentDataExchanger-011 | `app/data/exchangers/IssueCommentDataExchanger.java:79` | `@Override` |
| GL-data_exchangers_IssueCommentDataExchanger-012 | `app/data/exchangers/IssueCommentDataExchanger.java:85` | `@Override` |
| GL-data_exchangers_IssueCommentDataExchanger-013 | `app/data/exchangers/IssueCommentDataExchanger.java:92` | `@Override` |
| GL-data_exchangers_PropertyDataExchanger-001 | `app/data/exchangers/PropertyDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PropertyDataExchanger-005 | `app/data/exchangers/PropertyDataExchanger.java:43` | `@Override` |
| GL-data_exchangers_PropertyDataExchanger-006 | `app/data/exchangers/PropertyDataExchanger.java:52` | `@Override` |
| GL-data_exchangers_PropertyDataExchanger-007 | `app/data/exchangers/PropertyDataExchanger.java:61` | `@Override` |
| GL-data_exchangers_PropertyDataExchanger-008 | `app/data/exchangers/PropertyDataExchanger.java:67` | `@Override` |
| GL-data_exchangers_PropertyDataExchanger-009 | `app/data/exchangers/PropertyDataExchanger.java:73` | `@Override` |
| GL-data_exchangers_NotificationEventDataExchanger-001 | `app/data/exchangers/NotificationEventDataExchanger.java:31` | `/**` |
| GL-data_exchangers_NotificationEventDataExchanger-011 | `app/data/exchangers/NotificationEventDataExchanger.java:55` | `@Override` |
| GL-data_exchangers_NotificationEventDataExchanger-012 | `app/data/exchangers/NotificationEventDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_NotificationEventDataExchanger-013 | `app/data/exchangers/NotificationEventDataExchanger.java:85` | `@Override` |
| GL-data_exchangers_NotificationEventDataExchanger-014 | `app/data/exchangers/NotificationEventDataExchanger.java:91` | `@Override` |
| GL-data_exchangers_NotificationEventDataExchanger-015 | `app/data/exchangers/NotificationEventDataExchanger.java:99` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-001 | `app/data/exchangers/AttachmentDataExchanger.java:31` | `/**` |
| GL-data_exchangers_AttachmentDataExchanger-010 | `app/data/exchangers/AttachmentDataExchanger.java:54` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-011 | `app/data/exchangers/AttachmentDataExchanger.java:60` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-012 | `app/data/exchangers/AttachmentDataExchanger.java:74` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-013 | `app/data/exchangers/AttachmentDataExchanger.java:88` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-014 | `app/data/exchangers/AttachmentDataExchanger.java:94` | `@Override` |
| GL-data_exchangers_AttachmentDataExchanger-015 | `app/data/exchangers/AttachmentDataExchanger.java:101` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-001 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-004 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-005 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-006 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-007 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-008 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_IssueCommentVoterDataExchanger-009 | `app/data/exchangers/IssueCommentVoterDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_SiteAdminDataExchanger-001 | `app/data/exchangers/SiteAdminDataExchanger.java:31` | `/**` |
| GL-data_exchangers_SiteAdminDataExchanger-002 | `app/data/exchangers/SiteAdminDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_SiteAdminDataExchanger-003 | `app/data/exchangers/SiteAdminDataExchanger.java:39` | `private static final String ADMIN_ID = "admin_id"; // BIGINT(19)` |
| GL-data_exchangers_SiteAdminDataExchanger-004 | `app/data/exchangers/SiteAdminDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_SiteAdminDataExchanger-005 | `app/data/exchangers/SiteAdminDataExchanger.java:48` | `@Override` |
| GL-data_exchangers_SiteAdminDataExchanger-006 | `app/data/exchangers/SiteAdminDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_SiteAdminDataExchanger-007 | `app/data/exchangers/SiteAdminDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_SiteAdminDataExchanger-008 | `app/data/exchangers/SiteAdminDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_CommentThreadDataExchanger-001 | `app/data/exchangers/CommentThreadDataExchanger.java:31` | `/**` |
| GL-data_exchangers_CommentThreadDataExchanger-020 | `app/data/exchangers/CommentThreadDataExchanger.java:73` | `@Override` |
| GL-data_exchangers_CommentThreadDataExchanger-021 | `app/data/exchangers/CommentThreadDataExchanger.java:97` | `@Override` |
| GL-data_exchangers_CommentThreadDataExchanger-022 | `app/data/exchangers/CommentThreadDataExchanger.java:121` | `@Override` |
| GL-data_exchangers_CommentThreadDataExchanger-023 | `app/data/exchangers/CommentThreadDataExchanger.java:127` | `@Override` |
| GL-data_exchangers_CommentThreadDataExchanger-024 | `app/data/exchangers/CommentThreadDataExchanger.java:136` | `@Override` |
| GL-data_exchangers_ProjectDataExchanger-001 | `app/data/exchangers/ProjectDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectDataExchanger-017 | `app/data/exchangers/ProjectDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_ProjectDataExchanger-018 | `app/data/exchangers/ProjectDataExchanger.java:89` | `@Override` |
| GL-data_exchangers_ProjectDataExchanger-019 | `app/data/exchangers/ProjectDataExchanger.java:110` | `@Override` |
| GL-data_exchangers_ProjectDataExchanger-020 | `app/data/exchangers/ProjectDataExchanger.java:116` | `@Override` |
| GL-data_exchangers_ProjectDataExchanger-021 | `app/data/exchangers/ProjectDataExchanger.java:124` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-001 | `app/data/exchangers/ProjectLabelDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectLabelDataExchanger-004 | `app/data/exchangers/ProjectLabelDataExchanger.java:41` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-005 | `app/data/exchangers/ProjectLabelDataExchanger.java:49` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-006 | `app/data/exchangers/ProjectLabelDataExchanger.java:57` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-007 | `app/data/exchangers/ProjectLabelDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-008 | `app/data/exchangers/ProjectLabelDataExchanger.java:69` | `@Override` |
| GL-data_exchangers_ProjectLabelDataExchanger-009 | `app/data/exchangers/ProjectLabelDataExchanger.java:75` | `@Override` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-001 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-006 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-007 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-008 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:66` | `@Override` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-009 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:72` | `@Override` |
| GL-data_exchangers_IssueLabelCategoryDataExchanger-010 | `app/data/exchangers/IssueLabelCategoryDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_PullRequestCommitDataExchanger-001 | `app/data/exchangers/PullRequestCommitDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PullRequestCommitDataExchanger-002 | `app/data/exchangers/PullRequestCommitDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_PullRequestCommitDataExchanger-003 | `app/data/exchangers/PullRequestCommitDataExchanger.java:39` | `private static final String PULL_REQUEST_ID = "pull_request_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-004 | `app/data/exchangers/PullRequestCommitDataExchanger.java:41` | `private static final String COMMIT_ID = "commit_id"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-005 | `app/data/exchangers/PullRequestCommitDataExchanger.java:43` | `private static final String COMMIT_SHORT_ID = "commit_short_id"; // VARCHAR(7)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-006 | `app/data/exchangers/PullRequestCommitDataExchanger.java:45` | `private static final String COMMIT_MESSAGE = "commit_message"; // CLOB(2147483647)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-007 | `app/data/exchangers/PullRequestCommitDataExchanger.java:47` | `private static final String CREATED = "created"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-008 | `app/data/exchangers/PullRequestCommitDataExchanger.java:49` | `private static final String AUTHOR_DATE = "author_date"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-009 | `app/data/exchangers/PullRequestCommitDataExchanger.java:51` | `private static final String AUTHOR_EMAIL = "author_email"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-010 | `app/data/exchangers/PullRequestCommitDataExchanger.java:53` | `private static final String STATE = "state"; // VARCHAR(10)` |
| GL-data_exchangers_PullRequestCommitDataExchanger-011 | `app/data/exchangers/PullRequestCommitDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_PullRequestCommitDataExchanger-012 | `app/data/exchangers/PullRequestCommitDataExchanger.java:71` | `@Override` |
| GL-data_exchangers_PullRequestCommitDataExchanger-013 | `app/data/exchangers/PullRequestCommitDataExchanger.java:86` | `@Override` |
| GL-data_exchangers_PullRequestCommitDataExchanger-014 | `app/data/exchangers/PullRequestCommitDataExchanger.java:92` | `@Override` |
| GL-data_exchangers_PullRequestCommitDataExchanger-015 | `app/data/exchangers/PullRequestCommitDataExchanger.java:99` | `@Override` |
| GL-data_exchangers_EmailDataExchanger-001 | `app/data/exchangers/EmailDataExchanger.java:31` | `/**` |
| GL-data_exchangers_EmailDataExchanger-002 | `app/data/exchangers/EmailDataExchanger.java:36` | `private static final String ID = "id";  //BIGINT  nullable? 0` |
| GL-data_exchangers_EmailDataExchanger-003 | `app/data/exchangers/EmailDataExchanger.java:38` | `private static final String USER_ID = "user_id";  //BIGINT  nullable? 1` |
| GL-data_exchangers_EmailDataExchanger-004 | `app/data/exchangers/EmailDataExchanger.java:40` | `private static final String EMAIL = "email";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_EmailDataExchanger-005 | `app/data/exchangers/EmailDataExchanger.java:42` | `private static final String TOKEN = "token";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_EmailDataExchanger-006 | `app/data/exchangers/EmailDataExchanger.java:44` | `private static final String VALID = "valid";  //BOOLEAN  nullable? 1` |
| GL-data_exchangers_EmailDataExchanger-007 | `app/data/exchangers/EmailDataExchanger.java:47` | `@Override` |
| GL-data_exchangers_EmailDataExchanger-008 | `app/data/exchangers/EmailDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_EmailDataExchanger-009 | `app/data/exchangers/EmailDataExchanger.java:69` | `@Override` |
| GL-data_exchangers_EmailDataExchanger-010 | `app/data/exchangers/EmailDataExchanger.java:75` | `@Override` |
| GL-data_exchangers_EmailDataExchanger-011 | `app/data/exchangers/EmailDataExchanger.java:81` | `@Override` |
| GL-data_exchangers_ProjectVisitationDataExchanger-001 | `app/data/exchangers/ProjectVisitationDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectVisitationDataExchanger-002 | `app/data/exchangers/ProjectVisitationDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectVisitationDataExchanger-003 | `app/data/exchangers/ProjectVisitationDataExchanger.java:39` | `private static final String VISITED = "visited"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_ProjectVisitationDataExchanger-004 | `app/data/exchangers/ProjectVisitationDataExchanger.java:41` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_ProjectVisitationDataExchanger-005 | `app/data/exchangers/ProjectVisitationDataExchanger.java:43` | `private static final String RECENTLY_VISITED_PROJECTS_ID = "recently_visited_projects_id"; // BIGINT` |
| GL-data_exchangers_ProjectVisitationDataExchanger-006 | `app/data/exchangers/ProjectVisitationDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_ProjectVisitationDataExchanger-007 | `app/data/exchangers/ProjectVisitationDataExchanger.java:53` | `@Override` |
| GL-data_exchangers_ProjectVisitationDataExchanger-008 | `app/data/exchangers/ProjectVisitationDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_ProjectVisitationDataExchanger-009 | `app/data/exchangers/ProjectVisitationDataExchanger.java:73` | `@Override` |
| GL-data_exchangers_ProjectVisitationDataExchanger-010 | `app/data/exchangers/ProjectVisitationDataExchanger.java:79` | `@Override` |
| GL-data_exchangers_WatchDataExchanger-001 | `app/data/exchangers/WatchDataExchanger.java:31` | `/**` |
| GL-data_exchangers_WatchDataExchanger-006 | `app/data/exchangers/WatchDataExchanger.java:45` | `@Override` |
| GL-data_exchangers_WatchDataExchanger-007 | `app/data/exchangers/WatchDataExchanger.java:55` | `@Override` |
| GL-data_exchangers_WatchDataExchanger-008 | `app/data/exchangers/WatchDataExchanger.java:66` | `@Override` |
| GL-data_exchangers_WatchDataExchanger-009 | `app/data/exchangers/WatchDataExchanger.java:72` | `@Override` |
| GL-data_exchangers_WatchDataExchanger-010 | `app/data/exchangers/WatchDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_OrganizationDataExchanger-001 | `app/data/exchangers/OrganizationDataExchanger.java:31` | `/**` |
| GL-data_exchangers_OrganizationDataExchanger-006 | `app/data/exchangers/OrganizationDataExchanger.java:46` | `@Override` |
| GL-data_exchangers_OrganizationDataExchanger-007 | `app/data/exchangers/OrganizationDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_OrganizationDataExchanger-008 | `app/data/exchangers/OrganizationDataExchanger.java:66` | `@Override` |
| GL-data_exchangers_OrganizationDataExchanger-009 | `app/data/exchangers/OrganizationDataExchanger.java:72` | `@Override` |
| GL-data_exchangers_OrganizationDataExchanger-010 | `app/data/exchangers/OrganizationDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_CommitCommentDataExchanger-001 | `app/data/exchangers/CommitCommentDataExchanger.java:31` | `/**` |
| GL-data_exchangers_CommitCommentDataExchanger-013 | `app/data/exchangers/CommitCommentDataExchanger.java:59` | `@Override` |
| GL-data_exchangers_CommitCommentDataExchanger-014 | `app/data/exchangers/CommitCommentDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_CommitCommentDataExchanger-015 | `app/data/exchangers/CommitCommentDataExchanger.java:93` | `@Override` |
| GL-data_exchangers_CommitCommentDataExchanger-016 | `app/data/exchangers/CommitCommentDataExchanger.java:99` | `@Override` |
| GL-data_exchangers_CommitCommentDataExchanger-017 | `app/data/exchangers/CommitCommentDataExchanger.java:107` | `@Override` |
| GL-data_exchangers_PostingCommentDataExchanger-001 | `app/data/exchangers/PostingCommentDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PostingCommentDataExchanger-002 | `app/data/exchangers/PostingCommentDataExchanger.java:36` | `private static final String ID = "id";  //BIGINT  nullable? 0` |
| GL-data_exchangers_PostingCommentDataExchanger-003 | `app/data/exchangers/PostingCommentDataExchanger.java:38` | `private static final String CREATED_DATE = "created_date";  //TIMESTAMP  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-004 | `app/data/exchangers/PostingCommentDataExchanger.java:40` | `private static final String AUTHOR_ID = "author_id";  //BIGINT  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-005 | `app/data/exchangers/PostingCommentDataExchanger.java:42` | `private static final String AUTHOR_LOGIN_ID = "author_login_id";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-006 | `app/data/exchangers/PostingCommentDataExchanger.java:44` | `private static final String AUTHOR_NAME = "author_name";  //VARCHAR  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-007 | `app/data/exchangers/PostingCommentDataExchanger.java:46` | `private static final String POSTING_ID = "posting_id";  //BIGINT  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-008 | `app/data/exchangers/PostingCommentDataExchanger.java:48` | `private static final String CONTENTS = "contents";  //CLOB  nullable? 1` |
| GL-data_exchangers_PostingCommentDataExchanger-009 | `app/data/exchangers/PostingCommentDataExchanger.java:51` | `@Override` |
| GL-data_exchangers_PostingCommentDataExchanger-010 | `app/data/exchangers/PostingCommentDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_PostingCommentDataExchanger-011 | `app/data/exchangers/PostingCommentDataExchanger.java:77` | `@Override` |
| GL-data_exchangers_PostingCommentDataExchanger-012 | `app/data/exchangers/PostingCommentDataExchanger.java:83` | `@Override` |
| GL-data_exchangers_PostingCommentDataExchanger-013 | `app/data/exchangers/PostingCommentDataExchanger.java:90` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-001 | `app/data/exchangers/IssueVoterDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueVoterDataExchanger-002 | `app/data/exchangers/IssueVoterDataExchanger.java:37` | `private static final String ISSUE_ID = "issue_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueVoterDataExchanger-003 | `app/data/exchangers/IssueVoterDataExchanger.java:39` | `private static final String USER_ID = "user_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueVoterDataExchanger-004 | `app/data/exchangers/IssueVoterDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-005 | `app/data/exchangers/IssueVoterDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-006 | `app/data/exchangers/IssueVoterDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-007 | `app/data/exchangers/IssueVoterDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-008 | `app/data/exchangers/IssueVoterDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_IssueVoterDataExchanger-009 | `app/data/exchangers/IssueVoterDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-001 | `app/data/exchangers/NotificationEventUserDataExchanger.java:31` | `/**` |
| GL-data_exchangers_NotificationEventUserDataExchanger-004 | `app/data/exchangers/NotificationEventUserDataExchanger.java:41` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-005 | `app/data/exchangers/NotificationEventUserDataExchanger.java:49` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-006 | `app/data/exchangers/NotificationEventUserDataExchanger.java:57` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-007 | `app/data/exchangers/NotificationEventUserDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-008 | `app/data/exchangers/NotificationEventUserDataExchanger.java:69` | `@Override` |
| GL-data_exchangers_NotificationEventUserDataExchanger-009 | `app/data/exchangers/NotificationEventUserDataExchanger.java:75` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-001 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:31` | `/**` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-002 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:37` | `private static final String USER_ID = "user_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-003 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:39` | `private static final String PROJECT_ID = "project_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-004 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-005 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-006 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-007 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-008 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_UserEnrolledProjectDataExchanger-009 | `app/data/exchangers/UserEnrolledProjectDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_PullRequestDataExchanger-001 | `app/data/exchangers/PullRequestDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PullRequestDataExchanger-002 | `app/data/exchangers/PullRequestDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_PullRequestDataExchanger-003 | `app/data/exchangers/PullRequestDataExchanger.java:39` | `private static final String TITLE = "title"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-004 | `app/data/exchangers/PullRequestDataExchanger.java:41` | `private static final String BODY = "body"; // CLOB(2147483647)` |
| GL-data_exchangers_PullRequestDataExchanger-005 | `app/data/exchangers/PullRequestDataExchanger.java:43` | `private static final String TO_PROJECT_ID = "to_project_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestDataExchanger-006 | `app/data/exchangers/PullRequestDataExchanger.java:45` | `private static final String FROM_PROJECT_ID = "from_project_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestDataExchanger-007 | `app/data/exchangers/PullRequestDataExchanger.java:47` | `private static final String TO_BRANCH = "to_branch"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-008 | `app/data/exchangers/PullRequestDataExchanger.java:49` | `private static final String FROM_BRANCH = "from_branch"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-009 | `app/data/exchangers/PullRequestDataExchanger.java:51` | `private static final String CONTRIBUTOR_ID = "contributor_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestDataExchanger-010 | `app/data/exchangers/PullRequestDataExchanger.java:53` | `private static final String RECEIVER_ID = "receiver_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestDataExchanger-011 | `app/data/exchangers/PullRequestDataExchanger.java:55` | `private static final String CREATED = "created"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestDataExchanger-012 | `app/data/exchangers/PullRequestDataExchanger.java:57` | `private static final String UPDATED = "updated"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestDataExchanger-013 | `app/data/exchangers/PullRequestDataExchanger.java:59` | `private static final String RECEIVED = "received"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestDataExchanger-014 | `app/data/exchangers/PullRequestDataExchanger.java:61` | `private static final String STATE = "state"; // INTEGER(10)` |
| GL-data_exchangers_PullRequestDataExchanger-015 | `app/data/exchangers/PullRequestDataExchanger.java:63` | `private static final String LAST_COMMIT_ID = "last_commit_id"; //VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-016 | `app/data/exchangers/PullRequestDataExchanger.java:65` | `private static final String MERGED_COMMIT_ID_FROM = "merged_commit_id_from"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-017 | `app/data/exchangers/PullRequestDataExchanger.java:67` | `private static final String MERGED_COMMIT_ID_TO = "merged_commit_id_to"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestDataExchanger-018 | `app/data/exchangers/PullRequestDataExchanger.java:69` | `private static final String NUMBER = "number"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestDataExchanger-019 | `app/data/exchangers/PullRequestDataExchanger.java:71` | `private static final String IS_CONFLICT = "is_conflict"; // BOOLEAN(1)` |
| GL-data_exchangers_PullRequestDataExchanger-020 | `app/data/exchangers/PullRequestDataExchanger.java:73` | `private static final String IS_MERGING = "is_merging"; // BOOLEAN(1)` |
| GL-data_exchangers_PullRequestDataExchanger-021 | `app/data/exchangers/PullRequestDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_PullRequestDataExchanger-022 | `app/data/exchangers/PullRequestDataExchanger.java:101` | `@Override` |
| GL-data_exchangers_PullRequestDataExchanger-023 | `app/data/exchangers/PullRequestDataExchanger.java:126` | `@Override` |
| GL-data_exchangers_PullRequestDataExchanger-024 | `app/data/exchangers/PullRequestDataExchanger.java:132` | `@Override` |
| GL-data_exchangers_PullRequestDataExchanger-025 | `app/data/exchangers/PullRequestDataExchanger.java:141` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-001 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:31` | `/**` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-002 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:37` | `private static final String USER_ID = "user_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-003 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:39` | `private static final String ORGANIZATION_ID = "organization_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-004 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-005 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-006 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-007 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-008 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_UserEnrolledOrganizationDataExchanger-009 | `app/data/exchangers/UserEnrolledOrganizationDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_NotificationMailDataExchanger-001 | `app/data/exchangers/NotificationMailDataExchanger.java:31` | `/**` |
| GL-data_exchangers_NotificationMailDataExchanger-004 | `app/data/exchangers/NotificationMailDataExchanger.java:41` | `@Override` |
| GL-data_exchangers_NotificationMailDataExchanger-005 | `app/data/exchangers/NotificationMailDataExchanger.java:49` | `@Override` |
| GL-data_exchangers_NotificationMailDataExchanger-006 | `app/data/exchangers/NotificationMailDataExchanger.java:57` | `@Override` |
| GL-data_exchangers_NotificationMailDataExchanger-007 | `app/data/exchangers/NotificationMailDataExchanger.java:63` | `@Override` |
| GL-data_exchangers_NotificationMailDataExchanger-008 | `app/data/exchangers/NotificationMailDataExchanger.java:69` | `@Override` |
| GL-data_exchangers_ProjectMenuDataExchanger-001 | `app/data/exchangers/ProjectMenuDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ProjectMenuDataExchanger-010 | `app/data/exchangers/ProjectMenuDataExchanger.java:54` | `@Override` |
| GL-data_exchangers_ProjectMenuDataExchanger-011 | `app/data/exchangers/ProjectMenuDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_ProjectMenuDataExchanger-012 | `app/data/exchangers/ProjectMenuDataExchanger.java:82` | `@Override` |
| GL-data_exchangers_ProjectMenuDataExchanger-013 | `app/data/exchangers/ProjectMenuDataExchanger.java:88` | `@Override` |
| GL-data_exchangers_ProjectMenuDataExchanger-014 | `app/data/exchangers/ProjectMenuDataExchanger.java:95` | `@Override` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-001 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:31` | `/**` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-002 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-003 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:39` | `private static final String USER_ID = "user_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-004 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-005 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:48` | `@Override` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-006 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:56` | `@Override` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-007 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_RecentlyVisitedProjectsDataExchanger-008 | `app/data/exchangers/RecentlyVisitedProjectsDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_UserDataExchanger-001 | `app/data/exchangers/UserDataExchanger.java:31` | `/**` |
| GL-data_exchangers_UserDataExchanger-014 | `app/data/exchangers/UserDataExchanger.java:62` | `@Override` |
| GL-data_exchangers_UserDataExchanger-015 | `app/data/exchangers/UserDataExchanger.java:79` | `@Override` |
| GL-data_exchangers_UserDataExchanger-016 | `app/data/exchangers/UserDataExchanger.java:96` | `@Override` |
| GL-data_exchangers_UserDataExchanger-017 | `app/data/exchangers/UserDataExchanger.java:102` | `@Override` |
| GL-data_exchangers_UserDataExchanger-018 | `app/data/exchangers/UserDataExchanger.java:109` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-001 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-002 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:37` | `private static final String PULL_REQUEST_ID = "pull_request_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-003 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:39` | `private static final String USER_ID = "user_id"; // INTEGER(10) NOT NULL` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-004 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-005 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-006 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-007 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-008 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_PullRequestReviewersDataExchanger-009 | `app/data/exchangers/PullRequestReviewersDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_PullRequestEventDataExchanger-001 | `app/data/exchangers/PullRequestEventDataExchanger.java:31` | `/**` |
| GL-data_exchangers_PullRequestEventDataExchanger-002 | `app/data/exchangers/PullRequestEventDataExchanger.java:37` | `private static final String ID = "id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_PullRequestEventDataExchanger-003 | `app/data/exchangers/PullRequestEventDataExchanger.java:39` | `private static final String PULL_REQUEST_ID = "pull_request_id"; // BIGINT(19)` |
| GL-data_exchangers_PullRequestEventDataExchanger-004 | `app/data/exchangers/PullRequestEventDataExchanger.java:41` | `private static final String CREATED = "created"; // TIMESTAMP(23, 10)` |
| GL-data_exchangers_PullRequestEventDataExchanger-005 | `app/data/exchangers/PullRequestEventDataExchanger.java:43` | `private static final String SENDER_LOGIN_ID = "sender_login_id"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestEventDataExchanger-006 | `app/data/exchangers/PullRequestEventDataExchanger.java:45` | `private static final String EVENT_TYPE = "event_type"; // VARCHAR(255)` |
| GL-data_exchangers_PullRequestEventDataExchanger-007 | `app/data/exchangers/PullRequestEventDataExchanger.java:47` | `private static final String NEW_VALUE = "new_value"; // CLOB(2147483647)` |
| GL-data_exchangers_PullRequestEventDataExchanger-008 | `app/data/exchangers/PullRequestEventDataExchanger.java:49` | `private static final String OLD_VALUE = "old_value"; // CLOB(2147483647)` |
| GL-data_exchangers_PullRequestEventDataExchanger-009 | `app/data/exchangers/PullRequestEventDataExchanger.java:52` | `@Override` |
| GL-data_exchangers_PullRequestEventDataExchanger-010 | `app/data/exchangers/PullRequestEventDataExchanger.java:65` | `@Override` |
| GL-data_exchangers_PullRequestEventDataExchanger-011 | `app/data/exchangers/PullRequestEventDataExchanger.java:78` | `@Override` |
| GL-data_exchangers_PullRequestEventDataExchanger-012 | `app/data/exchangers/PullRequestEventDataExchanger.java:84` | `@Override` |
| GL-data_exchangers_PullRequestEventDataExchanger-013 | `app/data/exchangers/PullRequestEventDataExchanger.java:91` | `@Override` |
| GL-data_exchangers_UnwatchDataExchanger-001 | `app/data/exchangers/UnwatchDataExchanger.java:31` | `/**` |
| GL-data_exchangers_UnwatchDataExchanger-006 | `app/data/exchangers/UnwatchDataExchanger.java:45` | `@Override` |
| GL-data_exchangers_UnwatchDataExchanger-007 | `app/data/exchangers/UnwatchDataExchanger.java:55` | `@Override` |
| GL-data_exchangers_UnwatchDataExchanger-008 | `app/data/exchangers/UnwatchDataExchanger.java:65` | `@Override` |
| GL-data_exchangers_UnwatchDataExchanger-009 | `app/data/exchangers/UnwatchDataExchanger.java:71` | `@Override` |
| GL-data_exchangers_UnwatchDataExchanger-010 | `app/data/exchangers/UnwatchDataExchanger.java:77` | `@Override` |
| GL-data_exchangers_RoleDataExchanger-001 | `app/data/exchangers/RoleDataExchanger.java:31` | `/**` |
| GL-data_exchangers_RoleDataExchanger-005 | `app/data/exchangers/RoleDataExchanger.java:44` | `@Override` |
| GL-data_exchangers_RoleDataExchanger-006 | `app/data/exchangers/RoleDataExchanger.java:53` | `@Override` |
| GL-data_exchangers_RoleDataExchanger-007 | `app/data/exchangers/RoleDataExchanger.java:62` | `@Override` |
| GL-data_exchangers_RoleDataExchanger-008 | `app/data/exchangers/RoleDataExchanger.java:68` | `@Override` |
| GL-data_exchangers_RoleDataExchanger-009 | `app/data/exchangers/RoleDataExchanger.java:74` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-001 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-002 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:37` | `private static final String ISSUE_ID = "issue_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-003 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:39` | `private static final String ISSUE_LABEL_ID = "issue_label_id"; // BIGINT(19) NOT NULL` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-004 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:42` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-005 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:50` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-006 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:58` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-007 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-008 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:70` | `@Override` |
| GL-data_exchangers_IssueIssueLabelDataExchanger-009 | `app/data/exchangers/IssueIssueLabelDataExchanger.java:76` | `@Override` |
| GL-data_exchangers_IssueEventDataExchanger-001 | `app/data/exchangers/IssueEventDataExchanger.java:31` | `/**` |
| GL-data_exchangers_IssueEventDataExchanger-010 | `app/data/exchangers/IssueEventDataExchanger.java:53` | `@Override` |
| GL-data_exchangers_IssueEventDataExchanger-011 | `app/data/exchangers/IssueEventDataExchanger.java:67` | `@Override` |
| GL-data_exchangers_IssueEventDataExchanger-012 | `app/data/exchangers/IssueEventDataExchanger.java:81` | `@Override` |
| GL-data_exchangers_IssueEventDataExchanger-013 | `app/data/exchangers/IssueEventDataExchanger.java:87` | `@Override` |
| GL-data_exchangers_IssueEventDataExchanger-014 | `app/data/exchangers/IssueEventDataExchanger.java:95` | `@Override` |
| GL-data_exchangers_ReviewCommentDataExchanger-001 | `app/data/exchangers/ReviewCommentDataExchanger.java:31` | `/**` |
| GL-data_exchangers_ReviewCommentDataExchanger-009 | `app/data/exchangers/ReviewCommentDataExchanger.java:51` | `@Override` |
| GL-data_exchangers_ReviewCommentDataExchanger-010 | `app/data/exchangers/ReviewCommentDataExchanger.java:64` | `@Override` |
| GL-data_exchangers_ReviewCommentDataExchanger-011 | `app/data/exchangers/ReviewCommentDataExchanger.java:77` | `@Override` |
| GL-data_exchangers_ReviewCommentDataExchanger-012 | `app/data/exchangers/ReviewCommentDataExchanger.java:83` | `@Override` |
| GL-data_exchangers_ReviewCommentDataExchanger-013 | `app/data/exchangers/ReviewCommentDataExchanger.java:90` | `@Override` |
