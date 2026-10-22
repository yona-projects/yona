package dev.yona.domain.issue

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.TitleHeadRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 제목 머리말 키워드 모음(`title_head`)을 검증한다. 글 제목 맨 앞의 `[머리말]`을 세어 자동 완성에 쓴다.
 *
 * 원본: `app/models/TitleHead.java`, `app/utils/TemplateHelper.scala`(`extractHeaderWordsInBrackets`).
 */
@Transactional
class TitleHeadServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: TitleHeadService
    @Autowired private lateinit var titleHeads: TitleHeadRepository
    @Autowired private lateinit var projects: ProjectRepository

    private fun project(): Project = projects.save(Project(owner = "th", name = "tp${System.nanoTime()}", vcs = "GIT", projectScope = ProjectScope.PUBLIC))

    private fun frequency(project: Project, keyword: String) = titleHeads.findFirstByProjectIdAndHeadKeyword(project.id!!, keyword)?.frequency

    @Test
    fun `제목 앞의 대괄호 머리말을 세어 둔다`() {
        val project = project()
        service.save(project, "[버그][긴급] 로그인 오류")
        service.save(project, "[버그] 다른 오류")
        assertEquals(2, frequency(project, "버그"))
        assertEquals(1, frequency(project, "긴급"))
    }

    @Test
    fun `대괄호가 아니거나 비어 있거나 중간에 있는 것은 세지 않는다`() {
        val project = project()
        service.save(project, "[] 빈 머리말")
        service.save(project, "제목 [중간]")
        service.save(project, "[열림 제목")
        assertNull(frequency(project, ""))
        assertNull(frequency(project, "중간"))
        assertEquals(0, titleHeads.findAll().count { it.project?.id == project.id })
    }

    @Test
    fun `제목을 지우면 하나씩 줄이고 0이 되면 지운다`() {
        val project = project()
        service.save(project, "[버그] 하나"); service.save(project, "[버그] 둘")
        service.delete(project, "[버그] 하나")
        assertEquals(1, frequency(project, "버그"))
        service.delete(project, "[버그] 둘")
        assertNull(frequency(project, "버그"))
        service.delete(project, "[버그] 이미 없음")
    }

    @Test
    fun `검색어가 들어 있는 머리말을 대소문자 없이 찾는다`() {
        val project = project()
        service.save(project, "[Bug] a"); service.save(project, "[Feature] b")
        assertEquals(listOf("Bug"), service.search(project, "bu").map { it.headKeyword })
        assertEquals(2, service.search(project, "").size)
    }
}
