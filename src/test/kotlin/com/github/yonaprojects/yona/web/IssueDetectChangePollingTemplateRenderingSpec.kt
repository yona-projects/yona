package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.support.sha1Hex
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// P3-52 항목2 — legacy issue/view.scala.html이 로드하던 service/yona.detectChange.js(폴링으로
// 다른 사용자의 본문/댓글 변경을 감지해 "새로고침" 안내를 띄우는 UX 기능) 포팅. 백엔드 API
// (POST /api/projects/{projectId}/issues/{number}/detectChange)는 이미 존재하므로(P1-102),
// 이 스펙은 issue/view.html이 그 폴링을 실제로 시작하는 데 필요한 초기 상태값(hidden input)과
// 스크립트 로드/호출 배선만 검증한다.
@Transactional
class IssueDetectChangePollingTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    init {
        describe("이슈 상세 화면의 변경 감지 폴링(detectChange) 배선") {
            // 이름이 고유한(detectchg- 접두) 프로젝트/유저만 만들고, 클래스에 붙은 @Transactional이
            // 각 테스트 종료 시 롤백하므로 다른 스펙의 데이터를 건드리는 전역 deleteAll()은 쓰지 않는다.

            it("이슈 본문 체크섬/댓글 수/최종 수정일 hidden input과 폴링 스크립트가 실제로 렌더링돼야 한다") {
                val author = userRepository.save(User(loginId = "detectchg-author", name = "폴링작성자", email = "detectchg-author@yona.io"))
                val project = projectRepository.save(Project(name = "detectchg-proj", owner = "detectchg-owner", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.save(
                    Issue(
                        title = "폴링 테스트 이슈", body = "이슈 본문 내용", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId, createdDate = java.time.Instant.now()
                    )
                )
                issueCommentRepository.save(
                    IssueComment(contents = "댓글1", issue = issue, authorLoginId = author.loginId)
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/1"))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                val doc = Jsoup.parse(body)

                doc.select("#issueBodyChecksum").attr("value") shouldBe sha1Hex("이슈 본문 내용")
                doc.select("#numOfComments").attr("value") shouldBe "1"
                doc.select("#issueUpdateDate").attr("value").isNotBlank() shouldBe true

                body shouldContain "/javascripts/lib/favico/favico.min.js"
                body shouldContain "/javascripts/service/yona.detectChange.js"
                body shouldContain "detectPageChange("
                body shouldContain "/api/projects/${project.id}/issues/1/detectChange"
            }
        }
    }
}
