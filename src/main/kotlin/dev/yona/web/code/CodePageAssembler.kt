package dev.yona.web.code

import dev.yona.domain.project.Project
import dev.yona.io.LineEnding
import dev.yona.markdown.ProjectMarkdownRenderer
import dev.yona.repository.RepositoryService
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
import dev.yona.web.TemplateHelper
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.util.Date

/**
 * 코드 브라우저 화면의 값을 만든다. v1.6 `app/views/code/view.scala.html`, `partial_view_folder`, `partial_view_file`과 `TemplateHelper`의
 * `CodeBrowser`, `Branches` 도우미에 해당한다.
 *
 * 주소의 경로 변수(브랜치, 리비전)는 v1.6처럼 두 번 인코딩한다([UrlPaths.routeSegment]). 폴더 목록의 이동 주소에 `#cb-…` 앵커가
 * 붙지 않는 것은 v1.6이 `" folder"`(앞 공백)와 비교해 항상 거짓이 되기 때문이다([refactoring-backlog.md] R-38).
 */
@Component
class CodePageAssembler(
    private val repositories: RepositoryService,
    private val markdown: ProjectMarkdownRenderer,
    private val helper: TemplateHelper,
    private val siteUrls: SiteUrls
) {

    /**
     * @param chain [dev.yona.repository.CodeBrowserService.ancestors]의 결과
     * @param branch 주소의 브랜치 이름(`HEAD`, `master`처럼 접두어 없는 이름)
     * @param path 보고 있는 경로(루트는 빈 값)
     * @param loggedIn 로그인한 사용자이면 `true`(새 파일, 편집 링크)
     * @param siteManager 사이트 관리자이면 `true`(큰 파일 안내에 한도 변경 방법을 덧붙임)
     */
    fun assemble(project: Project, chain: List<Map<String, Any?>>, branch: String, path: String, loggedIn: Boolean, siteManager: Boolean, maxFileSize: Long): CodePage {
        val owner = project.owner.orEmpty()
        val name = project.name.orEmpty()
        val base = siteUrls.path("/$owner/$name")
        val branchSegment = UrlPaths.routeSegment(branch)
        val codeUrl = { segment: String, rest: String -> "$base/code/$segment/${UrlPaths.encodeEach(rest)}" }
        val lastType = chain.last()["type"]
        val dir = if (lastType == "folder" && path.isNotEmpty()) "$path/" else path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val branches = repositories.refNames(project).map { ref ->
            BranchOption(codeUrl(UrlPaths.routeSegment(branchItemName(ref)), path), ref, branchItemName(ref) == branchItemName(branch))
        }
        val basePath = codeUrl(branchSegment, "")
        val crumbs = if (path.isEmpty()) emptyList() else {
            var partial = ""
            path.split("/").map { part ->
                partial = if (partial.isEmpty()) part else "$partial/$part"
                Crumb(UrlPaths.join(basePath, partial), part)
            }
        }
        val items = chain.map { info ->
            if (info["type"] == "folder") CodeItem(folder(project, info, branchSegment, base), null)
            else CodeItem(null, file(project, info, branch, path, loggedIn, siteManager, maxFileSize, base))
        }
        val itemName = branchItemName(branch)
        return CodePage(
            showTabs = chain.first()["type"] == "folder",
            filesTabUrl = codeUrl(branchSegment, path.substringBeforeLast('/', "")),
            commitsTabUrl = "$base/commits/$branchSegment/",
            branchesTabUrl = "$base/branches",
            branches = branches, rootUrl = basePath, projectName = name, crumbs = crumbs,
            downloadUrl = "$base/code/$branchSegment/download",
            newFileUrl = if (loggedIn) "$base/postform?path=$dir&branch=$itemName" else null,
            items = items, initialPath = path,
            metaInfoUrl = "$base/code/$branchSegment/!/", basePathUrl = basePath
        )
    }

    private fun folder(project: Project, info: Map<String, Any?>, branchSegment: String, base: String): FolderView {
        val listPath = info["path"] as? String ?: ""
        @Suppress("UNCHECKED_CAST") val data = info["data"] as Map<String, Map<String, Any?>>
        val filePath = "$base/code/$branchSegment/" + UrlPaths.encodeEach(listPath)
        val rows = data.map { (fileName, file) ->
            val folder = file["type"] == "folder"
            val loginId = file["userLoginId"] as? String ?: ""
            FolderEntry(
                id = "cb-$listPath$fileName", name = fileName, folder = folder,
                dataPath = if (listPath.isEmpty()) fileName else UrlPaths.join(listPath, fileName),
                href = UrlPaths.join(filePath, fileName), fileClass = if (folder) "dynatree-ico-cf" else "dynatree-ico-c",
                userHref = userLink(loginId), avatarUrl = file["avatar"] as? String ?: "", commitUrl = file["commitUrl"] as? String ?: "",
                msg = file["msg"] as? String ?: "", ago = (file["createdDate"] as? Long)?.let { helper.agoOrDateString(Date(it)) }.orEmpty()
            )
        }
        return FolderView(listPath, rows.filter { it.folder } + rows.filter { !it.folder })
    }

    private fun file(project: Project, info: Map<String, Any?>, branch: String, path: String, loggedIn: Boolean, siteManager: Boolean, maxFileSize: Long, base: String): FileView {
        val revId = info["commitId"] as? String ?: ""
        val revSegment = UrlPaths.routeSegment(branchItemName(branch))
        val binary = info["isBinary"] == true
        val data = info["data"] as? String
        val size = (info["size"] as? Number)?.toLong() ?: 0L
        val mimeType = info["mimeType"] as? String ?: ""
        val mode = when {
            binary -> if (mimeType.startsWith("image/")) "image" else "binary"
            data == null && size > 0 -> "tooBig"
            MARKDOWN_EXTENSIONS.contains(path.substringAfterLast('.', "").lowercase()) -> "markdown"
            else -> "code"
        }
        val itemName = branchItemName(branch)
        return FileView(
            fileName = path.substringAfterLast('/'), path = path, revId = revId,
            rawUrl = "$base/rawcode/${UrlPaths.routeSegment(revId)}/${UrlPaths.encodeEach(path)}",
            openUrl = "$base/files/$revSegment/${UrlPaths.encodeEach(path)}",
            historyUrl = "$base/commits/${UrlPaths.routeSegment(branch)}/${UrlPaths.encodeEach(path)}",
            commitUrl = "$base/commit/${UrlPaths.routeSegment(revId)}" + (if (branch.isNotEmpty()) "?branch=${URLEncoder.encode(branch, Charsets.UTF_8)}" else "") + "#$path",
            userHref = userLink(info["userLoginId"] as? String ?: ""), avatarUrl = info["avatar"] as? String ?: "", author = info["author"] as? String ?: "",
            ago = (info["createdDate"] as? Long)?.let { helper.agoOrDateString(Date(it)) }.orEmpty(), commitMessage = info["commitMessage"] as? String ?: "",
            lineEnding = if (binary) "" else LineEnding.find(data.orEmpty()).name, commentCount = 0, binary = binary, mode = mode, size = size, mimeType = mimeType,
            data = data, renderedMarkdown = if (mode == "markdown" && data != null) markdown.renderFileInCodeBrowser(data, project) else null,
            editUrl = if (loggedIn && !binary) "$base/postform?path=$path&branch=$itemName&edit=true" else null, maxFileSize = maxFileSize, siteManager = siteManager
        )
    }

    private fun userLink(loginId: String): String = if (loginId.isNotEmpty()) "/$loginId" else "javascript:void(); return false;"

    companion object {
        private val MARKDOWN_EXTENSIONS = setOf("markdown", "mdown", "mkdn", "mkd", "md", "mdwn")

        /**
         * 참조 이름에서 브랜치나 태그 이름만 뽑는다. `refs/heads/master`는 `master`, 접두어가 없으면 그대로이다 (v1.6 `Branches.itemName`).
         */
        fun branchItemName(branch: String): String {
            val names = branch.split("/", limit = 3)
            return if (names.size >= 3 && names[0] == "refs") names[2] else branch
        }
    }
}
