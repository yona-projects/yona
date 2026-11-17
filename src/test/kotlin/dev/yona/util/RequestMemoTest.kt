package dev.yona.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 요청 하나 안에서만 쓰는 조회 결과 보관소를 검증한다(`decisions.md` A-628). 같은 요청에서 같은 값을 여러 번 읽는 질의를 줄이려는 것이므로
 * 쓰기 요청과 요청 밖에서는 보관하지 않고, 엔터티는 트랜잭션 밖에서만 보관한다.
 */
class RequestMemoTest {

    @AfterEach
    fun clear() {
        RequestContextHolder.resetRequestAttributes()
        TransactionSynchronizationManager.setActualTransactionActive(false)
    }

    private fun bind(method: String) = RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest(method, "/x")))

    private fun counter(): Pair<() -> String?, () -> Int> {
        var n = 0
        return ({ n++; "값" }) to { n }
    }

    @Test
    fun `읽기 요청 안에서 같은 키는 한 번만 읽는다`() {
        bind("GET")
        val (load, calls) = counter()
        repeat(5) { assertEquals("값", RequestMemo.entity("k", load)) }
        assertEquals(1, calls())
    }

    @Test
    fun `HEAD 요청도 보관한다`() {
        bind("HEAD")
        val (load, calls) = counter()
        repeat(3) { RequestMemo.entity("k", load) }
        assertEquals(1, calls())
    }

    @Test
    fun `키가 다르면 따로 읽는다`() {
        bind("GET")
        val (load, calls) = counter()
        RequestMemo.entity("a", load); RequestMemo.entity("b", load); RequestMemo.entity("a", load)
        assertEquals(2, calls())
    }

    @Test
    fun `쓰기 요청에서는 보관하지 않는다`() {
        for (method in listOf("POST", "PUT", "PATCH", "DELETE")) {
            bind(method)
            val (load, calls) = counter()
            repeat(3) { RequestMemo.entity("k", load) }
            assertEquals(3, calls(), method)
        }
    }

    @Test
    fun `요청 밖에서는 보관하지 않는다`() {
        val (load, calls) = counter()
        repeat(3) { RequestMemo.entity("k", load) }
        assertEquals(3, calls())
    }

    @Test
    fun `없는 값은 보관하지 않아 나중에 생기면 읽힌다`() {
        bind("GET")
        var created = false
        val load = { if (created) "생김" else null }
        assertNull(RequestMemo.entity("k", load))
        created = true
        assertEquals("생김", RequestMemo.entity("k", load))
    }

    @Test
    fun `엔터티는 트랜잭션 안에서는 보관하지 않는다 서로 다른 세션의 인스턴스를 섞지 않으려는 것이다`() {
        bind("GET")
        TransactionSynchronizationManager.setActualTransactionActive(true)
        val (load, calls) = counter()
        repeat(3) { RequestMemo.entity("k", load) }
        assertEquals(3, calls())
    }

    @Test
    fun `일반 값은 트랜잭션 안에서도 보관한다`() {
        bind("GET")
        TransactionSynchronizationManager.setActualTransactionActive(true)
        val (load, calls) = counter()
        repeat(3) { RequestMemo.value("k", load) }
        assertEquals(1, calls())
    }

    @Test
    fun `일반 값도 쓰기 요청에서는 보관하지 않는다`() {
        bind("POST")
        val (load, calls) = counter()
        repeat(3) { RequestMemo.value("k", load) }
        assertEquals(3, calls())
    }
}
