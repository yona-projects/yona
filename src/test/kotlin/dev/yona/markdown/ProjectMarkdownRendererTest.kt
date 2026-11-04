package dev.yona.markdown

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 프로젝트 문맥의 마크다운 렌더링 파이프라인과 결과 캐시의 v1.6 동작을 검증한다.
 *
 * 원본: `app/utils/Markdown.java:60-260, 311-376`.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-project-markdown"])
class ProjectMarkdownRendererTest : AbstractIntegrationTest() {

    @Autowired private lateinit var renderer: ProjectMarkdownRenderer
    @Autowired private lateinit var plain: MarkdownRenderer
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    @BeforeEach
    fun english() {
        LocaleContextHolder.setLocale(Locale.ENGLISH)
    }

    @AfterEach
    fun clean() {
        File("build/tmp/it-project-markdown").deleteRecursively()
        LocaleContextHolder.resetLocaleContext()
    }

    private fun project(): Project {
        // 캐시는 원문으로 구별하므로 테스트마다 다른 이름을 써서 서로 영향을 주지 않게 한다.
        val project = projects.save(Project(owner = "pm${++sequence}x${System.nanoTime()}", name = "pr$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        return project
    }

    private fun issue(project: Project, number: Long, title: String) =
        issues.save(Issue(project = project, number = number, title = title, state = State.OPEN))

    private fun text(html: String) = Jsoup.parse(html).body().text()

    @Test
    fun `글 안의 이슈 번호를 링크로 바꾼다`() {
        val project = project()
        issue(project, 1, "첫 이슈")
        val html = renderer.render("고쳤습니다 #1", project)
        assertTrue(html.contains("class=\"issueLink\""), html)
        assertTrue(text(html).contains("#1.첫 이슈"), html)
    }

    @Test
    fun `자바스크립트 링크를 막는다`() {
        val html = renderer.render("[x](javascript:alert(1))", project())
        assertTrue(!html.contains("javascript"), html)
    }

    @Test
    fun `이슈 주소 링크는 번호와 제목 문구로 바뀐다`() {
        val project = project()
        issue(project, 3, "주소 이슈")
        val html = renderer.render("[보기](/${project.owner}/${project.name}/issue/3)", project)
        assertTrue(text(html).contains("#3.주소 이슈"), html)
    }

    @Test
    fun `README의 상대 경로와 이슈 참조를 함께 처리한다`() {
        val project = project()
        issue(project, 2, "README 이슈")
        GitTestSupport.commitFiles(repositories, project, mapOf("README.md" to "x".toByteArray()))
        val html = renderer.renderFileInReadme(" ![로고](./a.png) 참고 #2", project)
        assertTrue(html.contains("src=\"/${project.owner}/${project.name}/files/master/a.png\""), html)
        assertTrue(text(html).contains("#2.README 이슈"), html)
    }

    @Test
    fun `코드 브라우저의 마크다운 파일은 이미지 경로만 바꾼다`() {
        val project = project()
        val html = renderer.renderFileInCodeBrowser("![로고](./a.png) [문서](./b.md)", project)
        assertTrue(html.contains("src=\"/${project.owner}/${project.name}/files/master/a.png\""), html)
        assertTrue(html.contains("href=\"./b.md\""), html)
    }

    @Test
    fun `같은 원문을 다시 렌더링하면 이전에 저장된 결과를 돌려주고 새 결과로 갱신한다 (v1 6 동작 재현)`() {
        val project = project()
        val issue = issue(project, 3, "예전 제목")
        val source = "[보기](/${project.owner}/${project.name}/issue/3)"
        assertTrue(text(renderer.render(source, project)).contains("#3.예전 제목"))

        issue.title = "새 제목"
        issues.save(issue)
        // 두 번째 호출은 캐시된 이전 결과를 돌려주면서 캐시를 새로 만든다 (refactoring-backlog R-16).
        assertTrue(text(renderer.render(source, project)).contains("#3.예전 제목"))
        assertTrue(text(renderer.render(source, project)).contains("#3.새 제목"))
    }

    @Test
    fun `자동 링크는 캐시를 거치지 않고 매번 새로 만든다`() {
        val project = project()
        val issue = issue(project, 1, "예전")
        val source = "참조 #1 ${System.nanoTime()}"
        assertTrue(text(renderer.render(source, project)).contains("#1.예전"))
        issue.title = "새것"
        issues.save(issue)
        assertTrue(text(renderer.render(source, project)).contains("#1.새것"))
    }

    @Test
    fun `프로젝트 없는 렌더링과 캐시를 나누어 쓴다 (R-16, v1 6은 같은 키를 함께 썼다)`() {
        val project = project()
        issue(project, 3, "공유")
        val source = "[보기](/${project.owner}/${project.name}/issue/3)"
        // 프로젝트 문맥이 없는 렌더링이 먼저 같은 원문을 저장해도 문맥 있는 렌더링의 첫 결과가 바르다. v1.6은 그 값을 그대로 돌려주었다
        plain.render(source)
        assertTrue(text(renderer.render(source, project)).contains("#3.공유"))
        assertTrue(text(renderer.render(source, project)).contains("#3.공유"))
    }
}
