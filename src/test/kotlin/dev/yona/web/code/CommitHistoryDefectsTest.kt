package dev.yona.web.code

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.gpgkey.SignedGitObjects
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.repository.CommitHistoryService
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevWalk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 커밋 이력 조회의 알려진 결함 수정을 검증한다(`architecture/v16-safe-fix-candidates.md`). */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class CommitHistoryDefectsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var history: CommitHistoryService

    private fun projectWithCommit(): Pair<Project, String> {
        val p = projects.save(Project(owner = "chd${System.nanoTime()}", name = "chdp", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        return p to SignedGitObjects.commit(repositories, p, null, "a@example.com")
    }

    /** 커밋이 아니라 트리를 가리키는 가벼운 태그를 만든다(`git tag t <tree>`). */
    private fun treeTag(project: Project, commitId: String, name: String) = repositories.openRepository(project).use { repository ->
        val tree = RevWalk(repository).use { it.parseCommit(ObjectId.fromString(commitId)).tree.id }
        repository.updateRef("refs/tags/$name").apply { setNewObjectId(tree); update() }
    }

    @Test
    fun `커밋이 아닌 객체를 가리키는 태그의 이력은 500이 아니라 없음이다 (R-128)`() {
        val (p, commit) = projectWithCommit()
        treeTag(p, commit, "treetag")
        assertNull(history.history(p, 0, 10, "treetag", null))
        mockMvc.perform(get("/${p.owner}/${p.name}/commits/treetag")).andExpect(status().isNotFound)
    }

    @Test
    fun `전체 커밋이 쪽 크기의 배수가 아니면 마지막 쪽은 남은 커밋만 보이고 앞 쪽과 겹치지 않는다 (R-40)`() {
        val p = projects.save(Project(owner = "chdpage${System.nanoTime()}", name = "pg", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        var parent: String? = null
        repeat(7) { parent = SignedGitObjects.commit(repositories, p, null, "a@example.com", message = "c$it", parent = parent, time = 1_800_000_000_000L + it * 1000L) }
        val first = history.history(p, 0, 5, null, null)!!.map { it.shortMessage }
        val second = history.history(p, 1, 5, null, null)!!.map { it.shortMessage }
        assertEquals(listOf("c6", "c5", "c4", "c3", "c2"), first)
        // v1.6은 `쪽 * 크기 + 크기`개를 읽어 마지막 `크기`개를 남겨, 전체가 두 쪽보다 적으면 둘째 쪽이 첫 쪽과 겹쳤다
        assertEquals(listOf("c1", "c0"), second)
    }
}
