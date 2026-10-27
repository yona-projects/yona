package dev.yona.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 세션 사용자 캐시가 v1.6 `CacheStore.yonaUsers`처럼 동작하는지 검증한다.
 *
 * 원본: `app/utils/CacheStore.java`(최대 2000개), `UserApp.addUserInfoToSession`(put), `User.java`의 판정 기억 필드.
 */
class SessionUserCacheTest {

    @Test
    fun `기억하기 전에는 항목이 없고 기억하면 생긴다`() {
        val cache = SessionUserCache()
        assertFalse(cache.contains(1L))
        assertNull(cache.memoOf(1L))
        cache.remember(1L)
        assertTrue(cache.contains(1L))
    }

    @Test
    fun `같은 사용자를 다시 기억하면 항목이 새것으로 바뀌고 기억한 판정이 비워진다`() {
        val cache = SessionUserCache()
        cache.remember(1L)
        val first = requireNotNull(cache.memoOf(1L))
        assertEquals(true, first.siteManager { true })
        // 이미 기억했으므로 계산 함수가 다른 값을 돌려줘도 처음 값이 유지된다
        assertEquals(true, first.siteManager { false })
        cache.remember(1L)
        val second = requireNotNull(cache.memoOf(1L))
        assertNotSame(first, second)
        assertEquals(false, second.siteManager { false })
    }

    @Test
    fun `무효화하면 항목이 사라진다`() {
        val cache = SessionUserCache()
        cache.remember(1L)
        cache.invalidate(1L)
        assertFalse(cache.contains(1L))
    }

    @Test
    fun `프로젝트 매니저와 조직 판정은 대상별로 따로 기억한다`() {
        val memo = SessionUserMemo()
        assertEquals(true, memo.projectManager(10L) { true })
        assertEquals(false, memo.projectManager(11L) { false })
        assertEquals(true, memo.projectManager(10L) { false })
        assertEquals(true, memo.orgMember(5L, "ORG_ADMIN") { true })
        assertEquals(false, memo.orgMember(5L, "ORG_MEMBER") { false })
        assertEquals(true, memo.orgMember(5L, "ORG_ADMIN") { false })
    }

    @Test
    fun `최대 개수를 넘으면 오래된 항목부터 밀려난다`() {
        val cache = SessionUserCache(maximumSize = 3)
        (1L..10L).forEach { cache.remember(it) }
        cache.cleanUp()
        assertTrue((1L..10L).count { cache.contains(it) } <= 3)
        assertSame(cache.memoOf(10L), cache.memoOf(10L))
    }
}
