package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.NonRangedCodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEventRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestReview
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestService
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestTimelineItem
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.vcs.PushedBranch
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import jakarta.persistence.criteria.Predicate
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.time.Duration
import java.time.Instant
import java.util.Locale

@Controller
class PullRequestViewController(
    private val projectRepository: ProjectRepository,
    private val pullRequestService: PullRequestService,
    private val pullRequestRepository: PullRequestRepository,
    private val repositoryService: RepositoryService,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val commentThreadRepository: CommentThreadRepository,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val issueRepository: IssueRepository,
    private val accessControl: AccessControl,
    private val codeReviewService: CodeReviewService,
    private val pushedBranchRepository: PushedBranchRepository,
    private val watchService: WatchService,
    private val messageSource: MessageSource,
    private val attachmentRepository: AttachmentRepository
) {
    // 이슈 자동 닫기 정규식 패턴 (대소문자 구분 없이 close(s/d), fix(es/ed), resolve(s/d) #숫자).
    // "fix[e[s|d]]?" 부분이 대괄호를 중첩해 문자클래스로 잘못 해석되는 바람에(정규식은 [] 안에서
    // []를 중첩 지원하지 않음) fix/fixes/fixed 중 어느 것도 실제로 매치하지 못하던 실버그를
    // 커버리지 감사 중 발견해 수정(TASK-0270, 사용자 지시로 기능은 유지하고 정규식만 고침).
    private val closePattern = "(?i)(?:close[s|d]?|fix(?:es|ed)?|resolve[s|d]?)\\s+#(\\d+)".toRegex()


    @GetMapping("/{owner}/{projectName}/pulls")
    fun listPullRequests(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "open") state: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false) filter: String?,
        @RequestParam(required = false) contributorId: Long?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!checkMemberAccess(project, loginUser)) {
            // yona PullRequestApp.pullRequests()의 forbidden(ErrorViews.Forbidden.render(
            // "error.forbidden", project)) 대응(P-템플릿 #47) — 2-arg render(key, project)는
            // 컨텍스트 인지형 error/forbidden.html로 귀결된다.
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val stateEnum = when (state.lowercase()) {
            "closed" -> State.CLOSED
            "all" -> State.ALL
            else -> State.OPEN
        }
        val states = if (stateEnum == State.ALL) null else listOf(stateEnum)

        val pageable = PageRequest.of(page, ITEMS_PER_PAGE, Sort.by(Sort.Direction.DESC, "id"))
        val spec = buildPullRequestSpec(project, matchFromProject = false, states = states, filter = filter, contributorId = contributorId)
        val prPage = pullRequestRepository.findAll(spec, pageable)

        return renderList(model, project, loginUser, prPage, state, filter, contributorId)
    }

    // yona PullRequestApp.closedPullRequests 대응. CLOSED/MERGED 상태를 모두 "닫힌 PR"로 취급한다.
    @GetMapping("/{owner}/{projectName}/closedPullRequests")
    fun closedPullRequests(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false) filter: String?,
        @RequestParam(required = false) contributorId: Long?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!checkMemberAccess(project, loginUser)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val pageable = PageRequest.of(page, ITEMS_PER_PAGE, Sort.by(Sort.Direction.DESC, "id"))
        val spec = buildPullRequestSpec(project, matchFromProject = false, states = listOf(State.CLOSED, State.MERGED), filter = filter, contributorId = contributorId)
        val prPage = pullRequestRepository.findAll(spec, pageable)

        return renderList(model, project, loginUser, prPage, "closed", filter, contributorId)
    }

    // yona PullRequestApp.sentPullRequests 대응. 이 프로젝트가 출발지(fromProject)인 PR 목록.
    @GetMapping("/{owner}/{projectName}/sentPullRequests")
    fun sentPullRequests(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false) filter: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!checkMemberAccess(project, loginUser)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val pageable = PageRequest.of(page, ITEMS_PER_PAGE, Sort.by(Sort.Direction.DESC, "id"))
        // legacy git/partial_search.scala.html: sent 탭은 상세검색(보낸이) 사이드바가 없어
        // contributorId 조건을 받지 않는다(검색창 filter는 계속 지원).
        val spec = buildPullRequestSpec(project, matchFromProject = true, states = null, filter = filter, contributorId = null)
        val prPage = pullRequestRepository.findAll(spec, pageable)

        return renderList(model, project, loginUser, prPage, "sent", filter, null)
    }

    private fun checkMemberAccess(
        project: Project,
        loginUser: User?
    ): Boolean {
        return accessControl.isAllowed(loginUser, project, Operation.READ)
    }

    // yona PullRequestApp.SearchCondition(category/filter/contributorId) 대응(그룹11 #167/#182) —
    // yona는 별도 SearchCondition 클래스 없이 JPA Specification으로 동일한 3가지 조건(대상 프로젝트,
    // 상태 목록, 제목 필터, 보낸이)을 표현한다. matchFromProject=true면 "sent" 탭처럼 fromProject
    // 기준으로 검색한다.
    private fun buildPullRequestSpec(
        project: Project,
        matchFromProject: Boolean,
        states: List<State>?,
        filter: String?,
        contributorId: Long?
    ): Specification<PullRequest> = Specification { root, _, cb ->
        val predicates = mutableListOf<Predicate>()
        predicates += cb.equal(root.get<Project>(if (matchFromProject) "fromProject" else "toProject"), project)
        if (states != null) {
            predicates += root.get<State>("state").`in`(states)
        }
        if (!filter.isNullOrBlank()) {
            predicates += cb.like(cb.lower(root.get("title")), "%${filter.lowercase()}%")
        }
        if (contributorId != null) {
            predicates += cb.equal(root.get<User>("contributor").get<Long>("id"), contributorId)
        }
        cb.and(*predicates.toTypedArray())
    }

    private fun renderList(
        model: Model,
        project: Project,
        loginUser: User?,
        prPage: Page<PullRequest>,
        state: String,
        filter: String?,
        contributorId: Long?
    ): String {
        model.addAttribute("project", project)
        model.addAttribute("prPage", prPage)
        model.addAttribute("state", state)
        // legacy git/list.scala.html의 requestType 파라미터(현재 활성 탭: open/closed/sent) 대응.
        model.addAttribute("requestType", state)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("filter", filter ?: "")
        model.addAttribute("contributorId", contributorId)

        // 탭 뱃지 카운트: legacy conditionForOpen/Closed/Accepted/Sent(condition.clone.setCategory(...))
        // 대응 — 현재 filter/contributorId 조건은 유지한 채 상태(카테고리)만 바꿔 카운트한다.
        model.addAttribute("openCount", pullRequestRepository.count(buildPullRequestSpec(project, false, listOf(State.OPEN), filter, contributorId)))
        model.addAttribute("closedCount", pullRequestRepository.count(buildPullRequestSpec(project, false, listOf(State.CLOSED, State.MERGED), filter, contributorId)))
        if (project.isForkedFromOrigin) {
            model.addAttribute("acceptedCount", pullRequestRepository.count(buildPullRequestSpec(project, true, listOf(State.MERGED), filter, null)))
            model.addAttribute("sentCount", pullRequestRepository.count(buildPullRequestSpec(project, true, null, filter, null)))
        }

        // legacy User.findPullRequestContributorsByProjectId(project.id) 대응 — 상세검색 "보낸이" 드롭다운.
        model.addAttribute("contributors", pullRequestRepository.findDistinctContributorsByToProject(project))

        // legacy git/partial_list.scala.html: branchItemName(project.defaultBranch()) == branchItemName(req.toBranch)
        // 대응 — 목록에서 "기본 브랜치로 병합" 여부를 강조 표시하는 데 쓴다.
        model.addAttribute("defaultBranch", defaultBranchFor(project))

        // legacy git/partial_recently_pushed_branches.scala.html 대응 — 1시간 이내에 push된 브랜치를
        // (포크 프로젝트면 이 프로젝트 자신의, 원본 프로젝트면 로그인 사용자가 소유한 fork들의) 노출한다.
        val cutoff = Instant.now().minus(Duration.ofHours(1))
        val pushedBranches: List<PushedBranch> = if (project.isForkedFromOrigin) {
            pushedBranchRepository.findByProjectAndPushedDateAfter(project, cutoff)
        } else if (loginUser != null && accessControl.isProjectResourceCreatable(loginUser, project, ResourceType.PULL_REQUEST)) {
            pushedBranchRepository.findByOriginalProjectAndOwnerAndPushedDateAfter(project, loginUser.loginId, cutoff)
        } else {
            emptyList()
        }
        model.addAttribute("pushedBranches", pushedBranches)
        model.addAttribute(
            "pushedBranchDefaultBranches",
            pushedBranches.associate { (it.id ?: 0L) to defaultBranchFor(it.project) }
        )

        return "pullrequest/list"
    }

    // legacy git/partial_recently_pushed_branches.scala.html의 defaultBranch(project) 로컬 헬퍼
    // (isForkedFromOrigin이면 originalProject 기준, 아니면 자기 자신 기준 기본 브랜치) 대응.
    private fun defaultBranchFor(project: Project?): String {
        if (project == null) return "master"
        val target = project.originalProject ?: project
        return try {
            repositoryService.getRepository(target).getDefaultBranch().substringAfter("refs/heads/")
        } catch (e: Exception) {
            "master"
        }
    }

    // legacy git/view.scala.html 대응 (그룹11 #170) — legacy는 이 URL이 단일 "개요(overview)" 페이지고
    // "변경사항(changes)" 탭은 완전히 별도 URL(viewChangesInternal, #171)이다. 과거에는 이 컨트롤러가
    // ?tab=conversation/commits/changes 세 값을 받는 자체 확장 구조였으나(legacy에 없는 "commits" 탭
    // 포함), 이번 재작업에서 legacy 구조에 맞춰 tab 쿼리파라미터를 제거하고 항상 "overview"만 렌더링
    //하도록 되돌렸다 — "changes"는 /pull/{number}/changes 경로(viewChangesInternal)가 전담한다.
    @GetMapping("/{owner}/{projectName}/pull/{number}")
    fun viewPullRequest(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            // yona IsAllowedAction의 forbidden(ErrorViews.Forbidden.render("error.forbidden",
            // project)) 대응(P-템플릿 #47) — 2-arg render(key, project)는 컨텍스트 인지형
            // error/forbidden.html로 귀결된다.
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // yona IsAllowedAction의 resourceObject==null 분기(notFound(ErrorViews.NotFound.render(
        // "error.notfound", project, resourceType.resource()))) 대응(P-템플릿 #45) — 3-arg
        // render(key, project, type)는 컨텍스트 인지형 error/notfound.html로 귀결되지만,
        // PULL_REQUEST.resource()=="pull_request"는 notfound.html의 4개 targetType case(issue_post/
        // board_post/milestone/code) 중 어느 것과도 매치되지 않아 항상 제네릭 문구로 빠진다 —
        // targetType을 비워 그 실제 도달 분기를 그대로 재현한다(프로젝트 헤더/메뉴는 유지).
        val pullRequest = pullRequestService.getPullRequest(project.id!!, number) ?: run {
            model.addAttribute("project", project)
            return "error/notfound"
        }

        val mergeResult = try {
            pullRequestService.attemptMerge(pullRequest.id!!)
        } catch (e: Exception) {
            null
        }

        // legacy git/view.scala.html의 renderEventsOnPullRequest(pull) + partial_pull_request_event.
        // scala.html 대응(P2-39/P1-106 범위 재정정) — 이전 세션은 legacy가 PULL_REQUEST_COMMIT_CHANGED를
        // "case _ => {}"로 제외한다고 잘못 기록했으나(P2-39 코멘트), legacy partial_pull_request_event.
        // scala.html을 다시 대조해보면 COMMIT_CHANGED에 대한 전용 case가 있어 실제로는 렌더링한다 —
        // 이번 재작업에서 필터에 포함시켜 바로잡는다.
        val renderedEventTypes = setOf(
            EventType.PULL_REQUEST_STATE_CHANGED,
            EventType.PULL_REQUEST_MERGED,
            EventType.PULL_REQUEST_REVIEW_STATE_CHANGED,
            EventType.PULL_REQUEST_COMMIT_CHANGED
        )
        val events = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest)
            .filter { it.eventType in renderedEventTypes }
        val timeline = events.map {
            PullRequestTimelineItem(date = it.created, event = it)
        }.sortedBy { it.date }
        model.addAttribute("timeline", timeline)

        val referredIssues = getReferredIssues(pullRequest)
        model.addAttribute("referredIssues", referredIssues)

        addCommonPrAttributes(model, project, pullRequest, loginUser)

        // legacy git/view.scala.html의 AttachmentApp.getFileList(ResourceType.PULL_REQUEST, pull.id)
        // 대응 — PR 본문에 첨부된 파일 목록(issue/board view.html과 동일한 attachmentsJson 패턴).
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.PULL_REQUEST, pullRequest.id.toString())
        val attachmentsJson = attachments.joinToString(prefix = "{\"attachments\":[", postfix = "]}", separator = ",") { attach ->
            val id = attach.id?.toString() ?: ""
            val mimeType = attach.mimeType ?: ""
            val name = attach.name.replace("\"", "\\\"").replace("\n", "\\n")
            val url = "/files/${attach.id}"
            val size = attach.size?.toString() ?: "0"
            """{"id":"$id","mimeType":"$mimeType","name":"$name","url":"$url","size":$size}"""
        }
        model.addAttribute("attachmentsJson", attachmentsJson)

        model.addAttribute("project", project)
        model.addAttribute("pr", pullRequest)
        model.addAttribute("mergeResult", mergeResult)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("tab", "overview")

        return "pullrequest/view"
    }

    // legacy git/partial_info.scala.html(리뷰 참여/뱃지)과 git/partial_state.scala.html(브랜치
    // 삭제/복구 가능 여부)이 필요로 하는, overview/changes 두 화면이 공통으로 쓰는 속성들을 계산해
    // 모델에 채운다(그룹11 #170/#171 공통 부분).
    private fun addCommonPrAttributes(model: Model, project: Project, pullRequest: PullRequest, loginUser: User?) {
        val isWatching = loginUser?.let {
            watchService.isWatching(it, ResourceType.PULL_REQUEST, pullRequest.id.toString())
        } ?: false
        model.addAttribute("isWatching", isWatching)

        // legacy PullRequest.isAcceptable() 대응 — 열림 상태 + 충돌 없음 + 병합중 아님 + (리뷰어
        // 수 강제 프로젝트면) 참여 리뷰어 수가 최소 인원 이상.
        val meetsReviewerCount = !project.isUsingReviewerCount || pullRequest.reviewers.size >= project.defaultReviewerCount
        val isAcceptable = pullRequest.state == State.OPEN && pullRequest.isConflict != true &&
            pullRequest.isMerging != true && meetsReviewerCount
        model.addAttribute("isAcceptable", isAcceptable)
        val disabledAcceptReason = when {
            isAcceptable -> null
            pullRequest.isConflict == true -> messageSource.getMessage("pullRequest.is.not.safe", null, LocaleContextHolder.getLocale())
            pullRequest.isMerging == true -> messageSource.getMessage("pullRequest.is.merging", null, Locale.KOREA)
            !meetsReviewerCount -> messageSource.getMessage("pullRequest.is.not.safe", null, LocaleContextHolder.getLocale())
            else -> null
        }
        model.addAttribute("disabledAcceptReason", disabledAcceptReason)

        val openThreadCount = commentThreadRepository.findByPullRequest(pullRequest)
            .count { it.state == CommentThread.ThreadState.OPEN }
        model.addAttribute("openThreadCount", openThreadCount)

        // legacy git/partial_state.scala.html의 canDeleteBranch/canRestoreBranch 대응 — 병합된 PR의
        // 원본(from) 브랜치가 아직 존재하면 삭제 버튼을, 이미 삭제됐다면 복구 버튼을 보여준다.
        var canDeleteBranch = false
        var canRestoreBranch = false
        if (pullRequest.state == State.MERGED) {
            try {
                val refs = repositoryService.getRepository(pullRequest.fromProject).getRefNames()
                val exists = refs.any { it.substringAfter("refs/heads/") == pullRequest.fromBranch }
                canDeleteBranch = exists
                canRestoreBranch = !exists
            } catch (e: Exception) {
                // 브랜치 존재 여부를 확인할 수 없으면 두 버튼 모두 감춘다(안전한 기본값).
            }
        }
        model.addAttribute("canDeleteBranch", canDeleteBranch)
        model.addAttribute("canRestoreBranch", canRestoreBranch)

        // yona-wiki P3-15(PR 승인/변경요청 워크플로) — GitHub PR 페이지의 리뷰 상태 표시(초록
        // 체크=승인, 빨간 X=변경요청) 대응. 최신순으로 보여준다(활동 로그 성격 — GitHub의 Conversation
        // 탭이 리뷰 이벤트를 시간순으로 나열하는 것과 동일). require_approvals 정책 판단과 동일한
        // 알고리즘(getLatestReviewStates, 리뷰어별 최신 APPROVE/REQUEST_CHANGES만 유효)을 그대로
        // 재사용해 화면에도 일관된 "현재 승인 상태"를 보여준다.
        val reviews = pullRequestService.getReviews(pullRequest.id!!).sortedByDescending { it.createdDate }
        model.addAttribute("reviews", reviews)
        val latestReviewStates = pullRequestService.getLatestReviewStates(pullRequest.id!!)
        model.addAttribute("approvalCount", latestReviewStates.values.count { it == PullRequestReview.ReviewState.APPROVE })
        model.addAttribute("changesRequestedCount", latestReviewStates.values.count { it == PullRequestReview.ReviewState.REQUEST_CHANGES })
        model.addAttribute("latestReviewStateByReviewerId", latestReviewStates)
        // 설계 결정 3번(GitHub 방식) 회귀 대응 — 자기 자신의 PR에는 Approve/Request changes 버튼을
        // 아예 감춘다(Comment는 자기 PR에도 허용하므로 그대로 노출).
        model.addAttribute("canApproveOrRequestChanges", loginUser != null && loginUser.id != pullRequest.contributor.id)
    }

    private fun getReferredIssues(pullRequest: PullRequest): List<Issue> {
        val project = pullRequest.toProject
        val textsToSearch = mutableListOf<String>()

        textsToSearch.add(pullRequest.title)
        pullRequest.body?.let { textsToSearch.add(it) }

        val commits = pullRequestCommitRepository.findByPullRequest(pullRequest)
        for (commit in commits) {
            textsToSearch.add(commit.commitMessage)
        }

        val issueNumbers = mutableSetOf<Long>()
        for (text in textsToSearch) {
            closePattern.findAll(text).forEach { matchResult ->
                val numberStr = matchResult.groups[1]?.value
                if (numberStr != null) {
                    numberStr.toLongOrNull()?.let { issueNumbers.add(it) }
                }
            }
        }

        if (issueNumbers.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<Issue>()
        for (number in issueNumbers) {
            val issue = issueRepository.findByProjectAndNumber(project, number)
            if (issue != null) {
                result.add(issue)
            }
        }
        return result
    }

    // yona PullRequestApp.newPullRequestForm(...)?fromBranch=...&toBranch=... 대응(그룹11 #167/#168) —
    // git/partial_recently_pushed_branches.scala.html의 "풀 리퀘스트 보내기" 버튼이 이 쿼리 파라미터로
    // 브랜치를 미리 채워 링크한다. fromProjectId/toProjectId 쿼리로 fork 프로젝트 간(cross-fork) PR
    // 생성도 지원한다(Project.associationProjects 대응, TASK-0263에서 완성).
    @GetMapping("/{owner}/{projectName}/pull/new")
    fun createPullRequestForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false) fromBranch: String?,
        @RequestParam(required = false) toBranch: String?,
        @RequestParam(required = false) fromProjectId: Long?,
        @RequestParam(required = false) toProjectId: Long?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
            // yona validateBeforePullRequest()의 forbidden(ErrorViews.BadRequest.render(
            // "Guest is not allowed this request", project)) 대응 — HTTP 상태는 403이지만 뷰는
            // badrequest.html(2-arg render(key, project)가 컨텍스트 인지형으로 귀결)이고 메시지가
            // 메시지 키가 아닌 리터럴 영어 문장이라는 legacy의 특이 케이스다. Thymeleaf #{...}는
            // 실제 메시지 키를 요구해 리터럴 문자열을 그대로 재현할 수 없어, 같은 "프로젝트
            // 리소스에 대한 권한 없음" 성격의 다른 호출부들과 동일하게 error/forbidden(project)로
            // 단순화한다(문서화된 근사치, #47).
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val associationProjects = project.associationProjects
        val fromProject = resolveAssociatedProject(project, associationProjects, fromProjectId, isToProject = false)
        val toProject = resolveAssociatedProject(project, associationProjects, toProjectId, isToProject = true)

        val fromBranches = branchNamesOf(fromProject)
        val toBranches = branchNamesOf(toProject)

        if (fromBranches.isEmpty()) {
            model.addAttribute("project", fromProject)
            model.addAttribute("messageKey", "error.pullRequest.empty.from.repository")
            return "error/badrequest"
        }
        if (toBranches.isEmpty()) {
            model.addAttribute("project", toProject)
            model.addAttribute("messageKey", "error.pullRequest.empty.to.repository")
            return "error/badrequest"
        }

        model.addAttribute("project", project)
        model.addAttribute("associationProjects", associationProjects)
        model.addAttribute(
            "memberAssociationProjects",
            associationProjects.filter { projectUserRepository.existsByProjectIdAndUserId(it.id!!, loginUser.id!!) }
        )
        model.addAttribute("fromProject", fromProject)
        model.addAttribute("toProject", toProject)
        model.addAttribute("branches", fromBranches)
        model.addAttribute("fromBranches", fromBranches)
        model.addAttribute("toBranches", toBranches)
        model.addAttribute("defaultBranch", toBranches.firstOrNull() ?: "master")
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("prefillFromBranch", fromBranch?.takeIf { it.isNotBlank() } ?: fromBranches.first())
        model.addAttribute("prefillToBranch", toBranch?.takeIf { it.isNotBlank() } ?: toBranches.first())

        return "pullrequest/create"
    }

    // yona PullRequestApp.getSelectedProject() 대응(그룹11 #168). toProject는 fork인 프로젝트에서
    // 별도 지정이 없으면 원본 프로젝트(코드/PR 메뉴가 모두 켜져 있을 때만)로 기본 설정된다(자신의
    // 포크에서 PR 화면을 열면 "원본으로 보내기"가 기본값이 되는 legacy UX) — fromProject는 이런 기본
    // 전환이 없다. projectId 쿼리가 있으면 association 목록 안에서만 찾는다(범위 밖 프로젝트로 임의
    // 지정 방지).
    private fun resolveAssociatedProject(project: Project, associationProjects: List<Project>, projectId: Long?, isToProject: Boolean): Project {
        var selected = project
        if (isToProject && project.isForkedFromOrigin) {
            val origin = project.originalProject
            if (origin != null && origin.isCodeEnabled && origin.isPullRequestEnabled) {
                selected = origin
            }
        }

        if (projectId != null) {
            associationProjects.find { it.id == projectId }?.let { selected = it }
        }

        return selected
    }

    private fun branchNamesOf(project: Project): List<String> {
        return try {
            repositoryService.getRepository(project).getRefNames().map { it.substringAfter("refs/heads/") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // yona PullRequestApp.mergeResult() 대응 (#178, TASK-0257). legacy 라우트
    // "GET /:ownerName/:project/newPullRequest/mergeResult"의 대응 경로. PR 생성/수정 화면에서
    // from/to 브랜치를 바꿀 때마다 AJAX(GET, query string)로 호출해 커밋 프리뷰 + 충돌 여부 조각을
    // 돌려받는다. fromProjectId/toProjectId로 연관 프로젝트(fork) 간 PR도 지원한다
    // (createPullRequestForm()과 동일한 Project.associationProjects 기반 해석, TASK-0263에서 완성).
    // legacy validateBeforePullRequest()(ProjectUser.isGuest 체크) 대응은 createPullRequestForm()과
    // 동일한 멤버/그룹 접근 체크를 재사용한다.
    @GetMapping("/{owner}/{projectName}/pull/mergeResult")
    fun mergeResult(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false) fromBranch: String?,
        @RequestParam(required = false) toBranch: String?,
        @RequestParam(required = false) fromProjectId: Long?,
        @RequestParam(required = false) toProjectId: Long?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
            // createPullRequestForm()과 동일한 guest 체크(위 주석 참고) — error/forbidden(project)로 단순화.
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val associationProjects = project.associationProjects
        val fromProject = resolveAssociatedProject(project, associationProjects, fromProjectId, isToProject = false)
        val toProject = resolveAssociatedProject(project, associationProjects, toProjectId, isToProject = true)

        val fromBranches = branchNamesOf(fromProject)
        val toBranches = branchNamesOf(toProject)

        // legacy "StringUtils.defaultIfBlank(request().getQueryString("fromBranch"),
        // fromBranches.get(0).getName())" 대응 — 브랜치가 지정되지 않으면 첫 번째 브랜치를 기본값으로 쓴다.
        val resolvedFromBranch = fromBranch?.takeIf { it.isNotBlank() } ?: fromBranches.firstOrNull()
        val resolvedToBranch = toBranch?.takeIf { it.isNotBlank() } ?: toBranches.firstOrNull()

        if (resolvedFromBranch == null || resolvedToBranch == null) {
            model.addAttribute("fromProject", fromProject)
            model.addAttribute("pullRequestTitle", null)
            model.addAttribute("pullRequestBody", null)
            model.addAttribute("commits", emptyList<Any>())
            model.addAttribute("conflict", null)
            return "pullrequest/partial_merge_result :: mergeResult"
        }

        // legacy attemptMerge()/mergeResult() 모두 JGit 예외를 그대로 던지지만(500), 이 컨트롤러의
        // 다른 attemptMerge() 호출부(PR 상세/수정 화면 렌더링, line 274/566)와 동일하게 화면을 깨뜨리지
        // 않도록 실패 시 "변경 사항 없음"으로 완화한다.
        val preview = try {
            pullRequestService.previewMerge(fromProject, toProject, resolvedFromBranch, resolvedToBranch)
        } catch (e: Exception) {
            null
        }

        model.addAttribute("fromProject", fromProject)
        model.addAttribute("pullRequestTitle", preview?.suggestedTitle)
        model.addAttribute("pullRequestBody", preview?.suggestedBody)
        model.addAttribute("commits", preview?.commits ?: emptyList<Any>())
        model.addAttribute("conflict", preview?.conflict)

        return "pullrequest/partial_merge_result :: mergeResult"
    }

    // yona PullRequestApp.editPullRequestForm 대응. 실제 제목/본문 수정은
    // PullRequestController.updatePullRequest(PUT /api/.../pullrequests/{number})가 처리하므로,
    // 여기서는 기존 값이 채워진 폼만 렌더링하고 동일한 권한 체크(작성자 또는 매니저)만 수행한다.
    @GetMapping("/{owner}/{projectName}/pull/{number}/edit")
    fun editPullRequestForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        val pullRequest = pullRequestService.getPullRequest(project.id!!, number) ?: run {
            // viewPullRequest()와 동일한 IsAllowedAction notFound 분기 대응(P-템플릿 #45, 위 주석 참고).
            model.addAttribute("project", project)
            return "error/notfound"
        }

        if (!isManagerOrContributor(project, pullRequest.contributor.id, loginUser)) {
            // viewPullRequest()와 동일한 IsAllowedAction forbidden 분기 대응(P-템플릿 #47, 위 주석 참고).
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        model.addAttribute("project", project)
        model.addAttribute("pr", pullRequest)
        model.addAttribute("currentUser", loginUser)

        return "pullrequest/edit"
    }

    private fun isManagerOrContributor(
        project: Project,
        contributorId: Long?,
        user: User?
    ): Boolean {
        if (user == null) return false
        if (contributorId == user.id) return true
        return projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    @GetMapping(value = [
        "/{owner}/{projectName}/pull/{number}/changes",
        "/{owner}/{projectName}/pullRequest/{number}/changes"
    ])
    fun viewChanges(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        return viewChangesInternal(owner, projectName, number, null, authentication, model)
    }

    @GetMapping(value = [
        "/{owner}/{projectName}/pull/{number}/changes/{commitId}",
        "/{owner}/{projectName}/pullRequest/{number}/changes/{commitId}"
    ])
    fun viewSpecificChange(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @PathVariable commitId: String,
        authentication: Authentication?,
        model: Model
    ): String {
        return viewChangesInternal(owner, projectName, number, commitId, authentication, model)
    }

    // yona PullRequest.java:1063-1103 getCodeCommentThreadsForChanges() + git/viewChanges.scala.html:142
    // renderNonRangedThreads(pull.commentThreads.toList, commitId, ...) 대응 (P1-114). yona는 diff에 [GL-models_PullRequest-100]
    // 라인 단위로 붙는 CodeCommentThread(ranged)는 getCodeCommentThreadsForChanges()로 outdated/커밋
    // 필터링해 노출하고, PR 전체에 붙는 NonRangedCodeCommentThread는 필터링 없이(단 commitId 지정 시
    // 그 커밋 것만) 그대로 노출한다 — 서로 다른 두 목록이다. yona 템플릿(pullrequest/view.html,
    // code/diff.html)은 이 둘을 하나의 commentThreads 모델 속성으로 합쳐서 쓰므로, 여기서 두 필터를
    // 각각 적용한 뒤 합쳐서 반환한다.
    private fun buildCommentThreadsForChanges(pullRequest: PullRequest, commitId: String?): List<CommentThread> {
        val allThreads = commentThreadRepository.findByPullRequest(pullRequest)

        val rangedThreads = allThreads.filterIsInstance<CodeCommentThread>().let { ranged ->
            if (!commitId.isNullOrEmpty()) {
                ranged.filter { it.commitId == commitId }
            } else {
                ranged.filter { !it.isCommitComment() }
                    .filter { !codeReviewService.isThreadOutdated(it.id!!) }
            }
        }

        val nonRangedThreads = allThreads.filterIsInstance<NonRangedCodeCommentThread>()
            .filter { commitId.isNullOrEmpty() || it.commitId == commitId }

        return rangedThreads + nonRangedThreads
    }

    private fun viewChangesInternal(
        owner: String,
        projectName: String,
        number: Long,
        commitId: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            // viewPullRequest()와 동일한 IsAllowedAction forbidden 분기 대응(P-템플릿 #47, 위 주석 참고).
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // viewPullRequest()와 동일한 IsAllowedAction notFound 분기 대응(P-템플릿 #45, 위 주석 참고).
        val pullRequest = pullRequestService.getPullRequest(project.id!!, number) ?: run {
            model.addAttribute("project", project)
            return "error/notfound"
        }

        val mergeResult = try {
            pullRequestService.attemptMerge(pullRequest.id!!)
        } catch (e: Exception) {
            null
        }

        val diffs = try {
            if (commitId.isNullOrEmpty()) {
                pullRequestService.getDiff(pullRequest)
            } else {
                pullRequestService.getDiff(pullRequest, commitId)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val commentThreads = buildCommentThreadsForChanges(pullRequest, commitId)
        val referredIssues = getReferredIssues(pullRequest)

        addCommonPrAttributes(model, project, pullRequest, loginUser)

        model.addAttribute("project", project)
        model.addAttribute("pr", pullRequest)
        model.addAttribute("diffs", diffs)
        model.addAttribute("commentThreads", commentThreads)
        model.addAttribute("referredIssues", referredIssues)
        model.addAttribute("mergeResult", mergeResult)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("commitId", commitId)
        model.addAttribute("tab", "changes")

        return "pullrequest/view"
    }

    companion object {
        // yona models/PullRequest.java:66 ITEMS_PER_PAGE 대응 (P1-105) — PR 목록은 AbstractPostingApp과
        // 별개의 독립 상수(값은 동일 15)를 쓰며, 고정값이고 클라이언트 오버라이드가 없다.
        private const val ITEMS_PER_PAGE = 15
    }
}
