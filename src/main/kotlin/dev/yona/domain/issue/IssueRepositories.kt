package dev.yona.domain.issue

import org.springframework.data.jpa.repository.Query
import dev.yona.domain.enumeration.State
import org.springframework.data.jpa.repository.JpaRepository

/** 이슈 담당자 저장소이다. */
interface AssigneeRepository : JpaRepository<Assignee, Long> {

    /** 프로젝트에서 사용자를 담당자로 등록한 행을 찾는다 (v1.6 `Assignee.add`). */
    fun findFirstByUserIdAndProjectId(userId: Long, projectId: Long): Assignee?

    /** 프로젝트의 담당자 행을 번호 순으로 조회한다 (v1.6 `project.assignees`). */
    fun findByProjectIdOrderByIdAsc(projectId: Long): List<Assignee>
}

/** 이슈 타임라인 이벤트 저장소이다. */
interface IssueEventRepository : JpaRepository<IssueEvent, Long> {

    /** 이슈의 이벤트 중 [since] 이후에 생긴 가장 마지막 것을 찾는다 (v1.6 `IssueEvent.add`가 합칠 대상). */
    fun findFirstByIssueIdAndCreatedAfterOrderByIdDesc(issueId: Long, since: java.util.Date): IssueEvent?

    /** 풀 리퀘스트 번호 [newValue]를 가리키는 [eventType] 이벤트 중 [senderLoginId]가 만든 것이다 (v1.6 `PullRequest.deleteIssueEvents`). */
    fun findByNewValueAndSenderLoginIdAndEventType(newValue: String, senderLoginId: String, eventType: dev.yona.domain.enumeration.EventType): List<IssueEvent>

    /** 이슈의 이벤트를 발생 시각 순으로 조회한다 (v1.6 `Issue.getTimeline`). */
    fun findByIssueIdOrderByCreatedAscIdAsc(issueId: Long): List<IssueEvent>
}

/** 이슈 공유자 저장소이다. */
interface IssueSharerRepository : JpaRepository<IssueSharer, Long> {

    /** 사용자가 이슈의 공유자인지 확인한다 (v1.6 `Issue.findSharerByUserId`). */
    fun existsByIssueIdAndUserId(issueId: Long, userId: Long): Boolean

    /** 이슈의 공유자 행을 모두 조회한다. */
    fun findByIssueId(issueId: Long): List<IssueSharer>

    /** 이슈의 공유자 행 중 [loginIds]에 해당하는 것을 추가된 날짜 순으로 조회한다 (v1.6 `IssueApi.findSharerByloginIds`). */
    fun findByIssueIdAndLoginIdInOrderByCreatedAscIdAsc(issueId: Long, loginIds: Collection<String>): List<IssueSharer>

    /** 이슈에서 로그인 아이디로 공유자 행을 찾는다. */
    fun findFirstByIssueIdAndLoginId(issueId: Long, loginId: String): IssueSharer?

    /** 사용자가 공유받은 열린 이슈 수이다 (v1.6 `IssueSharer.getNumberOfIssuesSharedWithUser`). */
    @Query("select count(s) from IssueSharer s where s.user.id = :userId and s.issue.state = dev.yona.domain.enumeration.State.OPEN")
    fun countOpenSharedWith(userId: Long): Long
}

/** 마일스톤 저장소이다. */
interface MilestoneRepository : JpaRepository<Milestone, Long> {

    /** 프로젝트의 마일스톤 중 지정한 상태인 것을 [sort] 순서로 조회한다 (v1.6 `Milestone.findMilestones`). */
    fun findByProjectIdAndState(projectId: Long, state: State, sort: org.springframework.data.domain.Sort): List<Milestone>

    /** 프로젝트의 모든 상태의 마일스톤을 [sort] 순서로 조회한다. */
    fun findByProjectId(projectId: Long, sort: org.springframework.data.domain.Sort): List<Milestone>

    /** 프로젝트에서 제목이 같은 마일스톤을 찾는다 (v1.6 `Milestone.findMilestoneByTitle`). */
    fun findFirstByProjectIdAndTitle(projectId: Long, title: String): Milestone?

    /** 프로젝트에 같은 제목의 마일스톤이 있는지 확인한다 (v1.6 `Milestone.isUniqueProjectIdAndTitle`의 반대). */
    fun existsByProjectIdAndTitle(projectId: Long, title: String): Boolean
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
interface LabelRepository : JpaRepository<Label, Long> {

    /** 분류와 이름이 같은 라벨이다. [category]가 `null`이면 분류가 비어 있는 라벨을 찾는다(v1.6 `eq("category", null)`). */
    fun findFirstByCategoryAndName(category: String?, name: String?): Label?

    /** 분류와 이름에 [category], [query]가 대소문자 없이 들어 있는 라벨의 수이다 (v1.6 `icontains`). */
    @Query("select count(l) from Label l where lower(l.category) like lower(concat('%', :#{T(dev.yona.util.LikeEscape).escape(#category)}, '%')) escape '\\' and lower(l.name) like lower(concat('%', :#{T(dev.yona.util.LikeEscape).escape(#query)}, '%')) escape '\\'")
    fun countMatching(category: String, query: String): Long

    /** [countMatching]과 같은 조건의 라벨을 아이디순으로 조회한다. */
    @Query("select l from Label l where lower(l.category) like lower(concat('%', :#{T(dev.yona.util.LikeEscape).escape(#category)}, '%')) escape '\\' and lower(l.name) like lower(concat('%', :#{T(dev.yona.util.LikeEscape).escape(#query)}, '%')) escape '\\' order by l.id")
    fun findMatching(category: String, query: String, pageable: org.springframework.data.domain.Pageable): List<Label>

    /** 분류에 [query]가 대소문자 없이 들어 있는 겹치지 않는 분류의 수이다. */
    @Query("select count(distinct l.category) from Label l where lower(l.category) like lower(concat('%', :#{T(dev.yona.util.LikeEscape).escape(#query)}, '%')) escape '\\'")
    fun countCategories(query: String): Long

    /** [countCategories]와 같은 조건의 분류를 이름순으로 조회한다. */
    @Query("select distinct l.category from Label l where lower(l.category) like lower(concat('%', :#{T(dev.yona.util.LikeEscape).escape(#query)}, '%')) escape '\\' order by l.category")
    fun findCategories(query: String, pageable: org.springframework.data.domain.Pageable): List<String>
}
