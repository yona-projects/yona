package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

// 사용자가 실제 브라우저에서 재현: 프로젝트 페이지(코드 브라우저 등)의 GNB "+" 드롭다운(새 이슈/새
// 프로젝트/새 그룹 만들기 링크)을 열면 글자가 안 보임(흰 배경에 흰 글자). legacy에는 없는 yona 전용
// 회귀 — git blame(TASK-0020, 최초 도입 커밋)으로 legacy target/web/.../yobi.css에는 존재하지 않는
// 규칙임을 확인.
//
// 원인: `.gnb-outer.project-header .gnb-inner .gnb-usermenu li a`/`.gnb-usermenu-dropdown a` 규칙이
// (project.header는 프로젝트/조직 페이지에서 GNB가 반투명 어두운 배경일 때 상단 메뉴 글자를 하얗게
// 보이게 하려는 의도) `!important`로 `color:#FFF`를 강제하는데, 셀렉터가 깊이 제한 없는 `li a`라서
// "+" 버튼이 여는 `.dropdown-menu`(흰 배경 팝업)의 `<li><a>`까지 전부 걸려버려 흰 배경에 흰 글자가
// 된다. `.dropdown-menu` 안의 링크만 원래 색으로 되돌리는, 더 구체적인 `!important` 규칙을 추가해
// 수정.
class GnbUserMenuDropdownColorSpec : DescribeSpec({
    val css = File("src/main/resources/static/stylesheets/yobi.css").readText()

    describe("GNB '+' 드롭다운(gnb-usermenu-dropdown) 글자색 회귀") {
        it("project-header 스코프의 .gnb-usermenu li a 흰색 강제 규칙이 여전히 존재해야 한다(상단 메뉴 자체 색상은 유지)") {
            css shouldContain ".gnb-outer.project-header .gnb-inner .gnb-usermenu li a"
        }

        it("그 흰색 강제보다 더 구체적인 선택자로 .dropdown-menu 안의 링크 색을 되돌리는 규칙이 있어야 한다") {
            css shouldContain ".gnb-outer.project-header .gnb-inner .gnb-usermenu-dropdown .dropdown-menu li a"
        }
    }
})
