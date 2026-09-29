package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// P3-75 — 게시판 목록의 2단 보기를 iframe/pageslide(yona.twoColumnMode.js)에서 Turbo Frames로
// 전환한 계약(이슈 목록 P3-74와 동일한 패턴): ?selected=<글번호>로 선택 상태를 표현하고,
// `Turbo-Frame: post-detail` 헤더가 오면 목록 조회를 건너뛰고 상세 fragment만 렌더링한다.
@Transactional
class BoardListTurboFrameRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val postingRepository: PostingRepository,
    private val entityManagerFactory: EntityManagerFactory
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    private fun seed(prefix: String): Project {
        val author = userRepository.save(User(loginId = "$prefix-author", name = "게시판작성자", email = "$prefix-author@yona.io"))
        val project = projectRepository.save(Project(name = "$prefix-proj", owner = "$prefix-owner", projectScope = ProjectScope.PUBLIC))
        postingRepository.save(
            Posting(title = "Other listed post", body = "Other body", project = project, number = 1L,
                authorId = author.id, authorLoginId = author.loginId, authorName = author.name)
        )
        postingRepository.save(
            Posting(title = "Turbo selected post", body = "Turbo selected detail body", project = project, number = 2L,
                authorId = author.id, authorLoginId = author.loginId, authorName = author.name)
        )
        return project
    }

    init {
        describe("게시판 목록 2단 보기 Turbo Frames") {
            it("목록 행은 선택 URL과 상세 URL을 함께 갖고, iframe/pageslide 스크립트를 더 이상 로드하지 않는다") {
                val project = seed("bturbo-list")

                val html = mockMvc.perform(get("/${project.owner}/${project.name}/posts"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select("turbo-frame#post-list").size shouldBe 1
                doc.select("turbo-frame#post-detail").size shouldBe 1
                val link = doc.select("#post-list a.title[data-selection-url\$='selected=2']").first()!!
                link.attr("data-detail-url") shouldBe "/${project.owner}/${project.name}/post/2"
                html shouldNotContain "yona.twoColumnMode.js"
                html shouldContain "/javascripts/service/yona.board.Turbo.js"
            }

            it("헤더 없이 ?selected=를 주면 목록과 상세를 모두 렌더링한다(직접 URL/reload/no-JS)") {
                val project = seed("bturbo-full")

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/posts").queryParam("selected", "2"))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "<!DOCTYPE html>"
                body shouldContain "id=\"post-list\""
                body shouldContain "id=\"post-detail\""
                body shouldContain "Other listed post"
                body shouldContain "Turbo selected detail body"
            }

            it("Turbo-Frame: post-detail 요청은 상세 fragment만 반환하고 목록/전체 문서를 그리지 않는다") {
                val project = seed("bturbo-frame")

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/posts").queryParam("selected", "2")
                        .header("Turbo-Frame", "post-detail")
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "id=\"post-detail\""
                body shouldContain "id=\"post-detail-content\""
                body shouldContain "Turbo selected detail body"
                body shouldNotContain "<!DOCTYPE html>"
                body shouldNotContain "id=\"post-list\""
                body shouldNotContain "Other listed post"
            }

            it("Turbo-Frame: post-detail 요청은 정상 단일 게시글 상세보다 많은 SQL을 실행하지 않는다") {
                val project = seed("bturbo-sql")
                val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
                statistics.setStatisticsEnabled(true)

                statistics.clear()
                mockMvc.perform(get("/${project.owner}/${project.name}/post/2")).andExpect(status().isOk)
                val normalDetailSqlCount = statistics.prepareStatementCount

                statistics.clear()
                mockMvc.perform(
                    get("/${project.owner}/${project.name}/posts").queryParam("selected", "2")
                        .header("Turbo-Frame", "post-detail")
                ).andExpect(status().isOk)
                val turboFrameSqlCount = statistics.prepareStatementCount

                turboFrameSqlCount shouldBeLessThanOrEqualTo normalDetailSqlCount
            }

            it("존재하지 않는 선택 번호는 notfound 뷰로 응답한다") {
                val project = seed("bturbo-miss")

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/posts").queryParam("selected", "999"))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                body shouldNotContain "Turbo selected detail body"
            }
        }
    }
}
