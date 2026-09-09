package com.github.yonaprojects.yona.domain.support

import jakarta.persistence.*
import java.time.Instant

@MappedSuperclass
abstract class Comment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    // @Lob를 붙이지 않는다 — Postgres + Hibernate 7.2.x 조합에서 String 필드에 @Lob를 얹으면
    // 물리 매핑이 달라져 LIKE 비교가 항상 매치 0건으로 조용히 실패한다(예외 없이 결과만 틀림 —
    // 가장 위험한 유형). MariaDB에서는 재현되지 않았다.
    // columnDefinition = "TEXT" 대신 length로 큰 값을 주는 이유 — "TEXT"는 MySQL/Postgres
    // 전용 키워드라 CUBRID(및 SQL Server)에서 "TEXT is not defined"로 DDL 자체가 깨진다.
    // columnDefinition은 방언과 무관하게 항상 그 리터럴 문자열 그대로 나가는 반면, length를
    // 충분히 크게 주면 Hibernate가 각 방언에 맞는 "큰 문자열" 타입(MariaDB는 longtext, Postgres는
    // text, CUBRID는 string 등)을 알아서 골라준다.
    @Column(length = 1_000_000, nullable = false)
    var contents: String = "",

    var createdDate: Instant? = null,

    var authorId: Long? = null,
    var authorLoginId: String? = null,
    var authorName: String? = null,

    var projectId: Long? = null
)
