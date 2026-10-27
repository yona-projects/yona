package dev.yona.markdown

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 렌더링한 HTML 안의 이슈 주소 링크를 `#번호.제목` 형태로 바꾸는 동작을 검증한다.
 *
 * 원본: `app/utils/Markdown.java:133-209` (`transformIssueLink`, `extractIssueLink`).
 */
@Transactional
@TestPropertySource(properties = ["yona.hostname=yona.test"])
class IssueLinkTransformerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var transformer: IssueLinkTransformer
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository

    private var sequence = 0

    @BeforeEach
    fun english() {
        LocaleContextHolder.setLocale(Locale.ENGLISH)
    }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC) =
        projects.save(Project(owner = "it${++sequence}", name = "p$sequence", projectScope = scope, vcs = "GIT"))

    private fun issue(project: Project, number: Long, title: String = "제목", state: State = State.OPEN) =
        issues.save(Issue(project = project, number = number, title = title, state = state))

    private fun doc(html: String) = org.jsoup.Jsoup.parse(transformer.transform(html)).body()

    @Test
    fun `상대 이슈 주소 링크는 번호와 제목 문구로 바뀐다`() {
        val project = project()
        issue(project, 3, "로그인 오류")
        val link = doc("<a href=\"/${project.owner}/${project.name}/issue/3\">보기</a>").selectFirst("a")!!
        assertEquals("#3.로그인 오류", link.ownText())
        assertTrue(link.hasClass("issueLink"))
        assertEquals("Open", link.selectFirst("span.issue-state.open")?.text())
        assertEquals(" noreferrer", link.attr("rel"))
    }

    @Test
    fun `주소에 조각이 있으면 문구 뒤에 붙인다`() {
        val project = project()
        issue(project, 4, "조각")
        val link = doc("<a href=\"/${project.owner}/${project.name}/issue/4#comment-9\">x</a>").selectFirst("a")!!
        assertEquals("#4.조각#comment-9", link.ownText())
    }

    @Test
    fun `같은 호스트의 절대 주소는 링크 문구가 주소와 같을 때만 바뀐다`() {
        val project = project()
        issue(project, 5, "절대")
        val url = "http://yona.test/${project.owner}/${project.name}/issue/5"
        assertEquals("#5.절대", doc("<a href=\"$url\">$url</a>").selectFirst("a")!!.ownText())
        assertEquals("다른 문구", doc("<a href=\"$url\">다른 문구</a>").selectFirst("a")!!.ownText())
    }

    @Test
    fun `다른 호스트의 링크는 건드리지 않는다`() {
        val html = "<a href=\"http://other.example/o/p/issue/1\">http://other.example/o/p/issue/1</a>"
        val link = doc(html).selectFirst("a")!!
        assertEquals("http://other.example/o/p/issue/1", link.text())
        assertEquals("", link.attr("rel"))
    }

    @Test
    fun `읽을 수 없는 이슈는 문구를 바꾸지 않고 이후 링크도 처리하지 않는다`() {
        val hidden = project(ProjectScope.PRIVATE)
        issue(hidden, 1, "비밀")
        val open = project()
        issue(open, 2, "공개")
        val body = doc(
            "<a href=\"/${hidden.owner}/${hidden.name}/issue/1\">비밀 링크</a>" +
                "<a href=\"/${open.owner}/${open.name}/issue/2\">공개 링크</a>"
        )
        val links = body.select("a")
        assertEquals("비밀 링크", links[0].text())
        // v1.6는 권한이 없는 링크를 만나면 반복을 멈춘다 (refactoring-backlog R-15).
        assertEquals("공개 링크", links[1].text())
    }

    @Test
    fun `없는 이슈의 링크는 문구를 그대로 두고 계속 처리한다`() {
        val project = project()
        issue(project, 2, "있음")
        val links = doc(
            "<a href=\"/${project.owner}/${project.name}/issue/99\">없는 링크</a>" +
                "<a href=\"/${project.owner}/${project.name}/issue/2\">있는 링크</a>"
        ).select("a")
        assertEquals("없는 링크", links[0].text())
        assertEquals("#2.있음", links[1].ownText())
    }

    @Test
    fun `이슈가 아닌 상대 링크에는 noreferrer만 붙는다`() {
        val link = doc("<a href=\"/some/path\">x</a>").selectFirst("a")!!
        assertEquals("x", link.text())
        assertEquals(" noreferrer", link.attr("rel"))
    }

    @Test
    fun `링크가 없으면 내용이 그대로이다`() {
        assertEquals("<p>안녕</p>", transformer.transform("<p>안녕</p>"))
    }

    @Test
    fun `줄바꿈 태그 앞뒤에 줄바꿈이나 들여쓰기를 넣지 않는다 (jsoup 1_8_3 출력과 같다)`() {
        val html = "<p>a<br>b</p>"
        assertEquals(html, transformer.transform(html))
    }
}
