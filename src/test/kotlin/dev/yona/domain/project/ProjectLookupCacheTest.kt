package dev.yona.domain.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 프로젝트 조회 캐시(v1.6 `CacheStore.projectMap`)를 검증한다. 한 번 찾은 이름은 프로젝트의 이름이 바뀌어도 계속 같은 프로젝트를 가리키고,
 * 프로젝트를 지우면 이름이 사라진 항목이 정리된다.
 */
@Transactional
class ProjectLookupCacheTest : AbstractIntegrationTest() {

    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var service: ProjectService

    private fun unique() = "c" + UUID.randomUUID().toString().take(8)

    @Test
    fun `이름을 바꿔도 한 번 찾은 예전 이름은 같은 프로젝트를 연다`() {
        val owner = unique(); val oldName = unique()
        val project = projects.save(Project(owner = owner, name = oldName, vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        assertEquals(project.id, service.findByOwnerAndProjectName(owner, oldName)?.id)
        project.name = unique()
        projects.saveAndFlush(project)
        assertEquals(project.id, service.findByOwnerAndProjectName(owner, oldName)?.id)
    }

    @Test
    fun `찾기 전에 이름을 바꾼 예전 이름은 찾지 못한다`() {
        val owner = unique(); val oldName = unique()
        val project = projects.save(Project(owner = owner, name = oldName, vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        project.name = unique()
        projects.saveAndFlush(project)
        assertNull(service.findByOwnerAndProjectName(owner, oldName))
    }

    @Test
    fun `프로젝트를 지우면 이름이 사라진 항목을 정리한다`() {
        val owner = unique(); val oldName = unique()
        val project = projects.save(Project(owner = owner, name = oldName, vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        assertNotNull(service.findByOwnerAndProjectName(owner, oldName))
        val renamed = unique()
        project.name = renamed
        projects.saveAndFlush(project)
        service.forget(project)
        assertNull(service.findByOwnerAndProjectName(owner, oldName))
        assertEquals(project.id, service.findByOwnerAndProjectName(owner, renamed)?.id)
    }

    @Test
    fun `지워진 프로젝트를 가리키는 항목은 버리고 다시 찾는다`() {
        val owner = unique(); val name = unique()
        val first = projects.save(Project(owner = owner, name = name, vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        assertEquals(first.id, service.findByOwnerAndProjectName(owner, name)?.id)
        projects.delete(first); projects.flush()
        val second = projects.save(Project(owner = owner, name = name, vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        assertEquals(second.id, service.findByOwnerAndProjectName(owner, name)?.id)
    }
}
