package dev.yona.domain.project

import dev.yona.domain.board.Posting
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
import kotlin.test.assertNull

/**
 * 저장소 종류 변경이 실패했을 때의 중간 상태를 검증한다. 원본: `app/controllers/ProjectApp.java:721-740`, `app/models/Project.java:658-670`.
 *
 * v1.6 `changeVCS`는 트랜잭션이 없어 README 표시 해제와 포크 연결 끊기가 저장 즉시 반영되고, 저장소 생성이 실패해도 되돌아가지 않는다
 * ([refactoring-backlog.md] R-74). 서비스에 트랜잭션이 걸려 있지 않아야 이 중간 상태가 재현된다.
 */
class ProjectVcsServiceTest {

    private val projects = mock(ProjectRepository::class.java)
    private val postings = mock(PostingRepository::class.java)
    private val readme = mock(ProjectReadmeService::class.java)
    private val repositories = mock(RepositoryService::class.java)
    private val service = ProjectVcsService(projects, postings, readme, repositories)

    @Test
    fun `서비스에는 트랜잭션이 걸려 있지 않다`() {
        assertNull(ProjectVcsService::class.java.getAnnotation(Transactional::class.java))
        assertNull(ProjectVcsService::class.java.getMethod("change", Project::class.java).getAnnotation(Transactional::class.java))
    }

    @Test
    fun `저장소 생성이 실패하면 README 해제와 포크 분리는 저장된 채 남고 종류는 저장되지 않는다`() {
        val project = Project(id = 1L, owner = "o", name = "p", vcs = "GIT")
        val fork = Project(id = 2L, owner = "f", name = "p", vcs = "GIT", originalProject = project)
        val posting = Posting(id = 3L, readme = true)
        `when`(readme.read(project)).thenReturn("README")
        `when`(postings.findFirstByProjectIdAndReadmeTrue(1L)).thenReturn(posting)
        `when`(projects.findByOriginalProjectId(1L)).thenReturn(listOf(fork))
        doThrow(IllegalStateException("create failed")).`when`(repositories).createRepository(project)

        assertFailsWith<IllegalStateException> { service.change(project) }

        assertEquals(false, posting.readme)
        verify(postings).save(posting)
        assertNull(fork.originalProject)
        verify(projects).save(fork)
        verify(repositories).deleteRepository(project)
        verify(projects, never()).save(project)
    }
}
