package com.github.yonaprojects.yona.web

import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.ui.Model

/**
 * 여러 프로젝트에 걸친 목록(내 이슈, 조직 이슈/게시판, 사용자 화면)의 2단 보기에서
 * 복합 선택 키([TwoColumnSelection.Key])를 받아 기존 상세 핸들러로 위임한다.
 *
 * 상세 model을 채우는 일은 프로젝트별 상세 핸들러가 그대로 수행하므로 프로젝트 읽기 권한 확인,
 * 방문 기록, 오류 화면 선택이 단독 상세 페이지와 동일하게 적용된다(이 클래스는 권한 판단을 하지 않는다).
 * 반환값은 상세 핸들러가 돌려주는 뷰 이름이며, 성공했는지는 호출자가 기대 뷰 이름과 비교해 판단한다.
 */
@Component
class CrossProjectDetailResolver(
    private val issueViewController: IssueViewController
) {
    companion object {
        /** 복합 선택 키를 싣는 쿼리 파라미터명. 사용자 화면은 `selected`가 탭 이름이라 겹치지 않게 따로 둔다. */
        const val DETAIL_PARAM = "detail"
        const val ISSUE_TYPE = "issue"
        const val ISSUE_VIEW = "issue/view"
        const val ISSUE_DETAIL_FRAME = "issue-detail"
    }

    /** 키가 없거나 이슈 키가 아니면 프로젝트를 특정할 수 없으므로 제네릭 404(`error/404`)를 돌려준다. */
    fun resolveIssue(key: TwoColumnSelection.Key?, authentication: Authentication?, model: Model): String {
        if (key == null || key.type != ISSUE_TYPE) {
            model.addAttribute("messageKey", "error.forbidden.or.notfound")
            return "error/404"
        }
        return issueViewController.viewIssue(key.owner, key.project, key.number, authentication, model)
    }
}
