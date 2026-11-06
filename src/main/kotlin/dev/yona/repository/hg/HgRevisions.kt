package dev.yona.repository.hg

import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.lib.HgRepository
import io.github.search5.hg4j.lib.NodeId

/**
 * Mercurial 저장소에서 리비전 문자열을 커밋 번호(40자 16진수 노드)로 풀고 이름표와 태그를 읽는다. hg4j의 명령은 `tip`, 숫자, 16진수 접두어만
 * 풀어 주므로(이름표와 브랜치 이름은 풀지 못한다) Yona의 코드 화면이 쓰는 이름을 여기서 푼다.
 *
 * 풀이 순서는 다음과 같다: 비어 있거나 `HEAD`이면 기본 위치, `refs/heads/<이름표>`, `refs/tags/<태그>`, 이름표, 태그, 이름 붙은 브랜치(`default` 등),
 * 40자 노드, `tip`, 숫자(로컬 리비전 번호), 16진수 접두어(하나로 정해질 때만). 이름표와 태그가 번호나 접두어보다 먼저인 것은 Mercurial과 같다.
 */
object HgRevisions {

    /** 이름표와 그 노드(40자)이다. */
    fun bookmarks(hg: Hg): Map<String, String> = hg.bookmark().list()

    /** 태그와 그 노드(40자)이다. 가상 태그 `tip`은 뺀다. */
    fun tags(hg: Hg): Map<String, String> = hg.tags().call().filter { it.name != "tip" }.associate { it.name to NodeId(it.node).toHex() }

    /**
     * 기본 위치의 노드이다. `main`, `master`, `@` 이름표 가운데 먼저 있는 것이고, 없으면 `default` 브랜치의 끝, 그것도 없으면 저장소 전체의 끝이다.
     * 커밋이 하나도 없으면 `null`이다.
     */
    fun head(hg: Hg): String? {
        val bookmarks = bookmarks(hg)
        HgRepositories.DEFAULT_BOOKMARKS.firstOrNull { it in bookmarks }?.let { return bookmarks.getValue(it) }
        hg.branches().call().firstOrNull { it.branch == "default" }?.let { return NodeId(it.node).toHex() }
        return tip(hg)
    }

    /** 저장소 전체의 끝 노드이다. 비어 있으면 `null`이다. */
    fun tip(hg: Hg): String? = hg.tip().call()?.takeIf { it.isNotEmpty() && !it.all { b -> b == 0.toByte() } }?.let { NodeId(it).toHex() }

    /**
     * [spec]이 가리키는 커밋의 노드(40자 16진수)이다. 풀 수 없으면 `null`이다.
     *
     * @throws java.io.IOException 16진수 접두어가 둘 이상의 커밋에 맞을 때
     */
    fun resolve(hg: Hg, spec: String?): String? {
        if (spec.isNullOrEmpty() || spec == "HEAD") return head(hg)
        if (spec.startsWith(HgRepositories.HEADS)) return bookmarks(hg)[spec.removePrefix(HgRepositories.HEADS)]
        if (spec.startsWith(HgRepositories.TAGS)) return tags(hg)[spec.removePrefix(HgRepositories.TAGS)]
        bookmarks(hg)[spec]?.let { return it }
        tags(hg)[spec]?.let { return it }
        hg.branches().call().firstOrNull { it.branch == spec }?.let { return NodeId(it.node).toHex() }
        if (spec.equals("tip", ignoreCase = true)) return tip(hg)
        val changelog = changelog(hg)
        val node = io.github.search5.hg4j.util.NodeIdUtil.resolveRevision(changelog, spec) ?: return null
        return NodeId(node.copyOf(20)).toHex()
    }

    /** 변경 로그(changelog)이다. 커밋의 부모와 번호를 읽을 때 쓴다. */
    fun changelog(hg: Hg): io.github.search5.hg4j.storage.Revlog = changelog(hg.repository)

    /** 푸시 훅이 넘겨 주는 저장소([HgRepository])의 변경 로그이다. */
    fun changelog(repository: HgRepository): io.github.search5.hg4j.storage.Revlog {
        val store = repository.storeDir
        return repository.getRevlog(java.io.File(store, "00changelog.i"), java.io.File(store, "00changelog.d"))
    }
}
