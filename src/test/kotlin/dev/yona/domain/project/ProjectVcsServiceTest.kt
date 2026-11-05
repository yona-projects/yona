package dev.yona.domain.project

import dev.yona.domain.board.PostingRepository
import dev.yona.repository.ProjectReadmeService
import dev.yona.repository.RepositoryService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * 저장소 종류 변경이 실패했을 때를 검증한다. 원본: `app/controllers/ProjectApp.java:721-740`, `app/models/Project.java:658-670`.
 *
 * v1.6 `changeVCS`는 트랜잭션이 없어 README 표시 해제와 포크 연결 끊기가 저장 즉시 반영되고, 기존 저장소를 먼저 지운 뒤 새 저장소를 만들다 실패하면
 * 저장소만 지워진 중간 상태로 남았다([refactoring-backlog.md] R-74). 지금은 변경을 트랜잭션으로 묶고 새 저장소를 먼저 만든 뒤 기존 저장소를 지운다.
 * 새 저장소 생성이 실패하면 기존 저장소는 그대로이고 DB 변경은 되돌아간다(여기서는 트랜잭션이 걸려 있는지와 호출 순서를 확인한다).
 */
class ProjectVcsServiceTest {

    private val projects = mock(ProjectRepository::class.java)
    private val postings = mock(PostingRepository::class.java)
    private val postingService = mock(dev.yona.domain.board.PostingService::class.java)
    private val readme = mock(ProjectReadmeService::class.java)
    private val repositories = mock(RepositoryService::class.java)
    private val service = ProjectVcsService(projects, postings, postingService, readme, repositories, mock(dev.yona.domain.audit.AuditRecorder::class.java))

    @Test
    fun `변경은 트랜잭션으로 묶여 있다 (R-74)`() {
        assertNotNull(ProjectVcsService::class.java.getMethod("change", Project::class.java).getAnnotation(Transactional::class.java))
    }

    @Test
    fun `새 저장소 생성이 실패하면 기존 저장소를 지우지 않고 종류도 되돌린다 (R-74, v1_6은 저장소만 지워졌다)`() {
        val project = Project(id = 1L, owner = "o", name = "p", vcs = "GIT")
        `when`(readme.read(project)).thenReturn(null)
        `when`(projects.findByOriginalProjectId(1L)).thenReturn(emptyList())
        doThrow(IllegalStateException("create failed")).`when`(repositories).createRepository(project)

        assertFailsWith<IllegalStateException> { service.change(project) }

        verify(repositories, never()).deleteRepository(project)
        assertEquals("GIT", project.vcs)
        verify(projects, never()).save(project)
    }

    @Test
    fun `새 저장소를 먼저 만들고 기존 저장소를 지운다 (R-74)`() {
        val project = Project(id = 1L, owner = "o", name = "p", vcs = "GIT")
        `when`(readme.read(project)).thenReturn(null)
        `when`(projects.findByOriginalProjectId(1L)).thenReturn(emptyList())
        val vcsAtCall = mutableListOf<String>()
        `when`(repositories.createRepository(project)).thenAnswer { vcsAtCall += "create:" + project.vcs; null }
        `when`(repositories.deleteRepository(project)).thenAnswer { vcsAtCall += "delete:" + project.vcs; null }

        service.change(project)

        assertEquals(listOf("create:Subversion", "delete:GIT"), vcsAtCall, "새 종류로 만들고, 기존 종류로 지운다")
        assertEquals("Subversion", project.vcs)
        verify(projects).save(project)
    }
}
