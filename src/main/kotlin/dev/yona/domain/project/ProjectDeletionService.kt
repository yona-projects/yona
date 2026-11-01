package dev.yona.domain.project

import dev.yona.domain.enumeration.ResourceType
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 프로젝트를 지운다. v1.6 `Project.delete()`(`app/models/Project.java:695-740`)와 `deletePullRequests`,
 * `deleteCommentThreads`, `deleteOriginal`에 해당한다.
 *
 * v1.6은 Ebean의 연쇄 삭제로 딸린 데이터를 지웠다. v2는 JPA 연관을 따라가지 않고 외래 키 순서대로 일괄 삭제문을 실행한다.
 * 한 트랜잭션이므로 중간에 실패하면 모두 취소된다.
 *
 * 지우는 것: 프로젝트가 보내고 받은 풀 리퀘스트(와 포크의 풀 리퀘스트), 그 코멘트 스레드와 이벤트, 프로젝트의 코멘트 스레드,
 * 이슈(코멘트, 이벤트, 라벨 연결, 투표, 공유, 즐겨찾기, 최근 본 기록), 게시글(코멘트), 웹훅(스레드), 담당자, 이슈 라벨과 분류, 마일스톤,
 * 구성원, 메뉴 설정, 푸시된 브랜치, 이전 요청, 알림 설정, 가입 신청, 즐겨찾기, 최근 방문, 제목 머리말, 코드 코멘트, 프로젝트 라벨 연결.
 * 포크는 지우지 않고 원본 연결만 끊는다. 다른 프로젝트 이슈의 부모 연결은 끊긴다(외래 키의 `SET NULL`).
 *
 * 이슈, 게시글, 댓글의 첨부와 알림 이벤트는 v1.6 `issue.delete()`, `posting.delete()`처럼 함께 지운다. v1.6과 같이 프로젝트 로고 같은
 * 프로젝트 자체의 첨부는 지우지 않는다([refactoring-backlog.md] R-18). 프로젝트와 풀 리퀘스트의 구독 기록은 v1.6 `ResourcePersistAdapter`처럼 지운다.
 */
@Service
@Transactional
class ProjectDeletionService(
    private val attachments: dev.yona.domain.misc.AttachmentService,
    private val issueService: dev.yona.domain.issue.IssueService,
    private val projectService: ProjectService,
    private val audit: dev.yona.domain.audit.AuditRecorder
) {

    @PersistenceContext
    private lateinit var em: EntityManager

    /** [project]와 딸린 데이터를 모두 지운다. 저장소 디렉터리는 호출한 쪽이 지운다. */
    fun delete(project: Project) {
        val projectId = requireNotNull(project.id)
        // 지우기 전에 이름과 소속을 담아 남긴다(같은 트랜잭션이라 지우기가 실패하면 기록도 사라진다)
        audit.record(dev.yona.domain.audit.auditProject("project.deleted", project, mapOf("scope" to project.projectScope?.name, "vcs" to project.vcs)))
        projectService.forget(project)

        // 이 프로젝트와 포크(원본 연결이 이 프로젝트인 프로젝트)의 풀 리퀘스트를 지운다.
        val forkIds = ids("select p.id from Project p where p.originalProject.id = :id", projectId)
        deletePullRequests(listOf(projectId) + forkIds)
        // v1.6 `ResourcePersistAdapter.postDelete`는 프로젝트(`Project.delete`)와 풀 리퀘스트(`PullRequest.delete`)의 구독, 구독 해제 기록도 지운다
        issueService.deleteWatchRows(ResourceType.PROJECT, projectId.toString())
        if (forkIds.isNotEmpty()) update("update Project p set p.originalProject = null where p.originalProject.id = :id", projectId)

        // 임시 저장 리뷰(X-26, D-38)의 숨겨진 스레드와 댓글도 지워야 하므로 엔티티의 숨김 조건을 타지 않는 SQL로 찾는다
        deleteThreads(threadIds("project_id = :id", projectId))
        deleteIssues(projectId)
        deletePostings(projectId)

        // 웹훅
        native("delete from webhook_thread where webhook_id in (select id from webhook where project_id = :id)", projectId)
        native("delete from webhook where project_id = :id", projectId)

        // 담당자, 라벨과 분류, 마일스톤
        native("delete from assignee where project_id = :id", projectId)
        native("delete from issue_issue_label where issue_label_id in (select id from issue_label where project_id = :id)", projectId)
        native("delete from pull_request_issue_label where issue_label_id in (select id from issue_label where project_id = :id)", projectId)
        native("delete from posting_issue_label where issue_label_id in (select id from issue_label where project_id = :id)", projectId)
        native("delete from issue_label where project_id = :id", projectId)
        native("delete from issue_label_category where project_id = :id", projectId)
        native("delete from milestone where project_id = :id", projectId)

        // 프로젝트에 직접 딸린 것
        listOf(
            "project_label", "project_user", "project_menu_setting", "project_pushed_branch", "project_transfer",
            "user_project_notification", "user_enrolled_project", "favorite_project", "recent_project", "project_visitation",
            "title_head", "commit_comment", "ruleset"
        ).forEach { native("delete from $it where project_id = :id", projectId) }

        native("delete from project where id = :id", projectId)
        em.flush()
        em.clear()
    }

    /** [projectIds]가 보내거나 받은 풀 리퀘스트와 그에 딸린 것을 지운다 (v1.6 `deletePullRequests`, `PullRequest.delete`). */
    private fun deletePullRequests(projectIds: List<Long>) {
        val rows = em.createQuery(
            "select pr.id, pr.contributor.loginId from PullRequest pr where pr.fromProject.id in :ids or pr.toProject.id in :ids",
            Array<Any>::class.java
        ).setParameter("ids", projectIds).resultList
        if (rows.isEmpty()) return
        val prIds = rows.map { it[0] as Long }
        prIds.forEach { issueService.deleteWatchRows(ResourceType.PULL_REQUEST, it.toString()) }

        // 풀 리퀘스트를 가리키는 이슈 이벤트
        rows.forEach { (id, loginId) ->
            em.createQuery(
                "delete from IssueEvent e where e.eventType = :type and e.newValue = :value and e.senderLoginId = :login"
            ).setParameter("type", dev.yona.domain.enumeration.EventType.ISSUE_REFERRED_FROM_PULL_REQUEST)
                .setParameter("value", id.toString()).setParameter("login", loginId).executeUpdate()
        }

        deleteThreads(threadIds("pull_request_id in (:id)", prIds))
        em.createQuery("delete from PullRequestCommit c where c.pullRequest.id in :ids").setParameter("ids", prIds).executeUpdate()
        em.createQuery("delete from PullRequestEvent e where e.pullRequest.id in :ids").setParameter("ids", prIds).executeUpdate()
        em.createNativeQuery("delete from pull_request_reviewers where pull_request_id in (:ids)").setParameter("ids", prIds).executeUpdate()
        // 추가 항목(X-14)의 담당자와 라벨 연결
        em.createNativeQuery("delete from pull_request_assignee where pull_request_id in (:ids)").setParameter("ids", prIds).executeUpdate()
        em.createNativeQuery("delete from pull_request_issue_label where pull_request_id in (:ids)").setParameter("ids", prIds).executeUpdate()
        em.createNativeQuery("delete from pull_request_review_request where pull_request_id in (:ids)").setParameter("ids", prIds).executeUpdate()
        // 추가 항목(X-12)의 리뷰 판정
        em.createNativeQuery("delete from pull_request_review where pull_request_id in (:ids)").setParameter("ids", prIds).executeUpdate()
        em.createQuery("delete from PullRequest pr where pr.id in :ids").setParameter("ids", prIds).executeUpdate()
    }

    /** 코멘트 스레드와 그 리뷰 코멘트, 코드 작성자 연결을 지운다 (v1.6 `CommentThread.delete`). */
    private fun deleteThreads(threadIds: List<Long>) {
        if (threadIds.isEmpty()) return
        em.createNativeQuery("delete from comment_thread_n4user where comment_thread_id in (:ids)").setParameter("ids", threadIds).executeUpdate()
        // 엔티티의 숨김 조건(`pending_date is null`)이 붙으면 임시 행이 남으므로 SQL로 지운다
        em.createNativeQuery("delete from review_comment where thread_id in (:ids)").setParameter("ids", threadIds).executeUpdate()
        em.createNativeQuery("delete from comment_thread where id in (:ids)").setParameter("ids", threadIds).executeUpdate()
    }

    /** 조건에 맞는 스레드의 번호이다. 임시 저장 리뷰의 임시 스레드도 포함한다(엔티티 질의는 이를 숨긴다). */
    private fun threadIds(condition: String, value: Any): List<Long> =
        em.createNativeQuery("select id from comment_thread where $condition").setParameter("id", value).resultList.map { (it as Number).toLong() }

    private fun deleteIssues(projectId: Long) {
        val issueIds = ids("select i.id from Issue i where i.project.id = :id", projectId)
        if (issueIds.isEmpty()) return
        // v1.6 `issue.delete()`는 댓글과 이슈의 첨부, 알림 이벤트를 지운다 (`Comment.delete`, `AbstractPosting.delete`).
        em.createQuery("select c.id from IssueComment c where c.issue.id in :ids", Long::class.javaObjectType).setParameter("ids", issueIds).resultList
            .forEach { removeAttachmentsAndEvents(ResourceType.ISSUE_COMMENT, it) }
        issueIds.forEach { removeAttachmentsAndEvents(ResourceType.ISSUE_POST, it) }
        em.createNativeQuery(
            "delete from issue_comment_voter where issue_comment_id in (select id from issue_comment where issue_id in (:ids))"
        ).setParameter("ids", issueIds).executeUpdate()
        em.createQuery("delete from IssueComment c where c.issue.id in :ids").setParameter("ids", issueIds).executeUpdate()
        em.createQuery("delete from IssueEvent e where e.issue.id in :ids").setParameter("ids", issueIds).executeUpdate()
        em.createNativeQuery("delete from issue_issue_label where issue_id in (:ids)").setParameter("ids", issueIds).executeUpdate()
        em.createNativeQuery("delete from issue_voter where issue_id in (:ids)").setParameter("ids", issueIds).executeUpdate()
        em.createQuery("delete from IssueSharer s where s.issue.id in :ids").setParameter("ids", issueIds).executeUpdate()
        em.createQuery("delete from FavoriteIssue f where f.issue.id in :ids").setParameter("ids", issueIds).executeUpdate()
        em.createQuery("delete from RecentIssue r where r.issue.id in :ids").setParameter("ids", issueIds).executeUpdate()
        em.createQuery("delete from Issue i where i.project.id = :id").setParameter("id", projectId).executeUpdate()
    }

    private fun deletePostings(projectId: Long) {
        // v1.6 `posting.delete()`도 댓글과 게시글의 첨부, 알림 이벤트를 지운다.
        ids("select c.id from PostingComment c where c.projectId = :id", projectId).forEach { removeAttachmentsAndEvents(ResourceType.NONISSUE_COMMENT, it) }
        ids("select p.id from Posting p where p.project.id = :id", projectId).forEach { removeAttachmentsAndEvents(ResourceType.BOARD_POST, it) }
        native("delete from posting_issue_label where posting_id in (select id from posting where project_id = :id)", projectId)
        native("delete from posting_comment where project_id = :id", projectId)
        native("delete from posting where project_id = :id", projectId)
    }

    /** 컨테이너에 붙은 첨부(같은 해시가 남지 않으면 파일도)와 알림 이벤트를 지운다 (v1.6 `Attachment.deleteAll`, `NotificationEvent.deleteBy`). */
    private fun removeAttachmentsAndEvents(type: ResourceType, id: Long) {
        attachments.deleteAll(type, id)
        issueService.deleteResourceRelations(type, id.toString())
    }

    private fun ids(jpql: String, projectId: Long): List<Long> =
        em.createQuery(jpql, Long::class.javaObjectType).setParameter("id", projectId).resultList

    private fun update(jpql: String, projectId: Long) {
        em.createQuery(jpql).setParameter("id", projectId).executeUpdate()
    }

    private fun native(sql: String, projectId: Long) {
        em.createNativeQuery(sql).setParameter("id", projectId).executeUpdate()
    }

    companion object {
        /**
         * 이 서비스가 직접 지우는 테이블이다. v1.6 외래 키 그래프에서 프로젝트로부터 닿는 모든 테이블이 여기에 있어야 하며
         * 테스트가 이를 확인한다. 새 테이블이 프로젝트를 참조하게 되면 목록과 [delete]를 함께 고친다.
         */
        val DELETED_TABLES: Set<String> = setOf(
            "assignee", "ruleset", "comment_thread", "comment_thread_n4user", "commit_comment", "favorite_issue", "favorite_project", "issue",
            "issue_comment", "issue_comment_voter", "issue_event", "issue_issue_label", "issue_label", "issue_label_category",
            "issue_sharer", "issue_voter", "milestone", "posting", "posting_comment", "posting_issue_label", "project_label",
            "project_menu_setting", "project_pushed_branch", "project_transfer", "project_user", "project_visitation", "pull_request",
            "pull_request_assignee", "pull_request_commit", "pull_request_event", "pull_request_issue_label", "pull_request_review", "pull_request_review_request", "pull_request_reviewers", "recent_issue", "recent_project", "review_comment",
            "title_head", "user_enrolled_project", "user_project_notification", "webhook", "webhook_thread"
        )
    }
}
