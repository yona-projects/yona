package dev.yona.domain.site

import dev.yona.util.LegacyText
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/** 한 쪽 분량의 사용자 목록이다. [pageIndex]는 0부터이다. */
data class UserPage(val items: List<User>, val pageIndex: Int, val totalPageCount: Int)

/**
 * 사이트 관리의 사용자 관리이다. v1.6 `User.findUsers`, `User.changeState`, `Project.isOnlyManager`와
 * `SiteApp`의 사용자 관련 액션(`app/controllers/SiteApp.java:131-205, 238-275`)이 쓰던 조회와 변경에 해당한다.
 */
@Service
@Transactional
class SiteUserService(
    private val entityManager: EntityManager,
    private val users: UserRepository,
    private val siteAdmins: SiteAdminRepository,
    private val sessionUsers: dev.yona.security.SessionUserCache
) {

    /**
     * 사용자를 가입 최근 순으로 한 쪽(30명) 찾는다. 기본 관리자(번호 1)와 익명 사용자는 항상 뺀다.
     *
     * [state]가 `GUEST`이면 게스트 표시가 있는 사용자(상태는 가리지 않음), `SITE_ADMIN`이면 사이트 관리자로 등록된 사용자, 그 밖에는 그 상태이다.
     * [query]가 비어 있지 않으면 로그인 ID, 이름, 영문 이름, 이메일 중 하나에 대소문자 구분 없이 포함된 사용자만 찾는다.
     */
    @Transactional(readOnly = true)
    fun findUsers(pageIndex: Int, query: String?, state: UserState): UserPage {
        val clauses = mutableListOf("u.id <> :defaultAdmin", "u.loginId <> :anonymous")
        val params = linkedMapOf<String, Any?>("defaultAdmin" to DEFAULT_ADMIN_ID, "anonymous" to ANONYMOUS_LOGIN_ID)
        when (state) {
            UserState.GUEST -> clauses += "u.isGuest = true"
            // v1.6은 사이트 관리자 행의 번호가 1인 것을 뺀다(기본 관리자의 행).
            UserState.SITE_ADMIN -> clauses += "u.id in (select sa.admin.id from SiteAdmin sa where sa.id <> $DEFAULT_ADMIN_ID)"
            else -> { clauses += "u.state = :state"; params["state"] = state }
        }
        if ((query != null && LegacyText.isNotBlank(query))) {
            clauses += listOf("loginId", "name", "englishName", "email").joinToString(" or ", "(", ")") { "lower(u.$it) like :query escape '\\'" }
            params["query"] = "%" + query.lowercase() + "%"
        }
        val where = clauses.joinToString(" and ")
        fun <T> typed(jpql: String, type: Class<T>) = entityManager.createQuery(jpql, type).also { q -> params.forEach { (k, v) -> if (Regex(":$k\\b").containsMatchIn(jpql)) q.setParameter(k, v) } }
        val total = typed("select count(u) from User u where $where", java.lang.Long::class.java).singleResult.toLong()
        val items = typed("select u from User u where $where order by u.createdDate desc, u.id desc", User::class.java)
            .setFirstResult(pageIndex * PAGE_SIZE).setMaxResults(PAGE_SIZE).resultList
        return UserPage(items, pageIndex, Math.ceil(total.toDouble() / PAGE_SIZE).toInt())
    }

    /** 사용자가 사이트 관리자인지 확인한다 (v1.6 `User.isSiteManager`). */
    @Transactional(readOnly = true)
    fun isSiteManager(user: User?): Boolean = user?.id?.let { siteAdmins.existsByAdminId(it) } ?: false

    /** 사이트 관리자로 등록된 사용자 수에서 기본 관리자를 뺀 수이다. 목록의 사이트 관리자 탭 배지에 쓴다. */
    @Transactional(readOnly = true)
    fun siteAdminBadgeCount(): Long = siteAdmins.count() - 1

    /** 사용자가 어느 프로젝트의 유일한 매니저인지 확인한다 (v1.6 `Project.isOnlyManager`). */
    @Transactional(readOnly = true)
    fun isOnlyManager(userId: Long): Boolean = entityManager.createQuery(
        "select count(pu) from ProjectUser pu where pu.user.id = :user and pu.role.id = :manager and not exists " +
            "(select 1 from ProjectUser other where other.project.id = pu.project.id and other.role.id = :manager and other.user.id <> :user)",
        java.lang.Long::class.java
    ).setParameter("user", userId).setParameter("manager", RoleType.MANAGER.id).singleResult > 0

    /**
     * 사용자를 삭제한 것으로 처리한다. 프로젝트 구성원에서 모두 빼고 상태를 [UserState.DELETED]로 바꾼다.
     * 사용자 행은 남기고 이름 앞에 `[DELETED]`를 붙이며 이메일과 비밀번호를 지운다.
     */
    fun deleteUser(user: User) {
        entityManager.createQuery("delete from ProjectUser pu where pu.user.id = :user").setParameter("user", user.id).executeUpdate()
        changeState(user, UserState.DELETED)
    }

    /**
     * 사용자의 상태를 바꾼다 (v1.6 `User.changeState`). 삭제로 바꾸면 이름, 이메일, 비밀번호, 가입 신청, 알림 수신 기록, 담당 이슈를 정리한다.
     */
    fun changeState(user: User, state: UserState) {
        user.state = state
        user.lastStateModifiedDate = Date()
        if (state == UserState.DELETED) {
            val id = requireNotNull(user.id)
            user.name = "[DELETED]" + user.name
            user.password = ""
            user.passwordSalt = ""
            user.email = "deleted-${user.loginId}@noreply.yona.io"
            user.rememberMe = false
            user.enrolledProjects.clear()
            entityManager.createNativeQuery("delete from notification_event_n4user where n4user_id = :id").setParameter("id", id).executeUpdate()
            entityManager.createQuery("update Issue i set i.assignee = null where i.assignee.id in (select a.id from Assignee a where a.user.id = :id)")
                .setParameter("id", id).executeUpdate()
            entityManager.createQuery("delete from Assignee a where a.user.id = :id").setParameter("id", id).executeUpdate()
            // 추가 항목(X-14): 풀 리퀘스트 담당자에서도 뺀다
            entityManager.createNativeQuery("delete from pull_request_assignee where user_id = :id").setParameter("id", id).executeUpdate()
            // 추가 항목(X-27): 대기 중인 리뷰 요청에서도 뺀다
            entityManager.createNativeQuery("delete from pull_request_review_request where user_id = :id").setParameter("id", id).executeUpdate()
            // 추가 항목(X-5): 삭제 처리한 사용자의 API 토큰은 모두 지운다
            entityManager.createNativeQuery("delete from api_token where user_id = :id").setParameter("id", id).executeUpdate()
        }
        users.save(user)
        // v1.6 User.changeState: 새로 읽은 사용자 인스턴스를 서버 캐시에 넣어 기억된 판정이 비워진다
        sessionUsers.remember(requireNotNull(user.id))
    }

    /** 게스트 여부를 뒤집는다 (v1.6 `SiteApp.toggleGuestMode`). */
    fun toggleGuest(user: User) {
        user.isGuest = user.isGuest != true
        users.save(user)
        // v1.6 SiteApp.toggleGuestMode: 새로 읽은 인스턴스를 서버 캐시에 넣는다
        sessionUsers.remember(requireNotNull(user.id))
    }

    /** 사이트 관리자이면 권한을 거두고 아니면 부여한다 (v1.6 `SiteApp.toggleSiteAdminRole`). */
    fun toggleSiteAdmin(user: User) {
        val id = requireNotNull(user.id)
        siteAdmins.findAll().filter { it.admin?.id == id }.let { rows ->
            if (rows.isNotEmpty()) siteAdmins.deleteAll(rows) else siteAdmins.save(SiteAdmin(admin = user))
        }
        // v1.6 SiteApp.toggleSiteAdminRole: 서버 캐시에서 지우므로 대상 사용자의 세션은 다음 요청에서 무효가 되어 다시 로그인해야 한다
        sessionUsers.invalidate(id)
    }

    companion object {
        /** 한 쪽의 사용자 수이다 (v1.6 `User.USER_COUNT_PER_PAGE`). */
        const val PAGE_SIZE = 30

        /** 기본 관리자의 사용자 번호이다 (v1.6 `User.SITE_MANAGER_ID`). */
        const val DEFAULT_ADMIN_ID = 1L

        /** 익명 사용자의 로그인 ID이다. */
        /** v1.6 `User.anonymous`는 `NullUser`이고 로그인 ID가 빈 문자열이라 목록에서 빼는 것은 그런 행뿐이다. 로그인 ID `anonymous`인 실제 사용자는 나온다. */
        const val ANONYMOUS_LOGIN_ID = ""
    }
}
