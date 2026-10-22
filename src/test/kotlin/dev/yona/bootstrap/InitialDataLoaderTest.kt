package dev.yona.bootstrap

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import dev.yona.security.LocalAuthenticator
import dev.yona.security.LoginResult
import dev.yona.support.AbstractIntegrationTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * v1.6의 `conf/initial-data.yml`(users, roles, siteAdmins)과 같은 초기 데이터가 적재되는지 확인한다.
 *
 * `User.SITE_MANAGER_ID = 1`과 `Role` id 1~7은 v1.6 코드가 하드코딩해서 쓰는 계약이다.
 */
class InitialDataLoaderTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var loader: InitialDataLoader

    @Autowired
    private lateinit var authenticator: LocalAuthenticator

    @Test
    fun `역할 7개가 v1_6과 같은 id와 이름으로 적재된다`() {
        val roles = jdbc.queryForList("select id, name, active from role order by id")
            .map { Triple((it["ID"] as Number).toLong(), it["NAME"], it["ACTIVE"]) }
        assertEquals(
            listOf(
                Triple(1L, "manager", true), Triple(2L, "member", true), Triple(3L, "sitemanager", true),
                Triple(4L, "anonymous", true), Triple(5L, "guest", true), Triple(6L, "org_admin", true),
                Triple(7L, "org_member", true)
            ),
            roles
        )
    }

    @Test
    fun `관리자 계정이 id 1로 적재되고 사이트 관리자로 등록된다`() {
        val admin = jdbc.queryForMap("select * from n4user where id = 1")
        assertEquals("Site Admin", admin["NAME"])
        assertEquals("admin", admin["LOGIN_ID"])
        assertEquals("5v4TVjzLo1bqullT1CU4/bENUNOUfX97WpdunGLvJvw=", admin["PASSWORD"])
        assertEquals("[B@1032a4", admin["PASSWORD_SALT"])
        assertEquals("ACTIVE", admin["STATE"])
        assertEquals("project.yona@gmail.com", admin["EMAIL"])
        assertEquals(1, jdbc.queryForObject("select count(*) from site_admin where admin_id = 1", Int::class.java))
    }

    @Test
    fun `초기 관리자는 v1_6의 기본 패스워드 admin00으로 로그인할 수 있다`() {
        val result = authenticator.authenticate("admin", "admin00")
        assertIs<LoginResult.Success>(result)
        assertEquals(1L, result.user.id)
    }

    @Test
    fun `이미 사용자가 있으면 다시 적재하지 않는다`() {
        loader.load()
        assertEquals(1, jdbc.queryForObject("select count(*) from n4user", Int::class.java))
        assertEquals(7, jdbc.queryForObject("select count(*) from role", Int::class.java))
    }
}
