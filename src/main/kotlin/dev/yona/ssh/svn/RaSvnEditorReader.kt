package dev.yona.ssh.svn

import org.tmatesoft.svn.core.SVNCommitInfo
import org.tmatesoft.svn.core.SVNPropertyValue
import org.tmatesoft.svn.core.internal.delta.SVNDeltaReader
import org.tmatesoft.svn.core.io.ISVNEditor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * 커밋하는 클라이언트가 보내는 편집기 명령을 읽어 트리로 모은 뒤, 닫을 때 SVNKit의 커밋 편집기([ISVNEditor])에 올바른 순서로 재생한다.
 *
 * 클라이언트는 디렉터리를 먼저 닫고(`close-dir`) 파일 내용(`apply-textdelta` 등)은 그 뒤에 보낼 수 있다(녹화로 확인했다). SVNKit의 편집기는 파일을 열고 닫는 사이에
 * 내용을 받아야 하므로 도착 순서대로 흘려보낼 수 없다. 그래서 `close-edit`까지 읽어 모은 뒤 디렉터리를 깊이 우선으로 돌며 재생한다. 파일 내용(svndiff 조각)은 메모리에
 * 쌓지 않고 임시 파일에 이어 쓰므로 큰 파일도 커밋할 수 있다. 사용을 끝내면 [close]로 임시 파일을 지운다.
 *
 * 복사의 원본(`add-dir`, `add-file`의 `copyfrom`)은 클라이언트가 전체 주소로 보내므로 [copySource]가 SVNKit이 받는 저장소 경로로 바꾼다.
 */
class RaSvnEditorReader(private val temporaryDirectory: File? = null, private val copySource: (String) -> String = { it }) : AutoCloseable {

    private sealed class Node(val path: String)

    private class Property(val name: String, val value: ByteArray?)

    private class Directory(path: String, val revision: Long, val added: Boolean, val copyPath: String?, val copyRevision: Long) : Node(path) {
        val properties = mutableListOf<Property>()
        val children = mutableListOf<Node>()
    }

    private class File_(path: String, val revision: Long, val added: Boolean, val copyPath: String?, val copyRevision: Long) : Node(path) {
        val properties = mutableListOf<Property>()
        var baseChecksum: String? = null
        var textChecksum: String? = null
        var delta: DeltaSpool? = null
    }

    private class Deletion(path: String, val revision: Long) : Node(path)

    private class Absent(path: String, val directory: Boolean) : Node(path)

    /** 파일 하나의 svndiff 바이트를 쌓는 곳이다. 작은 것은 메모리, [MEMORY_LIMIT]을 넘으면 임시 파일이다. */
    private inner class DeltaSpool {
        private var memory: ByteArrayOutputStream? = ByteArrayOutputStream()
        private var file: File? = null
        private var out: OutputStream? = null
        var size = 0L
            private set

        fun write(bytes: ByteArray) {
            size += bytes.size
            val inMemory = memory
            if (inMemory != null && size <= MEMORY_LIMIT) { inMemory.write(bytes); return }
            if (out == null) {
                val spool = File.createTempFile("ra-svn-delta", ".svndiff", temporaryDirectory).also { temporary += it }
                file = spool
                out = FileOutputStream(spool).buffered().also { stream -> inMemory?.let { stream.write(it.toByteArray()) } }
                memory = null
            }
            out!!.write(bytes)
        }

        fun open(): InputStream {
            out?.close(); out = null
            return file?.inputStream()?.buffered() ?: java.io.ByteArrayInputStream(memory?.toByteArray() ?: ByteArray(0))
        }
    }

    private val temporary = mutableListOf<File>()
    private val tokens = HashMap<String, Node>()
    private var root: Directory? = null
    private var done = false
    private var aborted = false

    /** `close-edit`까지 읽었으면 `true`이다. */
    val isDone: Boolean get() = done

    /** `abort-edit`를 받았으면 `true`이다(재생하지 않는다). */
    val isAborted: Boolean get() = aborted

    /**
     * 클라이언트의 편집기 명령 하나를 처리한다. [name]은 명령 이름, [params]는 인자 목록이다. `close-edit`나 `abort-edit`를 받으면 [isDone]이 `true`가 된다.
     * 모르는 명령이거나 앞선 명령과 맞지 않으면 [RaSvnProtocolException]이다.
     */
    fun accept(name: String, params: List<RaItem>) {
        when (name) {
            "open-root" -> { root = Directory("", revision(params.getOrNull(0)), false, null, -1).also { tokens[string(params.getOrNull(1))] = it } }
            "delete-entry" -> directory(params.getOrNull(2)).children += Deletion(string(params.getOrNull(0)), revision(params.getOrNull(1)))
            "add-dir" -> {
                val (copyPath, copyRevision) = copyFrom(params.getOrNull(3))
                val child = Directory(string(params.getOrNull(0)), -1, true, copyPath, copyRevision)
                directory(params.getOrNull(1)).children += child; tokens[string(params.getOrNull(2))] = child
            }
            "open-dir" -> {
                val child = Directory(string(params.getOrNull(0)), revision(params.getOrNull(3)), false, null, -1)
                directory(params.getOrNull(1)).children += child; tokens[string(params.getOrNull(2))] = child
            }
            "change-dir-prop" -> directory(params.getOrNull(0)).properties += Property(string(params.getOrNull(1)), optionalBytes(params.getOrNull(2)))
            "close-dir" -> tokens.remove(string(params.getOrNull(0)))
            "absent-dir" -> directory(params.getOrNull(1)).children += Absent(string(params.getOrNull(0)), true)
            "absent-file" -> directory(params.getOrNull(1)).children += Absent(string(params.getOrNull(0)), false)
            "add-file" -> {
                val (copyPath, copyRevision) = copyFrom(params.getOrNull(3))
                val file = File_(string(params.getOrNull(0)), -1, true, copyPath, copyRevision)
                directory(params.getOrNull(1)).children += file; tokens[string(params.getOrNull(2))] = file
            }
            "open-file" -> {
                val file = File_(string(params.getOrNull(0)), revision(params.getOrNull(3)), false, null, -1)
                directory(params.getOrNull(1)).children += file; tokens[string(params.getOrNull(2))] = file
            }
            "apply-textdelta" -> file(params.getOrNull(0)).also { it.baseChecksum = optionalString(params.getOrNull(1)); it.delta = DeltaSpool() }
            "textdelta-chunk" -> (file(params.getOrNull(0)).delta ?: throw RaSvnProtocolException("apply-textdelta 없이 조각이 왔다")).write((params.getOrNull(1) as? RaString)?.bytes ?: throw RaSvnProtocolException("조각이 문자열이 아니다"))
            "textdelta-end" -> Unit
            "change-file-prop" -> file(params.getOrNull(0)).properties += Property(string(params.getOrNull(1)), optionalBytes(params.getOrNull(2)))
            "close-file" -> file(params.getOrNull(0)).let { it.textChecksum = optionalString(params.getOrNull(1)); tokens.remove(string(params.getOrNull(0))) }
            "close-edit" -> done = true
            "abort-edit" -> { done = true; aborted = true }
            else -> throw RaSvnProtocolException("편집기에 올 수 없는 명령: $name")
        }
    }

    /** 모은 편집을 [editor]에 재생하고 [ISVNEditor.closeEdit]의 결과를 돌려준다. */
    fun replay(editor: ISVNEditor): SVNCommitInfo? {
        val top = root ?: throw RaSvnProtocolException("open-root 없이 편집이 끝났다")
        editor.openRoot(top.revision)
        replayDirectory(editor, top)
        editor.closeDir()
        return editor.closeEdit()
    }

    private fun replayDirectory(editor: ISVNEditor, directory: Directory) {
        directory.properties.forEach { editor.changeDirProperty(it.name, value(it.value)) }
        for (child in directory.children) when (child) {
            is Directory -> {
                if (child.added) editor.addDir(child.path, child.copyPath, child.copyRevision) else editor.openDir(child.path, child.revision)
                replayDirectory(editor, child)
                editor.closeDir()
            }
            is File_ -> replayFile(editor, child)
            is Deletion -> editor.deleteEntry(child.path, child.revision)
            is Absent -> if (child.directory) editor.absentDir(child.path) else editor.absentFile(child.path)
        }
    }

    private fun replayFile(editor: ISVNEditor, file: File_) {
        if (file.added) editor.addFile(file.path, file.copyPath, file.copyRevision) else editor.openFile(file.path, file.revision)
        file.properties.forEach { editor.changeFileProperty(file.path, it.name, value(it.value)) }
        file.delta?.let { spool ->
            editor.applyTextDelta(file.path, file.baseChecksum)
            val reader = SVNDeltaReader()
            spool.open().use { input ->
                val buffer = ByteArray(DELTA_BUFFER)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    reader.nextWindow(buffer, 0, n, file.path, editor)
                }
            }
            reader.reset(file.path, editor)
            editor.textDeltaEnd(file.path)
        }
        editor.closeFile(file.path, file.textChecksum)
    }

    override fun close() { temporary.forEach { it.delete() }; temporary.clear() }

    // ---- 인자 읽기 ----

    private fun string(item: RaItem?): String = (item as? RaString)?.text ?: throw RaSvnProtocolException("문자열이 와야 한다")

    private fun revision(item: RaItem?): Long = ((item as? RaList)?.items?.firstOrNull() as? RaNumber)?.value ?: -1L

    private fun optionalString(item: RaItem?): String? = ((item as? RaList)?.items?.firstOrNull() as? RaString)?.text

    private fun optionalBytes(item: RaItem?): ByteArray? = ((item as? RaList)?.items?.firstOrNull() as? RaString)?.bytes

    private fun copyFrom(item: RaItem?): Pair<String?, Long> {
        val items = (item as? RaList)?.items.orEmpty()
        return if (items.size >= 2) (items[0] as? RaString)?.text?.let(copySource) to ((items[1] as? RaNumber)?.value ?: -1L) else null to -1L
    }

    private fun directory(token: RaItem?): Directory = tokens[string(token)] as? Directory ?: throw RaSvnProtocolException("열지 않은 디렉터리 토큰: ${(token as? RaString)?.text}")

    private fun file(token: RaItem?): File_ = tokens[string(token)] as? File_ ?: throw RaSvnProtocolException("열지 않은 파일 토큰: ${(token as? RaString)?.text}")

    private fun value(bytes: ByteArray?): SVNPropertyValue? = bytes?.let { SVNPropertyValue.create("", it) }

    private companion object {
        /** 이 크기까지는 메모리에 쌓고 넘으면 임시 파일에 쓴다. */
        const val MEMORY_LIMIT = 1L * 1024 * 1024

        const val DELTA_BUFFER = 64 * 1024
    }
}
