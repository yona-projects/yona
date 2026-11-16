package dev.yona.ssh.svn

import org.tmatesoft.svn.core.ISVNLogEntryHandler
import org.tmatesoft.svn.core.SVNDirEntry
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNLogEntry
import org.tmatesoft.svn.core.SVNLogEntryPath
import org.tmatesoft.svn.core.SVNNodeKind
import org.tmatesoft.svn.core.SVNProperties
import org.tmatesoft.svn.core.SVNProperty
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.SVNRevisionProperty
import org.tmatesoft.svn.core.internal.util.SVNDate
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Date

/**
 * `ra_svn` 서버가 받는 명령의 처리이다(1단계: 읽기 명령). 모든 명령은 응답 맨 앞에 "인증이 더 필요하지 않다"는 응답을 먼저 보낸다
 * ([RaSvnServer.sendAuthOk]). 형식은 실제 `svnserve -t`와 `svn`의 대화를 녹화해 맞췄다.
 *
 * 경로 인자는 세션 주소([RaSvnServer.Session.path])를 기준으로 하고, SVNKit에는 저장소 루트 기준의 경로로 넘긴다. 저장소 밖으로 나가는 `..`은 거절한다.
 */
class RaSvnCommands(private val server: RaSvnServer) {

    /** 명령을 처리했으면 `true`이다. 모르는 명령이면 `false`이다. */
    fun dispatch(name: String, args: List<RaItem>): Boolean {
        when (name) {
            "get-latest-rev" -> getLatestRev()
            "check-path" -> checkPath(args)
            "stat" -> stat(args)
            "list" -> list(args)
            "get-file" -> getFile(args)
            "log" -> log(args)
            "reparent" -> reparent(args)
            "get-lock" -> { server.sendAuthOk(); server.send(RaSvnServer.success(raList(raList()))) }
            "get-locks" -> { server.sendAuthOk(); server.send(RaSvnServer.success(raList(raList()))) }
            "get-locations" -> getLocations(args)
            "rev-proplist" -> revProplist(args)
            "rev-prop" -> revProp(args)
            else -> return false
        }
        return true
    }

    private val repository get() = server.current().repository

    // ---- 인자 읽기 ----

    /** 선택 인자 `( )` 또는 `( 번호 )`이다. 비어 있으면 가장 최근(-1)이다. */
    private fun revision(item: RaItem?): Long = ((item as? RaList)?.items?.firstOrNull() as? RaNumber)?.value ?: -1L

    private fun text(item: RaItem?): String = (item as? RaString)?.text ?: throw IllegalArgumentException("문자열이 와야 한다")

    private fun bool(item: RaItem?): Boolean = (item as? RaWord)?.value == "true"

    /** 세션 주소 기준의 [relative]를 저장소 루트 기준 경로(앞 슬래시 없음)로 바꾼다. */
    private fun repositoryPath(relative: String): String {
        val parts = ArrayDeque<String>()
        (server.current().path + "/" + relative.trimStart('/')).split('/').filter { it.isNotEmpty() && it != "." }.forEach { part ->
            if (part == "..") { if (parts.isEmpty()) throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path escapes the repository")) else parts.removeLast() } else parts.addLast(part)
        }
        return parts.joinToString("/")
    }

    private fun latest(revision: Long): Long = if (revision < 0) repository.latestRevision else revision

    // ---- 명령 ----

    private fun getLatestRev() {
        server.sendAuthOk()
        server.send(RaSvnServer.success(raList(RaNumber(repository.latestRevision))))
    }

    private fun checkPath(args: List<RaItem>) {
        val path = repositoryPath(text(args.getOrNull(0)))
        server.sendAuthOk()
        server.send(RaSvnServer.success(raList(RaWord(kindWord(repository.checkPath(path, revision(args.getOrNull(1))))))))
    }

    private fun stat(args: List<RaItem>) {
        val path = repositoryPath(text(args.getOrNull(0)))
        val rev = revision(args.getOrNull(1))
        server.sendAuthOk()
        val entry = if (repository.checkPath(path, rev) == SVNNodeKind.NONE) null else repository.info(path, rev)
        server.send(RaSvnServer.success(raList(if (entry == null) raList() else raList(raList(
            RaWord(kindWord(entry.kind)), size(entry), bool(entry.hasProperties()), RaNumber(entry.revision), optional(dateString(entry.date)), optional(entry.author)
        )))))
    }

    /** `list`: 대상 자신과 [깊이]에 맞는 아래 항목을 요청한 칸만 채워 한 줄씩 보낸다. */
    private fun list(args: List<RaItem>) {
        val relative = text(args.getOrNull(0))
        val path = repositoryPath(relative)
        val rev = latest(revision(args.getOrNull(1)))
        val depth = (args.getOrNull(2) as? RaWord)?.value ?: "infinity"
        val fields = ((args.getOrNull(3) as? RaList)?.items.orEmpty()).mapNotNull { (it as? RaWord)?.value }.toSet()
        server.sendAuthOk()
        if (repository.checkPath(path, rev) == SVNNodeKind.NONE) throw notFound(path, rev)
        val target = repository.info(path, rev)
        server.send(listEntry("/$path", target, fields))
        if (target.kind == SVNNodeKind.DIR && depth != "empty") walk(path, rev, depth, fields)
        server.send(RaWord("done"))
        server.send(RaSvnServer.success(raList()))
    }

    private fun walk(path: String, rev: Long, depth: String, fields: Set<String>) {
        val entries = repository.getDir(path, rev, null, SVNDirEntry.DIRENT_ALL, ArrayList<SVNDirEntry>()).sortedBy { it.name }
        for (entry in entries) {
            val child = if (path.isEmpty()) entry.name else "$path/${entry.name}"
            if (depth == "files" && entry.kind != SVNNodeKind.FILE) continue
            server.send(listEntry("/$child", entry, fields))
            if (entry.kind == SVNNodeKind.DIR && depth == "infinity") walk(child, rev, depth, fields)
        }
    }

    /** `list`의 항목이다. 요청하지 않은 칸은 빈 목록이고 맨 끝의 빈 칸은 생략한다(`svnserve`와 같다). */
    private fun listEntry(path: String, entry: SVNDirEntry, fields: Set<String>): RaList {
        val slots = mutableListOf<RaItem>(
            if ("size" in fields) raList(size(entry)) else raList(),
            if ("has-props" in fields) raList(bool(entry.hasProperties())) else raList(),
            if ("created-rev" in fields) raList(RaNumber(entry.revision)) else raList(),
            if ("time" in fields) optional(dateString(entry.date)) else raList(),
            if ("last-author" in fields) optional(entry.author) else raList()
        )
        while (slots.isNotEmpty() && (slots.last() as? RaList)?.items?.isEmpty() == true) slots.removeLast()
        return RaList(listOf<RaItem>(RaString(path), RaWord(kindWord(entry.kind))) + slots)
    }

    /** `get-file`: 속성과 내용을 돌려준다. 내용은 조각 문자열을 이어 보내고 빈 문자열로 끝낸다. */
    private fun getFile(args: List<RaItem>) {
        val path = repositoryPath(text(args.getOrNull(0)))
        val rev = latest(revision(args.getOrNull(1)))
        val wantProps = bool(args.getOrNull(2)); val wantContents = bool(args.getOrNull(3))
        server.sendAuthOk()
        when (repository.checkPath(path, rev)) {
            SVNNodeKind.FILE -> Unit
            SVNNodeKind.NONE -> throw notFound(path, rev)
            else -> throw SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Path '/$path' is not a file in revision $rev"))
        }
        val properties = SVNProperties(); val content = ByteArrayOutputStream()
        repository.getFile(path, rev, properties, content)
        val bytes = content.toByteArray()
        val md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        val props = if (wantProps) entryProperties(path, rev, properties) else raList()
        server.send(RaSvnServer.success(raList(raList(RaString(md5)), RaNumber(rev), props)))
        if (wantContents) {
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(bytes.size, offset + CHUNK)
                server.send(RaString(bytes.copyOfRange(offset, end))); offset = end
            }
            server.send(RaString(""))
            server.send(RaSvnServer.success(raList()))
        }
    }

    /** 속성 목록이다. 저장소의 속성에 `svn:entry:*`(커밋 정보와 UUID)를 더한다. */
    private fun entryProperties(path: String, rev: Long, properties: SVNProperties): RaList {
        val entry = repository.info(path, rev)
        val result = mutableListOf<RaItem>()
        properties.nameSet().filter { !it.startsWith("svn:entry:") }.sorted().forEach { name -> result += raList(RaString(name), RaString(propertyBytes(properties.getSVNPropertyValue(name)))) }
        entry.date?.let { result += raList(RaString(SVNProperty.COMMITTED_DATE), RaString(dateString(it)!!)) }
        entry.author?.let { result += raList(RaString(SVNProperty.LAST_AUTHOR), RaString(it)) }
        result += raList(RaString(SVNProperty.UUID), RaString(repository.getRepositoryUUID(true)))
        result += raList(RaString(SVNProperty.COMMITTED_REVISION), RaString(entry.revision.toString()))
        return RaList(result)
    }

    /** `log`: 리비전 범위의 로그를 한 건씩 보낸다. 변경 경로와 요청한 리비전 속성을 담는다. */
    private fun log(args: List<RaItem>) {
        val paths = ((args.getOrNull(0) as? RaList)?.items.orEmpty()).map { repositoryPath(text(it)) }.ifEmpty { listOf(repositoryPath("")) }
        val start = latest(revision(args.getOrNull(1))); val end = latest(revision(args.getOrNull(2)))
        val changed = bool(args.getOrNull(3)); val strict = bool(args.getOrNull(4))
        val limit = (args.getOrNull(5) as? RaNumber)?.value ?: 0L
        val includeMerged = bool(args.getOrNull(6))
        val revpropMode = (args.getOrNull(7) as? RaWord)?.value ?: "all-revprops"
        val names = ((args.getOrNull(8) as? RaList)?.items.orEmpty()).map { text(it) }
        server.sendAuthOk()
        val wanted: Array<String>? = when (revpropMode) { "all-revprops" -> null; "revprops" -> names.toTypedArray(); else -> arrayOf(SVNRevisionProperty.AUTHOR, SVNRevisionProperty.DATE, SVNRevisionProperty.LOG) }
        repository.log(paths.toTypedArray(), start, end, changed, strict, limit, includeMerged, wanted, ISVNLogEntryHandler { entry -> server.send(logEntry(entry, names)) })
        server.send(RaWord("done"))
        server.send(RaSvnServer.success(raList()))
    }

    private fun logEntry(entry: SVNLogEntry, requested: List<String>): RaList {
        val changed = entry.changedPaths.orEmpty().values.sortedBy { it.path }.map { raList(RaString(it.path), RaWord(it.type.toString()), copyFrom(it), raList(RaString(kindWord(it.kind)))) }
        val properties = entry.revisionProperties ?: SVNProperties()
        fun value(name: String): RaItem = properties.getStringValue(name)?.let { raList(RaString(it)) } ?: raList()
        val others = properties.nameSet().filter { it !in STANDARD_REVPROPS }.sorted().map { raList(RaString(it), RaString(propertyBytes(properties.getSVNPropertyValue(it)))) }
        return raList(
            RaList(changed), RaNumber(entry.revision), value(SVNRevisionProperty.AUTHOR), value(SVNRevisionProperty.DATE), value(SVNRevisionProperty.LOG),
            RaWord("false"), RaWord("false"), RaNumber(others.size.toLong()), RaList(others), RaWord("false")
        )
    }

    private fun copyFrom(path: SVNLogEntryPath): RaItem = if (path.copyPath == null) raList() else raList(RaString(path.copyPath), RaNumber(path.copyRevision))

    private fun reparent(args: List<RaItem>) {
        val url = text(args.getOrNull(0))
        val session = server.current()
        val decoded = decode(URI(url).rawPath.orEmpty())
        val root = decode(URI(session.rootUrl).rawPath.orEmpty())
        server.sendAuthOk()
        if (!decoded.startsWith(root)) throw SVNException(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "'$url' is not a child of the session's repository root URL"))
        session.path = decoded.removePrefix(root).trim('/')
        server.send(RaSvnServer.success(raList()))
    }

    private fun getLocations(args: List<RaItem>) {
        val path = repositoryPath(text(args.getOrNull(0)))
        val peg = revision(raList(args.getOrNull(1) ?: RaNumber(-1)))
        val revs = ((args.getOrNull(2) as? RaList)?.items.orEmpty()).mapNotNull { (it as? RaNumber)?.value }.toLongArray()
        server.sendAuthOk()
        repository.getLocations(path, peg, revs) { entry -> server.send(raList(RaNumber(entry.revision), RaString("/${entry.path.trimStart('/')}"))) }
        server.send(RaWord("done"))
        server.send(RaSvnServer.success(raList()))
    }

    private fun revProplist(args: List<RaItem>) {
        val rev = (args.getOrNull(0) as? RaNumber)?.value ?: -1L
        server.sendAuthOk()
        val props = repository.getRevisionProperties(rev, null)
        server.send(RaSvnServer.success(raList(RaList(props.nameSet().sorted().map { raList(RaString(it), RaString(propertyBytes(props.getSVNPropertyValue(it)))) }))))
    }

    private fun revProp(args: List<RaItem>) {
        val rev = (args.getOrNull(0) as? RaNumber)?.value ?: -1L
        val name = text(args.getOrNull(1))
        server.sendAuthOk()
        val value = repository.getRevisionPropertyValue(rev, name)
        server.send(RaSvnServer.success(raList(if (value == null) raList() else raList(RaString(propertyBytes(value))))))
    }

    /** 경로가 없을 때의 오류이다. 코드(160013)와 문구는 `svnserve`와 같다. */
    private fun notFound(path: String, rev: Long) = SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "File not found: revision $rev, path '/$path'"))

    // ---- 값 만들기 ----

    private fun kindWord(kind: SVNNodeKind?): String = when (kind) { SVNNodeKind.DIR -> "dir"; SVNNodeKind.FILE -> "file"; SVNNodeKind.NONE -> "none"; else -> "unknown" }

    /** 파일의 크기이다. 디렉터리는 "알 수 없음"(부호 없는 64비트 최댓값)이다. */
    private fun size(entry: SVNDirEntry): RaItem = if (entry.kind == SVNNodeKind.FILE) RaNumber(entry.size) else RaNumber(-1)

    private fun bool(value: Boolean): RaItem = RaWord(if (value) "true" else "false")

    private fun optional(value: String?): RaItem = if (value == null) raList() else raList(RaString(value))

    private fun dateString(date: Date?): String? = date?.let { SVNDate.formatDate(it) }

    private fun propertyBytes(value: SVNPropertyValue?): ByteArray = value?.bytes ?: ByteArray(0)

    private fun decode(path: String): String = URLDecoder.decode(path.replace("+", "%2B"), Charsets.UTF_8)

    private companion object {
        /** 파일 내용을 나누어 보내는 조각의 크기이다. */
        const val CHUNK = 64 * 1024

        val STANDARD_REVPROPS = setOf(SVNRevisionProperty.AUTHOR, SVNRevisionProperty.DATE, SVNRevisionProperty.LOG)
    }
}
