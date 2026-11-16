package dev.yona.ssh.svn

import org.tmatesoft.svn.core.ISVNLogEntryHandler
import org.tmatesoft.svn.core.SVNDirEntry
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNLogEntry
import org.tmatesoft.svn.core.SVNLogEntryPath
import org.tmatesoft.svn.core.SVNNodeKind
import org.tmatesoft.svn.core.SVNProperties
import org.tmatesoft.svn.core.SVNProperty
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.SVNRevisionProperty
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.internal.util.SVNDate
import org.tmatesoft.svn.core.io.ISVNReporter
import org.tmatesoft.svn.core.io.ISVNReporterBaton
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
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
            "get-lock" -> getLock(args)
            "get-locks" -> getLocks(args)
            "lock-many" -> lock(args)
            "unlock-many" -> unlock(args)
            "lock" -> lock(listOf(args.getOrNull(1) ?: raList(), args.getOrNull(2) ?: RaWord("false"), raList(raList(args.getOrNull(0) ?: RaString(""), args.getOrNull(3) ?: raList()))), single = true)
            "unlock" -> unlock(listOf(args.getOrNull(1) ?: RaWord("false"), raList(raList(args.getOrNull(0) ?: RaString(""), args.getOrNull(2) ?: raList()))), single = true)
            "get-locations" -> getLocations(args)
            "rev-proplist" -> revProplist(args)
            "rev-prop" -> revProp(args)
            "update" -> update(args)
            "get-iprops" -> getInheritedProps(args)
            "get-dir" -> getDir(args)
            "commit" -> commit(args)
            "status" -> status(args)
            "diff" -> diff(args)
            "switch" -> switch(args)
            "get-location-segments" -> getLocationSegments(args)
            "get-file-revs" -> getFileRevs(args)
            "get-mergeinfo" -> getMergeInfo(args)
            "replay", "replay-range", "change-rev-prop", "change-rev-prop2", "get-deleted-rev" -> unsupported(name)
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
        val properties = SVNProperties()
        RaSvnFileSpool().use { spool ->
            repository.getFile(path, rev, properties, spool)
            val props = if (wantProps) entryProperties(path, rev, properties) else raList()
            server.send(RaSvnServer.success(raList(raList(RaString(spool.md5())), RaNumber(rev), props)))
            if (wantContents) {
                spool.open().use { input ->
                    val buffer = ByteArray(CHUNK)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        server.send(RaString(buffer.copyOf(n)))
                    }
                }
                server.send(RaString(""))
                server.send(RaSvnServer.success(raList()))
            }
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



    /**
     * `get-iprops`: 경로의 윗 디렉터리들이 가진 속성이다(저장소 루트부터 부모까지, 속성이 있는 것만). 클라이언트는 `svn:auto-props`처럼 상속하는 속성을 쓰려고 묻는다.
     * 형식은 `( success ( ( ( 경로 ( ( 이름 값 ) ... ) ) ... ) ) )`이고 없으면 `( success ( ( ) ) )`이다.
     */
    private fun getInheritedProps(args: List<RaItem>) {
        val path = repositoryPath(text(args.getOrNull(0)))
        val rev = latest(revision(args.getOrNull(1)))
        server.sendAuthOk()
        val ancestors = path.split('/').filter { it.isNotEmpty() }.let { parts -> (0 until parts.size).map { parts.take(it).joinToString("/") } }
        val inherited = ancestors.mapNotNull { ancestor ->
            val properties = SVNProperties()
            repository.getDir(ancestor, rev, properties, null as org.tmatesoft.svn.core.ISVNDirEntryHandler?)
            val regular = properties.nameSet().filter { !it.startsWith("svn:entry:") && !it.startsWith("svn:wc:") }.sorted()
            if (regular.isEmpty()) null else raList(RaString("/$ancestor"), RaList(regular.map { raList(RaString(it), RaString(propertyBytes(properties.getSVNPropertyValue(it)))) }))
        }
        server.send(RaSvnServer.success(raList(RaList(inherited))))
    }

    /** `get-dir`: 디렉터리의 속성과 항목이다(`list`가 없던 옛 클라이언트와 일부 작업이 쓴다). 항목은 이름, 종류, 크기, 속성 유무, 만든 리비전, 날짜, 작성자이다. */
    private fun getDir(args: List<RaItem>) {
        val path = repositoryPath(text(args.getOrNull(0)))
        val rev = latest(revision(args.getOrNull(1)))
        val wantProps = bool(args.getOrNull(2)); val wantContents = bool(args.getOrNull(3))
        server.sendAuthOk()
        if (repository.checkPath(path, rev) != SVNNodeKind.DIR) throw notFound(path, rev)
        val properties = SVNProperties()
        val entries = if (wantContents) repository.getDir(path, rev, properties, SVNDirEntry.DIRENT_ALL, ArrayList<SVNDirEntry>()).sortedBy { it.name } else {
            repository.getDir(path, rev, properties, null as org.tmatesoft.svn.core.ISVNDirEntryHandler?); emptyList()
        }
        val props = if (wantProps) entryProperties(path, rev, properties) else raList()
        val items = entries.map { raList(RaString(it.name), RaWord(kindWord(it.kind)), size(it), bool(it.hasProperties()), RaNumber(it.revision), optional(dateString(it.date)), optional(it.author)) }
        server.send(RaSvnServer.success(raList(RaNumber(rev), props, RaList(items))))
    }


    // ---- 커밋 ----

    /**
     * `commit`: 로그 메시지와 리비전 속성을 받고, 클라이언트가 보내는 편집기 명령을 `close-edit`까지 읽은 뒤 SVNKit 커밋 편집기에 재생한다([RaSvnEditorReader]).
     * 읽기 전용 접속은 `svnserve --read-only`처럼 인증 확인도 없이 바로 `Authorization failed`로 거절한다. 커밋하는 사용자는 터널의 사용자이다
     * (`svn:author`). 응답은 편집 수락(`success`), 인증 확인, 커밋 정보(`( 리비전 ( 날짜 ) ( 작성자 ) ( ) )`) 순서이고 커밋 정보에는 `success`가 붙지 않는다.
     */
    private fun commit(args: List<RaItem>) {
        if (!server.current().writable) { server.sendFailure(SVNErrorCode.RA_NOT_AUTHORIZED, "Authorization failed"); return }
        val message = text(args.getOrNull(0))
        val keepLocks = bool(args.getOrNull(2))
        val revisionProperties = SVNProperties()
        ((args.getOrNull(3) as? RaList)?.items.orEmpty()).forEach { item ->
            val pair = (item as? RaList)?.items.orEmpty()
            val name = (pair.getOrNull(0) as? RaString)?.text ?: return@forEach
            // 로그 메시지는 따로 넘기고, 클라이언트의 임시(`svn:txn-*`) 속성은 리비전에 남기지 않는다
            if (name != SVNRevisionProperty.LOG && !name.startsWith("svn:txn-")) revisionProperties.put(name, (pair.getOrNull(1) as? RaString)?.text.orEmpty())
        }
        val lockTokens = LinkedHashMap<String, String>()
        ((args.getOrNull(1) as? RaList)?.items.orEmpty()).forEach { item ->
            val pair = (item as? RaList)?.items.orEmpty()
            val path = (pair.getOrNull(0) as? RaString)?.text; val token = (pair.getOrNull(1) as? RaString)?.text
            if (path != null && token != null) lockTokens["/" + repositoryPath(path)] = token
        }
        server.sendAuthOk()
        server.send(RaSvnServer.success(raList()))
        RaSvnEditorReader(copySource = { repositoryPathOfUrl(it) }).use { reader ->
            while (!reader.isDone) {
                val command = server.receive() as? RaList ?: throw RaSvnProtocolException("편집이 끝나기 전에 입력이 끊겼다")
                reader.accept((command.items.getOrNull(0) as? RaWord)?.value.orEmpty(), (command.items.getOrNull(1) as? RaList)?.items.orEmpty())
            }
            if (reader.isAborted) { server.send(RaSvnServer.success(raList())); return }
            val located = locatedRepository()
            located.authenticationManager = BasicAuthenticationManager.newInstance(server.user, null)
            val editor = located.getCommitEditor(message, lockTokens, keepLocks, revisionProperties, null)
            val info = try {
                reader.replay(editor)
            } catch (e: SVNException) {
                runCatching { editor.abortEdit() }
                located.closeSession()
                throw e
            }
            located.closeSession()
            server.send(RaSvnServer.success(raList()))
            server.sendAuthOk()
            server.send(raList(RaNumber(info?.newRevision ?: -1), optional(dateString(info?.date)), optional(info?.author), raList()))
        }
    }

    // ---- 갱신 ----

    /** 클라이언트가 보고한 한 줄이다(`set-path`, `link-path`, `delete-path`). */
    private sealed interface Report {
        data class SetPath(val path: String, val revision: Long, val startEmpty: Boolean, val depth: SVNDepth) : Report
        data class LinkPath(val path: String, val url: SVNURL, val revision: Long, val startEmpty: Boolean, val depth: SVNDepth) : Report
        data class DeletePath(val path: String) : Report
    }

    /** `update`: 클라이언트가 가진 리비전에서 목표 리비전까지의 변경을 편집기 명령으로 보낸다. 인자는 `( ( 리비전 ) 대상 재귀 깊이 복사원본보냄 조상무시 )`이다. */
    private fun update(args: List<RaItem>) {
        val targetRevision = revision(args.getOrNull(0))
        val target = text(args.getOrNull(1)).trim('/')
        val depth = depth((args.getOrNull(3) as? RaWord)?.value, bool(args.getOrNull(2)))
        val sendCopyFrom = bool(args.getOrNull(4))
        driveReport { located, baton, editor -> located.update(targetRevision, target, depth, sendCopyFrom, baton, editor) }
    }

    /** `status`: 서버에서 바뀐 것을 알린다(`svn status -u`). 인자는 `( 대상 재귀 ( 리비전 ) 깊이 )`이다. */
    private fun status(args: List<RaItem>) {
        val target = text(args.getOrNull(0)).trim('/')
        val depth = depth((args.getOrNull(3) as? RaWord)?.value, bool(args.getOrNull(1)))
        val rev = revision(args.getOrNull(2))
        driveReport { located, baton, editor -> located.status(rev, target, depth, baton, editor) }
    }

    /** `diff`: 클라이언트가 보고한 상태와 `url`의 리비전 사이의 차이를 편집기 명령으로 보낸다. 인자는 `( ( 리비전 ) 대상 재귀 조상무시 주소 본문변경보냄 깊이 )`이다. */
    private fun diff(args: List<RaItem>) {
        val rev = revision(args.getOrNull(0))
        val target = text(args.getOrNull(1)).trim('/')
        val ignoreAncestry = bool(args.getOrNull(3))
        val url = fileUrl(text(args.getOrNull(4)))
        val textDeltas = bool(args.getOrNull(5))
        val depth = depth((args.getOrNull(6) as? RaWord)?.value, bool(args.getOrNull(2)))
        driveReport { located, baton, editor -> located.diff(url, rev, rev, target, ignoreAncestry, depth, textDeltas, baton, editor) }
    }

    /** `switch`: 작업 복사본을 다른 주소로 옮기는 변경을 보낸다. 인자는 `( ( 리비전 ) 대상 재귀 주소 깊이 복사원본보냄 조상무시 )`이다. */
    private fun switch(args: List<RaItem>) {
        val rev = revision(args.getOrNull(0))
        val target = text(args.getOrNull(1)).trim('/')
        val url = fileUrl(text(args.getOrNull(3)))
        val depth = depth((args.getOrNull(4) as? RaWord)?.value, bool(args.getOrNull(2)))
        driveReport { located, baton, editor -> located.update(url, rev, target, depth, baton, editor) }
    }

    /**
     * 보고서로 시작하는 작업(`update`, `status`, `diff`, `switch`)의 공통 흐름이다. 클라이언트의 보고를 `finish-report`까지 읽고, 세션 주소가 위치인 저장소에서
     * [operation]을 실행해 편집기 명령을 보낸 뒤, 클라이언트의 확인을 읽고 마지막 응답을 보낸다.
     */
    private fun driveReport(operation: (org.tmatesoft.svn.core.io.SVNRepository, ISVNReporterBaton, org.tmatesoft.svn.core.io.ISVNEditor) -> Unit) {
        server.sendAuthOk()
        val reports = readReport() ?: return
        server.sendAuthOk()
        val located = locatedRepository()
        try {
            operation(located, ISVNReporterBaton { reporter -> replay(reporter, reports) }, RaSvnEditorWriter(server))
        } finally {
            located.closeSession()
        }
        server.receive()
        server.send(RaSvnServer.success(raList()))
    }

    /** `finish-report`까지 보고를 읽는다. 클라이언트가 `abort-report`를 보내면 `null`이다(그때는 마지막 응답만 보낸다). */
    private fun readReport(): List<Report>? {
        val reports = mutableListOf<Report>()
        while (true) {
            val message = server.receive() as? RaList ?: throw RaSvnProtocolException("보고가 끝나기 전에 입력이 끊겼다")
            val name = (message.items.getOrNull(0) as? RaWord)?.value
            val params = (message.items.getOrNull(1) as? RaList)?.items.orEmpty()
            when (name) {
                "set-path" -> reports += Report.SetPath(text(params.getOrNull(0)), (params.getOrNull(1) as? RaNumber)?.value ?: 0, bool(params.getOrNull(2)), depth((params.getOrNull(4) as? RaWord)?.value, true))
                "link-path" -> reports += Report.LinkPath(text(params.getOrNull(0)), fileUrl(text(params.getOrNull(1))), (params.getOrNull(2) as? RaNumber)?.value ?: 0, bool(params.getOrNull(3)), depth((params.getOrNull(5) as? RaWord)?.value, true))
                "delete-path" -> reports += Report.DeletePath(text(params.getOrNull(0)))
                "finish-report" -> return reports
                "abort-report" -> { server.send(RaSvnServer.success(raList())); return null }
                else -> throw RaSvnProtocolException("보고에 올 수 없는 명령: $name")
            }
        }
    }

    private fun replay(reporter: ISVNReporter, reports: List<Report>) {
        for (report in reports) when (report) {
            is Report.SetPath -> reporter.setPath(report.path, null, report.revision, report.depth, report.startEmpty)
            is Report.LinkPath -> reporter.linkPath(report.url, report.path, null, report.revision, report.depth, report.startEmpty)
            is Report.DeletePath -> reporter.deletePath(report.path)
        }
        reporter.finishReport()
    }

    /** 세션 주소가 저장소 위치인 SVNKit 저장소이다. 편집기가 내는 경로는 이 위치(세션 주소) 기준이 된다. */
    private fun locatedRepository() = SVNRepositoryFactory.create(fileUrl("${server.current().rootUrl}/${server.current().path}"))

    /** 클라이언트가 보낸 주소를 저장소 디렉터리의 파일 주소로 바꾼다(저장소 루트 주소 아래만 허용한다). */
    private fun fileUrl(url: String): SVNURL {
        val session = server.current()
        val root = decode(URI(session.rootUrl).rawPath.orEmpty()).trimEnd('/')
        val path = decode(URI(url.trimEnd('/')).rawPath.orEmpty()).trimEnd('/')
        if (path != root && !path.startsWith("$root/")) throw SVNException(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "'$url' is not a child of the session's repository root URL"))
        return SVNURL.fromFile(session.directory).appendPath(path.removePrefix(root).trim('/'), false)
    }

    /** 클라이언트가 보낸 주소를 저장소 기준 절대 경로(`/trunk/a.txt`)로 바꾼다. 주소가 아니라 이미 경로이면 그대로 둔다. */
    private fun repositoryPathOfUrl(url: String): String {
        if (!url.contains("://")) return url
        val root = decode(URI(server.current().rootUrl).rawPath.orEmpty()).trimEnd('/')
        val path = decode(URI(url).rawPath.orEmpty()).trimEnd('/')
        if (path != root && !path.startsWith("$root/")) throw SVNException(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "'$url' is not a child of the session's repository root URL"))
        return "/" + path.removePrefix(root).trim('/')
    }

    private fun depth(word: String?, recurse: Boolean): SVNDepth {
        val parsed = word?.let { SVNDepth.fromString(it) } ?: SVNDepth.UNKNOWN
        return if (parsed != SVNDepth.UNKNOWN) parsed else if (recurse) SVNDepth.INFINITY else SVNDepth.FILES
    }



    // ---- 잠금 ----

    /** 잠금 하나를 프로토콜 모양으로 바꾼다: `( 경로 토큰 소유자 ( 설명 ) 만든시각 ( 만료시각 ) )`. */
    private fun lockItem(lock: org.tmatesoft.svn.core.SVNLock): RaList = raList(
        RaString("/" + lock.path.trimStart('/')), RaString(lock.id), RaString(lock.owner.orEmpty()), optional(lock.comment), RaString(dateString(lock.creationDate).orEmpty()), optional(dateString(lock.expirationDate))
    )

    private fun requireWritable(): Boolean {
        if (server.current().writable) return true
        server.sendFailure(SVNErrorCode.RA_NOT_AUTHORIZED, "Authorization failed")
        return false
    }

    /**
     * `lock`와 `lock-many`: 경로마다 잠그고 결과를 하나씩(`( success ( 잠금 ) )` 또는 실패) 보낸 뒤 `done`과 마지막 응답을 보낸다. 읽기 전용 접속은 인증 확인 없이
     * 거절한다. 인자는 `( ( 설명 ) 훔침 ( ( 경로 ( 리비전 ) ) ... ) )`이다. [single]이면 하나뿐인 `lock` 명령(옛 클라이언트)이라 결과를 `( success ( 잠금 ) )` 하나로 보낸다.
     */
    private fun lock(args: List<RaItem>, single: Boolean = false) {
        if (!requireWritable()) return
        val comment = ((args.getOrNull(0) as? RaList)?.items?.firstOrNull() as? RaString)?.text
        val steal = bool(args.getOrNull(1))
        val targets = LinkedHashMap<String, Long>()
        ((args.getOrNull(2) as? RaList)?.items.orEmpty()).forEach { item ->
            val pair = (item as? RaList)?.items.orEmpty()
            targets["/" + repositoryPath(text(pair.getOrNull(0)))] = revision(pair.getOrNull(1))
        }
        server.sendAuthOk()
        val results = ArrayList<RaItem>()
        repository.lock(targets, comment, steal, object : org.tmatesoft.svn.core.io.ISVNLockHandler {
            override fun handleLock(path: String?, lock: org.tmatesoft.svn.core.SVNLock?, error: SVNErrorMessage?) {
                results += if (error != null) RaSvnServer.failure(error) else RaSvnServer.success(lockItem(requireNotNull(lock)))
            }

            override fun handleUnlock(path: String?, lock: org.tmatesoft.svn.core.SVNLock?, error: SVNErrorMessage?) = Unit
        })
        if (single) { server.send(results.firstOrNull() ?: RaSvnServer.success(raList())); return }
        results.forEach { server.send(it) }
        server.send(RaWord("done"))
        server.send(RaSvnServer.success(raList()))
    }

    /** `unlock`와 `unlock-many`: 경로마다 풀고 결과(`( success ( 경로 ) )` 또는 실패)를 하나씩 보낸다. 인자는 `( 강제 ( ( 경로 ( 토큰 ) ) ... ) )`이다. */
    private fun unlock(args: List<RaItem>, single: Boolean = false) {
        if (!requireWritable()) return
        val force = bool(args.getOrNull(0))
        val targets = LinkedHashMap<String, String?>()
        ((args.getOrNull(1) as? RaList)?.items.orEmpty()).forEach { item ->
            val pair = (item as? RaList)?.items.orEmpty()
            targets["/" + repositoryPath(text(pair.getOrNull(0)))] = ((pair.getOrNull(1) as? RaList)?.items?.firstOrNull() as? RaString)?.text
        }
        server.sendAuthOk()
        val results = ArrayList<RaItem>()
        repository.unlock(targets, force, object : org.tmatesoft.svn.core.io.ISVNLockHandler {
            override fun handleLock(path: String?, lock: org.tmatesoft.svn.core.SVNLock?, error: SVNErrorMessage?) = Unit

            override fun handleUnlock(path: String?, lock: org.tmatesoft.svn.core.SVNLock?, error: SVNErrorMessage?) {
                results += if (error != null) RaSvnServer.failure(error) else RaSvnServer.success(raList(RaString(targets.keys.firstOrNull { it == path || it.trimStart('/') == path?.trimStart('/') }?.removePrefix("/")?.let { relativeToSession(it) } ?: path.orEmpty())))
            }
        })
        if (single) { server.send(results.firstOrNull() ?: RaSvnServer.success(raList())); return }
        results.forEach { server.send(it) }
        server.send(RaWord("done"))
        server.send(RaSvnServer.success(raList()))
    }

    /** 저장소 기준 경로를 클라이언트가 보낸 세션 기준 경로로 되돌린다(`unlock-many`의 결과가 보낸 경로를 그대로 되돌려 주기 때문이다). */
    private fun relativeToSession(repositoryPath: String): String = repositoryPath.removePrefix(server.current().path).trimStart('/')

    /** `get-lock`: 경로의 잠금이다. 없으면 빈 목록이다. */
    private fun getLock(args: List<RaItem>) {
        val path = repositoryPath(text(args.getOrNull(0)))
        server.sendAuthOk()
        val lock = repository.getLock(path)
        server.send(RaSvnServer.success(raList(if (lock == null) raList() else raList(lockItem(lock)))))
    }

    /** `get-locks`: 경로 아래의 모든 잠금이다. */
    private fun getLocks(args: List<RaItem>) {
        val path = repositoryPath(text(args.getOrNull(0)))
        server.sendAuthOk()
        val locks = repository.getLocks(path) ?: emptyArray()
        server.send(RaSvnServer.success(raList(RaList(locks.sortedBy { it.path }.map { lockItem(it) }))))
    }

    // ---- 이력 조회 ----

    /** `get-location-segments`: 경로가 시간에 따라 어디에 있었는지(이름 바꿈과 복사를 따라간다)를 구간으로 보낸다. 구간의 경로에는 앞 슬래시가 없다. */
    private fun getLocationSegments(args: List<RaItem>) {
        val path = repositoryPath(text(args.getOrNull(0)))
        val peg = revision(args.getOrNull(1)); val start = revision(args.getOrNull(2)); val end = revision(args.getOrNull(3))
        server.sendAuthOk()
        repository.getLocationSegments(path, peg, start, end) { segment ->
            server.send(raList(RaNumber(segment.startRevision), RaNumber(segment.endRevision), if (segment.path == null) raList() else raList(RaString(segment.path.trimStart('/')))))
        }
        server.send(RaWord("done"))
        server.send(RaSvnServer.success(raList()))
    }

    /**
     * `get-file-revs`: 파일이 바뀐 리비전마다 리비전 속성과 속성 변화와 내용의 변화를 보낸다(`svn blame`). 내용은 svndiff 조각 문자열이고 빈 문자열로 끝난다.
     * 내용이 바뀌지 않은 리비전도 빈 문자열은 보낸다.
     */
    private fun getFileRevs(args: List<RaItem>) {
        val path = repositoryPath(text(args.getOrNull(0)))
        val start = revision(args.getOrNull(1)); val end = revision(args.getOrNull(2))
        val includeMerged = bool(args.getOrNull(3))
        server.sendAuthOk()
        repository.getFileRevisions(path, start, end, includeMerged, object : org.tmatesoft.svn.core.io.ISVNFileRevisionHandler {
            private var headerWritten = false
            private var deltaEnded = false

            override fun openRevision(fileRevision: org.tmatesoft.svn.core.io.SVNFileRevision) {
                headerWritten = false; deltaEnded = false
                val revprops = fileRevision.revisionProperties ?: SVNProperties()
                val delta = fileRevision.propertiesDelta ?: SVNProperties()
                server.send(raList(
                    RaString("/" + fileRevision.path.trimStart('/')), RaNumber(fileRevision.revision),
                    RaList(revprops.nameSet().sorted().map { raList(RaString(it), RaString(propertyBytes(revprops.getSVNPropertyValue(it)))) }),
                    RaList(delta.nameSet().sorted().map { name -> delta.getSVNPropertyValue(name).let { raList(RaString(name), if (it == null) raList() else raList(RaString(propertyBytes(it)))) } }),
                    bool(fileRevision.isResultOfMerge)
                ))
            }

            override fun applyTextDelta(path: String?, baseChecksum: String?) { headerWritten = false }

            override fun textDeltaChunk(path: String?, window: org.tmatesoft.svn.core.io.diff.SVNDiffWindow): java.io.OutputStream {
                val bytes = ByteArrayOutputStream()
                window.writeTo(bytes, !headerWritten, false)
                headerWritten = true
                server.send(RaString(bytes.toByteArray()))
                return java.io.OutputStream.nullOutputStream()
            }

            override fun textDeltaEnd(path: String?) { server.send(RaString("")); deltaEnded = true }

            override fun closeRevision(token: String?) { if (!deltaEnded) server.send(RaString("")) }
        })
        server.send(RaWord("done"))
        server.send(RaSvnServer.success(raList()))
    }

    /** `get-mergeinfo`: 경로의 병합 정보(`svn:mergeinfo`)를 `( 경로 병합정보 )` 목록으로 보낸다. 없으면 빈 목록이다. */
    private fun getMergeInfo(args: List<RaItem>) {
        val paths = ((args.getOrNull(0) as? RaList)?.items.orEmpty()).map { repositoryPath(text(it)) }.toTypedArray()
        val rev = latest(revision(args.getOrNull(1)))
        val inheritance = when ((args.getOrNull(2) as? RaWord)?.value) {
            "explicit" -> org.tmatesoft.svn.core.SVNMergeInfoInheritance.EXPLICIT
            "nearest-ancestor" -> org.tmatesoft.svn.core.SVNMergeInfoInheritance.NEAREST_ANCESTOR
            else -> org.tmatesoft.svn.core.SVNMergeInfoInheritance.INHERITED
        }
        val includeDescendants = bool(args.getOrNull(3))
        server.sendAuthOk()
        val result = repository.getMergeInfo(paths, rev, inheritance, includeDescendants).orEmpty()
        val entries = result.entries.sortedBy { it.key }.map { (path, info) ->
            raList(RaString("/" + path.trimStart('/')), RaString(org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil.formatMergeInfoToString(info.mergeSourcesToMergeLists, null)))
        }
        server.send(RaSvnServer.success(raList(RaList(entries))))
    }

    /** 아직 지원하지 않는 명령이다. 인증 확인 뒤에 "지원하지 않는 기능"으로 답한다(클라이언트가 읽을 수 있는 오류이다). */
    private fun unsupported(name: String) {
        server.sendAuthOk()
        server.sendFailure(SVNErrorCode.UNSUPPORTED_FEATURE, "The '$name' command is not supported by this server")
    }

    // ---- 값 만들기 ----

    private fun kindWord(kind: SVNNodeKind?): String = when (kind) { SVNNodeKind.DIR -> "dir"; SVNNodeKind.FILE -> "file"; SVNNodeKind.NONE -> "none"; else -> "unknown" }

    /** 파일의 크기이다. 디렉터리는 "알 수 없음"(부호 없는 64비트 최댓값)이다. */
    private fun size(entry: SVNDirEntry): RaItem = if (entry.kind == SVNNodeKind.FILE) RaNumber(entry.size) else RaNumber(-1)

    private fun bool(value: Boolean): RaItem = RaWord(if (value) "true" else "false")

    private fun optional(value: String?): RaItem = if (value == null) raList() else raList(RaString(value))

    private fun dateString(date: Date?): String? = date?.let { SVNDate.formatDate(it) }

    private fun propertyBytes(value: SVNPropertyValue?): ByteArray = value.toRaBytes()

    private fun decode(path: String): String = URLDecoder.decode(path.replace("+", "%2B"), Charsets.UTF_8)

    private companion object {
        /** 파일 내용을 나누어 보내는 조각의 크기이다. */
        const val CHUNK = 64 * 1024

        val STANDARD_REVPROPS = setOf(SVNRevisionProperty.AUTHOR, SVNRevisionProperty.DATE, SVNRevisionProperty.LOG)
    }
}
