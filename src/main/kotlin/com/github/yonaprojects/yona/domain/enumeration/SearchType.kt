package com.github.yonaprojects.yona.domain.enumeration

enum class SearchType(val type: String) {
    AUTO("auto"),
    NA("not available"),
    USER("user"),
    PROJECT("project"),
    ISSUE("issue"),
    POST("post"),
    MILESTONE("milestone"),
    ISSUE_COMMENT("issue_comment"),
    POST_COMMENT("post_comment"),
    REVIEW("review"),
    // yona-wiki P3-02 Step8.6 항목3(2026-09-01, 우선순위 3위) — `yona search prs` 대응. PR 자체를
    // 색인하는 통합검색 SearchType이 원래 없었다(5라운드가 이 갭을 발견해 이월).
    PULL_REQUEST("pull_request");

    companion object {
        fun getValue(value: String): SearchType {
            return values().find { it.type == value } ?: NA
        }
    }
}
