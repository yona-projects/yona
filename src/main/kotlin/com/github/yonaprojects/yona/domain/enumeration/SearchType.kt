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
    // `yona search prs` 대응. PR 자체를 색인하는 통합검색 SearchType이 원래 없었다.
    PULL_REQUEST("pull_request");

    companion object {
        fun getValue(value: String): SearchType {
            return values().find { it.type == value } ?: NA
        }
    }
}
