package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.eclipse.jgit.api.errors.NoHeadException
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import org.springframework.http.HttpHeaders
import org.springframework.http.CacheControl
import java.util.concurrent.TimeUnit

@Controller
class CodeViewController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val commentThreadRepository: CommentThreadRepository,
    private val commitCommentRepository: CommitCommentRepository,
    private val accessControl: AccessControl,
    private val markdownService: MarkdownService,
    private val watchService: WatchService,
    // yona utils.Config.getSiteName() 대응 — code/nohead(_svn).html의 안내 문구 {0} 자리에 채워 넣는다.
    @Value("\${yona.site-name:Yona}")
    private val siteName: String
) {

    // yona code/{nohead,nohead_svn}.scala.html:46/33 "if(isAllowed(currentUser, project.asResource, UPDATE))"
    // 대응 — clone/init 안내 가이드는 UPDATE 권한 있는 사용자에게만 보여준다.
    private fun addNoHeadAttributes(model: Model, project: Project, loginUser: User?) {
        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("siteName", siteName)
        model.addAttribute("canManage", accessControl.isAllowed(loginUser, project, Operation.UPDATE))
    }

    @GetMapping("/{owner}/{projectName}/code")
    fun codeBrowser(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                // yona CodeApp.java forbidden(ErrorViews.Forbidden.render("error.forbidden",
                // project)) 대응 — Forbidden의 (String,Project) 2-arg 오버로드는
                // ErrorViews.java에서 보듯 실제로 컨텍스트 인지형 forbidden.render(messageKey,
                // project)로 귀결된다(NotFound/BadRequest의 2-arg와 달리 Forbidden만 이 오버로드가
                // 진짜 프로젝트 헤더/메뉴를 붙인다). project는 이미 찾았으므로 error/forbidden으로 변환.
                model.addAttribute("project", project)
                return "error/forbidden"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            // yona CodeApp.codeBrowser()의 클래스/메서드 어노테이션 @IsAllowed(Operation.READ) 대응
            // — actions/IsAllowedAction.java forbidden(ErrorViews.Forbidden.render(
            // "error.forbidden", project)) 그대로, 컨텍스트 인지형 error/forbidden으로 변환.
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val repository = repositoryService.getRepository(project)
        if (repository.isEmpty()) {
            addNoHeadAttributes(model, project, loginUser)
            return if (project.vcs == "SUBVERSION") "code/nohead_svn" else "code/nohead"
        }

        val headBranch = repository.getHeadBranch()
        // bookmark(headBranch)가 하나도 없는(흔한 최초 push 직후) Mercurial 프로젝트는 예전에는
        // "master"로 리다이렉트됐는데, Mercurial에는 그런 이름의 브랜치가 있을 리 없어(bookmark도
        // named branch도 아님) 곧바로 resolveRevisionNumber() 실패 -> "브랜치가 존재하지 않음" 404로
        // 이어지는 버그였다. Git/SVN 동작은 그대로 두고 Mercurial만 getDefaultBranch()(="default",
        // HgRepository 참고)로 정정한다.
        val defaultBranch = headBranch?.shortName
            ?: if (project.vcs?.uppercase() == "MERCURIAL") repository.getDefaultBranch() else "master"
        val encodedBranch = URLEncoder.encode(defaultBranch, "UTF-8")

        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/code/$encodedBranch"
    }

    @GetMapping("/{owner}/{projectName}/code/{branch}")
    fun codeBrowserWithBranchRoot(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        authentication: Authentication?,
        model: Model
    ): String {
        return codeBrowserWithBranch(owner, projectName, branch, "", authentication, model)
    }

    @GetMapping("/{owner}/{projectName}/code/{branch}/{*path}")
    fun codeBrowserWithBranch(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        @PathVariable path: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                // yona actions/CodeAccessCheckAction.java forbidden(ErrorViews.Forbidden.render(
                // "error.forbidden.or.notfound", context.request().path())) 대응 — 이 (String,String)
                // 오버로드는 project를 받지 않고 forbidden_default.render(messageKey)(제네릭)로
                // 귀결된다(ErrorViews.java). project가 이미 resolve됐어도 legacy 자체가 프로젝트
                // 컨텍스트를 보여주지 않으므로 신규 컨텍스트 인지형 error/forbidden으로 과잉 변환하지
                // 않고 제네릭 error/403을 유지한 채 messageKey만 legacy와 동일하게 맞춘다.
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/403"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            // legacy CodeApp.codeBrowserWithBranch()에는 이 일반 READ 체크에 대응하는 어노테이션/액션이
            // 없다(메서드 레벨 @With(CodeAccessCheckAction.class)뿐이고 그건 멤버 전용 케이스만 다룬다).
            // 대응하는 legacy 렌더링이 없어 추측으로 컨텍스트를 만들어 붙이지 않고 제네릭 error/403 유지.
            return "error/403"
        }

        val decodedBranch = URLDecoder.decode(branch, "UTF-8")
        val normalizedPath = path.removePrefix("/")

        val repository = repositoryService.getRepository(project)
        val branches = repository.getRefNames()
        // GitHub 방식의 브랜치/태그 통합 ref 셀렉터(그룹 헤더로 구분, code/view.html
        // 참고). PR 브랜치 선택기(PullRequestViewController/ProjectViewController)와 달리 코드
        // 브라우저는 태그로도 브라우징할 수 있어야 하므로 여기서만 tags를 추가로 노출한다.
        val tags = repository.getTagNames()
        // Mercurial named branch(`hg branch`). bookmark(위 branches, yona의 git
        // 스타일 "브랜치" 개념)와는 완전히 별개라 옛 Bitbucket 방식대로 셀렉터에 별도 그룹으로
        // 노출한다(Git/SVN은 항상 빈 목록 — PlayRepository 기본 구현).
        val namedBranches = repository.getNamedBranchNames()
        val recursiveData = repositoryService.getMetaDataFromAncestorDirectories(repository, decodedBranch, normalizedPath)
            ?: run {
                // yona CodeApp.java notFound(ErrorViews.NotFound.render(branch, project, "code"))
                // 대응 — NotFound의 (String,Project,String type) 3-arg 오버로드만
                // 컨텍스트 인지형 notfound.render(title, project, targetType)로 귀결된다
                // (ErrorViews.java). 여기서 첫 인자 "branch"는 메시지 키가 아니라 title(=브랜치
                // 이름)이며, error.notfound.code="{0} branch does not exist..."의 {0} 자리에 그대로
                // 들어간다(TemplateHelper.notFoundMessage). project는 이미 찾았으므로 브랜치/경로를
                // 못 찾은 서브 리소스 404로 error/notfound(targetType="code")로 변환.
                model.addAttribute("project", project)
                model.addAttribute("targetType", "code")
                model.addAttribute("title", decodedBranch)
                return "error/notfound"
            }

        model.addAttribute("project", project)
        model.addAttribute("branches", branches)
        model.addAttribute("tags", tags)
        model.addAttribute("namedBranches", namedBranches)
        model.addAttribute("recursiveData", recursiveData)
        model.addAttribute("branch", decodedBranch)
        model.addAttribute("path", normalizedPath)
        model.addAttribute("currentUser", loginUser)

        // yona code/view.scala.html makeBreadCrumbs() 대응 — 경로의 각 세그먼트와 그 세그먼트까지의
        // 누적 경로 쌍의 목록. (동일 이름 세그먼트가 반복될 때 문자열 indexOf로 서브패스를 재구성하면
        // 깨지는 문제를 피하기 위해 컨트롤러에서 직접 누적한다.)
        val breadcrumbs = if (normalizedPath.isNotEmpty()) {
            var cumulative = ""
            normalizedPath.split("/").map { segment ->
                cumulative = if (cumulative.isEmpty()) segment else "$cumulative/$segment"
                segment to cumulative
            }
        } else {
            emptyList()
        }
        model.addAttribute("breadcrumbs", breadcrumbs)

        // yona code/view.scala.html @dir 대응 — "새 파일" 링크가 새 파일을 놓을 디렉터리.
        // 현재 보고 있는 대상이 폴더면 그 폴더 자신, 파일이면 그 파일을 담고 있는 부모 디렉터리.
        val lastIsFolder = recursiveData.lastOrNull()?.get("type")?.asText() == "folder"
        val currentDir = if (lastIsFolder && normalizedPath.isNotEmpty()) {
            "$normalizedPath/"
        } else {
            normalizedPath.substringBeforeLast("/", "")
        }
        model.addAttribute("currentDir", currentDir)

        // yona code/view.scala.html pathWithoutFileName() 대응 — "파일" 탭이 가리키는 목적지
        // (파일을 보고 있을 때는 그 파일의 부모 폴더 목록으로, 이미 폴더 목록을 보고 있을 때는 그 폴더의
        // 부모로 한 단계 올라간다. legacy 그대로 이식).
        val filesTabPath = if (normalizedPath.lastIndexOf("/") > 0) {
            normalizedPath.substring(0, normalizedPath.lastIndexOf("/"))
        } else {
            ""
        }
        model.addAttribute("filesTabPath", filesTabPath)

        // yona views/code/partial_view_file.scala.html "if(isMarkdownExtension(path))" 대응
        // — 코드브라우저에서 .md류 파일은 원문 대신 렌더링된 HTML로 보여준다.
        val lastEntry = recursiveData.lastOrNull()
        if (lastEntry?.get("type")?.asText() == "file") {
            if (isMarkdownExtension(normalizedPath)) {
                val data = lastEntry.get("data")?.asText()
                if (data != null) {
                    model.addAttribute("markdownHtml", markdownService.renderFileInCodeBrowser(data, project))
                }
            }

            // yona views/code/partial_view_file.scala.html CommentThread.countOnCommit()/
            // CommitComment.count() 대응 — 파일뷰의 리비전 링크 옆 댓글 수 배지.
            val revisionId = lastEntry.get("revisionNo")?.asText()
            if (revisionId != null) {
                val isSvn = project.vcs?.uppercase() == "SUBVERSION" || project.vcs?.uppercase() == "SVN"
                val commentCount = if (isSvn) {
                    commitCommentRepository.countByProjectAndCommitIdAndPath(project, revisionId, normalizedPath)
                } else {
                    commentThreadRepository.countByProjectAndCommitIdAndCodeRangePath(project, revisionId, normalizedPath)
                }
                model.addAttribute("fileCommentCount", commentCount)
            }
        }

        return "code/view"
    }

    // yona utils/TemplateHelper.scala isMarkdownExtension() 대응.
    private fun isMarkdownExtension(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("markdown", "mdown", "mkdn", "mkd", "md", "mdwn")
    }

    @GetMapping("/{owner}/{projectName}/rawcode/{rev}/{*path}")
    fun showRawFile(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable rev: String,
        @PathVariable path: String,
        authentication: Authentication?
    ): ResponseEntity<ByteArray> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val decodedRev = URLDecoder.decode(rev, "UTF-8")
        val normalizedPath = path.removePrefix("/")

        val rawData = repositoryService.getFileAsRaw(owner, projectName, decodedRev, normalizedPath)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val headers = HttpHeaders()
        
        // MIME Type 감지
        val mimeType = try {
            val fileTmp = Files.createTempFile("yona-view-mime", null)
            Files.write(fileTmp, rawData)
            val detected = Files.probeContentType(fileTmp)
            Files.delete(fileTmp)
            detected ?: "text/plain"
        } catch (e: Exception) {
            "text/plain"
        }

        headers.contentType = MediaType.parseMediaType(mimeType)
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${normalizedPath.substringAfterLast('/')}\"")

        return ResponseEntity(rawData, headers, HttpStatus.OK)
    }

    // yona CodeApp.download() 대응(code/view.html "ZIP 다운로드" 버튼) — GitRepository.getArchive()는
    // 이미 구현돼 있었지만 이를 호출하는 컨트롤러 엔드포인트가 없어서 뷰의 다운로드 링크가 죽은 링크였다.
    @GetMapping("/{owner}/{projectName}/code/download/{branch}")
    fun download(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        authentication: Authentication?
    ): ResponseEntity<ByteArray> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val decodedBranch = URLDecoder.decode(branch, "UTF-8")
        val repository = repositoryService.getRepository(project)
        val out = ByteArrayOutputStream()
        repository.getArchive(out, decodedBranch)

        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType("application/zip")
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$projectName-$decodedBranch.zip")

        return ResponseEntity(out.toByteArray(), headers, HttpStatus.OK)
    }

    @GetMapping("/{owner}/{projectName}/image/{rev}/{*path}")
    fun showImageFile(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable rev: String,
        @PathVariable path: String,
        authentication: Authentication?
    ): ResponseEntity<ByteArray> {
        return showRawFile(owner, projectName, rev, path, authentication)
    }

    @GetMapping("/{owner}/{projectName}/files/{rev}/{*path}")
    fun openFile(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable rev: String,
        @PathVariable path: String,
        authentication: Authentication?
    ): ResponseEntity<ByteArray> {
        return showRawFile(owner, projectName, rev, path, authentication)
    }

    @GetMapping("/{owner}/{projectName}/commits")
    fun historyUntilHead(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        return history(owner, projectName, "HEAD", null, page, authentication, model)
    }

    @GetMapping(value = ["/{owner}/{projectName}/commits/{branch}", "/{owner}/{projectName}/commits/{branch}/{*path}"])
    fun history(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        @PathVariable(required = false) path: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                // yona actions/CodeAccessCheckAction.java 대응 — CodeHistoryApp.history()/
                // historyUntilHead()도 동일하게 @With(CodeAccessCheckAction.class)이며, 이 액션의
                // (String,String) 오버로드는 project 컨텍스트 없는 forbidden_default로 귀결된다
                // (ErrorViews.java). 컨텍스트 인지형으로 과잉 변환하지 않고 제네릭 error/403 유지,
                // messageKey만 legacy와 동일하게 맞춘다.
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/403"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            // legacy CodeHistoryApp.history()에는 이 일반 READ 체크에 대응하는 어노테이션/액션이 없다
            // (CodeAccessCheckAction은 멤버 전용 케이스만 다룬다). 대응하는 legacy 렌더링이 없어
            // 추측으로 컨텍스트를 만들어 붙이지 않고 제네릭 error/403 유지.
            return "error/403"
        }

        val decodedBranch = URLDecoder.decode(branch, "UTF-8")
        val decodedPath = path?.let { URLDecoder.decode(it, "UTF-8") }?.removePrefix("/")

        val repository = repositoryService.getRepository(project)
        val branches = repository.getRefNames()
        // code/view.html과 동일한 GitHub 방식 통합 ref 셀렉터를 커밋 히스토리
        // 화면에도 그대로 노출한다(둘 다 같은 select#branches 패턴을 공유).
        val tags = repository.getTagNames()
        // code/view.html과 동일하게 named branch도 별도 그룹으로 노출한다.
        val namedBranches = repository.getNamedBranchNames()

        // yona CodeHistoryApp.history()의 "catch (NoHeadException e) { return notFound(nohead.render(project)); }"
        // 대응 — 커밋이 하나도 없는 빈 저장소에서 히스토리를 조회하면 JGit이 NoHeadException을
        // 던진다. 잡지 않으면 500으로 전파되므로, 코드브라우저 루트(codeBrowserRoot)와 동일한
        // code/nohead(_svn) 뷰로 안내한다.
        val commits = try {
            repository.getHistory(page, 25, decodedBranch, decodedPath)
        } catch (e: NoHeadException) {
            addNoHeadAttributes(model, project, loginUser)
            return if (project.vcs == "SUBVERSION") "code/nohead_svn" else "code/nohead"
        }

        model.addAttribute("project", project)
        model.addAttribute("branches", branches)
        model.addAttribute("tags", tags)
        model.addAttribute("namedBranches", namedBranches)
        model.addAttribute("branch", decodedBranch)
        model.addAttribute("path", decodedPath ?: "")
        model.addAttribute("commits", commits)
        model.addAttribute("page", page)
        model.addAttribute("currentUser", loginUser)

        // yona code/history.scala.html makeBreadCrumbs() 대응 (view.scala.html과 동일한 이유로
        // 문자열 indexOf 재구성 대신 컨트롤러에서 누적 경로를 직접 계산한다).
        if (!decodedPath.isNullOrEmpty()) {
            var cumulative = ""
            val breadcrumbs = decodedPath.split("/").map { segment ->
                cumulative = if (cumulative.isEmpty()) segment else "$cumulative/$segment"
                segment to cumulative
            }
            model.addAttribute("breadcrumbs", breadcrumbs)
        }

        // yona code/history.scala.html CommentThread.count()/CommitComment.count() 대응 —
        // 커밋별 댓글 수 배지. 커밋마다 반복 쿼리라 비효율적이지만 legacy도 동일하게 N+1이었다(그대로 이식).
        val isSvn = project.vcs?.uppercase() == "SUBVERSION" || project.vcs?.uppercase() == "SVN"
        val commentCounts = commits.associate { commit ->
            val count = if (isSvn) {
                commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, commit.getId()).size.toLong()
            } else if (decodedPath.isNullOrEmpty()) {
                commentThreadRepository.findByCommitIdOrderByCreatedDateDesc(commit.getId()).size.toLong()
            } else {
                commentThreadRepository.countByProjectAndCommitIdAndCodeRangePath(project, commit.getId(), decodedPath)
            }
            commit.getId() to count
        }
        model.addAttribute("commentCounts", commentCounts)

        return "code/history"
    }

    @GetMapping("/{owner}/{projectName}/commit/{commitId}")
    fun showCommit(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable commitId: String,
        @RequestParam(required = false, defaultValue = "HEAD") branch: String,
        @RequestParam(required = false, defaultValue = "") path: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                // yona actions/CodeAccessCheckAction.java 대응 — CodeHistoryApp.show()도 동일하게
                // @With(CodeAccessCheckAction.class)이며, 이 액션의 (String,String) 오버로드는 project
                // 컨텍스트 없는 forbidden_default로 귀결된다(ErrorViews.java). 컨텍스트 인지형으로
                // 과잉 변환하지 않고 제네릭 error/403 유지, messageKey만 legacy와 동일하게 맞춘다.
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/403"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            // legacy CodeHistoryApp.show()에는 이 일반 READ 체크에 대응하는 어노테이션/액션이 없다
            // (CodeAccessCheckAction은 멤버 전용 케이스만 다룬다). 대응하는 legacy 렌더링이 없어
            // 추측으로 컨텍스트를 만들어 붙이지 않고 제네릭 error/403 유지.
            return "error/403"
        }

        val repository = repositoryService.getRepository(project)
        val commit = try {
            repository.getCommit(commitId)
        } catch (e: Exception) {
            null
        } ?: run {
            // yona CodeHistoryApp.show() notFound(ErrorViews.NotFound.render(
            // "error.notfound.commit", project)) 대응 — NotFound의 (String,Project) 2-arg 오버로드는
            // render(messageKey, project, MenuType.PROJECT_HOME) -> notfound_default(messageKey)로
            // 귀결되어 project를 실질적으로 무시한다(ErrorViews.java — NotFound는 3-arg
            // String type 오버로드만 컨텍스트 인지형). 컨텍스트 인지형 error/notfound로 과잉 변환하지
            // 않고 제네릭 error/404를 유지한 채 legacy와 동일한 messageKey만 맞춘다.
            model.addAttribute("messageKey", "error.notfound.commit")
            return "error/404"
        }

        val parentCommit = try {
            repository.getParentCommitOf(commitId)
        } catch (e: Exception) {
            null
        }

        model.addAttribute("project", project)
        model.addAttribute("commit", commit)
        model.addAttribute("parentCommit", parentCommit)
        model.addAttribute("selectedBranch", branch)
        model.addAttribute("path", path)
        model.addAttribute("currentUser", loginUser)
        // yona code/svnDiff.scala.html 브랜치 드롭다운(common/branchItem 대응) 대응.
        model.addAttribute("branches", repository.getRefNames())

        val isSvn = project.vcs?.uppercase() == "SUBVERSION" || project.vcs?.uppercase() == "SVN"
        val commentThreads = if (isSvn) {
            emptyList()
        } else {
            commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, commitId)
        }
        val comments = if (isSvn) {
            commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, commitId)
        } else {
            emptyList()
        }

        model.addAttribute("commentThreads", commentThreads)
        model.addAttribute("commitB", commit)

        // yona commit.getWatchers(project, false).contains(UserApp.currentUser()) 대응 —
        // Commit.asResource(project)의 합성 리소스 키("{project.id}:{commitId}")를 그대로 재사용
        // (CodeReviewServiceImpl.getCommitWatchers()가 알림 수신자 계산에 이미 쓰는 것과 동일한 포맷).
        val commitResourceId = "${project.id}:$commitId"
        model.addAttribute("commitResourceId", commitResourceId)
        model.addAttribute("isWatching", loginUser?.let { watchService.isWatching(it, ResourceType.COMMIT, commitResourceId) } ?: false)

        return if (isSvn) {
            val patch = try {
                repository.getPatch(commitId)
            } catch (e: Exception) {
                ""
            }
            model.addAttribute("patch", patch)
            model.addAttribute("comments", comments)
            "code/svnDiff"
        } else {
            val fileDiffs = try {
                repository.getDiff(commitId)
            } catch (e: Exception) {
                null
            }
            if (fileDiffs == null) {
                return "error/404"
            }
            model.addAttribute("fileDiffs", fileDiffs)
            model.addAttribute("comments", comments)
            "code/diff"
        }
    }
}

