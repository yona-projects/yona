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

/** 가입 신청 같은 요청의 처리 상태이다 (v1.6 `models.enumeration.RequestState`). */
enum class RequestState { REQUEST, CANCEL, ACCEPT, REJECT }

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
    RESOURCE_DELETED, MEMBER_ENROLL_ACCEPT, ORGANIZATION_MEMBER_ENROLL_ACCEPT,
    // v1.6에 없는 추가 항목(X-14, D-34): 풀 리퀘스트의 담당자와 라벨이 바뀌었다
    PULL_REQUEST_ASSIGNEE_CHANGED, PULL_REQUEST_LABEL_CHANGED
}

/**
 * 이벤트 종류의 설명 메시지 키이다. v1.6 `EventType.getDescr()`는 이 키를 요청 언어로 번역한 문구를 돌려주므로,
 * 문구가 필요하면 `MessageSource`로 번역한다(외부 API의 `eventDescription`, 알림 문구의 기본값).
 */
val EventType.descrKey: String
    get() = when (this) {
        EventType.NEW_ISSUE -> "notification.type.new.issue"
        EventType.NEW_POSTING -> "notification.type.new.posting"
        EventType.NEW_PULL_REQUEST -> "notification.type.new.pullrequest"
        EventType.ISSUE_STATE_CHANGED -> "notification.type.issue.state.changed"
        EventType.ISSUE_ASSIGNEE_CHANGED -> "notification.type.issue.assignee.changed"
        EventType.PULL_REQUEST_STATE_CHANGED -> "notification.type.pullrequest.state.changed"
        EventType.NEW_COMMENT -> "notification.type.new.comment"
        EventType.NEW_REVIEW_COMMENT -> "notification.type.new.simple.comment"
        EventType.MEMBER_ENROLL_REQUEST -> "notification.type.member.enroll"
        EventType.PULL_REQUEST_MERGED -> "notification.type.pullrequest.merged"
        EventType.ISSUE_REFERRED_FROM_COMMIT -> "notification.type.issue.referred.from.commit"
        EventType.PULL_REQUEST_COMMIT_CHANGED -> "notification.type.pullrequest.commit.changed"
        EventType.NEW_COMMIT -> "notification.type.new.commit"
        EventType.PULL_REQUEST_REVIEW_STATE_CHANGED -> "notification.type.pullrequest.review.action.changed"
        EventType.ISSUE_BODY_CHANGED -> "notification.type.issue.body.changed"
        EventType.ISSUE_REFERRED_FROM_PULL_REQUEST -> "notification.type.issue.referred.from.pullrequest"
        EventType.REVIEW_THREAD_STATE_CHANGED -> "notification.type.review.state.changed"
        EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST -> "notification.organization.type.member.enroll"
        EventType.COMMENT_UPDATED -> "notification.type.comment.updated"
        EventType.ISSUE_MOVED -> "notification.type.issue.is.moved"
        EventType.ISSUE_SHARER_CHANGED -> "notification.type.issue.sharer.changed"
        EventType.ISSUE_LABEL_CHANGED -> "notification.type.issue.label.changed"
        EventType.ISSUE_MILESTONE_CHANGED -> "notification.type.milestone.changed"
        EventType.POSTING_BODY_CHANGED -> "notification.type.posting.body.changed"
        EventType.RESOURCE_DELETED -> "notification.type.resource.deleted"
        EventType.MEMBER_ENROLL_ACCEPT, EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT -> "notification.member.enroll.accept"
        EventType.PULL_REQUEST_ASSIGNEE_CHANGED -> "notification.type.pullrequest.assignee.changed"
        EventType.PULL_REQUEST_LABEL_CHANGED -> "notification.type.pullrequest.label.changed"
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

/** 풀 리퀘스트 리뷰 동작이다. 리뷰를 마쳤거나(`DONE`) 취소했다(`CANCEL`) (v1.6 `PullRequestReviewAction`). */
enum class PullRequestReviewAction {
    DONE, CANCEL;

    /** 반대 동작이다. */
    val opposite: PullRequestReviewAction get() = if (this == DONE) CANCEL else DONE
}

/** 풀 리퀘스트 커밋의 상태이다. 이전 푸시의 커밋은 PRIOR, 현재 커밋은 CURRENT이다. */
enum class PullRequestCommitState { PRIOR, CURRENT }

/** `property` 테이블의 키 목록이다. */
enum class PropertyName { MAILBOX_LAST_SEEN_UID, MAILBOX_LAST_UID_VALIDITY }

/**
 * 리소스에 대해 수행하는 연산의 종류이다. 권한 검사의 입력이다 (v1.6 `models.enumeration.Operation`).
 *
 * @property value 화면과 URL에서 쓰는 소문자 이름
 */
enum class Operation(val value: String) {
    READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"),
    CLOSE("close"), WATCH("watch"), LEAVE("leave"), ASSIGN_ISSUE("assign_issue");

    companion object {
        /** 이름으로 연산을 찾는다. 알 수 없는 이름은 [READ]이다 (v1.6 `Operation.getValue`). */
        fun fromValue(value: String?): Operation = entries.firstOrNull { it.value == value } ?: READ
    }
}

/**
 * 새로 만들어지는 것(새 글, 새 이슈, 새 풀 리퀘스트, 새 댓글)이면 `true`이다 (v1.6 `EventType.isCreating`).
 * 이 알림 메일은 리소스 자체를 메시지 식별자로 가져 이후 메일이 같은 대화로 묶인다. 새 커밋은 프로젝트를 갱신하는 것으로 보아 제외한다.
 */
val EventType.isCreating: Boolean
    get() = this == EventType.NEW_ISSUE || this == EventType.NEW_POSTING || this == EventType.NEW_PULL_REQUEST ||
        this == EventType.NEW_COMMENT || this == EventType.NEW_REVIEW_COMMENT

/** 알림 설정 화면에서 이벤트 종류를 나열하는 순서이다 (v1.6 `EventType.getOrder`). 번호가 작을수록 앞에 나온다. */
val EventType.order: Int
    get() = when (this) {
        EventType.NEW_ISSUE -> 1
        EventType.NEW_POSTING -> 2
        EventType.NEW_PULL_REQUEST -> 3
        EventType.ISSUE_STATE_CHANGED -> 4
        EventType.ISSUE_ASSIGNEE_CHANGED -> 5
        EventType.PULL_REQUEST_STATE_CHANGED -> 6
        EventType.NEW_COMMENT -> 7
        EventType.NEW_REVIEW_COMMENT -> 8
        EventType.MEMBER_ENROLL_REQUEST -> 9
        EventType.PULL_REQUEST_MERGED -> 10
        EventType.ISSUE_REFERRED_FROM_COMMIT -> 11
        EventType.PULL_REQUEST_COMMIT_CHANGED -> 12
        EventType.NEW_COMMIT -> 13
        EventType.PULL_REQUEST_REVIEW_STATE_CHANGED -> 14
        EventType.ISSUE_BODY_CHANGED -> 17
        EventType.ISSUE_REFERRED_FROM_PULL_REQUEST -> 16
        EventType.REVIEW_THREAD_STATE_CHANGED -> 18
        EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST -> 19
        EventType.COMMENT_UPDATED -> 20
        EventType.ISSUE_MOVED -> 21
        EventType.ISSUE_SHARER_CHANGED -> 22
        EventType.ISSUE_LABEL_CHANGED -> 23
        EventType.ISSUE_MILESTONE_CHANGED -> 24
        EventType.POSTING_BODY_CHANGED -> 25
        EventType.RESOURCE_DELETED -> 26
        EventType.MEMBER_ENROLL_ACCEPT -> 27
        EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT -> 28
        EventType.PULL_REQUEST_ASSIGNEE_CHANGED -> 29
        EventType.PULL_REQUEST_LABEL_CHANGED -> 30
    }

/** 알림 설정 화면에 나열하는 이벤트 종류이다. 모든 종류를 [order] 순서로 놓는다 (v1.6 `EventType.notiTypes`). */
val notiTypes: List<EventType> = EventType.entries.sortedBy { it.order }

/**
 * 풀 리퀘스트 리뷰의 판정이다(추가 개발 항목 X-12, `decisions.md` D-35). GitHub의 리뷰 상태 가운데 제출할 수 있는 세 가지이며 열거형 이름 그대로 저장한다.
 *
 * - [APPROVED]: 승인(Approve). 병합 요건의 승인 수에 든다
 * - [CHANGES_REQUESTED]: 변경 요청(Request changes). 프로젝트가 리뷰어 수를 켠 경우 병합을 막는다
 * - [COMMENTED]: 댓글만(Comment)
 */
enum class ReviewVerdict { APPROVED, CHANGES_REQUESTED, COMMENTED }
