package dev.yona.domain.site

import dev.yona.domain.site.DataType.BOOLEAN
import dev.yona.domain.site.DataType.CLOB
import dev.yona.domain.site.DataType.DATE
import dev.yona.domain.site.DataType.INT
import dev.yona.domain.site.DataType.LONG
import dev.yona.domain.site.DataType.STRING
import dev.yona.domain.site.DataType.STRING_LONG
import dev.yona.domain.site.DataType.TIMESTAMP

/** 데이터 내보내기와 가져오기가 다루는 컬럼의 값 종류이다. */
enum class DataType {
    /** 정수(`long`). 가져올 때 JSON `null`은 0이다. 컬럼의 [DataColumn.nullableLong]이 참이면 SQL NULL이다. */
    LONG,

    /** `int`. 가져올 때 JSON `null`은 0이다. */
    INT,

    /** 문자열. JSON `null`이거나 문자열이 아니면 SQL NULL이다. */
    STRING,

    /** 불린. DB의 NULL은 `false`로 내보낸다. JSON `null`은 `false`이다. */
    BOOLEAN,

    /** 시각. 에폭 밀리초이며 0이거나 JSON `null`이면 SQL NULL이다. */
    TIMESTAMP,

    /** 날짜(`ATTACHMENT.created_date`만). 에폭 밀리초이며 0이거나 JSON `null`이면 SQL NULL이다. */
    DATE,

    /** 긴 문자열. JSON `null`이면 SQL NULL이다. */
    CLOB,

    /** DB는 정수이지만 JSON에는 문자열로 쓰는 컬럼이다(v1.6이 `getString`으로 읽는다). 가져올 때 숫자로 바꿔 넣는다. */
    STRING_LONG
}

/**
 * 가져올 때 다른 테이블의 값으로 채우는 컬럼의 출처이다. v1.6 파일에는 없고 이후 진화가 더한 `NOT NULL` 컬럼에 쓴다(R-130).
 *
 * @property table 값을 읽어 올 테이블 이름(대문자)
 * @property column 그 테이블에서 읽을 컬럼 이름
 * @property via 가져오는 행에서 그 테이블의 `id`를 가리키는 컬럼 이름
 */
data class DerivedColumn(val table: String, val column: String, val via: String)

/**
 * 테이블의 컬럼 하나이다.
 *
 * @property name DB 컬럼 이름(소문자)
 * @property type 값 종류
 * @property key JSON 키. 기본은 [name]이다
 * @property legacyKey 가져올 때 [key]가 없으면 읽는 옛 키이다. `ATTACHMENT.container_type`은 v1.6이 `content_type`으로 썼다(R-56-8)
 * @property nullableLong 가져올 때 JSON `null`을 SQL NULL로 바인딩하는 정수 컬럼이다
 * @property absentNull 가져오는 파일에 이 키가 없을 때 `null`로 채울지이다. 값이 비어 있을 수 있는 추가 개발 컬럼에 쓴다
 * @property absentString 가져오는 파일에 이 키가 없을 때 쓰는 문자열 값이다(`absentBoolean`의 문자열 판이다).
 * @property derivedFrom 이 컬럼은 내보내지 않고, 가져올 때 [DerivedColumn]이 가리키는 테이블에서 채운다. 파일에 이 키가 있어도 쓰지 않는다
 * @property absentInt 가져오는 파일에 이 키가 없을 때 쓰는 정수 값이다.
 * @property absentBoolean 가져오는 파일에 이 키가 없을 때 쓰는 불린 값이다. 추가 개발 항목이 더한 컬럼이 옛(v1.6) 파일에 없어도 가져오게 한다. `null`이면 키가 없는 행은 오류이다
 */
data class DataColumn(val name: String, val type: DataType, val key: String = name, val nullableLong: Boolean = false, val absentBoolean: Boolean? = null, val absentString: String? = null, val absentNull: Boolean = false, val derivedFrom: DerivedColumn? = null, val legacyKey: String? = null, val absentInt: Int? = null)

/**
 * 내보내기와 가져오기의 테이블 하나이다. v1.6 `app/data/exchangers 아래 `DataExchanger` 클래스들`의 `getTable`, `getSelectSql`, `hasSequence`에 해당한다.
 *
 * @property name DB 테이블 이름(대문자). JSON 필드 이름이기도 하다. 사용자 테이블은 `N4USER`이다
 * @property hasSequence 배열 뒤에 `<이름>_SEQ` 값을 함께 쓰는 테이블이다. 조인 테이블 9개는 거짓이다
 * @property columns 컬럼 목록. 내보내기의 컬럼 순서이기도 하다
 */
class DataTable(val name: String, val hasSequence: Boolean, vararg columns: DataColumn) {
    val columns: List<DataColumn> = columns.toList()

    /** SQL에서 쓰는 테이블 이름(소문자)이다. */
    val sqlName: String get() = name.lowercase()

    /** 시퀀스 필드 이름이다. */
    val sequenceName: String get() = name + "_SEQ"
}

/**
 * 내보내고 가져오는 69개 테이블이다(2026-11-03에 `LFS_LOCK`, `ORGANIZATION_OAUTH_APPROVAL`, `ORGANIZATION_OAUTH_POLICY`, `LDAP_SETTINGS`, `OIDC_SSO_SETTINGS`, `SAML2_SSO_SETTINGS` 6개를 더했다). v1.6 `DataService`의 Exchanger 47개에 추가 개발 항목 X-14(D-34)의 연결 테이블 2개(`PULL_REQUEST_ASSIGNEE`, `PULL_REQUEST_ISSUE_LABEL`)와 X-12(D-35)의 리뷰 테이블 `PULL_REQUEST_REVIEW`, X-5(D-36)의 토큰 테이블 `API_TOKEN`, X-4(D-65)의 GPG 공개키 테이블 `GPG_KEY`, X-3(D-66)의 `SSH_KEY`와 `DEPLOY_KEY`, X-2(D-69)의 `TWO_FACTOR_CREDENTIAL`, `TWO_FACTOR_BACKUP_CODE`, `KNOWN_DEVICE`, `SITE_SETTING`, X-1(D-71)의 `OAUTH_CLIENT`, `OAUTH_AUTHORIZATION`, `OAUTH_CONSENT` 등을 더했고 테이블 이름 순서(문자열 비교)로 정렬되어 있다.
 *
 * 엔티티에 있는 컬럼이어도 v1.6 Exchanger가 다루지 않는 것(예: `N4USER.is_guest`, `ISSUE.weight`)은 내보내지 않고 가져올 때 기본값이 된다.
 * 이 목록에 없는 테이블(즐겨찾기, 웹훅, 소셜 로그인 등 14개)은 내보내기와 가져오기에서 빠진다.
 */
object DataTables {

    val all: List<DataTable> = listOf(
        DataTable(
            "API_TOKEN", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("name", STRING),
            DataColumn("token_hash", STRING),
            DataColumn("token_prefix", STRING),
            DataColumn("scopes", STRING),
            DataColumn("is_legacy", BOOLEAN),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("expires_date", TIMESTAMP),
            DataColumn("last_used_date", TIMESTAMP)
        ),
        DataTable(
            "ASSIGNEE", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("project_id", LONG)
        ),
        DataTable(
            "ATTACHMENT", true,
            DataColumn("id", LONG),
            DataColumn("name", STRING),
            DataColumn("hash", STRING),
            DataColumn("container_type", STRING, legacyKey = "content_type"),
            DataColumn("mime_type", STRING),
            DataColumn("size", LONG),
            DataColumn("container_id", STRING_LONG),
            DataColumn("created_date", DATE),
            // v1.6 교환기가 다루지 않던 컬럼이다(R-56-2)
            DataColumn("owner_login_id", STRING, absentNull = true)
        ),
        // 추가 개발 항목 X-34(D-46)의 규칙 묶음이다. v1.6 파일에는 없고, 없는 파일을 가져와도 된다.
        DataTable(
            "AUDIT_CHAIN_HEAD", true,
            DataColumn("id", LONG),
            DataColumn("last_id", LONG),
            DataColumn("pruned_through_id", LONG),
            DataColumn("last_hash", STRING),
            DataColumn("pruned_hash", STRING)
        ),
        DataTable(
            "AUDIT_LOG", true,
            // 감사 로그와 해시 체인(R-56-2). 세 테이블을 함께 가져와야 해시 검증이 이어진다
            DataColumn("id", LONG),
            DataColumn("actor_type", INT),
            DataColumn("outcome", INT),
            DataColumn("actor_id", LONG, nullableLong = true),
            DataColumn("created_at", LONG),
            DataColumn("organization_id", LONG, nullableLong = true),
            DataColumn("project_id", LONG, nullableLong = true),
            DataColumn("redacted_at", LONG, nullableLong = true),
            DataColumn("token_id", LONG, nullableLong = true),
            DataColumn("target_type", STRING),
            DataColumn("content_hash", STRING),
            DataColumn("hash", STRING),
            DataColumn("ip", STRING),
            DataColumn("prev_hash", STRING),
            DataColumn("request_id", STRING),
            DataColumn("action", STRING),
            DataColumn("user_agent", STRING),
            DataColumn("token_scopes", STRING),
            DataColumn("actor_login", STRING),
            DataColumn("target_id", STRING),
            DataColumn("target_label", STRING),
            DataColumn("details", CLOB)
        ),
        DataTable(
            "AUDIT_STREAM_CURSOR", false,
            DataColumn("name", STRING),
            DataColumn("failures", INT),
            DataColumn("last_error_at", LONG, nullableLong = true),
            DataColumn("last_id", LONG),
            DataColumn("last_sent_at", LONG, nullableLong = true),
            DataColumn("last_error", STRING)
        ),
        DataTable(
            "FAVORITE_ISSUE", true,
            DataColumn("id", LONG),
            DataColumn("issue_id", LONG, nullableLong = true),
            DataColumn("user_id", LONG, nullableLong = true)
        ),
        DataTable(
            "FAVORITE_ORGANIZATION", true,
            DataColumn("id", LONG),
            DataColumn("organization_id", LONG, nullableLong = true),
            DataColumn("user_id", LONG, nullableLong = true),
            DataColumn("organization_name", STRING)
        ),
        DataTable(
            "FAVORITE_PROJECT", true,
            DataColumn("id", LONG),
            DataColumn("project_id", LONG, nullableLong = true),
            DataColumn("user_id", LONG, nullableLong = true),
            DataColumn("owner", STRING),
            DataColumn("project_name", STRING)
        ),
        DataTable(
            "ISSUE_SHARER", true,
            DataColumn("id", LONG),
            DataColumn("created", DATE),
            DataColumn("issue_id", LONG, nullableLong = true),
            DataColumn("user_id", LONG, nullableLong = true),
            DataColumn("login_id", STRING)
        ),
        DataTable(
            "POSTING_ISSUE_LABEL", false,
            DataColumn("posting_id", LONG),
            DataColumn("issue_label_id", LONG)
        ),
        DataTable(
            "RECENT_ISSUE", true,
            DataColumn("id", LONG),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("issue_id", LONG, nullableLong = true),
            DataColumn("posting_id", LONG, nullableLong = true),
            DataColumn("user_id", LONG, nullableLong = true),
            DataColumn("title", STRING),
            DataColumn("url", STRING)
        ),
        DataTable(
            "RECENT_PROJECT", true,
            DataColumn("id", LONG),
            DataColumn("project_id", LONG, nullableLong = true),
            DataColumn("user_id", LONG, nullableLong = true),
            DataColumn("owner", STRING),
            DataColumn("project_name", STRING)
        ),
        DataTable(
            "RULESET", true,
            DataColumn("id", LONG),
            DataColumn("project_id", LONG),
            DataColumn("name", STRING),
            DataColumn("enforcement", STRING, absentString = "active"),
            DataColumn("pattern", STRING),
            DataColumn("bypass_admins", BOOLEAN, absentBoolean = false),
            DataColumn("restrict_updates", BOOLEAN, absentBoolean = false),
            DataColumn("restrict_deletions", BOOLEAN, absentBoolean = true),
            DataColumn("block_force_pushes", BOOLEAN, absentBoolean = true),
            DataColumn("require_pull_request", BOOLEAN, absentBoolean = false),
            DataColumn("require_linear_history", BOOLEAN, absentBoolean = false),
            DataColumn("required_approvals", INT),
            DataColumn("require_no_changes_requested", BOOLEAN, absentBoolean = false),
            DataColumn("require_conversation_resolution", BOOLEAN, absentBoolean = false),
            DataColumn("require_signed_commits", BOOLEAN, absentBoolean = false),
            DataColumn("created_date", TIMESTAMP)
        ),
        DataTable(
            "COMMENT_THREAD", true,
            DataColumn("dtype", STRING),
            DataColumn("id", LONG),
            DataColumn("project_id", LONG),
            DataColumn("author_id", LONG, nullableLong = true),
            DataColumn("author_login_id", STRING),
            DataColumn("author_name", STRING),
            DataColumn("state", STRING),
            DataColumn("commit_id", STRING),
            DataColumn("path", STRING),
            DataColumn("start_side", STRING),
            DataColumn("start_line", LONG, nullableLong = true),
            DataColumn("start_column", LONG, nullableLong = true),
            DataColumn("end_side", STRING),
            DataColumn("end_line", LONG, nullableLong = true),
            DataColumn("end_column", LONG, nullableLong = true),
            DataColumn("pull_request_id", LONG, nullableLong = true),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("prev_commit_id", STRING)
        ),
        DataTable(
            "COMMENT_THREAD_N4USER", false,
            DataColumn("comment_thread_id", LONG),
            DataColumn("n4user_id", LONG)
        ),
        DataTable(
            "COMMIT_COMMENT", true,
            DataColumn("id", LONG),
            DataColumn("project_id", LONG, nullableLong = true),
            DataColumn("commit_id", STRING),
            DataColumn("path", STRING),
            DataColumn("line", LONG, nullableLong = true),
            DataColumn("side", STRING),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("author_id", LONG, nullableLong = true),
            DataColumn("author_login_id", STRING),
            DataColumn("author_name", STRING),
            DataColumn("contents", CLOB)
        ),
        DataTable(
            "EMAIL", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("email", STRING),
            DataColumn("token", STRING),
            DataColumn("valid", BOOLEAN)
        ),
        // 추가 개발 항목 X-2(D-69)의 사이트 설정이다. 2단계 인증 강제 방식 같은 값을 담는다
        DataTable(
            "SITE_SETTING", false,
            DataColumn("setting_key", STRING),
            DataColumn("setting_value", STRING)
        ),
        // 추가 개발 항목 X-2(D-69)의 2단계 인증이다. v1.6 파일에는 없고, 없는 파일을 가져와도 된다. 비밀 값은 암호화되어 있으나 사이트 비밀 키(`yona.secret`)가 다르면 읽을 수 없다.
        DataTable(
            "TITLE_HEAD", true,
            DataColumn("id", LONG),
            DataColumn("frequency", INT),
            DataColumn("project_id", LONG, nullableLong = true),
            DataColumn("head_keyword", STRING)
        ),
        DataTable(
            "TWO_FACTOR_CREDENTIAL", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("type", STRING),
            DataColumn("name", STRING),
            DataColumn("secret_encrypted", STRING),
            DataColumn("confirmed", BOOLEAN),
            DataColumn("last_used_step", LONG, nullableLong = true),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("last_used_date", TIMESTAMP),
            DataColumn("credential_id", STRING),
            DataColumn("public_key", STRING),
            DataColumn("sign_count", LONG, nullableLong = true)
        ),
        DataTable(
            "TWO_FACTOR_BACKUP_CODE", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("code_hash", STRING),
            DataColumn("used_date", TIMESTAMP),
            DataColumn("created_date", TIMESTAMP)
        ),
        DataTable(
            "KNOWN_DEVICE", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("token_hash", STRING),
            DataColumn("name", STRING),
            DataColumn("ip", STRING),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("last_used_date", TIMESTAMP),
            DataColumn("expires_date", TIMESTAMP)
        ),
        // 추가 개발 항목 X-3(D-66)의 배포 키이다. v1.6 파일에는 없고, 없는 파일을 가져와도 된다.
        DataTable(
            "DEPLOY_KEY", true,
            DataColumn("id", LONG),
            DataColumn("project_id", LONG),
            DataColumn("name", STRING),
            DataColumn("fingerprint", STRING),
            DataColumn("key_type", STRING),
            DataColumn("public_key", CLOB),
            DataColumn("read_only", BOOLEAN),
            DataColumn("created_by_id", LONG, nullableLong = true),
            DataColumn("created_date", TIMESTAMP)
        ),
        // 추가 개발 항목 X-3(D-66)의 사용자 SSH 공개키이다. v1.6 파일에는 없고, 없는 파일을 가져와도 된다.
        DataTable(
            "SSH_KEY", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("name", STRING),
            DataColumn("fingerprint", STRING),
            DataColumn("key_type", STRING),
            DataColumn("public_key", CLOB),
            DataColumn("created_date", TIMESTAMP)
        ),
        // 추가 개발 항목 X-4(D-65)의 GPG 공개키이다. v1.6 파일에는 없고, 없는 파일을 가져와도 된다. 하위 키 행은 `parent_id`로 주 키 행을 가리킨다.
        DataTable(
            "GPG_KEY", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("parent_id", LONG, nullableLong = true),
            DataColumn("name", STRING),
            DataColumn("fingerprint", STRING),
            DataColumn("key_id", STRING),
            DataColumn("public_key", CLOB),
            DataColumn("emails", STRING),
            DataColumn("can_sign", BOOLEAN),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("expires_date", TIMESTAMP)
        ),
        DataTable(
            "ISSUE", true,
            DataColumn("id", LONG),
            DataColumn("title", STRING),
            DataColumn("body", CLOB),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("num_of_comments", INT),
            DataColumn("milestone_id", LONG, nullableLong = true),
            DataColumn("author_id", LONG),
            DataColumn("author_login_id", STRING),
            DataColumn("author_name", STRING),
            DataColumn("state", INT),
            DataColumn("project_id", LONG),
            DataColumn("assignee_id", LONG, nullableLong = true),
            DataColumn("number", LONG),
            DataColumn("updated_date", TIMESTAMP),
            DataColumn("due_date", TIMESTAMP),
            // v1.6 교환기가 다루지 않던 컬럼이다(R-56-1). v1.6 파일에는 없으므로 없으면 기본값으로 채운다
            DataColumn("is_draft", BOOLEAN, absentBoolean = false),
            DataColumn("weight", INT, absentInt = 0),
            DataColumn("parent_id", LONG, nullableLong = true, absentNull = true),
            DataColumn("updated_by_author_id", LONG, nullableLong = true, absentNull = true),
            DataColumn("history", CLOB, absentNull = true)
        ),
        DataTable(
            "ISSUE_COMMENT", true,
            DataColumn("id", LONG),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("author_id", LONG),
            DataColumn("author_login_id", STRING),
            DataColumn("author_name", STRING),
            DataColumn("issue_id", LONG),
            DataColumn("contents", CLOB),
            // v1.6 파일에는 없고 이후 진화 9가 `NOT NULL`로 더한 컬럼이다. 내보내지 않고 가져올 때 이슈의 프로젝트로 채운다(R-130).
            DataColumn("project_id", LONG, derivedFrom = DerivedColumn("ISSUE", "project_id", "issue_id")),
            // v1.6 교환기가 다루지 않던 컬럼이다(R-56-2)
            DataColumn("parent_comment_id", LONG, nullableLong = true, absentNull = true)
        ),
        DataTable(
            "ISSUE_COMMENT_VOTER", false,
            DataColumn("issue_comment_id", LONG),
            DataColumn("user_id", LONG)
        ),
        DataTable(
            "ISSUE_EVENT", true,
            DataColumn("id", LONG),
            DataColumn("created", TIMESTAMP),
            DataColumn("sender_login_id", STRING),
            DataColumn("issue_id", LONG, nullableLong = true),
            DataColumn("event_type", STRING),
            DataColumn("old_value", CLOB),
            DataColumn("new_value", CLOB),
            DataColumn("sender_email", STRING)
        ),
        DataTable(
            "ISSUE_ISSUE_LABEL", false,
            DataColumn("issue_id", LONG),
            DataColumn("issue_label_id", LONG)
        ),
        DataTable(
            "ISSUE_LABEL", true,
            DataColumn("id", LONG),
            DataColumn("color", STRING),
            DataColumn("name", STRING),
            DataColumn("project_id", LONG, nullableLong = true),
            DataColumn("category_id", LONG)
        ),
        DataTable(
            "ISSUE_LABEL_CATEGORY", true,
            DataColumn("id", LONG),
            DataColumn("project_id", LONG),
            DataColumn("name", STRING),
            DataColumn("is_exclusive", BOOLEAN)
        ),
        DataTable(
            "ISSUE_VOTER", false,
            DataColumn("issue_id", LONG),
            DataColumn("user_id", LONG)
        ),
        DataTable(
            "LABEL", true,
            DataColumn("id", LONG),
            DataColumn("name", STRING),
            DataColumn("category", STRING)
        ),
        DataTable(
            "MENTION", true,
            DataColumn("id", LONG),
            DataColumn("resource_type", STRING),
            DataColumn("resource_id", STRING),
            DataColumn("user_id", LONG, nullableLong = true)
        ),
        DataTable(
            "MILESTONE", true,
            DataColumn("id", LONG),
            DataColumn("title", STRING),
            DataColumn("due_date", TIMESTAMP),
            DataColumn("contents", CLOB),
            DataColumn("state", INT),
            DataColumn("project_id", LONG, nullableLong = true)
        ),
        DataTable(
            "N4USER", true,
            DataColumn("id", LONG),
            DataColumn("name", STRING),
            DataColumn("login_id", STRING),
            DataColumn("password", STRING),
            DataColumn("password_salt", STRING),
            DataColumn("email", STRING),
            DataColumn("remember_me", BOOLEAN),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("state", STRING),
            DataColumn("last_state_modified_date", TIMESTAMP),
            DataColumn("lang", STRING),
            // v1.6 교환기가 다루지 않던 컬럼이다(R-56-1)
            DataColumn("is_guest", BOOLEAN, absentBoolean = false),
            DataColumn("english_name", STRING, absentNull = true),
            DataColumn("token", STRING, absentNull = true)
        ),
        DataTable(
            "NOTIFICATION_EVENT", true,
            DataColumn("id", LONG),
            DataColumn("title", STRING),
            DataColumn("sender_id", LONG, nullableLong = true),
            DataColumn("created", TIMESTAMP),
            DataColumn("resource_type", STRING),
            DataColumn("resource_id", STRING),
            DataColumn("event_type", STRING),
            DataColumn("old_value", CLOB),
            DataColumn("new_value", CLOB)
        ),
        DataTable(
            "NOTIFICATION_EVENT_N4USER", false,
            DataColumn("notification_event_id", LONG),
            DataColumn("n4user_id", LONG)
        ),
        DataTable(
            "NOTIFICATION_MAIL", true,
            DataColumn("id", LONG),
            DataColumn("notification_event_id", LONG, nullableLong = true)
        ),
        // 추가 개발 항목 X-1(D-71)의 OAuth 인가이다. 토큰 값은 해시만 있다. 기본 키가 글자(UUID)라 시퀀스가 없다
        DataTable(
            "OAUTH_AUTHORIZATION", false,
            DataColumn("id", STRING),
            DataColumn("client_row_id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("grant_type", STRING),
            DataColumn("authorized_scopes", STRING),
            DataColumn("attributes", STRING),
            DataColumn("state", STRING),
            DataColumn("code_hash", STRING),
            DataColumn("code_issued_date", TIMESTAMP),
            DataColumn("code_expires_date", TIMESTAMP),
            DataColumn("code_metadata", STRING),
            DataColumn("access_token_hash", STRING),
            DataColumn("access_token_issued_date", TIMESTAMP),
            DataColumn("access_token_expires_date", TIMESTAMP),
            DataColumn("access_token_metadata", STRING),
            DataColumn("access_token_scopes", STRING),
            DataColumn("refresh_token_hash", STRING),
            DataColumn("refresh_token_issued_date", TIMESTAMP),
            DataColumn("refresh_token_expires_date", TIMESTAMP),
            DataColumn("refresh_token_metadata", STRING),
            DataColumn("previous_refresh_token_hash", STRING),
            DataColumn("created_date", TIMESTAMP)
        ),
        // 추가 개발 항목 X-1(D-71)의 OAuth 앱이다. v1.6 파일에는 없고, 없는 파일을 가져와도 된다. 비밀 값은 해시이다
        DataTable(
            "OAUTH_CLIENT", true,
            DataColumn("id", LONG),
            DataColumn("client_id", STRING),
            DataColumn("name", STRING),
            DataColumn("description", STRING),
            DataColumn("home_url", STRING),
            DataColumn("redirect_uris", STRING),
            DataColumn("confidential", BOOLEAN),
            DataColumn("secret_hash", STRING),
            DataColumn("allowed_scopes", STRING),
            DataColumn("registration", STRING),
            DataColumn("owner_type", STRING),
            DataColumn("owner_id", LONG),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("updated_date", TIMESTAMP),
            DataColumn("fetched_date", TIMESTAMP),
            DataColumn("expires_date", TIMESTAMP)
        ),
        // 추가 개발 항목 X-1(D-71)의 OAuth 승인이다. 사용자가 앱에 허용한 범위이다
        DataTable(
            "OAUTH_CONSENT", true,
            DataColumn("id", LONG),
            DataColumn("client_row_id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("scopes", STRING),
            DataColumn("created_date", TIMESTAMP)
        ),
        DataTable(
            "ORGANIZATION", true,
            DataColumn("id", LONG),
            DataColumn("name", STRING),
            DataColumn("descr", STRING),
            DataColumn("created", TIMESTAMP)
        ),
        DataTable(
            "ORGANIZATION_USER", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("organization_id", LONG),
            DataColumn("role_id", LONG)
        ),
        DataTable(
            "ORIGINAL_EMAIL", true,
            DataColumn("id", LONG),
            DataColumn("message_id", STRING),
            DataColumn("resource_type", STRING),
            DataColumn("resource_id", STRING),
            DataColumn("handled_date", TIMESTAMP)
        ),
        DataTable(
            "POSTING", true,
            DataColumn("id", LONG),
            DataColumn("title", STRING),
            DataColumn("body", CLOB),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("num_of_comments", INT),
            DataColumn("author_id", LONG),
            DataColumn("author_login_id", STRING),
            DataColumn("author_name", STRING),
            DataColumn("project_id", LONG),
            DataColumn("number", LONG),
            DataColumn("notice", BOOLEAN),
            DataColumn("updated_date", TIMESTAMP),
            DataColumn("readme", BOOLEAN),
            // v1.6 교환기가 다루지 않던 컬럼이다(R-56-2)
            DataColumn("parent_id", LONG, nullableLong = true, absentNull = true),
            DataColumn("updated_by_author_id", LONG, nullableLong = true, absentNull = true),
            DataColumn("history", CLOB, absentNull = true)
        ),
        DataTable(
            "POSTING_COMMENT", true,
            DataColumn("id", LONG),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("author_id", LONG, nullableLong = true),
            DataColumn("author_login_id", STRING),
            DataColumn("author_name", STRING),
            DataColumn("posting_id", LONG, nullableLong = true),
            DataColumn("contents", CLOB),
            // 이후 진화 9가 `NOT NULL`로 더한 컬럼이다. 내보내지 않고 가져올 때 글의 프로젝트로 채운다(ISSUE_COMMENT와 같다, R-56-2)
            DataColumn("project_id", LONG, derivedFrom = DerivedColumn("POSTING", "project_id", "posting_id")),
            DataColumn("parent_comment_id", LONG, nullableLong = true, absentNull = true)
        ),
        DataTable(
            "PROJECT", true,
            DataColumn("id", LONG),
            DataColumn("name", STRING),
            DataColumn("overview", STRING),
            DataColumn("vcs", STRING),
            DataColumn("siteurl", STRING),
            DataColumn("owner", STRING),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("last_issue_number", LONG),
            DataColumn("last_posting_number", LONG),
            DataColumn("original_project_id", LONG, nullableLong = true),
            DataColumn("last_pushed_date", TIMESTAMP),
            DataColumn("is_using_reviewer_count", BOOLEAN),
            DataColumn("is_dismissing_stale_approvals", BOOLEAN, absentBoolean = true),
            // 병합 방식 설정(X-28, D-41): 이 열이 없는 v1.6 파일은 병합 커밋만 허용으로 읽는다
            DataColumn("allow_merge_commit", BOOLEAN, absentBoolean = true),
            DataColumn("allow_squash_merge", BOOLEAN, absentBoolean = false),
            DataColumn("allow_rebase_merge", BOOLEAN, absentBoolean = false),
            DataColumn("default_merge_method", STRING, absentString = "merge"),
            DataColumn("default_reviewer_count", LONG),
            DataColumn("organization_id", LONG, nullableLong = true),
            DataColumn("project_scope", STRING),
            // v1.6 교환기가 다루지 않던 컬럼이다(R-56-2)
            DataColumn("is_code_accessible_member_only", BOOLEAN, absentBoolean = false),
            DataColumn("previous_name", STRING, absentNull = true),
            DataColumn("previous_owner_login_id", STRING, absentNull = true),
            DataColumn("previous_name_changed_time", LONG, nullableLong = true, absentNull = true)
        ),
        DataTable(
            "PROJECT_LABEL", false,
            DataColumn("project_id", LONG),
            DataColumn("label_id", LONG)
        ),
        DataTable(
            "PROJECT_MENU_SETTING", true,
            DataColumn("id", LONG),
            DataColumn("project_id", LONG),
            DataColumn("code", BOOLEAN),
            DataColumn("issue", BOOLEAN),
            DataColumn("pull_request", BOOLEAN),
            DataColumn("review", BOOLEAN),
            DataColumn("milestone", BOOLEAN),
            DataColumn("board", BOOLEAN),
            DataColumn("wiki", BOOLEAN, absentBoolean = false)
        ),
        DataTable(
            "PROJECT_PUSHED_BRANCH", true,
            DataColumn("id", LONG),
            DataColumn("pushed_date", TIMESTAMP),
            DataColumn("name", STRING),
            DataColumn("project_id", LONG)
        ),
        DataTable(
            "PROJECT_TRANSFER", true,
            DataColumn("id", LONG),
            DataColumn("sender_id", STRING_LONG),
            DataColumn("destination", STRING),
            DataColumn("project_id", LONG),
            DataColumn("requested", TIMESTAMP),
            DataColumn("confirm_key", STRING),
            DataColumn("accepted", BOOLEAN),
            DataColumn("new_project_name", STRING)
        ),
        DataTable(
            "PROJECT_USER", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("project_id", LONG),
            DataColumn("role_id", LONG)
        ),
        DataTable(
            "PROJECT_VISITATION", true,
            DataColumn("id", LONG),
            DataColumn("visited", TIMESTAMP),
            DataColumn("project_id", LONG),
            DataColumn("recently_visited_projects_id", LONG)
        ),
        DataTable(
            "PROPERTY", true,
            DataColumn("id", LONG),
            DataColumn("name", STRING),
            DataColumn("value", STRING)
        ),
        DataTable(
            "PULL_REQUEST", true,
            DataColumn("id", LONG),
            DataColumn("title", STRING),
            DataColumn("body", CLOB),
            DataColumn("to_project_id", LONG),
            DataColumn("from_project_id", LONG),
            DataColumn("to_branch", STRING),
            DataColumn("from_branch", STRING),
            DataColumn("contributor_id", LONG, nullableLong = true),
            DataColumn("receiver_id", LONG, nullableLong = true),
            DataColumn("created", TIMESTAMP),
            DataColumn("updated", TIMESTAMP),
            DataColumn("received", TIMESTAMP),
            DataColumn("state", INT),
            DataColumn("last_commit_id", STRING),
            DataColumn("merged_commit_id_from", STRING),
            DataColumn("merged_commit_id_to", STRING),
            DataColumn("number", LONG),
            DataColumn("is_conflict", BOOLEAN),
            DataColumn("rebase_blocked", STRING, absentNull = true),
            DataColumn("is_merging", BOOLEAN)
        ),
        // 추가 개발 항목 X-14(D-34)가 더한 연결 테이블이다. v1.6 파일에는 없고, 없는 파일을 가져와도 된다.
        DataTable(
            "PULL_REQUEST_ASSIGNEE", false,
            DataColumn("pull_request_id", LONG),
            DataColumn("user_id", LONG)
        ),
        DataTable(
            "PULL_REQUEST_COMMIT", true,
            DataColumn("id", LONG),
            DataColumn("pull_request_id", LONG),
            DataColumn("commit_id", STRING),
            DataColumn("commit_short_id", STRING),
            DataColumn("commit_message", CLOB),
            DataColumn("created", TIMESTAMP),
            DataColumn("author_date", TIMESTAMP),
            DataColumn("author_email", STRING),
            DataColumn("state", STRING)
        ),
        DataTable(
            "PULL_REQUEST_EVENT", true,
            DataColumn("id", LONG),
            DataColumn("pull_request_id", LONG),
            DataColumn("created", TIMESTAMP),
            DataColumn("sender_login_id", STRING),
            DataColumn("event_type", STRING),
            DataColumn("new_value", CLOB),
            DataColumn("old_value", CLOB)
        ),
        DataTable(
            "PULL_REQUEST_ISSUE_LABEL", false,
            DataColumn("pull_request_id", LONG),
            DataColumn("issue_label_id", LONG)
        ),
        DataTable(
            "PULL_REQUEST_REVIEW", true,
            DataColumn("id", LONG),
            DataColumn("pull_request_id", LONG),
            DataColumn("author_id", LONG),
            DataColumn("author_login_id", STRING),
            DataColumn("verdict", STRING),
            DataColumn("contents", CLOB),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("dismissed_date", TIMESTAMP),
            DataColumn("dismissed_by_login_id", STRING),
            DataColumn("dismissed_reason", CLOB)
        ),
        // 추가 개발 항목 X-27(D-37)이 더한 연결 테이블이다. 대기 중인 리뷰 요청만 담는다. v1.6 파일에는 없고, 없는 파일을 가져와도 된다.
        DataTable(
            "PULL_REQUEST_REVIEW_REQUEST", false,
            DataColumn("pull_request_id", LONG),
            DataColumn("user_id", LONG)
        ),
        DataTable(
            "PULL_REQUEST_REVIEWERS", false,
            DataColumn("pull_request_id", LONG),
            DataColumn("user_id", LONG)
        ),
        DataTable(
            "RECENTLY_VISITED_PROJECTS", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG)
        ),
        DataTable(
            "REVIEW_COMMENT", true,
            DataColumn("id", LONG),
            DataColumn("contents", CLOB),
            DataColumn("created_date", TIMESTAMP),
            DataColumn("author_id", LONG, nullableLong = true),
            DataColumn("author_login_id", STRING),
            DataColumn("author_name", STRING),
            DataColumn("thread_id", LONG, nullableLong = true),
            // 임시 저장 리뷰의 `pending_date`는 공개되지 않은 댓글이라 내보내지 않는다(X-26). 고친 시각만 더한다(R-56-2)
            DataColumn("edited_date", TIMESTAMP, absentNull = true)
        ),
        DataTable(
            "ROLE", true,
            DataColumn("id", LONG),
            DataColumn("name", STRING),
            DataColumn("active", BOOLEAN)
        ),
        DataTable(
            "SITE_ADMIN", true,
            DataColumn("id", LONG),
            DataColumn("admin_id", LONG, nullableLong = true)
        ),
        DataTable(
            "UNWATCH", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG, nullableLong = true),
            DataColumn("resource_type", STRING),
            DataColumn("resource_id", STRING)
        ),
        DataTable(
            "USER_ENROLLED_ORGANIZATION", false,
            DataColumn("user_id", LONG),
            DataColumn("organization_id", LONG)
        ),
        DataTable(
            "USER_ENROLLED_PROJECT", false,
            DataColumn("user_id", LONG),
            DataColumn("project_id", LONG)
        ),
        DataTable(
            "USER_PROJECT_NOTIFICATION", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG),
            DataColumn("project_id", LONG),
            DataColumn("notification_type", STRING),
            DataColumn("allowed", BOOLEAN)
        ),
        DataTable(
            "USER_SETTING", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG, nullableLong = true),
            DataColumn("login_default_page", STRING)
        ),
        DataTable(
            "WATCH", true,
            DataColumn("id", LONG),
            DataColumn("user_id", LONG, nullableLong = true),
            DataColumn("resource_type", STRING),
            DataColumn("resource_id", STRING)
        ),
        // 추가 개발 항목 X-8(D-79)의 Git LFS 파일 잠금이다. v1.6 파일에는 없고, 없는 파일을 가져와도 된다. 객체 파일 자체는 데이터 폴더(`repo/lfs`)에 있어 이 파일에 들어가지 않는다
        DataTable(
            "LFS_LOCK", true,
            DataColumn("id", LONG),
            DataColumn("project_id", LONG),
            DataColumn("path", STRING),
            DataColumn("path_hash", STRING),
            DataColumn("owner_user_id", LONG),
            DataColumn("ref_name", STRING),
            DataColumn("locked_date", TIMESTAMP)
        ),
        // 추가 개발 항목 X-16(D-82)의 LDAP 설정이다(사이트에 한 행). 없는 파일을 가져오면 지금 설정을 그대로 둔다
        DataTable(
            "LDAP_SETTINGS", false,
            DataColumn("id", LONG),
            DataColumn("enabled", BOOLEAN),
            DataColumn("protocol", STRING),
            DataColumn("host", STRING),
            DataColumn("port", STRING),
            DataColumn("base_dn", STRING),
            DataColumn("distinguished_name_postfix", STRING),
            DataColumn("login_property", STRING),
            DataColumn("display_name_property", STRING),
            DataColumn("user_name_property", STRING),
            DataColumn("email_property", STRING),
            DataColumn("use_email_base_login", BOOLEAN),
            DataColumn("fallback_to_local_login", BOOLEAN),
            DataColumn("english_name_attribute_name", STRING),
            DataColumn("updated_date", TIMESTAMP)
        ),
        // 추가 개발 항목 X-16(D-80)의 사내 OIDC 설정이다(사이트에 한 행). 클라이언트 비밀 값은 암호화되어 있으나 사이트 비밀 키(`yona.secret`)가 다르면 읽을 수 없다. 없는 파일을 가져오면 지금 설정을 그대로 둔다
        DataTable(
            "OIDC_SSO_SETTINGS", false,
            DataColumn("id", LONG),
            DataColumn("enabled", BOOLEAN),
            DataColumn("display_name", STRING),
            DataColumn("issuer_uri", STRING),
            DataColumn("client_id", STRING),
            DataColumn("client_secret", STRING),
            DataColumn("scopes", STRING),
            DataColumn("email_claim", STRING),
            DataColumn("name_claim", STRING),
            DataColumn("login_id_claim", STRING),
            DataColumn("auto_create", BOOLEAN),
            DataColumn("link_by_email", BOOLEAN),
            DataColumn("allow_unverified_email", BOOLEAN),
            DataColumn("name_sync", BOOLEAN),
            DataColumn("initiate_enabled", BOOLEAN),
            DataColumn("clock_skew_seconds", INT),
            DataColumn("discovery_json", CLOB),
            DataColumn("discovery_checked_date", TIMESTAMP),
            DataColumn("updated_date", TIMESTAMP)
        ),
        // 추가 개발 항목 X-39(D-78)의 조직 OAuth 앱 승인 정책과 승인이다. 없는 파일을 가져오면 조직이 있는데 이 테이블이 없는 경우 남아 있던 행을 비운다
        DataTable(
            "ORGANIZATION_OAUTH_APPROVAL", true,
            DataColumn("id", LONG),
            DataColumn("organization_id", LONG),
            DataColumn("client_row_id", LONG),
            DataColumn("state", STRING),
            DataColumn("decided_by", LONG, nullableLong = true),
            DataColumn("decided_date", TIMESTAMP)
        ),
        DataTable(
            "ORGANIZATION_OAUTH_POLICY", false,
            DataColumn("organization_id", LONG),
            DataColumn("restricted", BOOLEAN),
            DataColumn("updated_date", TIMESTAMP),
            DataColumn("updated_by", LONG, nullableLong = true)
        ),
        // 추가 개발 항목 X-16(D-80)의 SAML 2 설정이다(사이트에 한 행). SP 비밀 키는 암호화되어 있으나 사이트 비밀 키(`yona.secret`)가 다르면 읽을 수 없다. 없는 파일을 가져오면 지금 설정을 그대로 둔다
        DataTable(
            "SAML2_SSO_SETTINGS", false,
            DataColumn("id", LONG),
            DataColumn("enabled", BOOLEAN),
            DataColumn("display_name", STRING),
            DataColumn("idp_entity_id", STRING),
            DataColumn("idp_sso_url", STRING),
            DataColumn("idp_sso_binding", STRING),
            DataColumn("idp_certificates", CLOB),
            DataColumn("sp_entity_id", STRING),
            DataColumn("name_id_format", STRING),
            DataColumn("email_attribute", STRING),
            DataColumn("name_attribute", STRING),
            DataColumn("login_id_attribute", STRING),
            DataColumn("auto_create", BOOLEAN),
            DataColumn("link_by_email", BOOLEAN),
            DataColumn("allow_unverified_email", BOOLEAN),
            DataColumn("name_sync", BOOLEAN),
            DataColumn("initiate_enabled", BOOLEAN),
            DataColumn("sign_requests", BOOLEAN),
            DataColumn("clock_skew_seconds", INT),
            DataColumn("sp_private_key", CLOB),
            DataColumn("sp_certificate", CLOB),
            DataColumn("sp_previous_private_key", CLOB),
            DataColumn("sp_previous_certificate", CLOB),
            DataColumn("sp_key_created_date", TIMESTAMP),
            DataColumn("updated_date", TIMESTAMP)
        ),
        DataTable(
            "WEBHOOK", true,
            DataColumn("id", LONG),
            DataColumn("git_push", BOOLEAN),
            DataColumn("webhook_type", INT),
            DataColumn("created_at", TIMESTAMP),
            DataColumn("project_id", LONG, nullableLong = true),
            DataColumn("secret", STRING),
            DataColumn("payload_url", STRING)
        ),
        DataTable(
            "WEBHOOK_THREAD", true,
            DataColumn("id", LONG),
            DataColumn("created_at", TIMESTAMP),
            DataColumn("webhook_id", LONG, nullableLong = true),
            DataColumn("resource_type", STRING),
            DataColumn("thread_id", STRING),
            DataColumn("resource_id", STRING)
        )
    ).sortedBy { it.name }

    private val byName: Map<String, DataTable> = all.associateBy { it.name }

    /** 이름(대소문자 무시)에 해당하는 테이블이다. 없으면 `null`이다. */
    fun find(name: String): DataTable? = byName[name.uppercase()]

    /** `<이름>_SEQ` 필드 이름에 해당하는 테이블이다. 없으면 `null`이다. */
    fun findBySequenceName(name: String): DataTable? = all.firstOrNull { it.hasSequence && it.sequenceName.equals(name, ignoreCase = true) }
}
