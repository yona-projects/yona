package dev.yona.repository.hg

import dev.yona.repository.TagAuthor
import dev.yona.repository.TagCreateError
import io.github.search5.hg4j.api.BookmarkCommand
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.lib.NodeId
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files

/**
 * Mercurial 저장소에 태그를 만들고 지운다(X-7 9단계 보완, `decisions.md` A-389). Mercurial의 태그는 `.hgtags` 파일의 한 줄이라 태그를 바꾸는 일은 곧 `.hgtags`를 고친
 * **체인지셋을 하나 만드는 일**이다. 서버 저장소에는 작업 파일이 없고 원본 `hg tag`도 작업 파일이 없는 저장소에서는 거절하므로(`abort: working directory is not at a branch
 * head`), 원본이 하는 방식 그대로 **임시 작업 사본에서** 태그 체인지셋을 만들어 서버 저장소로 가져온다.
 *
 * 1. 기본 위치(`HgRevisions.head`)의 노드를 부모로 삼아 임시 작업 사본을 만든다(서버 저장소를 가져온 뒤 그 노드로 갱신한다).
 * 2. 원본 `hg tag`와 같은 형식으로 `.hgtags`를 쓰고(옮기거나 지울 때는 이전 노드 줄을 먼저 쓴다) 지정한 작성자로 커밋한다.
 * 3. 새 체인지셋을 서버 저장소로 가져오고, 기본 위치가 이름표였다면 그 이름표를 새 체인지셋으로 옮긴다(원본에서 활성 이름표 위에 커밋하면 이름표가 따라오는 것과 같다).
 *
 * hg4j `TagCommand`는 쓰지 않는다. 이동과 삭제 때 이전 노드 줄을 쓰지 않아 `.hgtags`가 원본과 달라지고, 예약 이름 같은 원본의 검사가 없기 때문이다(hg4j 세션 확인).
 */
@Component
class HgTagWriter(private val repositories: HgRepositories) {

    /**
     * 태그 [tag]를 [target]에 단다. [target]은 이름표, 태그, 커밋 번호(앞부분만 줘도 된다)이고 없으면 기본 위치의 끝이다. [message]가 있으면 태그 체인지셋의 설명으로 쓴다
     * (Mercurial에는 주석 태그가 없다). 성공하면 `null`, 아니면 오류이다.
     */
    fun create(owner: String, name: String, tag: String, target: String?, message: String?, author: TagAuthor): TagCreateError? {
        if (!validName(tag)) return TagCreateError.INVALID_NAME
        val (base, node) = repositories.open(owner, name).use { hg ->
            val base = HgRevisions.head(hg) ?: return TagCreateError.EMPTY_REPOSITORY
            if (tag in HgRevisions.tags(hg)) return TagCreateError.ALREADY_EXISTS
            val node = (if (target == null) base else resolveCommit(hg, target)) ?: return TagCreateError.NO_TARGET
            base to node
        }
        commitTag(owner, name, base, tag, node, message ?: "Added tag $tag for changeset ${node.take(SHORT_ID)}", author)
        return null
    }

    /** 태그를 지운다. 그런 태그가 없으면 `false`이다. */
    fun delete(owner: String, name: String, tag: String, author: TagAuthor): Boolean {
        val base = repositories.open(owner, name).use { hg ->
            if (tag !in HgRevisions.tags(hg)) return false
            HgRevisions.head(hg) ?: return false
        }
        commitTag(owner, name, base, tag, null, "Removed tag $tag", author)
        return true
    }

    /** 대상이 저장소에 있는 커밋이면 전체 노드이다. 이름표나 태그 이름도 풀고, 없거나 둘 이상에 맞으면 `null`이다. */
    private fun resolveCommit(hg: Hg, target: String): String? = try {
        HgRevisions.resolve(hg, target)?.takeIf { HgChangesets.revOf(HgRevisions.changelog(hg), it) >= 0 }
    } catch (e: java.io.IOException) {
        null
    }

    /**
     * [base] 위에 `.hgtags`를 고친 체인지셋을 만들어 서버 저장소로 가져온다. [node]가 `null`이면 지우기이다. 임시 작업 사본은 끝나면 지운다.
     */
    private fun commitTag(owner: String, name: String, base: String, tag: String, node: String?, message: String, author: TagAuthor) {
        val scratch = repositories.scratchDirectory().also { it.mkdirs() }.let { Files.createTempDirectory(it.toPath(), "tag-").toFile() }
        try {
            val work = File(scratch, "work")
            val server = repositories.directoryOf(owner, name)
            io.github.search5.hg4j.api.Hg.init().setDirectory(work).call()
            val created = Hg.open(work).use { w ->
                io.github.search5.hg4j.api.PullCommand(w.repository).setSource(server.canonicalPath).call()
                w.update().setRevision(base).call()
                val tagsFile = File(work, ".hgtags")
                val previous = HgRevisions.tags(w)[tag]
                writeTagLine(tagsFile, tag, previous, node ?: NULL_NODE)
                w.add().addFile(".hgtags").call()
                NodeId(w.commit().setAuthor("${author.name} <${author.email}>").setMessage(message).call()).toHex()
            }
            repositories.open(owner, name).use { hg ->
                val before = HgRevisions.bookmarks(hg)
                io.github.search5.hg4j.api.PullCommand(hg.repository).setSource(work.canonicalPath).call()
                // 기본 위치가 이름표였고 그동안 움직이지 않았으면 새 체인지셋으로 옮긴다
                HgRepositories.DEFAULT_BOOKMARKS.firstOrNull { before[it] == base }?.let {
                    BookmarkCommand(hg.repository).setBookmarkName(it).setRevision(created).setForce(true).call()
                }
            }
        } finally {
            scratch.deleteRecursively()
        }
    }

    /** 원본 `hg tag`와 같이 `.hgtags` 끝에 줄을 더한다: 이미 있는 이름이면 이전 노드 줄을 먼저 쓰고, 끝에 줄바꿈이 없으면 먼저 줄바꿈을 쓴다. */
    private fun writeTagLine(file: File, tag: String, previous: String?, node: String) {
        val existing = if (file.isFile) file.readBytes() else ByteArray(0)
        val out = java.io.ByteArrayOutputStream()
        out.write(existing)
        if (existing.isNotEmpty() && existing.last() != '\n'.code.toByte()) out.write('\n'.code)
        if (previous != null) out.write("$previous $tag\n".toByteArray(Charsets.UTF_8))
        out.write("$node $tag\n".toByteArray(Charsets.UTF_8))
        file.writeBytes(out.toByteArray())
    }

    companion object {
        private const val SHORT_ID = 12
        private val NULL_NODE = "0".repeat(40)

        /** 원본 `hg tag`가 거절하는 이름: 비어 있음, 예약 이름(`tip`, `null`, `.`), 숫자뿐인 이름, `:`나 줄바꿈 포함, 앞뒤 공백이다. */
        fun validName(tag: String): Boolean =
            tag.isNotEmpty() && tag !in RESERVED && !tag.all { it.isDigit() } && tag == tag.trim() && tag.none { it == ':' || it == '\n' || it == '\r' }

        private val RESERVED = setOf("tip", "null", ".")
    }
}
