package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import java.lang.Long as JLong
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReviewThreadServiceImpl(
    @PersistenceContext
    private val entityManager: EntityManager
) : ReviewThreadService {

    @Transactional(readOnly = true)
    override fun getReviewThreads(project: Project, condition: ReviewSearchCondition, pageable: Pageable): Page<CommentThread> {
        val (jpql, params) = buildQuery(project, condition, isCount = false)

        val query = entityManager.createQuery(jpql, CommentThread::class.java)
        params.forEach { (k, v) -> query.setParameter(k, v) }

        query.firstResult = pageable.offset.toInt()
        query.maxResults = pageable.pageSize
        val list = query.resultList

        val count = countReviewThreads(project, condition)
        return PageImpl(list, pageable, count)
    }

    @Transactional(readOnly = true)
    override fun getReviewThreads(project: Project, condition: ReviewSearchCondition): List<CommentThread> {
        val (jpql, params) = buildQuery(project, condition, isCount = false)
        val query = entityManager.createQuery(jpql, CommentThread::class.java)
        params.forEach { (k, v) -> query.setParameter(k, v) }
        return query.resultList
    }

    @Transactional(readOnly = true)
    override fun countReviewThreads(project: Project, condition: ReviewSearchCondition): Long {
        val (jpql, params) = buildQuery(project, condition, isCount = true)
        val query = entityManager.createQuery(jpql, JLong::class.java)
        params.forEach { (k, v) -> query.setParameter(k, v) }
        return query.singleResult.toLong()
    }

    private fun buildQuery(project: Project, condition: ReviewSearchCondition, isCount: Boolean): Pair<String, Map<String, Any>> {
        val selectClause = if (isCount) "select count(distinct t)" else "select distinct t"
        val fromClause = "from CommentThread t left join t.reviewComments rc"

        val whereClauses = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()

        whereClauses.add("t.project = :project")
        params["project"] = project

        if (condition.authorId != null) {
            whereClauses.add("t.author.id = :authorId")
            params["authorId"] = condition.authorId!!
        }

        if (condition.participantId != null) {
            whereClauses.add("rc.author.id = :participantId")
            params["participantId"] = condition.participantId!!
        }

        val threadState = try {
            CommentThread.ThreadState.valueOf(condition.state.uppercase())
        } catch (e: Exception) {
            CommentThread.ThreadState.OPEN
        }
        whereClauses.add("t.state = :state")
        params["state"] = threadState

        if (condition.filter.isNotBlank()) {
            whereClauses.add("(rc.contents like :filter or t.commitId like :filter)")
            params["filter"] = "%${condition.filter}%"
        }

        // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고): whereClauses는 메서드 시작부에서
        // 조건 없이 "t.project = :project"를 항상 추가하므로 isNotEmpty()의 false 분기는 성립할 수 없다.
        val whereSection = if (whereClauses.isNotEmpty()) "where " + whereClauses.joinToString(" and ") else ""

        val orderSection = if (!isCount) {
            val direction = if (condition.orderDir.lowercase() == "asc") "asc" else "desc"
            val orderByField = if (condition.orderBy == "createdDate") "t.createdDate" else "t.createdDate"
            "order by $orderByField $direction"
        } else {
            ""
        }

        val jpql = "$selectClause $fromClause $whereSection $orderSection".trim()
        return Pair(jpql, params)
    }
}
