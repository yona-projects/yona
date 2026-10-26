package dev.yona.repository.svn

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.domain.user.UserRepository
import dev.yona.io.CharsetDetection
import dev.yona.io.MediaTypes
import dev.yona.security.AvatarService
import dev.yona.util.SiteUrls
import org.eclipse.jgit.diff.RawText
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.tmatesoft.svn.core.SVNNodeKind

/**
 * Subversion 프로젝트의 코드 브라우저 데이터를 만든다. v1.6 `SVNRepository.getMetaDataFromPath`, `fileAsJson`, `getAvatar`
 * (`app/playRepository/SVNRepository.java:84-190`)에 해당한다.
 *
 * 결과는 Git의 [dev.yona.repository.CodeBrowserService]와 같은 맵 모양이며 화면과 JSON 응답이 함께 쓴다. v1.6처럼 폴더 목록과 파일 내용은
 * 리비전을 알려 줘도 최신(HEAD)에서 읽고(R-62), 폴더 항목의 `commitId`는 리비전 번호이다. 보여 줄 수 있는 크기를 넘는 파일은 바이너리로 본다.
 */
@Component
class SvnCodeBrowser(
    private val svn: SvnRepositories,
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val messages: MessageSource,
    private val properties: YonaProperties,
    private val siteUrls: SiteUrls
) {

    /**
     * [path]의 폴더나 파일 정보이다. [revision]은 번호이고 번호가 아니면(`HEAD` 포함) 최신으로 본다. 경로가 없으면 `null`이다.
     * 폴더는 `{"type": "folder", "data": {이름: {…}}}`, 파일은 `{"type": "file", …}`이다.
     */
    fun metaData(project: Project, revision: String?, path: String): Map<String, Any?>? {
        val owner = project.owner.orEmpty()
        val name = project.name.orEmpty()
        val number = revision?.toLongOrNull()
        return when (svn.kindOf(owner, name, path, number)) {
            SVNNodeKind.DIR -> folder(project, path)
            SVNNodeKind.FILE -> file(owner, name, path)
            else -> null
        }
    }

    private fun folder(project: Project, path: String): Map<String, Any?>? {
        val entries = svn.entries(project.owner.orEmpty(), project.name.orEmpty(), path) ?: return null
        val data = LinkedHashMap<String, Any?>()
        for (entry in entries) {
            val author = entry.author
            val user = author?.let { users.findByLoginId(it) }
            val time = entry.date?.time
            data[entry.name] = linkedMapOf(
                "type" to if (entry.directory) "folder" else "file", "msg" to entry.message, "author" to author, "avatar" to avatar(user),
                "userName" to userName(user), "userLoginId" to user?.loginId.orEmpty(), "createdDate" to time, "commitMessage" to entry.message,
                "commiter" to author, "commitDate" to time, "commitId" to entry.revision,
                "commitUrl" to siteUrls.path("/${project.owner}/${project.name}/commit/${entry.revision}"), "size" to entry.size
            )
        }
        return linkedMapOf("type" to "folder", "data" to data)
    }

    private fun file(owner: String, name: String, path: String): Map<String, Any?>? {
        val file = svn.file(owner, name, path) ?: return null
        val binary: Boolean
        val mimeType: String
        var data: String? = null
        if (file.size > properties.codeBrowserMaxFileSize) {
            binary = true
            mimeType = "application/octet-stream"
        } else {
            binary = dev.yona.io.RawTextBinary.isBinary(file.bytes)
            if (!binary) data = String(file.bytes, charset(CharsetDetection.detect(file.bytes)))
            mimeType = MediaTypes.detect(file.bytes, path)
        }
        val user = file.author?.let { users.findByLoginId(it) }
        return linkedMapOf(
            "type" to "file", "revisionNo" to file.revision, "author" to file.author, "avatar" to avatar(user), "userName" to userName(user),
            "userLoginId" to user?.loginId.orEmpty(), "createdDate" to file.date, "commitMessage" to file.message, "commiter" to file.author,
            "size" to file.size, "isBinary" to binary, "mimeType" to mimeType, "data" to data
        )
    }

    /** 사용자를 알면 그 아바타를, 모르면 이메일 없는 Gravatar 주소(크기 34)를 쓴다 (v1.6 `getAvatar`). */
    private fun avatar(user: dev.yona.domain.user.User?): String = user?.let { avatars.avatarUrl(it) } ?: avatars.gravatarUrl("", AVATAR_SIZE, dev.yona.security.AvatarService.SVN_DEFAULT_GRAVATAR_IMAGE)

    private fun userName(user: dev.yona.domain.user.User?): String = user?.name ?: messages.getMessage("user.notExists.name", null, LocaleContextHolder.getLocale())

    private companion object {
        const val AVATAR_SIZE = 34
    }
}
