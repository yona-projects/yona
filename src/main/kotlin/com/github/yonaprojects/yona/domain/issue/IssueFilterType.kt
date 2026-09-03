package com.github.yonaprojects.yona.domain.issue

// yona models/enumeration/IssueFilterType.java 대응 (P2-52) — UserApi.getIssuesByUser()의
// filter 쿼리 파라미터 값(assigned/created/mentioned/favorite/all)과 1:1 매핑된다.
enum class IssueFilterType(val filterValue: String) {
    ASSIGNED("assigned"),
    CREATED("created"),
    MENTIONED("mentioned"),
    FAVORITE("favorite"),
    ALL("all");

    companion object {
        fun getValue(value: String): IssueFilterType =
            entries.find { it.filterValue == value }
                ?: throw IllegalArgumentException("No matching issue filter type found for [$value]")
    }
}
