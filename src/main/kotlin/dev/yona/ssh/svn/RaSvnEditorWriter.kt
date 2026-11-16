package dev.yona.ssh.svn

import org.tmatesoft.svn.core.SVNCommitInfo
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.io.ISVNEditor
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow
import java.io.ByteArrayOutputStream

/**
 * SVNKit의 편집기 호출([ISVNEditor])을 `ra_svn`의 편집기 명령으로 바꿔 클라이언트에 보낸다. `update`, `switch`, `diff`, `status`가 서버에서 클라이언트로
 * 변경을 흘려보내는 방식이다(`( open-root ( ( 기준리비전 ) 토큰 ) )` 같은 명령의 연속). 형식은 실제 `svnserve -t`가 보낸 것을 녹화해 맞췄다.
 *
 * 디렉터리 토큰은 `d<번호>`, 파일 토큰은 `c<번호>`이고 번호는 하나의 순번을 같이 쓴다(`svnserve`와 같다). 텍스트 델타는 압축하지 않은 svndiff0으로 보낸다
 * (서버가 `svndiff1`을 내놓지 않기 때문이다).
 */
class RaSvnEditorWriter(private val server: RaSvnServer) : ISVNEditor {

    private var counter = 0
    private val directories = ArrayDeque<String>()
    private val files = HashMap<String, String>()

    /** 파일 하나의 텍스트 델타를 이어 보내는 동안의 상태이다. 첫 조각에만 svndiff 머리글이 붙는다. */
    private var headerWritten = false

    private fun send(name: String, vararg params: RaItem) = server.send(raList(RaWord(name), RaList(params.toList())))

    private fun newToken(prefix: Char) = "$prefix${counter++}"

    private fun revisionList(revision: Long): RaItem = if (revision < 0) raList() else raList(RaNumber(revision))

    private val parent get() = directories.last()

    override fun targetRevision(revision: Long) = send("target-rev", RaNumber(revision))

    override fun openRoot(revision: Long) {
        val token = newToken('d')
        directories.addLast(token)
        send("open-root", revisionList(revision), RaString(token))
    }

    override fun deleteEntry(path: String, revision: Long) = send("delete-entry", RaString(path), revisionList(revision), RaString(parent))

    override fun absentDir(path: String) = send("absent-dir", RaString(path), RaString(parent))

    override fun absentFile(path: String) = send("absent-file", RaString(path), RaString(parent))

    override fun addDir(path: String, copyFromPath: String?, copyFromRevision: Long) {
        val token = newToken('d')
        send("add-dir", RaString(path), RaString(parent), RaString(token), copyFrom(copyFromPath, copyFromRevision))
        directories.addLast(token)
    }

    override fun openDir(path: String, revision: Long) {
        val token = newToken('d')
        send("open-dir", RaString(path), RaString(parent), RaString(token), revisionList(revision))
        directories.addLast(token)
    }

    override fun changeDirProperty(name: String, value: SVNPropertyValue?) = send("change-dir-prop", RaString(parent), RaString(name), propertyValue(value))

    override fun closeDir() { send("close-dir", RaString(directories.removeLast())) }

    override fun addFile(path: String, copyFromPath: String?, copyFromRevision: Long) {
        val token = newToken('c')
        files[path] = token
        send("add-file", RaString(path), RaString(parent), RaString(token), copyFrom(copyFromPath, copyFromRevision))
    }

    override fun openFile(path: String, revision: Long) {
        val token = newToken('c')
        files[path] = token
        send("open-file", RaString(path), RaString(parent), RaString(token), revisionList(revision))
    }

    override fun changeFileProperty(path: String, name: String, value: SVNPropertyValue?) = send("change-file-prop", RaString(token(path)), RaString(name), propertyValue(value))

    override fun closeFile(path: String, textChecksum: String?) {
        send("close-file", RaString(token(path)), if (textChecksum == null) raList() else raList(RaString(textChecksum)))
        files.remove(path)
    }

    override fun applyTextDelta(path: String, baseChecksum: String?) {
        headerWritten = false
        send("apply-textdelta", RaString(token(path)), if (baseChecksum == null) raList() else raList(RaString(baseChecksum)))
    }

    override fun textDeltaChunk(path: String, window: SVNDiffWindow): java.io.OutputStream {
        val bytes = ByteArrayOutputStream()
        window.writeTo(bytes, !headerWritten, false)
        headerWritten = true
        send("textdelta-chunk", RaString(token(path)), RaString(bytes.toByteArray()))
        return java.io.OutputStream.nullOutputStream()
    }

    override fun textDeltaEnd(path: String) { send("textdelta-end", RaString(token(path))) }

    override fun closeEdit(): SVNCommitInfo? {
        send("close-edit")
        return null
    }

    override fun abortEdit() { send("abort-edit") }

    private fun token(path: String): String = files[path] ?: throw IllegalStateException("열지 않은 파일: $path")

    private fun copyFrom(path: String?, revision: Long): RaItem = if (path == null) raList() else raList(RaString(path), RaNumber(revision))

    private fun propertyValue(value: SVNPropertyValue?): RaItem = if (value == null) raList() else raList(RaString(value.toRaBytes()))
}
