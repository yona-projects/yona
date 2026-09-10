package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.board.PostingService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import org.springframework.beans.factory.annotation.Value
import com.github.yonaprojects.yona.domain.vcs.BareCommit
import com.github.yonaprojects.yona.domain.support.LineEnding
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import org.eclipse.jgit.lib.Constants
import tools.jackson.databind.ObjectMapper
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.role.RoleType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Controller
class BoardViewController(
    private val projectRepository: ProjectRepository,
    private val postingService: PostingService,
    private val postingRepository: PostingRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val watchService: WatchService,
    private val attachmentRepository: AttachmentRepository,
    private val objectMapper: ObjectMapper,
    private val repositoryService: RepositoryService,
    @Value("\${yona.git.base-dir:/tmp/yona/git}")
    private val gitBaseDir: String,
    private val recentIssueService: RecentIssueService,
    private val accessControl: AccessControl,
    private val attachmentService: AttachmentService
) {

    @GetMapping("/{owner}/{projectName}/posts")
    fun listPosts(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false) pageNum: Int?,
        @RequestParam(required = false) filter: String?,
        @RequestParam(required = false, defaultValue = "createdDate") orderBy: String,
        @RequestParam(required = false, defaultValue = "desc") orderDir: String,
        @RequestParam(required = false) labelIds: List<Long>?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val actualPage = if (pageNum != null) {
            if (pageNum > 0) pageNum - 1 else 0
        } else {
            page
        }

        val sort = if (orderDir.equals("asc", ignoreCase = true)) {
            Sort.by(Sort.Direction.ASC, orderBy)
        } else {
            Sort.by(Sort.Direction.DESC, orderBy)
        }
        // 게시글 목록 페이지 크기는 고정 15, 클라이언트 오버라이드 없음.
        val pageable = PageRequest.of(actualPage, ITEMS_PER_PAGE, sort)

        val labelFilter = labelIds?.filterNotNull()?.takeIf { it.isNotEmpty() }
        val postingPage = if (labelFilter != null) {
            postingRepository.findByProjectAndLabelIdsIn(
                project, labelFilter, if (filter.isNullOrBlank()) null else "%$filter%", pageable
            )
        } else if (!filter.isNullOrBlank()) {
            postingRepository.searchPostingsInProject(project, "%$filter%", pageable)
        } else {
            postingRepository.findByProjectAndNotice(project, false, pageable)
        }
        val notices = postingService.getNotices(project.id!!)

        model.addAttribute("project", project)
        model.addAttribute("postingPage", postingPage)
        model.addAttribute("notices", notices)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("filter", filter)
        model.addAttribute("orderBy", orderBy)
        model.addAttribute("orderDir", orderDir)
        model.addAttribute("labelIds", labelFilter ?: emptyList<Long>())

        return "board/list"
    }

    @GetMapping("/{owner}/{projectName}/post/{number}")
    fun viewPost(
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
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val posting = postingService.getPosting(project.id!!, number) ?: run {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "board_post")
            return "error/notfound"
        }
        val comments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!)

        if (loginUser != null) {
            try {
                recentIssueService.recordPostingVisit(loginUser, posting)
            } catch (e: Exception) {
                // NOOP: 방문 이력 기록 실패가 게시글 조회 자체를 막지 않아야 한다
            }
        }

        val isWatching = loginUser?.let {
            watchService.isWatching(it, ResourceType.BOARD_POST, posting.id.toString())
        } ?: false

        val isAllowedUpdate = loginUser != null && (posting.authorLoginId == loginUser.loginId || projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) || accessControl.isAllowedIfGroupMember(project, loginUser))

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, posting.id.toString())
        val attachmentsList = attachments.map { attach ->
            mapOf(
                "id" to (attach.id?.toString() ?: ""),
                "mimeType" to (attach.mimeType ?: ""),
                "name" to attach.name,
                "url" to "/files/${attach.id}",
                "size" to (attach.size?.toString() ?: "0")
            )
        }
        val attachmentsJson = objectMapper.writeValueAsString(mapOf("attachments" to attachmentsList))

        // legacy board/partial_comments.scala.html의 childComments 대응 — 대댓글은 최상위 댓글
        // 목록에서 제외하고 부모별로 묶어 common/childComments에 넘긴다(issue/view.html과 동일한 패턴).
        val topLevelComments = comments.filter { it.parentComment == null }
        val childCommentsByParentId: Map<Long, List<PostingComment>> =
            comments.filter { it.parentComment != null }
                .groupBy { it.parentComment!!.id!! }

        val isProjectManager = loginUser != null && projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        model.addAttribute("project", project)
        model.addAttribute("post", posting)
        model.addAttribute("comments", topLevelComments)
        model.addAttribute("childCommentsByParentId", childCommentsByParentId)
        model.addAttribute("isProjectManager", isProjectManager)
        model.addAttribute("commentApiBase", "/api/projects/${project.id}/posts/${posting.number}/comments")
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("isWatching", isWatching)
        model.addAttribute("isAllowedUpdate", isAllowedUpdate)
        model.addAttribute("attachmentsJson", attachmentsJson)
        // P3-50: 댓글 수정 폼(common/commentUpdateForm)이 "이미 첨부된 파일" 목록을 보여주려면
        // 댓글별 첨부파일 목록이 필요하다 — issue/view.html과 동일한 패턴.
        model.addAttribute(
            "commentAttachmentsByCommentId",
            comments.associate { comment ->
                comment.id!! to attachmentRepository.findByContainerTypeAndContainerId(
                    ResourceType.NONISSUE_COMMENT, comment.id.toString()
                )
            }
        )

        return "board/view"
    }

    @GetMapping(value = ["/{owner}/{projectName}/post/new", "/{owner}/{projectName}/postform"])
    fun createPostForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false) readme: Boolean?,
        @RequestParam(required = false) issueTemplate: Boolean?,
        @RequestParam(required = false) branch: String?,
        @RequestParam(required = false) path: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        // yona BoardApp.java @IsCreatable(ResourceType.BOARD_POST) 대응. 공개 프로젝트의
        // 비멤버 로그인 사용자도 게시글을 쓸 수 있는데(다른 리소스 타입과 동일 규칙), 여기서는
        // 프로젝트 멤버/그룹멤버로만 좁게 검사해 yona보다 과도하게 제한하고 있었다 — yona
        // IssueViewController.createIssueForm이 이미 쓰고 있는 정답 패턴을 그대로 재사용.
        if (!accessControl.isProjectResourceCreatable(loginUser, project, ResourceType.BOARD_POST)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val isAllowedToNotice = loginUser != null && (projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) || accessControl.isAllowedIfGroupMember(project, loginUser))

        // yona board/create.scala.html의 titleMessage 대응 — README/이슈 템플릿 편집 진입점은
        // ("만들기"/"편집" 버튼 둘 다) 기존 게시글의 실제 제목을 조회하지 않고 항상 이 create
        // 폼으로 온다(newFork()처럼 별도 "편집" 화면이 없다 — legacy도 동일한 구조). legacy는 그
        // 대신 title 필드를 고정 문자열로 미리 채워둬 매번 다시 입력할 필요가 없게 했는데, 이
        // 프리필이 빠져 있어 재편집할 때마다 필수 입력값을 새로 타이핑해야 했다(빈 문자열로는
        // 저장 자체가 막힘).
        var preparedTitle = ""
        var preparedPostBody = ""
        if (readme == true) {
            preparedTitle = "Update README.md"
            preparedPostBody = readReadmeCandidate(project)
        } else if (issueTemplate == true) {
            preparedTitle = "ISSUE_TEMPLATE.md: Project Issue Template"
            preparedPostBody = getIssueTemplate(project)
        } else if (!path.isNullOrBlank()) {
            try {
                val bytes = repositoryService.getRepository(project).getRawFile(branch ?: "HEAD", path)
                if (bytes != null) {
                    preparedPostBody = String(bytes, StandardCharsets.UTF_8)
                }
            } catch (e: Exception) {}
        }

        // yona board/create.scala.html 대응 — readme 체크박스는 커밋 생성 권한이 있고, Git
        // 프로젝트이고, ?readme= 쿼리로 열렸을 때만 보인다(보이면 항상 체크된 상태). yona는
        // 그동안 이 checkbox를 hidden input으로 값만 전달하고 있었을 뿐 사용자에게 보여주지
        // 않았음 — 실제 체크박스로 복구.
        val canReadmefy = readme == true &&
            project.vcs?.uppercase() == "GIT" &&
            accessControl.isProjectResourceCreatable(loginUser, project, ResourceType.COMMIT)

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("isAllowedToNotice", isAllowedToNotice)
        model.addAttribute("readme", readme ?: false)
        model.addAttribute("canReadmefy", canReadmefy)
        model.addAttribute("preparedPostBody", preparedPostBody)
        model.addAttribute("preparedTitle", preparedTitle)

        return "board/create"
    }

    @GetMapping("/{owner}/{projectName}/post/{number}/editform")
    fun editPostForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val posting = postingService.getPosting(project.id!!, number) ?: run {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "board_post")
            return "error/notfound"
        }

        val isAllowedToNotice = loginUser != null && (projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) || accessControl.isAllowedIfGroupMember(project, loginUser))

        // yona board/edit.scala.html 대응 — readme 체크박스는 커밋 생성 권한이 있고 Git 프로젝트일
        // 때만 보이며(생성 화면과 달리 쿼리파라미터 조건은 없음), 현재 posting.readme 값을 그대로
        // 반영해 토글 가능해야 한다.
        val canReadmefy = project.vcs?.uppercase() == "GIT" &&
            accessControl.isProjectResourceCreatable(loginUser, project, ResourceType.COMMIT)

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, posting.id.toString())
        val attachmentsList = attachments.map { attach ->
            mapOf(
                "id" to (attach.id?.toString() ?: ""),
                "mimeType" to (attach.mimeType ?: ""),
                "name" to attach.name,
                "url" to "/files/${attach.id}",
                "size" to (attach.size?.toString() ?: "0")
            )
        }
        val attachmentsJson = objectMapper.writeValueAsString(mapOf("attachments" to attachmentsList))

        model.addAttribute("project", project)
        model.addAttribute("post", posting)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("isAllowedToNotice", isAllowedToNotice)
        model.addAttribute("canReadmefy", canReadmefy)
        model.addAttribute("attachmentsJson", attachmentsJson)

        return "board/edit"
    }

    @PostMapping(value = ["/{owner}/{projectName}/post/{number}/editform", "/{owner}/{projectName}/post/{number}/edit"])
    fun editPost(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @ModelAttribute request: PostingForm,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: run {
                // yona AbstractPostingApp.editPosting()이 요구하는 로그인 전제 — 미로그인 상태는
                // error/forbidden으로 통일.
                model.addAttribute("project", project)
                return "error/forbidden"
            }

        val posting = postingService.getPosting(project.id!!, number) ?: run {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "board_post")
            return "error/notfound"
        }

        if (posting.authorLoginId != loginUser.loginId &&
            !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) &&
            !accessControl.isAllowedIfGroupMember(project, loginUser)
        ) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // yona BoardApp.editPost()의 "if (post.readme) { ... }"는 제출된(새) readme 값을 쓴다(기존
        // posting.readme가 아니다) — README.md 실제 git 커밋 + 다른 readme 글 해제는
        // PostingServiceImpl.updatePosting()으로 옮겨 이 경로와 REST 경로(board/edit.html)가 항상
        // 같은 결과를 내도록 통일했다. 이전에는 여기서 stale한 posting.readme(기존 DB 값)를 써서
        // 체크박스로 readme를 새로 켜는 게 반영되지 않는 버그가 있었음.
        val isReadme = request.readme ?: false

        // yona BoardApp.editPost의 isSelectedToSendNotificationMail() 대응 — 서비스 계층에 위임.
        postingService.updatePosting(
            projectId = project.id!!,
            number = number,
            title = request.title,
            body = request.body ?: "",
            notice = request.notice ?: false,
            readme = isReadme,
            authorId = loginUser.id!!,
            sendNotificationMail = request.sendNotificationMail ?: false
        )

        if (isReadme) {
            return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}"
        }
        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/post/$number"
    }

    @PostMapping("/{owner}/{projectName}/posts")
    fun createPost(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @ModelAttribute request: PostingForm,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: run {
                model.addAttribute("project", project)
                return "error/forbidden"
            }

        // yona BoardApp.java @IsCreatable(ResourceType.BOARD_POST) 대응. 공개 프로젝트의
        // 비멤버 로그인 사용자도 게시글을 쓸 수 있는데, 여기서는 프로젝트 멤버/그룹멤버로만 좁게
        // 검사해 yona보다 과도하게 제한하고 있었다 — createPostForm과 동일한 정답 패턴으로 교체.
        if (!accessControl.isProjectResourceCreatable(loginUser, project, ResourceType.BOARD_POST)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // yona BoardApp.newPost()의 "if (post.readme) { Posting readmePosting = ...; if (readmePosting
        // != null) return editPost(...); }" 대응 — README 게시글은 프로젝트당 하나만 존재해야 하는데,
        // 이미 있으면 새로 만들지 않고 기존 것을 수정하는 editPost로 위임한다(같은 요청의
        // request/authentication을 그대로 재사용 — legacy도 같은 폼 데이터를 재바인딩해 in-process로
        // editPost를 호출하는 것과 동일).
        if (request.readme == true) {
            val existingReadme = postingRepository.findByProjectAndReadme(project, true).firstOrNull()
            if (existingReadme != null) {
                return editPost(owner, projectName, existingReadme.number!!, request, authentication, model)
            }
        }

        // yona BoardApp.newPost()의 "if (post.issueTemplate.equals("true")) { commitIssueTemplateFile(...);
        // return redirect(...); }" 대응 — 게시글 DB 행을 만들지 않고 ISSUE_TEMPLATE.md만 커밋.
        if (request.issueTemplate == "true") {
            try {
                val bare = BareCommit(project, loginUser, gitBaseDir)
                bare.commitTextFile("ISSUE_TEMPLATE.md", request.body ?: "", request.title)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}"
        }

        // yona BoardApp.newPost()의 "if(StringUtils.isNotEmpty(post.path) && ...isMemberOf(project)){
        // GitUtil.commitTextFile(...); return redirect(...); }" 대응 — 코드브라우저 "편집"에서
        // 넘어온 요청은 게시글 DB 행을 만들지 않고 지정 브랜치(post.branch)의 지정 경로(post.path,
        // 하위 경로 가능)에 바로 텍스트 파일을 커밋한다. BareCommit의 branch+nested-path 지원
        // 오버로드가 전제 조건이다.
        if (!request.path.isNullOrBlank() && projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            val branch = request.branch ?: ""
            val path = request.path!!
            val body = LineEnding.changeLineEnding(request.body ?: "", request.lineEnding)
            // Mercurial 프로젝트는 BareCommit(JGit 전용, bare 저장소를 직접 다룬다)을 쓸 수 없으므로
            // HgRepository.commitTextFile()로 분기한다. request.namedBranch가 채워지면(코드 브라우저
            // "편집" 폼의 신규 필드, Mercurial 프로젝트에서만 노출) 그 이름으로 `hg branch`를 실행한
            // 뒤 커밋해 새 named branch를 만든다 — Mercurial에서 named branch를 만드는 유일한
            // 방법(별도 생성 커맨드가 없다).
            if (project.vcs?.uppercase() == "MERCURIAL") {
                try {
                    val repository = repositoryService.getRepository(project)
                    repository.commitTextFile(branch, request.namedBranch, path, body, request.title ?: "", loginUser.name, loginUser.email)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    val bare = BareCommit(project, loginUser, gitBaseDir)
                    bare.setRefName(Constants.R_HEADS + branch)
                    bare.commitTextFile(branch, path, body, request.title)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val encodedPath = path.split("/").joinToString("/") { segment ->
                URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
            // Mercurial은 branch(=bookmark)를 지정하지 않고 편집하는 경우가 흔하다(브랜치 없이 "tip"을
            // 보고 있던 경우) — 그 경우 빈 문자열을 그대로 URL에 넣으면 코드브라우저가 깨지므로 "tip"으로
            // 대체한다. Git은 항상 실제 브랜치명이 채워져 있던 기존 동작을 그대로 유지한다(변경 없음).
            val redirectBranch = if (project.vcs?.uppercase() == "MERCURIAL") branch.ifBlank { "tip" } else branch
            return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/code/$redirectBranch/$encodedPath"
        }

        val posting = Posting(
            title = request.title,
            body = request.body ?: "",
            notice = request.notice ?: false,
            readme = request.readme ?: false,
            project = project
        )

        val saved = postingService.createPosting(project.id!!, posting, loginUser.id!!)

        if (!request.temporaryUploadFiles.isNullOrBlank()) {
            val fileIds = request.temporaryUploadFiles!!.split(",").mapNotNull { it.trim().toLongOrNull() }
            // yona Attachment.moveOnlySelected() 대응 — 소유권 검증 없이 요청받은 ID를 그대로
            // 재배선하지 않고, 실제로 이 로그인 사용자가 업로드한 임시 첨부만 옮긴다.
            attachmentService.moveOnlySelected(
                fromType = ResourceType.NOT_A_RESOURCE,
                fromId = "",
                toType = ResourceType.BOARD_POST,
                toId = saved.id.toString(),
                selectedIds = fileIds,
                moverLoginId = loginUser.loginId ?: ""
            )
        }

        if (saved.readme) {
            try {
                val bare = BareCommit(project, loginUser, gitBaseDir)
                bare.commitTextFile("README.md", saved.body ?: "", saved.title ?: "")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}"
        }
        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/post/${saved.number}"
    }

    // yona BareRepository의 readREADME()/getFirstFoundREADMEfileObjectId()/READMEFileNameFilter()
    // 대응. 4개 후보 파일명을 이 순서 그대로 순회해 첫 매치를 사용한다.
    private fun readReadmeCandidate(project: Project): String {
        val candidates = listOf("README.md", "readme.md", "README.markdown", "readme.markdown")
        for (candidate in candidates) {
            try {
                val bytes = repositoryService.getRepository(project).getRawFile("HEAD", candidate)
                if (bytes != null) {
                    return String(bytes, StandardCharsets.UTF_8)
                }
            } catch (e: Exception) {
                // 이 후보 파일이 없으면(또는 저장소 조회 자체가 실패해도) 다음 후보로 계속 진행
            }
        }
        return ""
    }

    private fun getIssueTemplate(project: Project): String {
        return try {
            val bytes = repositoryService.getRepository(project).getRawFile("HEAD", "ISSUE_TEMPLATE.md")
            if (bytes != null) String(bytes, StandardCharsets.UTF_8) else ""
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        // yona AbstractPostingApp의 ITEMS_PER_PAGE 대응.
        private const val ITEMS_PER_PAGE = 15
    }
}

data class PostingForm(
    var title: String = "",
    var body: String? = "",
    var notice: Boolean? = false,
    var readme: Boolean? = false,
    var temporaryUploadFiles: String? = null,
    var sendNotificationMail: Boolean? = false,
    // yona Posting.java의 issueTemplate 대응 — "true"일 때 게시글 대신 ISSUE_TEMPLATE.md로 커밋.
    var issueTemplate: String? = null,
    // yona Posting.java의 path/branch/lineEnding(@Transient) 대응 — 코드브라우저 "편집"에서
    // 넘어오는 온라인 커밋 전용 필드. path가 채워지면 게시글 DB 행 대신 지정 브랜치에 텍스트 파일을 커밋한다.
    var path: String? = null,
    var branch: String? = null,
    var lineEnding: String? = null,
    // Mercurial named branch(`hg branch`) 지원. 온라인 커밋 폼에 채워지면(Git 프로젝트에서는
    // 노출되지 않음, board/create.html 참고) 커밋 전에 `hg branch <name>`을 실행해 그 커밋을 새
    // named branch로 시작시킨다. Mercurial 전용 필드라 Git 경로에서는 항상 무시된다.
    var namedBranch: String? = null
)

