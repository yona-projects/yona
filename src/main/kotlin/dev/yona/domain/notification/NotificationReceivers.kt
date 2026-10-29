package dev.yona.domain.notification

import dev.yona.access.AccessControl
import dev.yona.access.GlobalResource
import dev.yona.access.ResourceLookup
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.project.Project
import dev.yona.domain.issue.IssueSharerRepository
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 알림을 받을 사용자를 계산한다. v1.6 `NotificationEvent`의 `getReceivers`, `getMandatoryReceivers`, `getReceiversForIssueBodyChanged`,
 * `getReceiversWhenAssigneeChanged`와 `Watch.findActualWatchers`, `AbstractPosting.getWatchers`(`app/models/NotificationEvent.java:1024-1150, 1377-1390`,
 * `app/models/Watch.java`)에 해당한다.
 *
 * 구독자의 "실제" 범위(프로젝트 구독자와 리소스 구독자에서 구독 해제자를 뺀 것)는 새 글과 삭제에 쓰고, 변경과 댓글에는 리소스를 직접 구독한 사용자와
 * 작성자, 담당자, 공유자, 프로젝트에서 이 이벤트를 받겠다고 한 사용자(필수 수신자)를 쓴다.
 */
@Service
class NotificationReceivers(
    private val watches: WatchRepository,
    private val unwatches: UnwatchRepository,
    private val projectNotifications: UserProjectNotificationRepository,
    private val users: UserRepository,
    private val userLookup: dev.yona.domain.user.UserLookup,
    private val issueSharers: IssueSharerRepository,
    private val issueComments: IssueCommentRepository,
    private val postingComments: PostingCommentRepository,
    private val lookup: ResourceLookup,
    private val accessControl: AccessControl,
    private val mentions: MentionService,
    private val commentThreads: dev.yona.domain.comment.CommentThreadRepository,
    private val relatedAuthors: dev.yona.repository.RelatedAuthorsService,
    private val repositories: dev.yona.repository.RepositoryService,
    private val projectUsers: dev.yona.domain.project.ProjectUserRepository,
    private val commitComments: dev.yona.domain.comment.CommitCommentRepository
) {

    /** 중복 없이 사용자를 모으는 집합이다. 사용자 번호가 같으면 같은 사용자이다. */
    private class Group {
        private val map = linkedMapOf<Long, User>()
        fun add(user: User?) { user?.id?.let { map.putIfAbsent(it, user) } }
        fun addAll(list: Collection<User>) = list.forEach { add(it) }
        fun remove(user: User?) { user?.id?.let { map.remove(it) } }
        fun removeAll(list: Collection<User>) = list.forEach { remove(it) }
        fun retain(predicate: (User) -> Boolean) { map.values.removeIf { !predicate(it) } }
        fun toSet(): Set<User> = LinkedHashSet(map.values)
    }

    // --- 구독자 ---

    /**
     * [type], [resourceId] 리소스의 실제 구독자이다(v1.6 `Watch.findActualWatchers`). [base]에 프로젝트 구독자와 리소스를 직접 구독한 사용자를 더하고
     * 리소스의 구독 해제자를 뺀 뒤 리소스를 읽을 수 없는 사용자를 거른다.
     */
    @Transactional(readOnly = true)
    fun actualWatchers(type: ResourceType, resourceId: String, base: Collection<User>, allowedOnly: Boolean = true): Set<User> {
        val resource = lookup.get(type, resourceId)
        val group = Group().also { it.addAll(base) }
        val project = if (resource is GlobalResource) null else resource.project
        project?.id?.let { group.addAll(watches.findByResourceTypeAndResourceId(ResourceType.PROJECT, it.toString()).mapNotNull { w -> w.user }) }
        group.addAll(watches.findByResourceTypeAndResourceId(type, resourceId).mapNotNull { it.user })
        group.removeAll(unwatches.findByResourceTypeAndResourceId(type, resourceId).mapNotNull { it.user })
        if (allowedOnly) group.retain { accessControl.isAllowed(it, resource, Operation.READ) }
        return group.toSet()
    }

    /**
     * 커밋의 실제 구독자이다. 커밋 작성자(알려진 사용자일 때)와 이 커밋에 코드 리뷰 댓글을 단 사용자에 프로젝트 구독자와 커밋 구독자를 더하고
     * 구독 해제자를 뺀다 (v1.6 `Commit.getWatchers`). [allowedOnly]이면 커밋을 읽을 수 없는 사용자는 거른다.
     */
    @Transactional(readOnly = true)
    fun commitWatchers(project: Project, commitId: String, authorEmail: String?, allowedOnly: Boolean): Set<User> {
        val projectId = requireNotNull(project.id)
        val base = ArrayList<User>()
        authorEmail?.let { userLookup.findByEmail(it) }?.let { base += it }
        (commentThreads.findCodeThreadsNotOnPullRequest(projectId, commitId) + commentThreads.findNonRangedThreadsNotOnPullRequest(projectId, commitId))
            .flatMap { it.reviewComments }.mapNotNull { c -> c.authorId?.let { users.findById(it).orElse(null) } }.forEach { base += it }
        return actualWatchers(ResourceType.COMMIT, "$projectId:$commitId", base, allowedOnly)
    }

    /**
     * Subversion 커밋의 실제 구독자이다. 커밋 작성자(로그인 ID로 찾은 사용자)와 이 커밋에 코멘트를 단 사용자에 프로젝트 구독자와 커밋 구독자를 더하고
     * 구독 해제자를 뺀다 (v1.6 `Commit.getWatchers`의 Subversion 분기).
     */
    @Transactional(readOnly = true)
    fun svnCommitWatchers(project: Project, commitId: String, authorLoginId: String?, allowedOnly: Boolean): Set<User> {
        val projectId = requireNotNull(project.id)
        val base = ArrayList<User>()
        authorLoginId?.takeIf { it.isNotEmpty() }?.let { users.findByLoginId(it) }?.let { base += it }
        commitComments.findByProjectIdAndCommitIdOrderByCreatedDateAscIdAsc(projectId, commitId).mapNotNull { c -> c.authorId?.let { users.findById(it).orElse(null) } }.forEach { base += it }
        return actualWatchers(ResourceType.COMMIT, "$projectId:$commitId", base, allowedOnly)
    }

    /** 이슈의 실제 구독자이다. 담당자와 추천자가 기본 구독자이고 작성자를 더한다. 이슈를 옮기기 전에 구독자를 정할 때도 쓴다 (v1.6 `Issue.getWatchers`). */
    @Transactional(readOnly = true)
    fun issueWatchers(issue: Issue): Set<User> =
        actualWatchers(ResourceType.ISSUE_POST, requireNotNull(issue.id).toString(), listOfNotNull(issue.assignee?.user) + issue.voters + listOfNotNull(author(issue.authorLoginId)))

    // --- 풀 리퀘스트 ---

    /**
     * 풀 리퀘스트의 실제 구독자이다. 보낸 사람과 리뷰 댓글을 단 사용자에 프로젝트 구독자와 풀 리퀘스트 구독자를 더하고 구독 해제자를 뺀다
     * (v1.6 `PullRequest.getWatchers`).
     */
    @Transactional(readOnly = true)
    fun pullRequestWatchers(pullRequest: dev.yona.domain.pullrequest.PullRequest, allowedOnly: Boolean = true): Set<User> {
        val id = requireNotNull(pullRequest.id)
        val base = ArrayList<User>()
        pullRequest.contributor?.let { base += it }
        commentThreads.findByPullRequestId(id).flatMap { it.reviewComments }.mapNotNull { c -> c.authorId?.let { users.findById(it).orElse(null) } }.forEach { base += it }
        return actualWatchers(ResourceType.PULL_REQUEST, id.toString(), base, allowedOnly)
    }

    /** 풀 리퀘스트 변경을 받을 사용자이다. 구독자와 본문에서 멘션된 사용자에서 [sender]를 뺀다 (v1.6 `getReceivers(sender, pullRequest)`). */
    @Transactional(readOnly = true)
    fun forPullRequest(pullRequest: dev.yona.domain.pullrequest.PullRequest, sender: User?): Set<User> = Group().also {
        it.addAll(pullRequestWatchers(pullRequest)); it.addAll(mentions.mentionedUsers(pullRequest.body.orEmpty())); it.remove(sender)
    }.toSet()

    /**
     * 리뷰 판정을 제출하거나 해제할 때 받을 사용자이다(추가 개발 항목 X-12, `decisions.md` D-35). v1.6 리뷰 완료 알림(`afterReviewed`)과 같이 풀 리퀘스트 구독자에
     * 작성자를 더하고 행위자 [sender]를 뺀다. 해제되는 리뷰어([extra])는 행위자가 아니면 더한다.
     */
    @Transactional(readOnly = true)
    fun forPullRequestReview(pullRequest: dev.yona.domain.pullrequest.PullRequest, sender: User?, extra: User? = null): Set<User> = Group().also {
        it.addAll(pullRequestWatchers(pullRequest)); it.add(pullRequest.contributor); it.add(extra); it.remove(sender)
    }.toSet()

    /**
     * 풀 리퀘스트의 담당자가 바뀔 때 받을 사용자이다(추가 항목 X-14, `decisions.md` D-34). v1.6 이슈 담당자 변경(`getReceiversWhenAssigneeChanged`)에 맞춰,
     * 풀 리퀘스트를 직접 구독한 사용자, 작성자, **새 담당자**, 프로젝트에서 이 종류를 받겠다고 한 사용자에서 구독 해제자, 이 종류를 받지 않겠다고 한 사용자,
     * [actor]를 뺀다. 이전 담당자([oldAssigneeLoginId])는 [actor]가 아니면 더한다.
     */
    @Transactional(readOnly = true)
    fun forPullRequestAssigneeChanged(pullRequest: dev.yona.domain.pullrequest.PullRequest, oldAssigneeLoginId: String?, actor: User?): Set<User> {
        val id = requireNotNull(pullRequest.id).toString()
        val projectId = requireNotNull(pullRequest.toProject?.id)
        val group = Group()
        group.addAll(watches.findByResourceTypeAndResourceId(ResourceType.PULL_REQUEST, id).mapNotNull { it.user })
        group.add(pullRequest.contributor)
        pullRequest.assignees.forEach { group.add(it) }
        group.addAll(eventUsers(projectId, EventType.PULL_REQUEST_ASSIGNEE_CHANGED, true))
        group.removeAll(unwatches.findByResourceTypeAndResourceId(ResourceType.PULL_REQUEST, id).mapNotNull { it.user })
        group.removeAll(eventUsers(projectId, EventType.PULL_REQUEST_ASSIGNEE_CHANGED, false))
        group.remove(actor)
        val old = oldAssigneeLoginId?.let { author(it) }
        if (old != null && old.loginId != actor?.loginId) group.add(old)
        return group.toSet()
    }

    /**
     * 새 풀 리퀘스트와 병합을 받을 사용자이다. [forPullRequest]에 병합 대상 코드를 마지막으로 고친 사람들을 더한다. 병합 커밋이 아직 없으면 더하지 않는다.
     * 바뀐 파일이 너무 많으면 프로젝트 구성원 모두를 더하고, 구하지 못하면 구한 만큼만 쓴다 (v1.6 `getReceiversWithRelatedAuthors`).
     */
    @Transactional(readOnly = true)
    fun forPullRequestWithRelatedAuthors(pullRequest: dev.yona.domain.pullrequest.PullRequest, sender: User?): Set<User> {
        val group = Group().also { it.addAll(pullRequestWatchers(pullRequest)); it.addAll(mentions.mentionedUsers(pullRequest.body.orEmpty())) }
        val from = pullRequest.mergedCommitIdFrom
        val to = pullRequest.mergedCommitIdTo
        if (from != null && to != null) {
            try {
                repositories.openRepository(requireNotNull(pullRequest.toProject)).use { group.addAll(relatedAuthors.find(it, from, to)) }
            } catch (e: dev.yona.repository.LimitExceededException) {
                projectUsers.findMembers(requireNotNull(pullRequest.toProject?.id), dev.yona.domain.enumeration.RoleType.SITEMANAGER.id).mapNotNull { it.user }.forEach { group.add(it) }
            } catch (e: Exception) {
                org.slf4j.LoggerFactory.getLogger(javaClass).warn("Failed to get authors related to the pullrequest {}", pullRequest.id, e)
            }
        }
        group.remove(sender)
        return group.toSet()
    }

    // --- 프로젝트 ---

    /**
     * 새 커밋 푸시를 받을 사용자이다. 프로젝트를 구독한 구성원에서 프로젝트 구독 해제자와 이 종류의 알림을 끈 사용자, 보낸 사람을 뺀다
     * (v1.6 `getProjectCommitReceivers`).
     */
    @Transactional(readOnly = true)
    fun forProjectCommits(project: Project, eventType: EventType, sender: User?): Set<User> {
        val projectId = requireNotNull(project.id)
        val members = projectUsers.findMembers(projectId, dev.yona.domain.enumeration.RoleType.SITEMANAGER.id).mapNotNull { it.user }.mapNotNull { it.id }.toSet()
        return Group().also { group ->
            group.addAll(watches.findByResourceTypeAndResourceId(ResourceType.PROJECT, projectId.toString()).mapNotNull { it.user }.filter { it.id in members })
            group.removeAll(unwatches.findByResourceTypeAndResourceId(ResourceType.PROJECT, projectId.toString()).mapNotNull { it.user })
            group.removeAll(eventUsers(projectId, eventType, false))
            group.remove(sender)
        }.toSet()
    }

    // --- 이슈 ---

    /** 새 이슈를 받을 사용자이다. 실제 구독자와 본문에서 멘션된 사용자에서 [author]를 뺀다 (v1.6 `getReceivers(issue, author)`). */
    @Transactional(readOnly = true)
    fun forNewIssue(issue: Issue, author: User): Set<User> = Group().also {
        it.addAll(issueWatchers(issue)); it.addAll(mentions.mentionedUsers(issue.body.orEmpty())); it.remove(author)
    }.toSet()

    /**
     * 이슈의 상태, 담당자, 마일스톤, 본문이 바뀔 때 받을 필수 수신자이다. 이슈를 직접 구독한 사용자, 작성자, 공유자, 담당자와 프로젝트에서 [eventType]을
     * 받겠다고 한 사용자에서 구독 해제자, 이를 받지 않겠다고 한 사용자, [actor]를 뺀다.
     */
    @Transactional(readOnly = true)
    fun mandatoryForIssue(issue: Issue, eventType: EventType, actor: User?): Set<User> {
        val issueId = requireNotNull(issue.id)
        val projectId = requireNotNull(issue.project?.id)
        val group = Group()
        group.addAll(watches.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, issueId.toString()).mapNotNull { it.user })
        group.add(author(issue.authorLoginId))
        issueSharers.findByIssueId(issueId).forEach { group.add(it.loginId?.let { login -> users.findByLoginIdIgnoreCase(login) }) }
        group.add(issue.assignee?.user)
        group.addAll(eventUsers(projectId, eventType, true))
        group.removeAll(unwatches.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, issueId.toString()).mapNotNull { it.user })
        group.removeAll(eventUsers(projectId, eventType, false))
        group.remove(actor ?: issue.authorId?.let { users.findById(it).orElse(null) })
        return group.toSet()
    }

    /** 담당자가 바뀔 때 받을 사용자이다. 필수 수신자에 이전 담당자(익명이 아니고 [actor]가 아닐 때)를 더한다. */
    @Transactional(readOnly = true)
    fun forAssigneeChanged(issue: Issue, oldAssigneeLoginId: String?, actor: User?): Set<User> {
        val group = Group().also { it.addAll(mandatoryForIssue(issue, EventType.ISSUE_ASSIGNEE_CHANGED, actor)) }
        val old = oldAssigneeLoginId?.let { author(it) }
        if (old != null && old.loginId != actor?.loginId) group.add(old)
        return group.toSet()
    }

    /** 본문이 바뀔 때 받을 사용자이다. 필수 수신자에 새로 멘션된 사용자를 더하고 [actor]를 뺀다 (v1.6 `getReceiversForIssueBodyChanged`). */
    @Transactional(readOnly = true)
    fun forBodyChanged(issue: Issue, oldBody: String, actor: User?): Set<User> {
        val group = Group().also { it.addAll(mandatoryForIssue(issue, EventType.ISSUE_BODY_CHANGED, actor)) }
        group.addAll(mentions.mentionedUsers(issue.body.orEmpty()) - mentions.mentionedUsers(oldBody).toSet())
        group.remove(actor ?: issue.authorId?.let { users.findById(it).orElse(null) })
        return group.toSet()
    }

    // --- 글 ---

    /** 새 글이나 삭제를 받을 사용자이다. 실제 구독자와 본문에서 멘션된 사용자에서 [except]를 뺀다 (v1.6 `getReceivers(abstractPosting, except)`). */
    @Transactional(readOnly = true)
    fun forPosting(posting: Posting, except: User?): Set<User> = Group().also {
        it.addAll(actualWatchers(ResourceType.BOARD_POST, requireNotNull(posting.id).toString(), listOfNotNull(author(posting.authorLoginId))))
        it.addAll(mentions.mentionedUsers(posting.body.orEmpty())); it.remove(except)
    }.toSet()

    /** 이슈 삭제를 받을 사용자이다 ([forPosting]과 같은 규칙). */
    @Transactional(readOnly = true)
    fun forDeletedIssue(issue: Issue, except: User?): Set<User> = Group().also {
        it.addAll(issueWatchers(issue)); it.addAll(mentions.mentionedUsers(issue.body.orEmpty())); it.remove(except)
    }.toSet()

    /** 글 본문이 바뀔 때 받을 필수 수신자이다. 글을 직접 구독한 사용자, 작성자와 프로젝트에서 이 이벤트를 받겠다고 한 사용자에서 구독 해제자 등을 뺀다. */
    @Transactional(readOnly = true)
    fun mandatoryForPosting(posting: Posting, eventType: EventType, actor: User?): Set<User> {
        val id = requireNotNull(posting.id).toString()
        val projectId = requireNotNull(posting.project?.id)
        val group = Group()
        group.addAll(watches.findByResourceTypeAndResourceId(ResourceType.BOARD_POST, id).mapNotNull { it.user })
        group.add(author(posting.authorLoginId))
        group.addAll(eventUsers(projectId, eventType, true))
        group.removeAll(unwatches.findByResourceTypeAndResourceId(ResourceType.BOARD_POST, id).mapNotNull { it.user })
        group.removeAll(eventUsers(projectId, eventType, false))
        group.remove(actor ?: posting.authorId?.let { users.findById(it).orElse(null) })
        return group.toSet()
    }

    // --- 댓글 ---

    /**
     * 이슈에 댓글이 달릴 때 알림을 받을 사용자이다. 저장되지 않은 댓글에도 쓴다(댓글 입력 중 수신자 미리 보기).
     *
     * 이슈의 구독자, 작성자, 담당자와 프로젝트에서 이 이벤트를 받겠다고 한 사용자가 받는다. [parentComment]가 있으면(한 줄 댓글) 이슈 작성자 대신
     * 부모 댓글의 작성자와 부모 댓글에서 멘션된 사용자가 받고, 부모 댓글 작성자가 답할 때는 앞의 한 줄 댓글 작성자도 받는다.
     * 구독을 해제했거나, 이 이벤트를 받지 않겠다고 했거나, 활성 상태가 아닌 사용자와 댓글을 쓰는 사람은 빠지고, 댓글에서 멘션된 사용자는 마지막에 더해진다.
     *
     * @param commentAuthorId 댓글을 쓰는 사용자의 번호
     * @param current 지금 로그인한 사용자. 없으면 시스템이 처리하는 것으로 보고 댓글 작성자를 뺀다
     */
    @Transactional(readOnly = true)
    fun forIssueComment(issue: Issue, commentAuthorId: Long?, contents: String, parentComment: IssueComment?, eventType: EventType, current: User?): Set<User> {
        val resourceId = requireNotNull(issue.id).toString()
        return commentReceivers(
            ResourceType.ISSUE_POST, resourceId, requireNotNull(issue.project?.id), issue.authorLoginId, issue.authorId, issue.assignee?.user,
            commentAuthorId, contents, parentComment?.let { Parent(it.authorId, it.contents.orEmpty(), issueComments.findByParentCommentIdOrderByIdAsc(requireNotNull(it.id)).mapNotNull { c -> c.authorId }) },
            eventType, current
        )
    }

    /** 글에 댓글이 달릴 때 알림을 받을 사용자이다. 규칙은 [forIssueComment]와 같고 담당자가 없다. */
    @Transactional(readOnly = true)
    fun forPostingComment(posting: Posting, commentAuthorId: Long?, contents: String, parentComment: PostingComment?, eventType: EventType, current: User?): Set<User> =
        commentReceivers(
            ResourceType.BOARD_POST, requireNotNull(posting.id).toString(), requireNotNull(posting.project?.id), posting.authorLoginId, posting.authorId, null,
            commentAuthorId, contents, parentComment?.let { Parent(it.authorId, it.contents.orEmpty(), postingComments.findByParentCommentIdOrderByIdAsc(requireNotNull(it.id)).mapNotNull { c -> c.authorId }) },
            eventType, current
        )

    private class Parent(val authorId: Long?, val contents: String, val childAuthorIds: List<Long>)

    private fun commentReceivers(
        type: ResourceType, resourceId: String, projectId: Long, postAuthorLogin: String?, postAuthorId: Long?, assignee: User?,
        commentAuthorId: Long?, contents: String, parent: Parent?, eventType: EventType, current: User?
    ): Set<User> {
        val group = Group()
        group.addAll(watches.findByResourceTypeAndResourceId(type, resourceId).mapNotNull { it.user })
        group.add(author(postAuthorLogin))
        group.add(assignee)
        if (parent != null) {
            group.remove(postAuthorId?.let { users.findById(it).orElse(null) })
            group.add(parent.authorId?.let { users.findById(it).orElse(null) })
            group.addAll(mentions.mentionedUsers(parent.contents))
            if (parent.authorId == commentAuthorId) parent.childAuthorIds.forEach { group.add(users.findById(it).orElse(null)) }
        }
        group.addAll(eventUsers(projectId, eventType, true))
        group.removeAll(unwatches.findByResourceTypeAndResourceId(type, resourceId).mapNotNull { it.user })
        group.removeAll(eventUsers(projectId, eventType, false))
        group.retain { it.state == UserState.ACTIVE }
        // 로그인하지 않았으면 시스템이 작성자를 대신해 처리하는 것으로 보고 작성자를 뺀다.
        group.remove(current ?: commentAuthorId?.let { users.findById(it).orElse(null) })
        group.addAll(mentions.mentionedUsers(contents))
        return group.toSet()
    }

    private fun author(loginId: String?): User? = loginId?.let { users.findByLoginIdIgnoreCase(it) }

    private fun eventUsers(projectId: Long, eventType: EventType, allowed: Boolean): List<User> =
        projectNotifications.findByProjectIdAndNotificationTypeAndAllowed(projectId, eventType, allowed).mapNotNull { it.user }
}
