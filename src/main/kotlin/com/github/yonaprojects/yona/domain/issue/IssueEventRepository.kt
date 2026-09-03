package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.EventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface IssueEventRepository : JpaRepository<IssueEvent, Long> {
    fun findByIssueOrderByCreatedAsc(issue: Issue): List<IssueEvent>

    fun findFirstByIssueAndCreatedAfterOrderByIdDesc(issue: Issue, created: Instant): IssueEvent?

    // yona PullRequest.deleteIssueEvents() 대응 (P1-68). 특정 PR이 이전에 남긴
    // ISSUE_REFERRED_FROM_PULL_REQUEST 이벤트들을 찾는다(newValue=PR id, senderLoginId=기여자).
    //
    // Spring Data 파생 쿼리 대신 네이티브 쿼리를 쓰는 이유: new_value/old_value가 원래 @Lob로
    // 매핑돼 있었는데, Postgres + Hibernate 7.2.x에서 @Lob String 컬럼과 비교하는 파생 쿼리가
    // `text = bigint` 타입 오류로 항상 실패했다(MariaDB에서는 재현 안 됨). 근본 원인은 @Lob
    // 자체였고 지금은 제거했지만(domain/support/Comment.kt 주석 참고), 이 메서드는 네이티브
    // 쿼리로도 이미 검증됐으므로 그대로 유지한다.
    @Query(
        value = "SELECT * FROM issue_event WHERE new_value = :newValue AND sender_login_id = :senderLoginId AND event_type = :#{#eventType.name()}",
        nativeQuery = true
    )
    fun findByNewValueAndSenderLoginIdAndEventType(
        @Param("newValue") newValue: String,
        @Param("senderLoginId") senderLoginId: String,
        @Param("eventType") eventType: EventType
    ): List<IssueEvent>
}
