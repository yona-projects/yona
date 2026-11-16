package dev.yona.ssh.svn

import org.slf4j.LoggerFactory
import org.tmatesoft.svn.core.SVNDirEntry
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNLogEntry
import org.tmatesoft.svn.core.SVNNodeKind
import org.tmatesoft.svn.core.SVNProperties
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory
import org.tmatesoft.svn.core.internal.util.SVNDate
import org.tmatesoft.svn.core.io.SVNRepository
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Date

/** 주소의 경로(`/소유자/이름/하위경로`)가 가리키는 저장소를 찾고 이 접속이 쓸 수 있는지 판정하는 쪽이다. 인가는 이 구현이 한다([dev.yona.ssh.SshAccess]와 같은 규칙). */
fun interface RaSvnResolver {
    fun resolve(path: String): RaSvnResolution
}

/** [RaSvnResolver]의 결과이다. */
sealed interface RaSvnResolution {

    /**
     * 저장소를 찾았고 읽을 수 있다. [directory]는 저장소 디렉터리, [repositoryPath]는 주소에서 저장소까지의 경로(`/소유자/이름`, 이 뒤는 저장소 안의 하위 경로),
     * [writable]은 커밋할 수 있는지이다.
     */
    data class Found(val directory: File, val repositoryPath: String, val writable: Boolean) : RaSvnResolution

    /** 저장소가 없거나 읽을 권한이 없다. [message]를 클라이언트에 보인다(있는지 없는지 구분해 알려 주지 않으려고 둘을 같은 문구로 쓴다). */
    data class Failed(val message: String) : RaSvnResolution
}

/**
 * Subversion `ra_svn` 프로토콜을 서버로서 처리한다(`svn+ssh`의 `svnserve -t`가 하던 일). 한 접속을 맡으며, 인사와 인증(터널에서 이미 인증한 사용자 [user]를 `EXTERNAL`로 받는다)을 마치면
 * 클라이언트의 명령을 하나씩 읽어 SVNKit으로 저장소에 실행하고 응답한다. 연결이 끊기거나 규칙을 어기면 [serve]가 끝난다. 스레드 하나가 한 접속을 맡는다.
 *
 * 구현한 명령은 [RaSvnCommands]에 있다. 프로토콜의 형식은 실제 `svnserve -t`(1.14.5)와 `svn` 클라이언트의 대화를 녹화해 맞췄다.
 */
class RaSvnServer(input: InputStream, private val output: OutputStream, val user: String, private val resolver: RaSvnResolver) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val reader = RaSvnReader(input)
    private val writer = RaSvnWriter(output)

    /** 지금 세션의 저장소이다. 인증을 마치면 정해진다. */
    private var session: Session? = null

    /** 한 접속이 쓰는 저장소의 정보이다. [path]는 세션 주소의 저장소 안 경로(앞 슬래시 없음)로, `reparent`가 바꾼다. */
    class Session(val repository: SVNRepository, val directory: File, val rootUrl: String, val writable: Boolean, var path: String)

    /** 인사부터 마지막까지 처리한다. 접속을 닫으면 돌아온다. */
    fun serve() {
        try {
            if (!handshake()) return
            commandLoop()
        } catch (e: RaSvnProtocolException) {
            log.debug("ra_svn protocol ended: {}", e.message)
        } catch (e: java.io.IOException) {
            log.debug("ra_svn connection closed: {}", e.message)
        } finally {
            session?.repository?.closeSession()
        }
    }

    // ---- 인사와 인증 ----

    private fun handshake(): Boolean {
        send(success(raList(RaNumber(2), RaNumber(2), raList(), caps(SERVER_CAPABILITIES))))
        val response = receive() as? RaList ?: return false
        val version = (response.items.getOrNull(0) as? RaNumber)?.value
        val url = (response.items.getOrNull(2) as? RaString)?.text
        if (version != 2L || url == null) return fail(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed network data")
        val resolution = resolve(url) ?: return false
        val (found, rest, rootUrl) = resolution
        val repository = try {
            FSRepositoryFactory.setup()
            SVNRepositoryFactory.create(SVNURL.fromFile(found.directory))
        } catch (e: SVNException) {
            return fail(SVNErrorCode.RA_SVN_REPOS_NOT_FOUND, "No repository found in '$url'")
        }
        // 잠금과 커밋은 저장소가 사용자 이름을 알아야 한다(터널에서 이미 인증한 사용자)
        repository.authenticationManager = BasicAuthenticationManager.newInstance(user, null)
        val uuid = try { repository.getRepositoryUUID(true) } catch (e: SVNException) { return fail(e) }
        session = Session(repository, found.directory, rootUrl, found.writable, rest)
        // 터널에서 이미 인증했으므로 EXTERNAL만 내놓는다. 클라이언트가 ANONYMOUS를 고르면 사용자가 없는 접속이 되므로 내놓지 않는다
        send(success(raList(raList(RaWord("EXTERNAL")), RaString(uuid))))
        val auth = receive() as? RaList ?: return false
        if ((auth.items.firstOrNull() as? RaWord)?.value != "EXTERNAL") return fail(SVNErrorCode.RA_NOT_AUTHORIZED, "Unsupported authentication mechanism")
        send(success(raList()))
        send(success(raList(RaString(uuid), RaString(rootUrl), caps(REPOSITORY_CAPABILITIES))))
        return true
    }

    /** 주소에서 저장소를 찾는다. 찾지 못하면 클라이언트에 이유를 알리고 `null`이다. 돌려주는 값은 (찾은 결과, 저장소 안 경로, 저장소 루트 주소)이다. */
    private fun resolve(url: String): Triple<RaSvnResolution.Found, String, String>? {
        val uri = try { URI(url) } catch (e: Exception) { fail(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed URL '$url'"); return null }
        val rawPath = uri.rawPath.orEmpty()
        val decoded = decode(rawPath)
        return when (val resolution = resolver.resolve(decoded)) {
            is RaSvnResolution.Failed -> { fail(SVNErrorCode.RA_SVN_REPOS_NOT_FOUND, resolution.message); null }
            is RaSvnResolution.Found -> {
                val root = resolution.repositoryPath.trimEnd('/')
                val rest = decoded.removePrefix(root).trim('/')
                // 저장소 루트 주소는 클라이언트가 보낸 글자 그대로(인코딩 포함) 저장소 경로까지 자른 것이다
                val rootUrl = url.removeSuffix(rawPath) + rawPath.substring(0, rawPrefixLength(rawPath, root))
                Triple(resolution, rest, rootUrl)
            }
        }
    }

    /** 인코딩된 경로에서 디코딩하면 [root]가 되는 앞부분의 길이이다. */
    private fun rawPrefixLength(rawPath: String, root: String): Int {
        val segments = root.trim('/').split('/').size
        var index = 0; var count = 0
        while (index < rawPath.length && count < segments) {
            if (rawPath[index] == '/') { index++; continue }
            val next = rawPath.indexOf('/', index).let { if (it < 0) rawPath.length else it }
            index = next; count++
        }
        return index
    }

    private fun decode(path: String): String = URLDecoder.decode(path.replace("+", "%2B"), Charsets.UTF_8)

    // ---- 명령 처리 ----

    private fun commandLoop() {
        val commands = RaSvnCommands(this)
        while (true) {
            val message = receive() as? RaList ?: return
            val name = (message.items.getOrNull(0) as? RaWord)?.value ?: throw RaSvnProtocolException("명령 이름이 없다")
            val args = (message.items.getOrNull(1) as? RaList)?.items.orEmpty()
            try {
                if (!commands.dispatch(name, args)) { sendAuthOk(); sendFailure(SVNErrorCode.RA_SVN_UNKNOWN_CMD, "Unknown command '$name'") }
            } catch (e: SVNException) {
                sendFailure(e)
            } catch (e: IllegalArgumentException) {
                sendFailure(SVNErrorCode.RA_SVN_MALFORMED_DATA, e.message ?: "Malformed network data")
            }
            writer.flush()
        }
    }

    /** 명령마다 맨 앞에 보내는 "인증이 더 필요하지 않다"는 응답이다(`( success ( ( ) 0: ) )`). */
    fun sendAuthOk() = send(success(raList(raList(), RaString(""))))

    fun send(item: RaItem) { writer.write(item) }

    /**
     * 클라이언트의 다음 항목을 읽는다. 기다리기 전에 보낼 것을 먼저 비운다. SSH 채널은 내보내기를 비워야 나가므로, 인사말처럼 응답을 기다리는 메시지가
     * 갇히면 양쪽이 서로를 기다리며 멈춘다.
     */
    fun receive(): RaItem? { writer.flush(); return reader.read() }

    fun flush() = writer.flush()

    fun current(): Session = checkNotNull(session) { "세션이 없다" }

    fun sendFailure(e: SVNException) = send(failure(e.errorMessage))

    fun sendFailure(code: SVNErrorCode, message: String) = send(failure(SVNErrorMessage.create(code, message)))

    /** 인사 중에 실패를 알리고 `false`를 돌려준다(접속을 끝낸다). */
    private fun fail(code: SVNErrorCode, message: String): Boolean { sendFailure(code, message); writer.flush(); return false }

    private fun fail(e: SVNException): Boolean { sendFailure(e); writer.flush(); return false }

    private fun caps(words: List<String>) = RaList(words.map { RaWord(it) })

    companion object {
        /**
         * 서버가 내놓는 기능이다. `edit-pipeline`은 커밋의 편집기 명령을 응답을 기다리지 않고 이어 보내게 한다. `svndiff1`과 `accepts-svndiff2`는 내놓지 않아
         * 클라이언트가 압축하지 않은 svndiff0을 쓰게 한다(구현이 단순하다). `depth`와 `mergeinfo`는 아직 구현하지 않았다.
         */
        val SERVER_CAPABILITIES = listOf("edit-pipeline", "absent-entries", "commit-revprops", "log-revprops", "inherited-props", "list")

        /** 저장소 정보 응답의 기능 목록이다. */
        val REPOSITORY_CAPABILITIES = listOf("mergeinfo")

        /** SVNKit의 [SVNErrorMessage.getMessage]는 `svn: E160013: ` 접두어를 포함한다. 클라이언트가 접두어를 직접 붙이므로 본문만 보낸다. */
        fun plainMessage(message: SVNErrorMessage): String = message.message.orEmpty().replaceFirst(Regex("^svn: [EW]\\d+: "), "")

        fun success(vararg params: RaItem): RaList = raList(RaWord("success"), RaList(params.toList()))

        fun success(params: RaList): RaList = raList(RaWord("success"), params)

        /** 오류 응답이다(`( failure ( ( 코드 메시지 파일 줄 ) ) )`). 연결된 오류는 모두 목록에 담는다. */
        fun failure(message: SVNErrorMessage): RaList {
            val chain = generateSequence(message) { it.childErrorMessage }.toList()
            return raList(RaWord("failure"), RaList(chain.map {
                raList(RaNumber(it.errorCode.code.toLong()), RaString(plainMessage(it)), RaString(""), RaNumber(0))
            }))
        }
    }
}
