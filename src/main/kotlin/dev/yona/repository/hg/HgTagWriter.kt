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
 * 2. hg4j `TagCommand`(커밋은 하지 않는다)로 `.hgtags`를 쓰고, 지정한 작성자와 메시지로 `CommitCommand`가 커밋한다. 쓰는 형식과 이름 검사는 hg4j가 원본 `hg tag`와
 *    같게 맡는다.
 * 3. 새 체인지셋을 서버 저장소로 가져오고, 기본 위치가 이름표였다면 그 이름표를 새 체인지셋으로 옮긴다(원본에서 활성 이름표 위에 커밋하면 이름표가 따라오는 것과 같다).
 *
 * 커밋을 `TagCommand`에 맡기지 않는 것은 그 커밋의 작성자가 `hg4j <hg4j@google.com>`로 고정되어 있어서이다(작성자는 태그를 다는 사용자여야 한다).
 */
@Component
class HgTagWriter(private val repositories: HgRepositories) {

    /**
     * 태그 [tag]를 [target]에 단다. [target]은 이름표, 태그, 커밋 번호(앞부분만 줘도 된다)이고 없으면 기본 위치의 끝이다. [message]가 있으면 태그 체인지셋의 설명으로 쓴다
     * (Mercurial에는 주석 태그가 없다). 성공하면 `null`, 아니면 오류이다.
     */
    fun create(owner: String, name: String, tag: String, target: String?, message: String?, author: TagAuthor): TagCreateError? {
        // 빈 이름은 hg4j `TagCommand`에서 태그 목록 모드이고, 앞뒤 공백은 hg4j가 지운 이름으로 만들므로(원본 `hg tag`와 같다) 요청한 이름과 달라진다. 둘 다 여기서 거절한다
        if (tag.isBlank() || tag != tag.trim()) return TagCreateError.INVALID_NAME
        val (base, node) = repositories.open(owner, name).use { hg ->
            val base = HgRevisions.head(hg) ?: return TagCreateError.EMPTY_REPOSITORY
            if (tag in HgRevisions.tags(hg)) return TagCreateError.ALREADY_EXISTS
            val node = (if (target == null) base else resolveCommit(hg, target)) ?: return TagCreateError.NO_TARGET
            base to node
        }
        return try {
            commitTag(owner, name, base, tag, node, message ?: "Added tag $tag for changeset ${node.take(SHORT_ID)}", author)
            null
        } catch (e: io.github.search5.hg4j.errors.HgValidationException) {
            // 이미 있는 이름은 위에서 걸렀고, 갓 만든 사본은 기본 위치의 머리라서 hg4j가 거절할 수 있는 것은 이름의 형식(원본 `hg tag`의 규칙)뿐이다
            TagCreateError.INVALID_NAME
        }
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
                // `.hgtags`를 쓰고 추적 대상에 올리는 것까지 hg4j가 한다(`setCommit(false)`라 커밋은 하지 않는다)
                io.github.search5.hg4j.api.TagCommand(w.repository).setTagName(tag).setCommit(false)
                    .let { if (node == null) it.setRemove(true) else it.setNodeId(io.github.search5.hg4j.util.NodeIdUtil.fromHex(node)) }.call()
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

    private companion object {
        const val SHORT_ID = 12
    }
}
