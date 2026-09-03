package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val MENTION_ADMIN_LOGIN_ID = "admin"
private const val ISSUE_MENTION_SHOW_LIMIT = 20

// yona ProjectApp.mentionList()/mentionListAtCommitDiff()/mentionListAtPullRequest() 대응 (P1-14, P1-42, P1-43).
@RestController
class MentionController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val watchRepository: WatchRepository,
    private val repositoryService: RepositoryService,
    private val commentThreadRepository: CommentThreadRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val commitCommentRepository: CommitCommentRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.READ)
    }

    @GetMapping("/api/{owner}/{projectName}/mentionList")
    fun mentionList(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "") query: String,
        @RequestParam(required = false, defaultValue = "") mentionType: String,
        @RequestParam(required = false) number: Long?,
        @RequestParam(required = false) resourceType: String?,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val loginUser = getLoginUser(authentication)
        if (!checkReadPermission(project, loginUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val result = mutableMapOf<String, List<Map<String, String>>>()

        if (mentionType.equals("user", ignoreCase = true)) {
            result["result"] = buildUserMentionList(project, query, loginUser, number, resourceType)
        }

        if (mentionType.equals("issue", ignoreCase = true)) {
            result["result"] = buildIssueMentionList(project, query)
        }

        return ResponseEntity.ok(result)
    }

    // yona addProjectMemberList/addGroupMemberList/addProjectAuthorsAndWatchersList/addSharers/
    // addSearchedUsers + collectedUsersToMentionList/addProjectNameToMentionList/addOrganizationNameToMentionList 대응
    private fun buildUserMentionList(
        project: Project,
        query: String,
        loginUser: User?,
        number: Long?,
        resourceType: String?
    ): List<Map<String, String>> {
        val candidates = LinkedHashMap<Long, User>()

        fun addCandidate(user: User?) {
            val id = user?.id ?: return
            candidates.putIfAbsent(id, user)
        }

        if (query.isBlank() || project.projectScope != ProjectScope.PUBLIC) {
            collectAuthorAndCommenter(project, number, resourceType).forEach { addCandidate(it) }
            projectUserRepository.findByProjectId(project.id!!).forEach { addCandidate(it.user) }
            project.organization?.let { org ->
                organizationUserRepository.findByOrganizationId(org.id!!).forEach { addCandidate(it.user) }
            }
            collectProjectAuthorsAndWatchers(project).forEach { addCandidate(it) }
            collectSharers(project, number, resourceType).forEach { addCandidate(it) }
        } else {
            userRepository.searchUsers(query, PageRequest.of(0, ISSUE_MENTION_SHOW_LIMIT)).content.forEach { addCandidate(it) }
        }

        // yona: userList.remove(currentUser); userList.add(currentUser) — 나를 항상 맨 뒤로 보낸다.
        val loginUserId = loginUser?.id
        if (loginUserId != null) {
            candidates.remove(loginUserId)
            candidates[loginUserId] = loginUser
        }

        val userMentions = toMentionMaps(candidates.values, loginUser).toMutableList()

        addProjectNameToMentionList(userMentions, project)
        addOrganizationNameToMentionList(userMentions, project)

        return userMentions
    }

    // yona collectedUsersToMentionList 대응
    // yona ProjectApp.collectedUsersToMentionList() 대응 (P1-58). "name"은 user.name이 아니라
    // user.getDisplayName()(요청한 사용자의 언어 설정에 따라 englishName+부서로 바뀔 수 있음), "searchText"는
    // name+getDisplayName()+loginId 3필드를 그대로 이어붙인다.
    private fun toMentionMaps(users: Collection<User>, currentUser: User?): List<Map<String, String>> {
        return users
            .filter { it.loginId.isNotBlank() && it.loginId != MENTION_ADMIN_LOGIN_ID }
            .map { user ->
                val displayName = currentUser?.let { user.getDisplayName(it) } ?: user.getDisplayName()
                mapOf(
                    "loginid" to user.loginId,
                    "searchText" to "${user.name}${displayName}${user.loginId}",
                    "name" to displayName,
                    "image" to user.avatarUrl
                )
            }
    }

    // yona ProjectApp.mentionListAtCommitDiff() 대응 (P1-43)
    @GetMapping("/api/{owner}/{projectName}/mentionListAtCommitDiff")
    fun mentionListAtCommitDiff(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "") commitId: String,
        @RequestParam(required = false, defaultValue = "-1") pullRequestId: Long,
        @RequestParam(required = false, defaultValue = "") query: String,
        @RequestParam(required = false, defaultValue = "") mentionType: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val loginUser = getLoginUser(authentication)
        if (!checkReadPermission(project, loginUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val fromProject = if (pullRequestId != -1L) {
            pullRequestRepository.findById(pullRequestId).orElse(null)?.fromProject ?: project
        } else {
            project
        }

        val result = mutableMapOf<String, List<Map<String, String>>>()

        if (mentionType.equals("user", ignoreCase = true)) {
            val candidates = LinkedHashMap<Long, User>()
            fun addCandidate(user: User?) {
                val id = user?.id ?: return
                candidates.putIfAbsent(id, user)
            }

            if (query.isBlank()) {
                if (commitId.isNotBlank()) {
                    collectCommitAuthor(fromProject, commitId).forEach { addCandidate(it) }
                    collectCodeCommenters(fromProject, commitId).forEach { addCandidate(it) }
                }
                projectUserRepository.findByProjectId(project.id!!).forEach { addCandidate(it.user) }
                project.organization?.let { org ->
                    organizationUserRepository.findByOrganizationId(org.id!!).forEach { addCandidate(it.user) }
                }
            } else {
                userRepository.searchUsers(query, PageRequest.of(0, ISSUE_MENTION_SHOW_LIMIT)).content.forEach { addCandidate(it) }
            }

            val loginUserId = loginUser?.id
            if (loginUserId != null) {
                candidates.remove(loginUserId)
                candidates[loginUserId] = loginUser
            }

            result["result"] = toMentionMaps(candidates.values, loginUser)
        }

        if (mentionType.equals("issue", ignoreCase = true)) {
            result["result"] = buildIssueMentionList(project, query)
        }

        return ResponseEntity.ok(result)
    }

    // yona ProjectApp.mentionListAtPullRequest() 대응 (P1-43)
    @GetMapping("/api/{owner}/{projectName}/mentionListAtPullRequest")
    fun mentionListAtPullRequest(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "") commitId: String,
        @RequestParam pullRequestId: Long,
        @RequestParam(required = false, defaultValue = "") query: String,
        @RequestParam(required = false, defaultValue = "") mentionType: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val loginUser = getLoginUser(authentication)
        if (!checkReadPermission(project, loginUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val result = mutableMapOf<String, List<Map<String, String>>>()

        if (mentionType.equals("user", ignoreCase = true)) {
            val candidates = LinkedHashMap<Long, User>()
            fun addCandidate(user: User?) {
                val id = user?.id ?: return
                candidates.putIfAbsent(id, user)
            }

            if (query.isBlank()) {
                collectCommentAuthors(pullRequest).forEach { addCandidate(it) }
                projectUserRepository.findByProjectId(project.id!!).forEach { addCandidate(it.user) }
                project.organization?.let { org ->
                    organizationUserRepository.findByOrganizationId(org.id!!).forEach { addCandidate(it.user) }
                }
                if (commitId.isNotBlank()) {
                    collectCommitAuthor(pullRequest.fromProject, commitId).forEach { addCandidate(it) }
                }
            } else {
                userRepository.searchUsers(query, PageRequest.of(0, ISSUE_MENTION_SHOW_LIMIT)).content.forEach { addCandidate(it) }
            }

            addCandidate(pullRequest.contributor)

            val loginUserId = loginUser?.id
            if (loginUserId != null) {
                candidates.remove(loginUserId)
                candidates[loginUserId] = loginUser
            }

            result["result"] = toMentionMaps(candidates.values, loginUser)
        }

        if (mentionType.equals("issue", ignoreCase = true)) {
            result["result"] = buildIssueMentionList(project, query)
        }

        return ResponseEntity.ok(result)
    }

    // yona addCommitAuthor 대응 - 커밋 작성자를 이메일로 조회하고, email prefix를 loginId로 보는 폴백 검색도 함께 시도한다.
    private fun collectCommitAuthor(project: Project, commitId: String): List<User> {
        val commit = try {
            repositoryService.getRepository(project).getCommit(commitId)
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val result = mutableListOf<User>()
        commit.getAuthor()?.let { result.add(it) }

        val authorEmail = commit.getAuthorEmail()
        if (authorEmail != null && authorEmail.contains("@")) {
            val byEmailPrefix = userRepository.findByLoginId(authorEmail.substringBefore("@")).orElse(null)
            if (byEmailPrefix != null && result.none { it.id == byEmailPrefix.id }) {
                result.add(byEmailPrefix)
            }
        }
        return result
    }

    // yona addCodeCommenters 대응 - 특정 커밋(PR에 속하지 않은)에 달린 코드 댓글의 작성자를 최근 순으로 모은다.
    private fun collectCodeCommenters(project: Project, commitId: String): List<User> {
        val isSvn = project.vcs?.uppercase() == "SUBVERSION" || project.vcs?.uppercase() == "SVN"
        val loginIds = LinkedHashSet<String>()

        if (!isSvn) {
            val threads = commentThreadRepository
                .findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, commitId)
                .reversed()
            for (thread in threads) {
                reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(thread.id!!).forEach { comment ->
                    comment.author?.loginId?.let { loginIds.remove(it); loginIds.add(it) }
                }
            }
        } else {
            commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, commitId).forEach { comment ->
                comment.author?.loginId?.let { loginIds.remove(it); loginIds.add(it) }
            }
        }

        return loginIds.toList().reversed().mapNotNull { userRepository.findByLoginId(it).orElse(null) }
    }

    // yona addCommentAuthors 대응 - PR의 코드리뷰 댓글 작성자를 최근 순으로 모은다.
    private fun collectCommentAuthors(pullRequest: PullRequest): List<User> {
        val threads = commentThreadRepository.findByPullRequest(pullRequest)
        val loginIds = LinkedHashSet<String>()
        for (thread in threads) {
            reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(thread.id!!).forEach { comment ->
                comment.author?.loginId?.let { loginIds.remove(it); loginIds.add(it) }
            }
        }
        return loginIds.toList().reversed().mapNotNull { userRepository.findByLoginId(it).orElse(null) }
    }

    // yona collectAuthorAndCommenter 대응 - 댓글 작성자를 최근 순으로, 마지막에 게시물 작성자를 추가한다.
    private fun collectAuthorAndCommenter(project: Project, number: Long?, resourceType: String?): List<User> {
        if (number == null || resourceType == null) return emptyList()

        val (commenterLoginIdsAsc, authorLoginId) = when {
            resourceType.equals("ISSUE_POST", ignoreCase = true) -> {
                val issue = issueRepository.findByProjectAndNumber(project, number) ?: return emptyList()
                val comments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)
                comments.mapNotNull { it.authorLoginId } to issue.authorLoginId
            }
            resourceType.equals("BOARD_POST", ignoreCase = true) -> {
                val posting = postingRepository.findByProjectAndNumber(project, number) ?: return emptyList()
                val comments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!)
                comments.mapNotNull { it.authorLoginId } to posting.authorLoginId
            }
            else -> return emptyList()
        }

        // yona: 댓글을 오래된 순으로 순회하며 등장할 때마다 리스트 끝으로 옮기고(remove+add), 마지막에 전체를 뒤집는다
        // => 결과적으로 최근에 댓글을 남긴 사람이 맨 앞에 온다.
        val ordered = LinkedHashSet<String>()
        commenterLoginIdsAsc.forEach {
            ordered.remove(it)
            ordered.add(it)
        }
        val loginIds = ordered.toList().reversed().toMutableList()
        if (authorLoginId != null && !loginIds.contains(authorLoginId)) {
            loginIds.add(authorLoginId)
        }

        return loginIds.mapNotNull { userRepository.findByLoginId(it).orElse(null) }
    }

    // yona Project.findAuthorsAndWatchers 대응 - 프로젝트의 이슈/게시글/PR 작성자와 프로젝트 워처를 모은다.
    private fun collectProjectAuthorsAndWatchers(project: Project): List<User> {
        val authorIds = LinkedHashSet<Long>()
        issueRepository.findByProject(project).forEach { it.authorId?.let { id -> authorIds.add(id) } }
        postingRepository.findByProject(project).forEach { it.authorId?.let { id -> authorIds.add(id) } }
        pullRequestRepository.findByToProject(project).forEach { pr -> pr.contributor.id?.let { authorIds.add(it) } }

        val watcherIds = watchRepository
            .findByResourceTypeAndResourceId(ResourceType.PROJECT, project.id.toString())
            .mapNotNull { it.user.id }

        val allIds = LinkedHashSet<Long>()
        allIds.addAll(authorIds)
        allIds.addAll(watcherIds)

        return allIds.mapNotNull { userRepository.findById(it).orElse(null) }
    }

    // yona addSharers 대응 - 이슈 공유자 목록(현재는 ISSUE_POST만 공유 기능이 있음)
    private fun collectSharers(project: Project, number: Long?, resourceType: String?): List<User> {
        if (number == null || !resourceType.equals("ISSUE_POST", ignoreCase = true)) return emptyList()
        val issue = issueRepository.findByProjectAndNumber(project, number) ?: return emptyList()
        return issue.sharers.map { it.user }
    }

    // yona addProjectNameToMentionList 대응 - "@project all:" 특수 멘션 항목을 추가한다.
    private fun addProjectNameToMentionList(users: MutableList<Map<String, String>>, project: Project) {
        val entry = mapOf(
            "loginid" to "${project.owner}/${project.name}",
            "username" to project.name,
            "name" to "@project all:",
            "searchText" to "${project.owner}/${project.name}/project/member/all",
            "image" to "/projects/${project.id}/logo"
        )
        if (users.size > 9) {
            val index = if (project.organization != null) 8 else 9
            users.add(index.coerceAtMost(users.size), entry)
        } else {
            users.add(entry)
        }
    }

    // yona addOrganizationNameToMentionList 대응 - "@group all:" 특수 멘션 항목을 추가한다.
    // (legacy는 size>9일 때 인덱스 9에 추가 + 항상 끝에도 추가해 중복이 생기는 버그가 있어, 여기서는 한 번만 추가한다.)
    private fun addOrganizationNameToMentionList(users: MutableList<Map<String, String>>, project: Project) {
        val organization = project.organization ?: return
        val entry = mapOf(
            "loginid" to organization.name,
            "username" to organization.name,
            "name" to "@group all: ",
            "searchText" to "${organization.name}/group/org/member/all",
            "image" to "/organizations/${organization.id}/logo"
        )
        if (users.size > 9) {
            users.add(9.coerceAtMost(users.size), entry)
        } else {
            users.add(entry)
        }
    }

    // yona getIssueList/collectedIssuesToMap/getMentionIssueList 대응
    private fun buildIssueMentionList(project: Project, query: String): List<Map<String, String>> {
        val issues = issueRepository.findForMention(project, query.trim(), PageRequest.of(0, ISSUE_MENTION_SHOW_LIMIT))
        return issues.map { issue ->
            mapOf(
                "name" to "${issue.number}${issue.title}",
                "issueNo" to (issue.number?.toString() ?: ""),
                "title" to issue.title
            )
        }
    }
}
