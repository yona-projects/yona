package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

interface IssueService {
    // yona models/support/IssueSearchCondition.java 대응 (P2-52) — assigned/created/mentioned/
    // favorite 필터는 해당 조건 하나만, all은 넷을 합쳐(중복 제거) updatedDate desc로 반환한다.
    fun getIssuesByFilter(filter: IssueFilterType, user: User): List<Issue>

    // yona AbstractPosting.isPublish(transient)/Issue.isDraft(영속) 대응 (P1-65).
    // isDraft=true면 State.DRAFT로 생성되고(신규 이슈 알림 미발행), 그 외엔 기존과 동일.
    //
    // yona controllers/api/IssueApi.java createIssuesNode()의 "number 필드가 있으면
    // saveWithNumber(number), 없으면 save()" 대응 (P2-56 복원) — explicitNumber가 주어지면
    // project.lastIssueNumber 카운터를 건드리지 않고 그 번호를 그대로 쓴다(legacy AbstractPosting.
    // saveWithNumber()와 동일 — 마이그레이션으로 과거 번호를 보존할 때 씀). sendNotification=false면
    // isDraft 여부와 무관하게 신규 이슈 알림을 발행하지 않는다(legacy "if (sendNotification) {
    // NotificationEvent.afterNewIssue(issue); }" 대응).
    fun createIssue(
        issue: Issue,
        author: User,
        assigneeUser: User? = null,
        milestoneId: Long? = null,
        labelIds: List<Long>? = null,
        isDraft: Boolean = false,
        explicitNumber: Long? = null,
        sendNotification: Boolean = true
    ): Issue

    fun updateIssue(
        issueId: Long,
        title: String,
        body: String,
        updater: User,
        assigneeUser: User? = null,
        milestoneId: Long? = null,
        labelIds: List<Long>? = null
    ): Issue

    fun changeState(issueId: Long, newState: State, updaterLoginId: String): Issue

    fun changeAssignee(issueId: Long, newAssigneeUser: User?, updaterLoginId: String): Issue

    fun changeMilestone(issueId: Long, newMilestoneId: Long?, updaterLoginId: String): Issue

    // yona IssueApp.editIssue()의 hasTargetProject()/moveIssueToOtherProject()/addIssueMovedNotification()
    // 대응 (P1-48). 이슈(및 서브태스크)를 다른 프로젝트로 옮기고, 번호를 새로 매기고, 라벨을 이전하며,
    // ISSUE_MOVED + NEW_ISSUE 알림을 발행한다.
    fun moveIssue(issueId: Long, targetProjectId: Long, mover: User): Issue

    // yona IssueApp.editIssue()의 "if (issue.isPublish) { ... }" 발행 전환 대응 (P1-65). 초안을
    // 정식 이슈로 발행한다 — createdDate 재설정, (DRAFT였다면) state DRAFT→OPEN, 프로젝트
    // lastIssueNumber 기준 재채번(생성 시 받았던 번호를 대체), 편집 이력 초기화, NEW_ISSUE 알림 발행.
    fun publishIssue(issueId: Long, publisher: User): Issue

    fun voteIssue(issueId: Long, user: User)
    fun unvoteIssue(issueId: Long, user: User)
    fun voteComment(commentId: Long, user: User)
    fun unvoteComment(commentId: Long, user: User)

    // yona IssueApi.java:1176-1210 upvoteWeight()/downvoteWeight() 대응 (P1-101). Issue.voters(공감 [GL-controllers_api_IssueApi-064;GL-controllers_api_IssueApi-065]
    // 투표)와는 별개로, 이슈 자체에 +1/-1 가중치를 매기는 정수 카운터.
    fun upvoteWeight(issueId: Long): Issue
    fun downvoteWeight(issueId: Long): Issue


    // yona Project.delete()의 이슈 삭제 시 연관 데이터(댓글/이벤트/즐겨찾기/첨부파일/타이틀헤드) 정리
    // 대응 (P0-19). issueRepository.delete(issue) 단독 호출은 IssueComment/IssueEvent/FavoriteIssue가
    // 전부 issue FK nullable=false라 FK 제약 위반으로 실패한다 — 반드시 이 함수를 통해 삭제해야 한다.
    fun deleteIssueCascade(issue: Issue)
}

// yona Issue.checkLabels()/IssueLabel.IssueLabelException 대응 (P1-80).
@ResponseStatus(
    value = HttpStatus.BAD_REQUEST,
    reason = "같은 배타(exclusive) 카테고리의 라벨을 두 개 이상 붙일 수 없습니다."
)
class IssueLabelExclusiveCategoryException(message: String) : RuntimeException(message)
