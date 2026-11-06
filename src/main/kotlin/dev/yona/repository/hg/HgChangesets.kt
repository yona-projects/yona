package dev.yona.repository.hg

import io.github.search5.hg4j.api.CommitCommand
import io.github.search5.hg4j.storage.Revlog
import java.nio.charset.StandardCharsets

/**
 * Mercurial 변경 로그(changelog)의 체인지셋 하나이다. hg4j의 `LogCommand`는 매번 모든 리비전을 읽어 해석하므로, 커밋 하나나 한 쪽만 필요한 Yona는
 * 필요한 리비전만 읽는다.
 *
 * @property rev 이 저장소 안의 리비전 번호(복제본끼리 다르다. 화면과 주소에는 쓰지 않는다)
 * @property node 40자 16진수 커밋 번호
 * @property author 변경 로그에 적힌 사용자 문자열(보통 `이름 <이메일>`)
 * @property timestamp 커밋 시각(유닉스 초)
 * @property files 이 체인지셋이 바꾼 파일. 병합 체인지셋은 거의 비어 있다
 * @property message 커밋 메시지(끝의 줄바꿈 없이)
 * @property parents 부모의 리비전 번호(0개에서 2개, 없는 부모는 뺀다)
 * @property branch 이름 붙은 브랜치(없으면 `default`)
 */
class HgChangeset(
    val rev: Int, val node: String, val author: String, val timestamp: Long, val files: List<String>, val message: String, val parents: List<Int>, val branch: String
)

/** [Revlog]에서 체인지셋을 읽는 도우미이다. */
object HgChangesets {

    /** 리비전 [rev]의 체인지셋이다. 변경 로그의 모양이 어긋나면 `null`이다. */
    fun read(changelog: Revlog, rev: Int): HgChangeset? {
        val record = changelog.getIndexRecord(rev)
        val text = String(changelog.getRevisionContent(rev), StandardCharsets.UTF_8)
        // 머리(관리자 노드, 사용자, 날짜 줄, 파일 목록)와 메시지는 첫 빈 줄로 갈린다
        val split = text.indexOf("\n\n")
        val head = (if (split < 0) text else text.substring(0, split)).split("\n")
        if (head.size < 3) return null
        val message = if (split < 0) "" else text.substring(split + 2)
        val dateLine = head[2]
        // `시각 시간대 [여분]`: 여분은 `키:값`을 NUL로 이은 것이다
        val parts = dateLine.split(" ", limit = 3)
        val timestamp = parts[0].toLongOrNull() ?: 0L
        var branch = "default"
        if (parts.size == 3 && parts[2].isNotEmpty()) {
            for (item in parts[2].split('\u0000')) {
                val colon = CommitCommand.findUnescapedColon(item)
                if (colon != -1 && CommitCommand.decodeExtraKey(item.substring(0, colon)) == "branch") branch = CommitCommand.decodeExtraKey(item.substring(colon + 1))
            }
        }
        val parents = listOf(record.parent1, record.parent2).filter { it >= 0 }
        val node = io.github.search5.hg4j.lib.NodeId(record.nodeId.copyOf(20)).toHex()
        return HgChangeset(rev, node, head[1], timestamp, head.drop(3).filter { it.isNotEmpty() }, message, parents, branch)
    }

    /** [node](40자)의 리비전 번호이다. 이 저장소에 없으면 `-1`이다. */
    fun revOf(changelog: Revlog, node: String): Int = changelog.findRevision(io.github.search5.hg4j.util.NodeIdUtil.fromHex(node))

    /** [rev]와 그 조상의 리비전 번호이다(자신 포함). */
    fun ancestors(changelog: Revlog, rev: Int): Set<Int> = io.github.search5.hg4j.revwalk.ChangesetGraph(changelog).getAllAncestors(rev)
}
