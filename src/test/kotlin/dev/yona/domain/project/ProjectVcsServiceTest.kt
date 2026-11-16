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
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 저장소 종류 변경을 검증한다. 원본: `app/controllers/ProjectApp.java:721-740`, `app/models/Project.java:658-670`.
 *
 * v1.6 `changeVCS`는 트랜잭션이 없어 README 표시 해제와 포크 분리가 저장된 뒤 기존 저장소를 지웠다. 새 저장소 생성이 실패하면
 * 기존 저장소도 사라진 상태로 남았다([refactoring-backlog.md] R-74). 지금은 새 저장소를 먼저 만들고 커밋 뒤 기존 저장소를 지운다.
 * 롤백하면 새 저장소를 지우며, 커밋 뒤 기존 저장소 삭제가 실패하면 오류를 기록하고 디렉터리를 남긴다.
 */
class ProjectVcsServiceTest {

    private val projects = mock(ProjectRepository::class.java)
    private val postings = mock(PostingRepository::class.java)
    private val postingService = mock(dev.yona.domain.board.PostingService::class.java)
    private val readme = mock(ProjectReadmeService::class.java)
    private val repositories = mock(RepositoryService::class.java)
    private val backends = dev.yona.repository.VcsBackends.of(lifecycles = listOf(FakeLifecycle(dev.yona.domain.vcs.VcsType.GIT), FakeLifecycle(dev.yona.domain.vcs.VcsType.SUBVERSION)))
    private val service = ProjectVcsService(projects, postings, postingService, readme, repositories, mock(dev.yona.domain.audit.AuditRecorder::class.java), backends)

    /** 등록부에 어떤 종류가 있는지만 보는 자리 표시 구현이다. */
    private class FakeLifecycle(override val type: dev.yona.domain.vcs.VcsType) : dev.yona.repository.RepositoryLifecycle {
        override fun create(owner: String, name: String) = Unit
        override fun delete(owner: String, name: String) = Unit
        override fun move(owner: String, name: String, newOwner: String, newName: String) = true
        override fun refNames(owner: String, name: String) = emptyList<String>()
        override fun defaultBranch(owner: String, name: String) = "HEAD"
        override fun setDefaultBranch(owner: String, name: String, target: String) = Unit
    }

    @Test
    fun `변경은 트랜잭션으로 묶여 있다 (R-74)`() {
        val annotation = assertNotNull(ProjectVcsService::class.java.getMethod("change", Project::class.java, String::class.java).getAnnotation(Transactional::class.java))
        assertTrue(Exception::class in annotation.rollbackFor)
    }

    @Test
    fun `새 저장소 생성이 실패하면 기존 저장소를 지우지 않고 종류도 되돌린다 (R-74, v1_6은 저장소만 지워졌다)`() {
        val project = Project(id = 1L, owner = "o", name = "p", vcs = "GIT")
        `when`(readme.read(project)).thenReturn(null)
        `when`(projects.findByOriginalProjectId(1L)).thenReturn(emptyList())
        doThrow(IllegalStateException("create failed")).`when`(repositories).createRepository(project)

        assertFailsWith<IllegalStateException> { service.change(project) }

        verify(repositories, never()).deleteRepository(project)
        verify(repositories, never()).deleteRepository("GIT", "o", "p")
        verify(repositories, never()).deleteRepository("Subversion", "o", "p")
        assertEquals("GIT", project.vcs)
        verify(projects, never()).save(project)
    }

    @Test
    fun `커밋 뒤 기존 저장소를 지운다 (R-74)`() = withSynchronization {
        val project = Project(id = 1L, owner = "o", name = "p", vcs = "GIT")
        `when`(readme.read(project)).thenReturn(null)
        `when`(projects.findByOriginalProjectId(1L)).thenReturn(emptyList())
        val vcsAtCall = mutableListOf<String>()
        `when`(repositories.createRepository(project)).thenAnswer { vcsAtCall += "create:" + project.vcs; null }
        service.change(project)
        verify(repositories, never()).deleteRepository(project)
        verify(repositories, never()).deleteRepository("GIT", "o", "p")
        project.vcs = "Mercurial"
        project.owner = "other"
        project.name = "other"
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        verify(repositories).deleteRepository("GIT", "o", "p")
        verify(repositories, never()).deleteRepository("Subversion", "o", "p")
        assertEquals(listOf("create:Subversion"), vcsAtCall)
        verify(projects).save(project)
    }

    @Test
    fun `롤백 뒤 새 저장소만 지운다`() = withSynchronization {
        val project = Project(id = 1L, owner = "o", name = "p", vcs = "GIT")
        service.change(project)
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
        verify(repositories, never()).deleteRepository(project)
        verify(repositories, never()).deleteRepository("GIT", "o", "p")
        verify(repositories).deleteRepository("Subversion", "o", "p")
    }

    @Test
    fun `커밋 뒤 삭제가 실패해도 예외를 전파하지 않는다`() = withSynchronization {
        val project = Project(id = 1L, owner = "o", name = "p", vcs = "GIT")
        doThrow(IllegalStateException("delete failed")).`when`(repositories).deleteRepository("GIT", "o", "p")
        service.change(project)
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        verify(repositories).deleteRepository("GIT", "o", "p")
    }

    @Test
    fun `롤백 뒤 삭제가 실패해도 예외를 전파하지 않는다`() = withSynchronization {
        val project = Project(id = 1L, owner = "o", name = "p", vcs = "GIT")
        doThrow(IllegalStateException("delete failed")).`when`(repositories).deleteRepository("Subversion", "o", "p")
        service.change(project)
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
        verify(repositories).deleteRepository("Subversion", "o", "p")
    }

    private fun withSynchronization(block: () -> Unit) {
        TransactionSynchronizationManager.initSynchronization()
        try { block() } finally { TransactionSynchronizationManager.clearSynchronization() }
    }

    @Test
    fun `바꿀 수 있는 종류는 등록된 종류에서 지금 종류를 뺀 것이다`() {
        assertEquals(listOf("Subversion"), service.targets(Project(vcs = "GIT")))
        assertEquals(listOf("GIT"), service.targets(Project(vcs = "Subversion")))
    }

    @Test
    fun `대상을 정해 바꾸면 그 종류로 만들고 기존 저장소를 지운다`() {
        val project = Project(id = 1L, owner = "o", name = "p", vcs = "GIT")
        `when`(readme.read(project)).thenReturn(null)
        `when`(projects.findByOriginalProjectId(1L)).thenReturn(emptyList())
        service.change(project, "Subversion")
        assertEquals("Subversion", project.vcs)
        verify(repositories).createRepository(project)
        verify(repositories).deleteRepository("GIT", "o", "p")
    }

    @Test
    fun `대상이 지금 종류이거나 등록되지 않은 종류이면 아무것도 바꾸지 않는다`() {
        val project = Project(id = 1L, owner = "o", name = "p", vcs = "GIT")
        assertFailsWith<IllegalArgumentException> { service.change(project, "GIT") }
        assertFailsWith<IllegalArgumentException> { service.change(project, "Mercurial") }
        assertFailsWith<IllegalArgumentException> { service.change(project, "") }
        verify(repositories, never()).createRepository(project)
        verify(repositories, never()).deleteRepository(project)
        assertEquals("GIT", project.vcs)
    }

    @Test
    fun `대상을 주는 변경도 트랜잭션으로 묶여 있다`() {
        assertNotNull(ProjectVcsService::class.java.getMethod("change", Project::class.java, String::class.java).getAnnotation(Transactional::class.java))
    }
}
