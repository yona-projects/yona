package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// P3-52 항목3 — legacy common/yobi.CommentForm.js(빈 값 제출 방지, Ctrl+Shift+Enter 단축 제출,
// 페이지 이탈 시 beforeunload 경고, localStorage 임시저장)의 기능적 동치를 #comment-form에
// 이식했다. 원본 파일을 그대로 <script src>로 로드하지는 않는다 — 그 파일의 onSubmitCommentForm은
// event.preventDefault() 후 300ms 뒤 실제 네이티브 form.submit()을 다시 스케줄링하는데,
// #comment-form은 이미 P3-48/50에서 검증된 AJAX 제출 핸들러($(document).on('submit', ...))로
// 전환돼 있어 그 흐름과 공존시키면 이중 제출 버그가 난다(사용자 결정, 2026-09-11) — 그래서 이미
// 검증된 AJAX 핸들러 쪽에 4가지 기능을 직접 재구현했다. yobi.CommentForm.js 파일 자체는
// 삭제하지 않고 계속 미참조 상태로 남겨둔다(사용자 결정).
@Transactional
class CommentFormGuardsWiringTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("이슈 상세 화면 #comment-form의 빈값 제출 방지/단축 제출/이탈 경고/임시저장 가드") {
            it("가드 스크립트(빈값 체크, Ctrl+Shift+Enter, beforeunload, temporarySaveHandler)가 모두 렌더링돼야 한다") {
                val author = userRepository.save(User(loginId = "cfguard-author", name = "가드테스트작성자", email = "cfguard-author@yona.io"))
                val project = projectRepository.save(Project(name = "cfguard-proj", owner = "cfguard-owner", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.save(
                    Issue(title = "가드 테스트 이슈", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                )
                val authorDetails = YonaUserDetails(
                    id = author.id!!,
                    loginId = author.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/${issue.number}").with(user(authorDetails)))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                // 빈 값 제출 방지 (post.comment.empty 메시지 키의 root 값)
                body shouldContain "Comment should not be empty."
                // Ctrl(⌘)+Shift+Enter 단축 제출
                body shouldContain "isSubmitCombo"
                body shouldContain "shiftKey"
                // beforeunload 이탈 경고 (common.comment.beforeunload.confirm 메시지 키의 root 값)
                body shouldContain "Would you like to exit this page without submitting comment?"
                // localStorage 임시저장(기존 전역 헬퍼 재사용)
                body shouldContain "temporarySaveHandler("
                body shouldContain "removeCurrentPageTemprarySavedContent("
            }
        }
    }
}
