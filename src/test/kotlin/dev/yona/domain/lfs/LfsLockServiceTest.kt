package dev.yona.domain.lfs

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * LFS 파일 잠금(X-8 단계 3, `decisions.md` D-79 2번)을 검증한다: 프로젝트와 경로가 유일하고, 이미 잠겨 있으면 기존 잠금을 돌려주며, 푸는 것은 잠근 사람이고
 * 강제 풀기는 허용된 사람만 한다. 목록은 경로와 번호로 거르고 쪽 나누기를 지원하며, `verify`는 내 잠금과 남의 잠금을 나눈다. 모든 변경은 감사 기록을 남긴다.
 */
@Transactional
class LfsLockServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: LfsLockService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var logs: dev.yona.domain.audit.AuditLogRepository

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000

    private fun user() = users.save(User(loginId = "ll${++n}-$stamp", name = "ll$n", email = "ll$n-$stamp@example.com", state = UserState.ACTIVE))
    private fun project() = projects.save(Project(owner = "ll$stamp", name = "p${++n}", vcs = "GIT", createdDate = java.util.Date()))

    @Test
    fun `잠그면 번호와 경로와 주인과 시각이 생기고 같은 경로를 또 잠그면 기존 잠금을 돌려준다`() {
        val p = project(); val a = user(); val b = user()
        val created = assertIs<LfsLockService.Created>(service.create(p, a, "assets/logo.psd", "refs/heads/main")).lock
        assertEquals("assets/logo.psd", created.path); assertEquals(a.id, created.ownerUserId); assertEquals("refs/heads/main", created.refName); assertTrue(created.id!! > 0)
        val conflict = assertIs<LfsLockService.Conflict>(service.create(p, b, "assets/logo.psd", null))
        assertEquals(created.id, conflict.existing.id); assertEquals(a.id, conflict.existing.ownerUserId)
        assertIs<LfsLockService.Created>(service.create(project(), b, "assets/logo.psd", null))
    }

    @Test
    fun `경로는 비어 있거나 절대 경로이거나 상위 경로를 가리키면 거절한다`() {
        val p = project(); val a = user()
        for (bad in listOf("", "  ", "/etc/passwd", "a/../b", "..", "a/./b/..", "x".repeat(2000))) assertIs<LfsLockService.Invalid>(service.create(p, a, bad, null), bad)
        assertIs<LfsLockService.Created>(service.create(p, a, "한글/파일 이름.bin", null))
    }

    @Test
    fun `푸는 것은 잠근 사람이고 강제 풀기는 허용된 사람만 한다`() {
        val p = project(); val owner = user(); val other = user()
        val id = assertIs<LfsLockService.Created>(service.create(p, owner, "a.bin", null)).lock.id!!
        assertIs<LfsLockService.Forbidden>(service.unlock(p, other, id, force = false, mayForce = true))
        assertIs<LfsLockService.Forbidden>(service.unlock(p, other, id, force = true, mayForce = false))
        assertEquals(id, assertIs<LfsLockService.Released>(service.unlock(p, other, id, force = true, mayForce = true)).lock.id)
        assertIs<LfsLockService.NotFound>(service.unlock(p, owner, id, force = false, mayForce = false))
        val again = assertIs<LfsLockService.Created>(service.create(p, owner, "a.bin", null)).lock.id!!
        assertIs<LfsLockService.Released>(service.unlock(p, owner, again, force = false, mayForce = false))
    }

    @Test
    fun `다른 프로젝트의 잠금은 풀 수 없다`() {
        val p = project(); val a = user(); val id = assertIs<LfsLockService.Created>(service.create(p, a, "a.bin", null)).lock.id!!
        assertIs<LfsLockService.NotFound>(service.unlock(project(), a, id, force = true, mayForce = true))
    }

    @Test
    fun `목록은 경로와 번호로 거르고 쪽을 나눈다`() {
        val p = project(); val a = user()
        val ids = (1..5).map { assertIs<LfsLockService.Created>(service.create(p, a, "f$it.bin", null)).lock.id!! }
        val first = service.list(p, path = null, id = null, cursor = null, limit = 2)
        assertEquals(ids.take(2), first.locks.map { it.id }); assertEquals(ids[1].toString(), first.nextCursor)
        val second = service.list(p, null, null, first.nextCursor, 2); assertEquals(ids.subList(2, 4), second.locks.map { it.id })
        val last = service.list(p, null, null, second.nextCursor, 2); assertEquals(listOf(ids[4]), last.locks.map { it.id }); assertNull(last.nextCursor)
        assertEquals(listOf(ids[2]), service.list(p, "f3.bin", null, null, 100).locks.map { it.id })
        assertEquals(listOf(ids[3]), service.list(p, null, ids[3], null, 100).locks.map { it.id })
        assertTrue(service.list(project(), null, null, null, 100).locks.isEmpty())
    }

    @Test
    fun `verify는 내 잠금과 남의 잠금을 나눈다`() {
        val p = project(); val me = user(); val other = user()
        service.create(p, me, "mine.bin", null); service.create(p, other, "theirs.bin", null)
        val result = service.verify(p, me, null, 100)
        assertEquals(listOf("mine.bin"), result.ours.map { it.path }); assertEquals(listOf("theirs.bin"), result.theirs.map { it.path })
    }

    @Test
    fun `잠그고 풀고 강제로 푸는 일은 감사 기록을 남기고 프로젝트에 속한다`() {
        val p = project(); val a = user(); val b = user()
        val id = assertIs<LfsLockService.Created>(service.create(p, a, "x.bin", null)).lock.id!!
        service.unlock(p, b, id, force = true, mayForce = true)
        val id2 = assertIs<LfsLockService.Created>(service.create(p, a, "y.bin", null)).lock.id!!
        service.unlock(p, a, id2, force = false, mayForce = false)
        val events = logs.findAll().filter { it.projectId == p.id }.map { it.action }
        assertEquals(listOf("lfs.lock_created", "lfs.lock_released", "lfs.lock_created", "lfs.lock_released"), events)
        assertTrue(logs.findAll().filter { it.projectId == p.id && it.action == "lfs.lock_released" }.first().details!!.contains("\"force\":true"))
    }

    @Test
    fun `프로젝트나 사용자의 잠금을 한꺼번에 지울 수 있다`() {
        val p = project(); val q = project(); val a = user(); val b = user()
        service.create(p, a, "a.bin", null); service.create(p, b, "b.bin", null); service.create(q, a, "c.bin", null)
        service.deleteOfUser(a); assertEquals(listOf("b.bin"), service.list(p, null, null, null, 100).locks.map { it.path }); assertTrue(service.list(q, null, null, null, 100).locks.isEmpty())
        service.deleteOfProject(p); assertTrue(service.list(p, null, null, null, 100).locks.isEmpty())
    }
}
