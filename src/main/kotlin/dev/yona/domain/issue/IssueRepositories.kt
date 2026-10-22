package dev.yona.domain.issue

import dev.yona.domain.enumeration.State
import org.springframework.data.jpa.repository.JpaRepository

/** 이슈 담당자 저장소이다. */
interface AssigneeRepository : JpaRepository<Assignee, Long>

/** 이슈 공유자 저장소이다. */
interface IssueSharerRepository : JpaRepository<IssueSharer, Long> {

    /** 사용자가 이슈의 공유자인지 확인한다 (v1.6 `Issue.findSharerByUserId`). */
    fun existsByIssueIdAndUserId(issueId: Long, userId: Long): Boolean
}

/** 마일스톤 저장소이다. */
interface MilestoneRepository : JpaRepository<Milestone, Long> {

    /** 프로젝트의 마일스톤 중 지정한 상태인 것을 [sort] 순서로 조회한다 (v1.6 `Milestone.findMilestones`). */
    fun findByProjectIdAndState(projectId: Long, state: State, sort: org.springframework.data.domain.Sort): List<Milestone>
}

/** 이슈 라벨 저장소이다. */
interface IssueLabelRepository : JpaRepository<IssueLabel, Long> {

    /** 프로젝트의 라벨을 분류 이름, 라벨 이름 순으로 조회한다 (v1.6 `IssueLabel.findByProject`). */
    fun findByProjectIdOrderByCategoryNameAscNameAsc(projectId: Long): List<IssueLabel>

    /** 같은 프로젝트, 분류, 이름의 라벨이 있는지 확인한다 (v1.6 `IssueLabel.exists`). */
    fun existsByProjectIdAndCategoryIdAndName(projectId: Long, categoryId: Long, name: String): Boolean

    /** 분류에 속한 라벨을 조회한다. */
    fun findByCategoryId(categoryId: Long): List<IssueLabel>

    /** 프로젝트에서 분류에 속한 라벨의 수이다. */
    fun countByProjectIdAndCategoryId(projectId: Long, categoryId: Long): Long
}

/** 이슈 라벨 분류 저장소이다. */
interface IssueLabelCategoryRepository : JpaRepository<IssueLabelCategory, Long> {

    /** 프로젝트의 분류를 이름순으로 조회한다 (v1.6 `IssueLabelCategory.findByProject`). */
    fun findByProjectIdOrderByNameAsc(projectId: Long): List<IssueLabelCategory>

    /** 프로젝트에서 이름이 같은 분류를 조회한다 (v1.6 `IssueLabelCategory.findBy`). */
    fun findFirstByProjectIdAndName(projectId: Long, name: String): IssueLabelCategory?
}

/** 전역 라벨(언어, 라이선스, OS 등) 저장소이다. */
interface LabelRepository : JpaRepository<Label, Long>
