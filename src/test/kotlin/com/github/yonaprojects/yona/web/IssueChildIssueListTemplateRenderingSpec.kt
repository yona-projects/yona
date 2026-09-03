package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository

// yona issue/partial_view_child.scala.html(#134)/partial_view_childIssueList.scala.html(#135)/
// partial_view_childIssueListOnly.scala.html(#136) 대응 (그룹7 TASK-0256). 이슈 상세화면
// (issue/view.html)의 "하위이슈" 영역(부모 헤더 + 진행률 바 + 초안/오픈/클로즈 하위이슈 목록)이
// 실제 Thymeleaf 렌더링으로 정확히 나타나는지 확인한다.
@Transactional
class IssueChildIssueListTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    init {
        describe("이슈 상세화면 하위이슈 영역 렌더링") {
            it("부모 이슈 화면에 진행률 바와 오픈/클로즈 하위이슈가 실제로 렌더링돼야 한다") {
                val author = userRepository.save(User(loginId = "tmpl-child-author", name = "작성자", email = "tmpl-child-author@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-child-proj", owner = "tmpl-child-owner", projectScope = ProjectScope.PUBLIC))

                val parent = issueRepository.save(
                    Issue(title = "부모 이슈", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN)
                )
                issueRepository.save(
                    Issue(title = "열린 하위이슈", body = "본문", project = project, number = 2L, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN, parent = parent)
                )
                issueRepository.save(
                    Issue(title = "닫힌 하위이슈", body = "본문", project = project, number = 3L, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.CLOSED, parent = parent)
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/1"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "child-issues"
                body shouldContain "parent-issue"
                body shouldContain "열린 하위이슈"
                body shouldContain "닫힌 하위이슈"
                body shouldContain "upload-progress"
            }

            it("다른 사용자의 초안 하위이슈는 노출되지 않아야 한다") {
                val author = userRepository.save(User(loginId = "tmpl-child-author2", name = "작성자2", email = "tmpl-child-author2@yona.io"))
                val otherAuthor = userRepository.save(User(loginId = "tmpl-child-other", name = "다른작성자", email = "tmpl-child-other@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-child-proj2", owner = "tmpl-child-owner2", projectScope = ProjectScope.PUBLIC))

                val parent = issueRepository.save(
                    Issue(title = "부모 이슈2", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN)
                )
                issueRepository.save(
                    Issue(title = "열린 하위이슈2", body = "본문", project = project, number = 2L, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN, parent = parent)
                )
                issueRepository.save(
                    Issue(title = "타인의 초안 하위이슈", body = "본문", project = project, number = 3L, authorId = otherAuthor.id, authorLoginId = otherAuthor.loginId, authorName = otherAuthor.name, state = State.DRAFT, isDraft = true, parent = parent)
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/1"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "열린 하위이슈2"
                body shouldNotContain "타인의 초안 하위이슈"
            }
        }
    }
}
