package com.github.yonaprojects.yona.domain.apitoken

import com.github.yonaprojects.yona.domain.enumeration.ResourceType

// yona-wiki P3-02 설계 결정(2026-08-28) — 토큰 발급 UI/스코프 판정은 기존 ResourceType(33종, 알림·감사
// 로그와 공유)을 그대로 재사용하되, GitHub Fine-grained PAT처럼 사용자에게는 대분류 단위로만 노출한다.
// 신규 축을 따로 만들지 않고 매핑 테이블로 재사용하기로 한 결정의 근거는 계획 문서 "권한 스코프
// 카테고리 — 미결정 사항 확정 필요" 절 참고.
enum class ApiTokenScopeGroup {
    ISSUES,
    PULL_REQUESTS,
    CODE,
    BOARD,
    WIKI,
    WEBHOOKS,
    ADMINISTRATION,
    USERS
}

// ResourceType.NOT_A_RESOURCE는 어느 그룹에도 속하지 않는다(실제 리소스가 아니므로) — null 반환은
// "스코프 판정 불가 = 항상 거부"로 이어지는 안전한 기본값이다(ApiTokenAuthorizer 참고).
fun ResourceType.toApiTokenScopeGroup(): ApiTokenScopeGroup? {
    return when (this) {
        ResourceType.ISSUE_POST,
        ResourceType.ISSUE_ASSIGNEE,
        ResourceType.ISSUE_STATE,
        ResourceType.ISSUE_CATEGORY,
        ResourceType.ISSUE_MILESTONE,
        ResourceType.ISSUE_LABEL,
        ResourceType.ISSUE_COMMENT,
        ResourceType.ISSUE_LABEL_CATEGORY,
        ResourceType.MILESTONE -> ApiTokenScopeGroup.ISSUES

        ResourceType.PULL_REQUEST,
        ResourceType.COMMIT_COMMENT,
        ResourceType.COMMENT_THREAD,
        ResourceType.REVIEW_COMMENT -> ApiTokenScopeGroup.PULL_REQUESTS

        ResourceType.CODE,
        ResourceType.COMMIT,
        ResourceType.FORK -> ApiTokenScopeGroup.CODE

        ResourceType.BOARD_POST,
        ResourceType.BOARD_CATEGORY,
        ResourceType.BOARD_NOTICE,
        ResourceType.NONISSUE_COMMENT -> ApiTokenScopeGroup.BOARD

        ResourceType.WIKI_PAGE -> ApiTokenScopeGroup.WIKI

        ResourceType.WEBHOOK -> ApiTokenScopeGroup.WEBHOOKS

        ResourceType.PROJECT_SETTING,
        ResourceType.SITE_SETTING,
        ResourceType.PROJECT,
        ResourceType.PROJECT_LABELS,
        ResourceType.LABEL,
        ResourceType.PROJECT_TRANSFER,
        ResourceType.ORGANIZATION,
        ResourceType.ATTACHMENT -> ApiTokenScopeGroup.ADMINISTRATION

        ResourceType.USER,
        ResourceType.USER_AVATAR -> ApiTokenScopeGroup.USERS

        ResourceType.NOT_A_RESOURCE -> null
    }
}
