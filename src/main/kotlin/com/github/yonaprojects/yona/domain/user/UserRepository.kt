package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.support.toSnakeCaseSort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByLoginId(loginId: String): Optional<User>
    fun findByEmail(email: String): Optional<User>
    fun findByToken(token: String): Optional<User>
    fun findByState(state: UserState): List<User>

    // JPQL 대신 네이티브 쿼리를 쓰는 이유는 IssueRepository.searchIssues() 주석 참고 (Postgres
    // Hibernate 7.2.x LIKE 2개 이상 버그 회피 — name/login_id(/english_name) 컬럼이 여러 개라
    // 1개로 인수분해 불가). enum(UserState)은 문자열 이름으로 바인딩한다.
    @Query(
        value = "SELECT * FROM n4user WHERE state = 'ACTIVE' AND (LOWER(name) LIKE LOWER(:keyword) OR LOWER(login_id) LIKE LOWER(:keyword) OR LOWER(english_name) LIKE LOWER(:keyword))",
        countQuery = "SELECT COUNT(*) FROM n4user WHERE state = 'ACTIVE' AND (LOWER(name) LIKE LOWER(:keyword) OR LOWER(login_id) LIKE LOWER(:keyword) OR LOWER(english_name) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun searchUsersQuery(@Param("keyword") keyword: String, pageable: Pageable): Page<User>

    fun searchUsers(keyword: String, pageable: Pageable): Page<User> =
        searchUsersQuery(keyword, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM n4user WHERE state = 'ACTIVE' AND (LOWER(name) LIKE LOWER(:keyword) OR LOWER(login_id) LIKE LOWER(:keyword) OR LOWER(english_name) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun countSearchUsers(@Param("keyword") keyword: String): Int

    @Query(
        value = "SELECT * FROM n4user WHERE state = :#{#state.name()} AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        countQuery = "SELECT COUNT(*) FROM n4user WHERE state = :#{#state.name()} AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        nativeQuery = true
    )
    fun findUsersByStateQuery(@Param("state") state: UserState, @Param("query") query: String, pageable: Pageable): Page<User>

    @Query(
        value = "SELECT * FROM n4user WHERE is_site_admin = :flag AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        countQuery = "SELECT COUNT(*) FROM n4user WHERE is_site_admin = :flag AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        nativeQuery = true
    )
    fun findSiteAdminsQuery(@Param("flag") flag: Boolean, @Param("query") query: String, pageable: Pageable): Page<User>

    @Query(
        value = "SELECT * FROM n4user WHERE is_guest = :flag AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        countQuery = "SELECT COUNT(*) FROM n4user WHERE is_guest = :flag AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        nativeQuery = true
    )
    fun findGuestsQuery(@Param("flag") flag: Boolean, @Param("query") query: String, pageable: Pageable): Page<User>

    // v1.6 User.findUsers: SITE_ADMIN 탭은 SiteAdmin 소속, GUEST 탭은 isGuest, 그 밖에는 state로 조회한다(세 축은 독립).
    fun findUsersForAdmin(state: UserState, query: String, pageable: Pageable): Page<User> {
        val sorted = pageable.toSnakeCaseSort()
        return when (state) {
            UserState.SITE_ADMIN -> findSiteAdminsQuery(true, query, sorted)
            UserState.GUEST -> findGuestsQuery(true, query, sorted)
            else -> findUsersByStateQuery(state, query, sorted)
        }
    }

    @Query(
        value = "SELECT COUNT(*) FROM n4user WHERE state = :#{#state.name()} AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        nativeQuery = true
    )
    fun countUsersByStateQuery(@Param("state") state: UserState, @Param("query") query: String): Int

    @Query(
        value = "SELECT COUNT(*) FROM n4user WHERE is_site_admin = :flag AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        nativeQuery = true
    )
    fun countSiteAdminsQuery(@Param("flag") flag: Boolean, @Param("query") query: String): Int

    @Query(
        value = "SELECT COUNT(*) FROM n4user WHERE is_guest = :flag AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        nativeQuery = true
    )
    fun countGuestsQuery(@Param("flag") flag: Boolean, @Param("query") query: String): Int

    fun countUsersForAdmin(state: UserState, query: String): Int = when (state) {
        UserState.SITE_ADMIN -> countSiteAdminsQuery(true, query)
        UserState.GUEST -> countGuestsQuery(true, query)
        else -> countUsersByStateQuery(state, query)
    }
}
