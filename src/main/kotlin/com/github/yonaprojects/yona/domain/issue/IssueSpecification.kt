package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.milestone.Milestone
import jakarta.persistence.criteria.*
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object IssueSpecification {
    fun filterIssues(
        project: Project,
        state: State,
        filter: String?,
        authorId: Long?,
        assigneeId: Long?,
        milestoneId: Long?,
        commenterId: Long?,
        labelIds: List<Long>?,
        dueDate: String?
    ): Specification<Issue> {
        return Specification { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            // 1. 프로젝트 조건
            predicates.add(cb.equal(root.get<Project>("project"), project))

            // 2. 상태 조건
            predicates.add(cb.equal(root.get<State>("state"), state))

            // 3. 작성자 조건
            if (authorId != null && authorId > 0) {
                predicates.add(cb.equal(root.get<Long>("authorId"), authorId))
            }

            // 4. 담당자 조건
            if (assigneeId != null) {
                if (assigneeId == -1L) {
                    predicates.add(cb.isNull(root.get<Assignee>("assignee")))
                } else if (assigneeId > 0) {
                    predicates.add(cb.equal(root.join<Issue, Assignee>("assignee").get<User>("user").get<Long>("id"), assigneeId))
                }
            }

            // 5. 마일스톤 조건
            if (milestoneId != null) {
                if (milestoneId == -1L) {
                    predicates.add(cb.isNull(root.get<Milestone>("milestone")))
                } else if (milestoneId > 0) {
                    predicates.add(cb.equal(root.get<Milestone>("milestone").get<Long>("id"), milestoneId))
                }
            }

            // 6. 댓글단 사람 조건
            if (commenterId != null && commenterId > 0) {
                val subquery = query.subquery(Long::class.java)
                val commentRoot = subquery.from(IssueComment::class.java)
                subquery.select(commentRoot.get<Issue>("issue").get<Long>("id"))
                subquery.where(cb.equal(commentRoot.get<Long>("authorId"), commenterId))
                
                val inClause = cb.`in`(root.get<Long>("id"))
                inClause.value(subquery)
                predicates.add(inClause)
            }

            // 7. 라벨 조건 (labelIds)
            if (!labelIds.isNullOrEmpty()) {
                query.distinct(true)
                val labelJoin = root.join<Issue, IssueLabel>("labels")
                
                val inClause = cb.`in`(labelJoin.get<Long>("id"))
                labelIds.forEach { inClause.value(it) }
                predicates.add(inClause)
            }

            // 8. 검색어 조건 (filter)
            if (!filter.isNullOrBlank()) {
                val keyword = "%$filter%"
                val titleLike = cb.like(root.get("title"), keyword)
                val bodyLike = cb.like(root.get("body"), keyword)
                
                // 댓글 내용도 검색 대상에 포함
                val subquery = query.subquery(Long::class.java)
                val commentRoot = subquery.from(IssueComment::class.java)
                subquery.select(commentRoot.get<Issue>("issue").get<Long>("id"))
                subquery.where(cb.like(commentRoot.get("contents"), keyword))

                val inClause = cb.`in`(root.get<Long>("id"))
                inClause.value(subquery)

                predicates.add(cb.or(titleLike, bodyLike, inClause))
            }

            // 9. 마감일 조건 (dueDate)
            if (!dueDate.isNullOrBlank()) {
                try {
                    val localDate = LocalDate.parse(dueDate)
                    val zone = ZoneId.systemDefault()
                    val nextDayInstant = localDate.plusDays(1).atStartOfDay(zone).toInstant()
                    predicates.add(cb.lessThan(root.get<Instant>("dueDate"), nextDayInstant))
                } catch (e: Exception) {
                    // 날짜 형식이 잘못된 경우 무시
                }
            }

            cb.and(*predicates.toTypedArray())
        }
    }

    // yona organization/group_issue_search_partial.scala.html 대응. 프로젝트 그룹(#filterIssues)과
    // 달리 단일 project가 아니라 조직에 속한(공개 범위로 걸러진) 여러 project를 대상으로 검색하고,
    // 필터도 authorId/assigneeId/mentionId 3종뿐이다(마일스톤/라벨/댓글단사람/마감일 필터는 legacy
    // group_issue_search_partial.scala.html에 아예 없음).
    fun filterOrganizationIssues(
        projects: List<Project>,
        state: State,
        filter: String?,
        authorId: Long?,
        assigneeId: Long?,
        mentionedIssueIds: List<Long>?
    ): Specification<Issue> {
        return Specification { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            if (projects.isEmpty()) {
                predicates.add(cb.disjunction())
            } else {
                predicates.add(root.get<Project>("project").`in`(projects))
            }

            predicates.add(cb.equal(root.get<State>("state"), state))

            if (authorId != null && authorId > 0) {
                predicates.add(cb.equal(root.get<Long>("authorId"), authorId))
            }

            if (assigneeId != null && assigneeId > 0) {
                predicates.add(cb.equal(root.join<Issue, Assignee>("assignee").get<User>("user").get<Long>("id"), assigneeId))
            }

            if (mentionedIssueIds != null) {
                if (mentionedIssueIds.isEmpty()) {
                    predicates.add(cb.disjunction())
                } else {
                    predicates.add(root.get<Long>("id").`in`(mentionedIssueIds))
                }
            }

            if (!filter.isNullOrBlank()) {
                val keyword = "%$filter%"
                predicates.add(cb.or(cb.like(root.get("title"), keyword), cb.like(root.get("body"), keyword)))
            }

            cb.and(*predicates.toTypedArray())
        }
    }
}
