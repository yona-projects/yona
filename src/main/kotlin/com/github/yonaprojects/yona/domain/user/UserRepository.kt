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
        value = "SELECT * FROM n4user WHERE state IN ('ACTIVE', 'SITE_ADMIN') AND (LOWER(name) LIKE LOWER(:keyword) OR LOWER(login_id) LIKE LOWER(:keyword) OR LOWER(english_name) LIKE LOWER(:keyword))",
        countQuery = "SELECT COUNT(*) FROM n4user WHERE state IN ('ACTIVE', 'SITE_ADMIN') AND (LOWER(name) LIKE LOWER(:keyword) OR LOWER(login_id) LIKE LOWER(:keyword) OR LOWER(english_name) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun searchUsersQuery(@Param("keyword") keyword: String, pageable: Pageable): Page<User>

    fun searchUsers(keyword: String, pageable: Pageable): Page<User> =
        searchUsersQuery(keyword, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM n4user WHERE state IN ('ACTIVE', 'SITE_ADMIN') AND (LOWER(name) LIKE LOWER(:keyword) OR LOWER(login_id) LIKE LOWER(:keyword) OR LOWER(english_name) LIKE LOWER(:keyword))",
        nativeQuery = true
    )
    fun countSearchUsers(@Param("keyword") keyword: String): Int

    @Query(
        value = "SELECT * FROM n4user WHERE state = :#{#state.name()} AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        countQuery = "SELECT COUNT(*) FROM n4user WHERE state = :#{#state.name()} AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        nativeQuery = true
    )
    fun findUsersForAdminQuery(@Param("state") state: UserState, @Param("query") query: String, pageable: Pageable): Page<User>

    fun findUsersForAdmin(state: UserState, query: String, pageable: Pageable): Page<User> =
        findUsersForAdminQuery(state, query, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM n4user WHERE state = :#{#state.name()} AND (LOWER(name) LIKE LOWER(:query) OR LOWER(login_id) LIKE LOWER(:query))",
        nativeQuery = true
    )
    fun countUsersForAdmin(@Param("state") state: UserState, @Param("query") query: String): Int
}
