package dev.yona.domain.enumeration

/**
 * 이슈, 마일스톤, 풀 리퀘스트, 목록 필터가 공유하는 상태 값이다.
 *
 * 이슈와 마일스톤, 풀 리퀘스트는 이 값을 **서수(ordinal)** 로 저장하므로 선언 순서를 바꾸면
 * 기존 데이터가 깨진다. v1.6의 `models.enumeration.State`와 순서가 같아야 한다.
 */
enum class State(val state: String) {
    ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"),
    CONFLICT("conflict"), RESOLVED("resolved"), MERGED("merged"), DRAFT("draft")
}

/** 프로젝트 공개 범위이다. */
enum class ProjectScope { PRIVATE, PROTECTED, PUBLIC }

/** 사용자 계정 상태이다. 앞의 세 값만 영속화된다. */
enum class UserState { ACTIVE, LOCKED, DELETED, GUEST, SITE_ADMIN }

/**
 * 웹훅 종류이다. 서수 값(0~3)이 `webhook.webhook_type` 컬럼에 그대로 저장된다.
 */
enum class WebhookType { SIMPLE, DETAIL_SLACK, DETAIL_HANGOUT_CHAT, JSON }

/** 알림, 이슈 이벤트, 풀 리퀘스트 이벤트의 종류이다. */
enum class EventType {
    NEW_ISSUE, NEW_POSTING, NEW_PULL_REQUEST, ISSUE_STATE_CHANGED, ISSUE_ASSIGNEE_CHANGED,
    PULL_REQUEST_STATE_CHANGED, NEW_COMMENT, NEW_REVIEW_COMMENT, MEMBER_ENROLL_REQUEST,
    PULL_REQUEST_MERGED, ISSUE_REFERRED_FROM_COMMIT, PULL_REQUEST_COMMIT_CHANGED, NEW_COMMIT,
    PULL_REQUEST_REVIEW_STATE_CHANGED, ISSUE_BODY_CHANGED, ISSUE_REFERRED_FROM_PULL_REQUEST,
    REVIEW_THREAD_STATE_CHANGED, ORGANIZATION_MEMBER_ENROLL_REQUEST, COMMENT_UPDATED, ISSUE_MOVED,
    ISSUE_SHARER_CHANGED, ISSUE_LABEL_CHANGED, ISSUE_MILESTONE_CHANGED, POSTING_BODY_CHANGED,
    RESOURCE_DELETED, MEMBER_ENROLL_ACCEPT, ORGANIZATION_MEMBER_ENROLL_ACCEPT
}

/** 리소스 종류이다. 첨부, 관심, 멘션, 알림이 다형 참조에 사용한다. */
enum class ResourceType(val resource: String) {
    ISSUE_POST("issue_post"), ISSUE_ASSIGNEE("issue_assignee"), ISSUE_STATE("issue_state"),
    ISSUE_CATEGORY("issue_category"), ISSUE_MILESTONE("issue_milestone"), ISSUE_LABEL("issue_label"),
    BOARD_POST("board_post"), BOARD_CATEGORY("board_category"), BOARD_NOTICE("board_notice"),
    CODE("code"), MILESTONE("milestone"), WIKI_PAGE("wiki_page"), PROJECT_SETTING("project_setting"),
    SITE_SETTING("site_setting"), USER("user"), USER_AVATAR("user_avatar"), PROJECT("project"),
    ATTACHMENT("attachment"), ISSUE_COMMENT("issue_comment"), NONISSUE_COMMENT("nonissue_comment"),
    LABEL("label"), PROJECT_LABELS("project_labels"), FORK("fork"), COMMIT_COMMENT("code_comment"),
    PULL_REQUEST("pull_request"), COMMIT("commit"), COMMENT_THREAD("comment_thread"),
    REVIEW_COMMENT("review_comment"), ORGANIZATION("organization"), PROJECT_TRANSFER("project_transfer"),
    ISSUE_LABEL_CATEGORY("issue_label_category"), WEBHOOK("webhook"), NOT_A_RESOURCE("")
}

/** 코드 비교 화면에서 변경 전(A)과 변경 후(B)를 구분한다. DB에는 `A`, `B` 한 글자로 저장한다. */
enum class Side { A, B }

/** 코드 리뷰 스레드의 상태이다. */
enum class ThreadState { OPEN, CLOSED }

/** 풀 리퀘스트 커밋의 상태이다. 이전 푸시의 커밋은 PRIOR, 현재 커밋은 CURRENT이다. */
enum class PullRequestCommitState { PRIOR, CURRENT }

/** `property` 테이블의 키 목록이다. */
enum class PropertyName { MAILBOX_LAST_SEEN_UID, MAILBOX_LAST_UID_VALIDITY }
