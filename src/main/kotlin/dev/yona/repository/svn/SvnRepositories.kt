package dev.yona.repository.svn

import dev.yona.repository.DataDirectory
import org.springframework.stereotype.Component
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNDirEntry
import org.tmatesoft.svn.core.SVNProperties
import org.tmatesoft.svn.core.SVNProperty
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNLogEntry
import org.tmatesoft.svn.core.SVNNodeKind
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory
import org.tmatesoft.svn.core.io.SVNRepository
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date

/**
 * Subversion 커밋 하나이다. v1.6 `playRepository.SvnCommit`(`app/playRepository/SvnCommit.java`)에 해당한다.
 *
 * @property id 리비전 번호(문자열)
 * @property authorName 작성자의 로그인 ID(`svn:author`). 없으면 `null`
 * @property message 커밋 메시지
 * @property date 커밋 시각
 */
data class SvnCommit(val id: String, val authorName: String?, val message: String?, val date: Date?) {

    /** 메시지의 첫 줄이다. 메시지가 없으면 빈 문자열이다. 앞뒤는 Java `String.trim()`처럼 U+0020 이하의 문자만 지운다(Kotlin `trim()`은 NBSP, U+3000 등도 지운다). */
    val shortMessage: String
        get() = if (message.isNullOrEmpty()) "" else message.trim { it <= ' ' }.split("\n").firstOrNull().orEmpty()

    /** 리비전 번호를 줄여 쓰지 않는다(Subversion의 짧은 ID는 번호 그대로이다). */
    val shortId: String get() = id

    /** 첫 리비전이 아니면 부모가 하나이다. */
    val parentCount: Int get() = if (id.toLong() > 1) 1 else 0

    companion object {
        fun of(entry: SVNLogEntry) = SvnCommit(entry.revision.toString(), entry.author, entry.message, entry.date)
    }
}

/**
 * 폴더 안의 항목 하나이다. v1.6 `SVNDirEntry`에서 코드 브라우저가 쓰는 값이다.
 *
 * @property directory 폴더이면 `true`
 * @property revision 마지막으로 바꾼 리비전
 * @property message 그 리비전의 커밋 메시지
 */
data class SvnEntry(val name: String, val directory: Boolean, val revision: Long, val author: String?, val date: Date?, val message: String?, val size: Long)

/**
 * 파일 하나의 내용과 마지막 커밋 정보이다. v1.6 `SVNRepository.fileAsJson`이 읽는 값이다.
 *
 * @property revision 마지막으로 바꾼 리비전(`svn:entry:committed-rev`)
 * @property date 마지막으로 바꾼 시각(`svn:entry:committed-date`을 밀리초로 바꾼 값)
 */
data class SvnFile(val bytes: ByteArray, val size: Long, val revision: String?, val author: String?, val date: Long, val message: String?)

/**
 * Subversion 저장소의 위치를 정하고 SVNKit으로 만들고 지우고 읽는다. v1.6 `playRepository.SVNRepository`
 * (`app/playRepository/SVNRepository.java`)의 저장소 조작, 파일 읽기, 이력, 패치에 해당한다 (D-13).
 *
 * 저장소는 `<데이터 디렉터리>/repo/svn/<소유자>/<이름>`에 있다. 리비전은 `HEAD`(최신) 또는 번호이다.
 * 메서드마다 SVNKit 세션을 열고 닫는다.
 */
@Component
class SvnRepositories(private val dataDirectory: DataDirectory) {

    init {
        FSRepositoryFactory.setup()
    }

    /** 모든 Subversion 저장소가 들어 있는 디렉터리이다. DAV 서블릿이 `<이 디렉터리>/<소유자>/`를 부모 경로로 쓴다. */
    fun rootDirectory(): File = File(dataDirectory.root(), REPO_PREFIX)

    /** 저장소 디렉터리이다. */
    fun directoryOf(owner: String, name: String): File = File(File(dataDirectory.root(), REPO_PREFIX), "$owner/$name")

    /** 저장소를 만든다 (v1.6 `SVNRepositoryFactory.createLocalRepository(dir, true, false)`). */
    fun create(owner: String, name: String) {
        SVNRepositoryFactory.createLocalRepository(directoryOf(owner, name), true, false)
    }

    /** 저장소 디렉터리를 지운다. 없으면 아무 일도 하지 않는다. */
    fun delete(owner: String, name: String) {
        directoryOf(owner, name).deleteRecursively()
    }

    /** 저장소 디렉터리를 다른 소유자나 이름의 위치로 옮긴다. 원래 디렉터리가 없으면 성공이고, 옮기지 못하면 `false`이다 (v1.6 `SVNRepository.move`). */
    fun move(owner: String, name: String, newOwner: String, newName: String): Boolean {
        val source = directoryOf(owner, name)
        val target = directoryOf(newOwner, newName)
        source.setWritable(true)
        return try {
            if (source.exists()) {
                target.parentFile.mkdirs()
                java.nio.file.Files.move(source.toPath(), target.toPath())
            }
            true
        } catch (e: java.io.IOException) {
            false
        }
    }

    /** 브랜치는 `HEAD` 하나뿐이다 (v1.6 `getRefNames`). */
    fun refNames(): List<String> = listOf(HEAD)

    /** 기본 브랜치는 항상 `HEAD`이다 (v1.6 `getDefaultBranch`). */
    fun defaultBranch(): String = HEAD

    private inline fun <T> session(owner: String, name: String, block: (SVNRepository) -> T): T {
        val repository = SVNRepositoryFactory.create(SVNURL.fromFile(directoryOf(owner, name)))
        try {
            return block(repository)
        } finally {
            repository.closeSession()
        }
    }

    private fun revisionOf(revision: String): Long = if (revision == HEAD) -1L else revision.toLong()

    /** 최신 리비전 번호이다. 저장소가 없으면 예외이다. */
    fun latestRevision(owner: String, name: String): Long = session(owner, name) { it.latestRevision }

    /** 커밋이 하나도 없으면 `true`이다 (v1.6 `isEmpty`). */
    fun isEmpty(owner: String, name: String): Boolean = latestRevision(owner, name) == 0L

    /** [path]가 폴더이면 항목 목록(SVNKit가 돌려주는 순서 그대로, v1.6 `getMetaDataFromPath`처럼 정렬하지 않는다)을, 아니면 `null`이다. 항상 최신 리비전에서 읽는다 (v1.6 `getMetaDataFromPath`). */
    fun entries(owner: String, name: String, path: String): List<SvnEntry>? = session(owner, name) { repository ->
        if (repository.checkPath(path, -1) != SVNNodeKind.DIR) return null
        val found = repository.getDir(path, -1, SVNProperties(), SVNDirEntry.DIRENT_ALL, null as Collection<*>?)
        found.map { entry ->
            entry as SVNDirEntry
            SvnEntry(entry.name, entry.kind == SVNNodeKind.DIR, entry.revision, entry.author, entry.date, entry.commitMessage, entry.size)
        }
    }

    /** [path]의 종류이다. 리비전은 번호이고 `null`이면 최신이다. 없는 리비전이면 `NONE`이다. */
    fun kindOf(owner: String, name: String, path: String, revision: Long? = null): SVNNodeKind = session(owner, name) { repository ->
        try {
            repository.checkPath(path, revision ?: -1)
        } catch (e: SVNException) {
            if (e.errorMessage.errorCode == SVNErrorCode.FS_NO_SUCH_REVISION) SVNNodeKind.NONE else throw e
        }
    }

    /** 파일의 내용과 마지막 커밋 정보이다. 최신 리비전에서 읽고, 파일이 아니면 `null`이다. */
    fun file(owner: String, name: String, path: String): SvnFile? = session(owner, name) { repository ->
        if (repository.checkPath(path, -1) != SVNNodeKind.FILE) return null
        val out = ByteArrayOutputStream()
        val properties = SVNProperties()
        repository.getFile(path, -1, properties, out)
        val info = repository.info(path, -1)
        val committed = properties.getStringValue(SVNProperty.COMMITTED_DATE)
        SvnFile(
            out.toByteArray(), info.size, properties.getStringValue(SVNProperty.COMMITTED_REVISION), properties.getStringValue(SVNProperty.LAST_AUTHOR),
            java.time.OffsetDateTime.parse(committed).toInstant().toEpochMilli(), info.commitMessage
        )
    }

    /** 파일 내용이다. 파일이 아니거나 없으면 `null`이다. [revision]은 `HEAD` 또는 번호이다 (v1.6 `getRawFile`). */
    fun rawFile(owner: String, name: String, revision: String, path: String): ByteArray? = session(owner, name) { repository ->
        val number = revisionOf(revision)
        if (repository.checkPath(path, number) != SVNNodeKind.FILE) return null
        ByteArrayOutputStream().also { repository.getFile(path, number, null, it) }.toByteArray()
    }

    /** 최신 리비전에서 [path]가 파일이면 `true`이다. 리비전을 주면 그 리비전에서 확인한다. */
    fun isFile(owner: String, name: String, path: String, revision: String? = null): Boolean = session(owner, name) { repository ->
        repository.checkPath(path, revision?.toLong() ?: repository.latestRevision) == SVNNodeKind.FILE
    }

    /**
     * 최신 리비전부터 거슬러 오르며 [limit]개 단위의 [page]쪽 이력이다. [path]로 좁힐 수 있다.
     *
     * 한 쪽은 [limit]개이다. v1.6은 시작과 끝 리비전을 모두 포함해서 한 쪽에 `limit + 1`개가 나오고 다음 쪽의 첫 리비전과 겹쳤다(R-62-1).
     */
    fun history(owner: String, name: String, page: Int, limit: Int, path: String?): List<SvnCommit> = session(owner, name) { repository ->
        val paths = arrayOf(if (path != null) "/$path" else "/")
        val start = repository.latestRevision - page.toLong() * limit
        var end = start - limit + 1
        if (end < 1) end = 1
        if (start < end) return emptyList()
        repository.log(paths, null, start, end, false, false).map { SvnCommit.of(it as SVNLogEntry) }
    }

    /** 리비전 하나의 커밋이다. 없는 리비전이면 `null`이다. */
    fun commit(owner: String, name: String, revision: Long): SvnCommit? = session(owner, name) { repository ->
        try {
            repository.log(arrayOf("/"), null, revision, revision, false, false).firstOrNull()?.let { SvnCommit.of(it as SVNLogEntry) }
        } catch (e: SVNException) {
            if (e.errorMessage.errorCode == SVNErrorCode.FS_NO_SUCH_REVISION) null else throw e
        }
    }

    /** 리비전 [revision]이 만든 변경을 통합 형식 패치로 돌려준다 (v1.6 `getPatch(commitId)`). */
    fun patch(owner: String, name: String, revision: String): String = patch(owner, name, revision.toLong() - 1, revision.toLong())

    /** 두 리비전 사이의 변경을 통합 형식 패치로 돌려준다 (v1.6 `getPatch(revA, revB)`). */
    fun patch(owner: String, name: String, revisionA: Long, revisionB: Long): String {
        val manager = SVNClientManager.newInstance()
        try {
            val out = ByteArrayOutputStream()
            manager.diffClient.doDiff(SVNURL.fromFile(directoryOf(owner, name)), null, SVNRevision.create(revisionA), SVNRevision.create(revisionB), SVNDepth.INFINITY, true, out)
            return out.toString(Charsets.UTF_8)
        } finally {
            manager.dispose()
        }
    }

    companion object {
        /** `repo/svn` 아래에 소유자와 이름 디렉터리를 둔다. */
        const val REPO_PREFIX = "repo/svn"

        /** 최신 리비전이다. */
        const val HEAD = "HEAD"
    }
}
