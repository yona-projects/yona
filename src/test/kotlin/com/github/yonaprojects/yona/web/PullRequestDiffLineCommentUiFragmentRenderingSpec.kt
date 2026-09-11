package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.vcs.FileDiff
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.RawText
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.context.WebApplicationContext
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.WebContext
import org.thymeleaf.web.servlet.JakartaServletWebApplication
import java.util.Locale

// P3-52 항목4 — Twirl-vs-Thymeleaf 렌더링 감사 중 "code/diff.html에는 있던 diff 줄 클릭 -> 새
// 리뷰 댓글 작성 폼 UI가 pullrequest/*.html(PR 코드리뷰 changes 탭)에는 없다"는 격차가 발견됐다
// (기존 리뷰 스레드는 partial_diff_comment_on_line으로 잘 보이지만, 새 스레드를 "시작"하는 클릭
// UI 자체가 없었음 — 백엔드 POST .../pullRequest/{id}/comments는 이미 codeRange를 받을 수
// 있어 프론트엔드 배선만 빠져 있었다). 실제 git bare 저장소로 diff를 만들지 않고, FileDiff를
// 직접 구성해 partial_filediff 프래그먼트 하나만 실제 Thymeleaf 엔진으로 렌더링해 검증한다
// (BranchItemFragmentRenderingSpec과 동일한 패턴).
class PullRequestDiffLineCommentUiFragmentRenderingSpec @Autowired constructor(
    private val templateEngine: ITemplateEngine,
    private val wac: WebApplicationContext
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    init {
        describe("P3-52: partial_filediff는 각 diff 줄에 클릭 가능한 add-comment-btn-cell(path/side/line 포함)을 렌더링해야 한다") {
            it("ADD 라인에 path=diff.pathB, side=B, line 값이 담긴 add-comment-btn-cell이 렌더링돼야 한다") {
                val servletContext = wac.servletContext
                val webApplication = JakartaServletWebApplication.buildApplication(servletContext)
                val request = MockHttpServletRequest(servletContext)
                val response = MockHttpServletResponse()
                val webExchange = webApplication.buildExchange(request, response)

                val diff = FileDiff().apply {
                    changeType = DiffEntry.ChangeType.ADD
                    pathB = "src/Test.kt"
                    b = RawText("line one\nline two\n".toByteArray(Charsets.UTF_8))
                    isBinaryB = false
                }
                val project = Project(id = 1L, owner = "diffui-owner", name = "diffui-proj", vcs = "GIT")

                val variables = mapOf<String, Any?>(
                    "diff" to diff,
                    "threads" to emptyList<Any>(),
                    "project" to project,
                    "projectA" to project,
                    "projectB" to project
                )
                val context = WebContext(webExchange, Locale.KOREAN, variables)

                val html = templateEngine.process("pullrequest/partial_filediff", setOf("filediff"), context)
                val doc = Jsoup.parse(html)

                val cells = doc.select("td.add-comment-btn-cell")
                cells.size shouldBe 2

                val firstRow = cells.first()!!.closest("tr")!!
                firstRow.attr("data-path") shouldBe "src/Test.kt"
                firstRow.attr("data-side") shouldBe "B"
                firstRow.attr("data-line") shouldBe "1"
            }
        }
    }
}
