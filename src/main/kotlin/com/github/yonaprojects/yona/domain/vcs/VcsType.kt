package com.github.yonaprojects.yona.domain.vcs

// project.vcs는 여전히 Project.kt의 순수 String 필드로
// 유지한다(전면 enum 전환은 범위 밖). "VCS 전환" 화면(legacy부터 이어진 GIT<->SUBVERSION
// 2지선다 토글 UI)이 3종으로 늘어난 뒤에도 그대로 동작하도록, "다음 VCS"를 순환(cycle)으로 계산하는
// 로직만 여기 한 곳에 모은다 — ProjectViewController(미리보기 GET)와 ProjectServiceImpl(실제 변경
// POST)가 동일한 계산을 공유해야 화면에 보여준 대상과 실제로 바뀌는 대상이 어긋나지 않는다.
private val VCS_CYCLE = listOf("GIT", "SUBVERSION", "MERCURIAL")

fun nextVcsInCycle(currentVcs: String?): String {
    val normalized = when (currentVcs?.uppercase()) {
        "SVN" -> "SUBVERSION"
        "HG" -> "MERCURIAL"
        null -> "GIT"
        else -> currentVcs.uppercase()
    }
    val index = VCS_CYCLE.indexOf(normalized)
    val nextIndex = if (index == -1) 0 else (index + 1) % VCS_CYCLE.size
    return VCS_CYCLE[nextIndex]
}
