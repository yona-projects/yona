package dev.yona.bootstrap

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.Role
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.User
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * 애플리케이션 기동 시 v1.6의 `conf/initial-data.yml`과 같은 초기 데이터를 적재한다.
 *
 * 적재 대상은 사이트 관리자 계정(`admin`), 사이트 관리자 등록, 역할 7종이며, 사용자가 한 명도
 * 없을 때에만 적재한다(v1.6 `Global.insertInitialData`와 같은 조건). 라벨 154건은 v1.6도 적재하지 않는다.
 *
 * 관리자 `id = 1`과 역할 `id` 1~7은 v1.6 코드가 상수로 참조하는 계약이므로, 빈 테이블에 순서대로
 * 저장해 자동 증가 값이 이 번호와 일치하게 한다.
 */
@Component
class InitialDataLoader(private val entityManager: EntityManager) {

    /** 사용자가 없을 때에만 초기 데이터를 저장한다. */
    @Transactional
    fun load() {
        val userCount = entityManager.createQuery("select count(u) from User u", java.lang.Long::class.java)
            .singleResult.toLong()
        if (userCount > 0) return

        val admin = User(
            name = "Site Admin",
            loginId = "admin",
            password = "5v4TVjzLo1bqullT1CU4/bENUNOUfX97WpdunGLvJvw=",
            passwordSalt = "[B@1032a4",
            state = UserState.ACTIVE,
            email = "project.yona@gmail.com",
            createdDate = GregorianCalendar(2012, Calendar.NOVEMBER, 1, 8, 0, 0).time
        )
        entityManager.persist(admin)
        entityManager.persist(SiteAdmin(admin = admin))

        ROLE_NAMES.forEachIndexed { index, name ->
            val role = Role(name = name, active = true)
            entityManager.persist(role)
            check(role.id == index + 1L) { "역할 id가 v1.6과 다릅니다: $name = ${role.id}" }
        }
    }

    private companion object {
        /** `RoleType`의 id 순서(1~7)와 같은 역할 이름이다. */
        val ROLE_NAMES = listOf("manager", "member", "sitemanager", "anonymous", "guest", "org_admin", "org_member")
    }
}

/**
 * 기동 직후 [InitialDataLoader]를 호출한다.
 *
 * 트랜잭션 프록시를 거치도록 적재 로직과 별도의 빈으로 분리했다.
 */
@Component
class InitialDataRunner(private val loader: InitialDataLoader) : org.springframework.boot.ApplicationRunner {
    override fun run(args: org.springframework.boot.ApplicationArguments) = loader.load()
}
