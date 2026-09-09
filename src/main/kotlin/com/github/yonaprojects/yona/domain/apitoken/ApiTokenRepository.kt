package com.github.yonaprojects.yona.domain.apitoken

import com.github.yonaprojects.yona.domain.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface ApiTokenRepository : JpaRepository<ApiToken, Long> {
    // ApiTokenAuthenticationFilter는 서블릿 필터라 @Transactional 경계 밖에서 실행되므로, 세션이
    // 이미 닫힌 뒤 ApiTokenAuthorizer가 owner/scopes/scopedProjects를 읽으면 LazyInitializationException이
    // 난다 — 조회 시점에 필요한 연관관계를 전부 즉시 로딩한다.
    @Query(
        "SELECT DISTINCT t FROM ApiToken t " +
            "LEFT JOIN FETCH t.owner " +
            "LEFT JOIN FETCH t.scopes " +
            "LEFT JOIN FETCH t.scopedProjects " +
            "WHERE t.tokenHash = :tokenHash"
    )
    fun findByTokenHash(@Param("tokenHash") tokenHash: String): Optional<ApiToken>

    // 토큰 발급/관리 웹 UI의 목록 화면(사용자당 여러 토큰) 대응.
    // 목록 화면은 권한 요약 뱃지(scopes)와 저장소 범위 요약(scopedProjects)을 함께 렌더링해야 하므로
    // findByTokenHash와 동일한 이유로 연관관계를 즉시 로딩한다.
    @Query(
        "SELECT DISTINCT t FROM ApiToken t " +
            "LEFT JOIN FETCH t.scopes " +
            "LEFT JOIN FETCH t.scopedProjects " +
            "WHERE t.owner = :owner " +
            "ORDER BY t.createdAt DESC"
    )
    fun findByOwner(@Param("owner") owner: User): List<ApiToken>
}
