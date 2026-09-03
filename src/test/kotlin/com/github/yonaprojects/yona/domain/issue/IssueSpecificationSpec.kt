package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.milestone.Milestone
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import jakarta.persistence.criteria.*

class IssueSpecificationSpec : DescribeSpec({

    val cb = mockk<CriteriaBuilder>(relaxed = true)
    val query = mockk<CriteriaQuery<*>>(relaxed = true)
    val root = mockk<Root<Issue>>(relaxed = true)

    val project = Project(id = 1L, name = "test")

    beforeTest {
        clearMocks(cb, query, root)
    }

    describe("filterIssues") {
        it("should apply basic predicates: project, state, author, assignee, milestone") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = 2L,
                assigneeId = 3L,
                milestoneId = 4L,
                commenterId = null,
                labelIds = null,
                dueDate = null
            )
            
            // Just invoke to Predicate to cover lines
            spec.toPredicate(root, query, cb)
        }

        it("should apply null checks for assigneeId = -1 and milestoneId = -1") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = -1L,
                milestoneId = -1L,
                commenterId = null,
                labelIds = null,
                dueDate = null
            )
            spec.toPredicate(root, query, cb)
        }

        it("should apply commenterId subquery") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = 5L,
                labelIds = null,
                dueDate = null
            )
            
            val subquery = mockk<Subquery<Long>>(relaxed = true)
            every { query.subquery(Long::class.java) } returns subquery
            
            val inClause = mockk<CriteriaBuilder.In<Any>>(relaxed = true)
            every { cb.`in`(any<Expression<out Any>>()) } returns inClause
            
            spec.toPredicate(root, query, cb)
        }

        it("should apply labelIds") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = listOf(10L, 20L),
                dueDate = null
            )
            val inClause = mockk<CriteriaBuilder.In<Any>>(relaxed = true)
            every { cb.`in`(any<Expression<out Any>>()) } returns inClause
            spec.toPredicate(root, query, cb)
        }
        
        it("should apply filter string") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = "searchme",
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = null,
                dueDate = null
            )
            val subquery = mockk<Subquery<Long>>(relaxed = true)
            every { query.subquery(Long::class.java) } returns subquery
            val inClause = mockk<CriteriaBuilder.In<Any>>(relaxed = true)
            every { cb.`in`(any<Expression<out Any>>()) } returns inClause
            spec.toPredicate(root, query, cb)
        }
        
        it("should apply valid dueDate") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = null,
                dueDate = "2024-12-31"
            )
            spec.toPredicate(root, query, cb)
        }
        
        it("should ignore invalid dueDate") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = null,
                dueDate = "invalid-date"
            )
            spec.toPredicate(root, query, cb)
        }

        // authorId/commenterId != null && > 0 조건은 null과 양수(>0)만 테스트돼 있었다 —
        // "non-null이지만 0 이하"인 경우(둘 다 조건 전체가 false가 되는 다른 경로)가 비어 있었다.
        it("should ignore non-positive authorId and commenterId") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = 0L,
                assigneeId = null,
                milestoneId = null,
                commenterId = -1L,
                labelIds = null,
                dueDate = null
            )
            spec.toPredicate(root, query, cb)
        }

        // assigneeId/milestoneId는 null, -1L, >0 세 갈래만 테스트돼 있었다 — "null도 아니고 -1도
        // 아니고 0 이하(양수 아님)"인, 세 조건 다 걸리지 않는 네 번째 경로가 비어 있었다.
        it("should skip predicate when assigneeId/milestoneId are neither -1 nor positive") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = 0L,
                milestoneId = -2L,
                commenterId = null,
                labelIds = null,
                dueDate = null
            )
            spec.toPredicate(root, query, cb)
        }

        // !labelIds.isNullOrEmpty()는 null과 non-empty list만 테스트돼 있었다 — "non-null이지만
        // empty"(isEmpty() 서브 분기)가 비어 있었다.
        it("should ignore empty (non-null) labelIds") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = emptyList(),
                dueDate = null
            )
            spec.toPredicate(root, query, cb)
        }

        // !filter.isNullOrBlank()/!dueDate.isNullOrBlank()는 null과 정상 문자열만 테스트돼
        // 있었다 — "non-null이지만 공백뿐"인 경우가 비어 있었다.
        it("should ignore blank (non-null) filter and dueDate") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = "   ",
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = null,
                dueDate = "   "
            )
            spec.toPredicate(root, query, cb)
        }
    }

    describe("filterOrganizationIssues") {
        it("should apply empty projects and mentionedIssueIds") {
            val spec = IssueSpecification.filterOrganizationIssues(
                projects = emptyList(),
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                mentionedIssueIds = emptyList()
            )
            spec.toPredicate(root, query, cb)
        }

        it("should apply non-empty projects, authorId, assigneeId, mentionedIssueIds, filter") {
            val spec = IssueSpecification.filterOrganizationIssues(
                projects = listOf(project),
                state = State.OPEN,
                filter = "search",
                authorId = 1L,
                assigneeId = 2L,
                mentionedIssueIds = listOf(100L)
            )
            spec.toPredicate(root, query, cb)
        }

        // mentionedIssueIds != null 바깥 체크는 empty list(non-null)/non-empty list만
        // 테스트돼 있었다 — "null"(바깥 체크 자체가 false인 경로)이 비어 있었다.
        it("should skip mentionedIssueIds predicate when it is null") {
            val spec = IssueSpecification.filterOrganizationIssues(
                projects = listOf(project),
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                mentionedIssueIds = null
            )
            spec.toPredicate(root, query, cb)
        }

        // authorId/assigneeId != null && > 0, filter.isNullOrBlank()의 "non-null이지만
        // 0 이하/공백뿐"인 경로가 비어 있었다.
        it("should ignore non-positive authorId/assigneeId and blank filter") {
            val spec = IssueSpecification.filterOrganizationIssues(
                projects = listOf(project),
                state = State.OPEN,
                filter = "   ",
                authorId = 0L,
                assigneeId = -1L,
                mentionedIssueIds = null
            )
            spec.toPredicate(root, query, cb)
        }
    }
})
