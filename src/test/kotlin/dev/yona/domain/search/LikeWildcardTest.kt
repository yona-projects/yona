package dev.yona.domain.search

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Label
import dev.yona.domain.issue.LabelRepository
import dev.yona.domain.site.SiteUserService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 검색어의 `%`, `_`가 와일드카드가 아니라 글자 그대로 찾아지는지 검증한다. v1.6(Ebean `icontains`)은 이스케이프하지 않아 `_`만 넣으면 모든 값이 맞았다(R-96).
 */
@Transactional
class LikeWildcardTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var labels: LabelRepository
    @Autowired private lateinit var siteUsers: SiteUserService

    private fun user(loginId: String) = users.save(User(loginId = loginId, name = "이름$loginId", email = "$loginId@example.com", state = UserState.ACTIVE, createdDate = Date()))

    @Test
    fun `사용자 검색에서 밑줄은 글자 그대로이다 (저장소 쿼리)`() {
        user("wc_one"); user("wcxone"); user("pct%two")
        fun found(q: String) = users.search(q, PageRequest.of(0, 50)).content.mapNotNull { it.loginId }.filter { it.startsWith("wc") || it.startsWith("pct") }.sorted()
        assertEquals(listOf("wc_one"), found("wc_one"))
        assertEquals(listOf("wc_one"), found("c_o"))
        assertEquals(listOf("pct%two"), found("t%t"))
        assertEquals(listOf("pct%two"), found("%"), "% 하나만으로는 모든 값에 맞지 않고 %가 든 값에만 맞는다")
    }

    @Test
    fun `사이트 관리의 사용자 검색에서 밑줄은 글자 그대로이다 (조립한 쿼리)`() {
        user("ws_alpha"); user("wsxalpha")
        val names = siteUsers.findUsers(0, "ws_alpha", UserState.ACTIVE).items.mapNotNull { it.loginId }
        assertEquals(listOf("ws_alpha"), names)
        assertEquals(listOf("ws_alpha"), siteUsers.findUsers(0, "_", UserState.ACTIVE).items.mapNotNull { it.loginId }.filter { it.startsWith("ws") }, "_ 하나는 밑줄이 든 값에만 맞는다")
    }

    @Test
    fun `라벨 검색에서 밑줄은 글자 그대로이다 (저장소 쿼리의 두 인자)`() {
        labels.save(Label(category = "cat_one", name = "n_one")); labels.save(Label(category = "catxone", name = "nxone"))
        assertEquals(1, labels.countMatching("cat_one", "n_one"))
        assertEquals(1, labels.countCategories("t_o"))
    }
}
