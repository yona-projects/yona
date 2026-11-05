package dev.yona.repository

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.domain.user.UserRepository
import dev.yona.io.CharsetDetection
import dev.yona.security.AvatarService
import dev.yona.util.SiteUrls
import org.eclipse.jgit.api.ArchiveCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.archive.ZipFormat
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Service
import java.io.OutputStream
import java.util.SortedMap
import java.util.TreeMap

/**
 * 코드 브라우저가 보여 줄 폴더와 파일의 정보를 저장소에서 읽는다. v1.6 `GitRepository.getMetaDataFromPath`, `treeAsJson`, `fileAsJson`,
 * `ObjectFinder`, `extendPath`, `isIntermediateFolder`, `getRawFile`, `getArchive`와 `RepositoryService.getMetaDataFromAncestorDirectories`
 * (`app/playRepository/GitRepository.java`, `RepositoryService.java`)에 해당한다.
 *
 * 결과는 화면과 JSON 응답이 함께 쓰는 맵이다. 항목마다 마지막으로 바꾼 커밋(작성자, 메시지, 시각)을 담는다. 이 커밋은 v1.6의 빠른 경로(커밋을
 * 거슬러 오르며 개체가 바뀐 시점을 찾음)가 아니라 항목 경로로 한 번에 하나씩 `git log -1`로 찾는다. 결과는 같고 느린 경로로 처리한다.
 */
@Service
class CodeBrowserService(
    private val repositories: RepositoryService,
    private val users: UserRepository,
    private val userLookup: dev.yona.domain.user.UserLookup,
    private val avatars: AvatarService,
    private val messages: MessageSource,
    private val properties: YonaProperties,
    private val siteUrls: SiteUrls,
    private val svnBrowser: dev.yona.repository.svn.SvnCodeBrowser,
    private val svn: dev.yona.repository.svn.SvnRepositories,
    private val metrics: dev.yona.observability.VcsMetrics
) {

    private fun isSvn(project: Project) = project.vcs == RepositoryService.VCS_SUBVERSION

    /** 저장소에 브랜치, 태그, 원격 참조가 하나도 없으면 `true`이다 (v1.6 `GitRepository.isEmpty`). */
    fun isEmpty(project: Project): Boolean =
        if (isSvn(project)) svn.isEmpty(project.owner.orEmpty(), project.name.orEmpty()) else repositories.refNames(project).isEmpty()

    /**
     * [path]의 폴더나 파일 정보이다. [branch]가 `null`이면 `HEAD`이다. 폴더는 `{"type": "folder", "data": {이름: {…}}}`, 파일은
     * `{"type": "file", …}`이다. 저장소가 비었거나 브랜치와 경로가 없으면, 또는 항목이 없는 폴더이면 `null`이다.
     */
    fun metaData(project: Project, branch: String?, path: String): Map<String, Any?>? =
        metrics.operation(project.vcs, "browse") { if (isSvn(project)) svnBrowser.metaData(project, branch, path) else gitMetaData(project, branch, path) }

    private fun gitMetaData(project: Project, branch: String?, path: String): Map<String, Any?>? = repositories.openRepository(project).use { repository ->
        val head = resolveCommit(repository, branch) ?: return null
        if (path.isEmpty()) return folder(repository, project, "", repository.newTreeWalk(head), head)
        val walk = TreeWalk.forPath(repository, path, head.tree) ?: return null
        if (walk.isSubtree) {
            val folderWalk = TreeWalk(repository)
            folderWalk.addTree(walk.getObjectId(0))
            folder(repository, project, path, folderWalk, head)
        } else {
            file(repository, walk, head)
        }
    }

    /**
     * [path]까지의 상위 폴더를 차례로 모은 메타데이터이다. 맨 앞은 루트(`path` 빈 값), 마지막은 [path] 자신이다.
     * 비어 있는 중간 폴더(항목이 폴더 하나뿐)는 건너뛴다. 하나라도 읽지 못하면 `null`이다.
     */
    fun ancestors(project: Project, branch: String?, path: String): List<Map<String, Any?>>? {
        val chain = ArrayList<Map<String, Any?>>()
        chain += LinkedHashMap(metaData(project, branch, "") ?: return null).also { it["path"] = "" }
        val parts = path.split("/")
        val length = if (path.isEmpty()) 0 else parts.size
        var partial = ""
        for (i in 0 until length) {
            partial = if (partial.isEmpty()) parts[i] else partial + "/" + parts[i]
            // Subversion은 중간 폴더를 건너뛰지 않는다 (v1.6 `SVNRepository.isIntermediateFolder`).
            val intermediate = !isSvn(project) && repositories.openRepository(project).use { isIntermediateFolder(it, partial) }
            if (!intermediate) chain += LinkedHashMap(metaData(project, branch, partial) ?: return null).also { it["path"] = partial }
        }
        return chain
    }

    /** [revision]의 [path] 파일 내용이다. 폴더이거나 없으면 `null`이다. */
    fun rawFile(project: Project, revision: String, path: String): ByteArray? = metrics.operation(project.vcs, "raw_file") { rawFileImpl(project, revision, path) }

    private fun rawFileImpl(project: Project, revision: String, path: String): ByteArray? {
        if (isSvn(project)) {
            val number = if (revision == "HEAD") revision else revision.toLongOrNull()?.toString() ?: return null
            return svn.rawFile(project.owner.orEmpty(), project.name.orEmpty(), number, path)
        }
        return gitRawFile(project, revision, path)
    }

    /**
     * Git 파일이 JGit의 큰 객체 한도(`core.streamFileThreshold`, 50MB)를 넘으면 메모리에 올리지 않고 흘려보낼 수 있게 크기와 앞부분, 스트림 열기를 돌려준다.
     * 한도 이하이거나 파일이 없으면 `null`이다(그때는 [rawFile]을 쓴다). v1.6은 `getBytes()`가 `LargeObjectException`을 던져 500이었다(R-115).
     */
    fun largeRawFile(project: Project, revision: String, path: String): LargeRawFile? {
        if (isSvn(project)) return null
        val repository = repositories.openRepository(project)
        try {
            val id = repository.resolve(revision) ?: return null.also { repository.close() }
            val tree = try { RevWalk(repository).use { it.parseTree(id) } } catch (e: Exception) { return null.also { repository.close() } }
            val walk = TreeWalk.forPath(repository, path, tree) ?: return null.also { repository.close() }
            if (walk.isSubtree) return null.also { repository.close() }
            val loader = repository.open(walk.getObjectId(0))
            if (!loader.isLarge) return null.also { repository.close() }
            val prefix = loader.openStream().use { it.readNBytes(MIME_SNIFF_BYTES) }
            return LargeRawFile(loader.size, prefix, repository) { loader.openStream() }
        } catch (e: Exception) {
            repository.close()
            throw e
        }
    }

    /** 메모리에 올리지 않고 흘려보내는 큰 파일이다. 다 쓰면 [close]로 저장소를 닫는다. */
    class LargeRawFile(val size: Long, val prefix: ByteArray, private val repository: Repository, private val opener: () -> java.io.InputStream) : java.io.Closeable {
        fun open(): java.io.InputStream = opener()
        override fun close() = repository.close()
    }

    private fun gitRawFile(project: Project, revision: String, path: String): ByteArray? = repositories.openRepository(project).use { repository ->
        val id = repository.resolve(revision) ?: return null
        val tree = try { RevWalk(repository).use { it.parseTree(id) } } catch (e: Exception) { return null }
        val walk = TreeWalk.forPath(repository, path, tree) ?: return null
        if (walk.isSubtree) return null
        repository.open(walk.getObjectId(0)).bytes
    }

    /** [branch]의 내용을 zip으로 [out]에 쓴다. 브랜치를 찾지 못하면 아무것도 쓰지 않는다. */
    fun archive(project: Project, branch: String, out: OutputStream) = metrics.operation(project.vcs, "archive") { archiveImpl(project, branch, out) }

    private fun archiveImpl(project: Project, branch: String, out: OutputStream) {
        // v1.6 `SVNRepository.getArchive`는 아무것도 쓰지 않아 빈 파일이 내려갔다. Subversion은 최신 리비전의 내용을 zip으로 만든다(R-62-3)
        if (isSvn(project)) return svn.exportZip(project.owner.orEmpty(), project.name.orEmpty(), out)
        repositories.openRepository(project).use { repository ->
            val id = repository.resolve(branch) ?: return
            // v1.6 JGit 4.5는 항목의 수정 시각을 내려받는 시각으로 쓴다. 7.x는 커밋 시각을 쓰므로 트리로 지정해 맞춘다
            val tree = repository.resolve("$branch^{tree}") ?: id
            synchronized(ARCHIVE_LOCK) {
                ArchiveCommand.registerFormat("zip", ZipFormat())
                try {
                    Git(repository).archive().setTree(tree).setFormat("zip").setOutputStream(out).call()
                } finally {
                    ArchiveCommand.unregisterFormat("zip")
                }
            }
        }
    }

    // --- 폴더 ---

    private fun Repository.newTreeWalk(commit: RevCommit): TreeWalk = TreeWalk(this).also { it.addTree(commit.tree) }

    private fun folder(repository: Repository, project: Project, basePath: String, walk: TreeWalk, head: RevCommit): Map<String, Any?>? {
        // v1.6 `ObjectFinder`: git log -1 과 달리 객체 번호를 부모와 비교해 마지막으로 바뀐 커밋을 찾는다
        val found = ObjectFinder(repository, basePath, walk, head.id, { entry, commit -> describe(entry, commit, project) }, { name -> extendPath(repository, basePath, name) }).find()
        if (found.isEmpty()) return null
        return linkedMapOf("type" to "folder", "data" to found)
    }

    /** 폴더 항목에 마지막으로 바꾼 커밋의 정보를 채운다. */
    private fun describe(entry: MutableMap<String, Any?>, commit: RevCommit, project: Project) {
        val email = commit.authorIdent.emailAddress
        val user = userLookup.findByEmail(email)
        entry["msg"] = commit.shortMessage
        entry["avatar"] = avatar(user, email)
        entry["userName"] = user?.name ?: messages.getMessage("user.notExists.name", null, LocaleContextHolder.getLocale())
        entry["userLoginId"] = user?.loginId.orEmpty()
        entry["createdDate"] = commit.commitTime * 1000L
        entry["author"] = commit.authorIdent.name
        val shortId = commit.abbreviate(7).name()
        entry["commitId"] = shortId
        entry["commitUrl"] = siteUrls.path(dev.yona.util.UrlPaths.projectPath(project.owner, project.name) + "/commit/$shortId")
    }

    /**
     * 작성자 아바타 주소이다 (v1.6 `GitRepository.getAvatar`). 등록하지 않은 커미터는 **커밋 이메일**의 Gravatar(크기 34)이고, 등록한 사용자는
     * 업로드한 아바타 또는 그 사용자 이메일의 Gravatar(크기 64, `User.avatarUrl()`의 기본값)이다.
     */
    private fun avatar(user: dev.yona.domain.user.User?, commitEmail: String?): String =
        if (user == null) avatars.gravatarUrl(commitEmail, AVATAR_SIZE) else avatars.avatarUrl(user)

    // --- 파일 ---

    private fun file(repository: Repository, walk: TreeWalk, head: RevCommit): Map<String, Any?> {
        val commit = Git(repository).use { git -> git.log().add(head).addPath(walk.pathString).call().iterator().next() }
        val user = userLookup.findByEmail(commit.authorIdent.emailAddress)
        val time = commit.commitTime * 1000L
        val result = linkedMapOf<String, Any?>("type" to "file", "msg" to commit.shortMessage)
        result["author"] = commit.authorIdent.name
        result["avatar"] = avatar(user, commit.authorIdent.emailAddress)
        result["userName"] = user?.name ?: messages.getMessage("user.notExists.name", null, LocaleContextHolder.getLocale())
        result["userLoginId"] = user?.loginId.orEmpty()
        result["createdDate"] = time
        result["commitMessage"] = commit.shortMessage
        result["commiter"] = commit.committerIdent.name
        result["commitDate"] = time
        result["commitId"] = head.name
        val loader = repository.open(walk.getObjectId(0))
        result["size"] = loader.size
        val binary = loader.openStream().use { dev.yona.io.RawTextBinary.isBinary(it) }
        result["isBinary"] = binary
        if (!binary && loader.size <= properties.codeBrowserMaxFileSize) {
            val bytes = loader.bytes
            result["data"] = String(bytes, charset(CharsetDetection.detect(bytes)))
        }
        // v1.6 getMetaDataFromPath: 이름과 내용으로 종류만 감지한다 (문자 집합 없음)
        // v1.6 `new Tika().detect(file.openStream(), meta)`는 스트림의 앞부분만 읽는다. 전체를 읽으면 큰 파일에서 LargeObjectException이 난다
        result["mimeType"] = dev.yona.io.MediaTypes.detectRaw(loader.openStream().use { it.readNBytes(MIME_SNIFF_BYTES) }, walk.nameString)
        return result
    }

    // --- 경로 도우미 (v1.6은 브랜치가 아니라 HEAD의 트리로 판단한다) ---

    /**
     * 비어 있는 중간 폴더(항목이 폴더 하나뿐)를 건너뛰고 이어 붙인 경로이다. 예: `one`이 `two`만 담고 `two`가 `c.txt`를 담으면 `one/two`
     * ("folder jumping").
     */
    private fun extendPath(repository: Repository, basePath: String, name: String): String {
        var path = name
        try {
            val headId = repository.resolve(Constants.HEAD) ?: return path
            val tree = RevWalk(repository).use { it.parseTree(headId) }
            while (true) {
                val fullPath = if (basePath.isEmpty()) path else "$basePath/$path"
                val walk = TreeWalk.forPath(repository, fullPath, tree)
                if (walk == null || !walk.isSubtree) return path
                walk.enterSubtree()
                walk.next()
                if (!walk.isSubtree) return path
                val next = path + "/" + walk.nameString
                if (walk.next()) return path
                path = next
            }
        } catch (e: java.io.IOException) {
            return path
        }
    }

    /** [path]가 폴더 하나만 담은 폴더이면 `true`이다. */
    private fun isIntermediateFolder(repository: Repository, path: String): Boolean {
        if (path.isEmpty()) return false
        try {
            val headId = repository.resolve(Constants.HEAD) ?: return false
            val tree = RevWalk(repository).use { it.parseTree(headId) }
            val walk = TreeWalk.forPath(repository, path, tree)
            if (walk == null || !walk.isSubtree) return false
            walk.enterSubtree()
            walk.next()
            if (!walk.isSubtree) return false
            return !walk.next()
        } catch (e: java.io.IOException) {
            return false
        }
    }

    private fun resolveCommit(repository: Repository, branch: String?): RevCommit? {
        val id: ObjectId = (if (branch == null) repository.resolve(Constants.HEAD) else repository.resolve(branch)) ?: return null
        return try { RevWalk(repository).use { it.parseCommit(id) } } catch (e: Exception) { null }
    }

    private companion object {
        const val AVATAR_SIZE = 34
        val ARCHIVE_LOCK = Any()

        /** 파일 종류를 감지할 때 읽는 앞부분의 바이트 수이다. Tika 1.x의 최소 길이(64KB)와 같다. */
        const val MIME_SNIFF_BYTES = 65536
    }
}
