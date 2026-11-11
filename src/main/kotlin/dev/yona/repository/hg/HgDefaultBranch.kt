package dev.yona.repository.hg

import io.github.search5.hg4j.api.Hg
import java.io.File

/**
 * Mercurial 저장소의 기본 브랜치를 기억한다(UI 계약 재조사 code-F7, 선생님 결정 2026-11-11). Git의 `HEAD`가 브랜치 이름을 가리키는 포인터인 것과 같게,
 * 기본으로 정한 **북마크 이름**을 저장소 안 `.hg/yona-default-branch` 파일에 적는다. v1.6에서 옮긴 "기본으로 설정" 단추는 요청이 성공하는데 아무 일도 하지 않았다.
 *
 * Mercurial의 `@` 북마크는 리비전에 고정된 이름표라 그 북마크에 새 커밋이 쌓여도 따라가지 않는다. 그래서 `@`를 옮기는 방식이 아니라 이름을 기억한다.
 * 이 파일은 저장소 사본이 아니라 서버가 가진 저장소에만 있다(`hg clone`이 가져가지 않는다). `hg clone`이 체크아웃하는 리비전은 Mercurial의 규칙을 따른다.
 *
 * 판정 순서는 정한 북마크(있고 지금도 존재할 때), `main`, `master`, `@`이다([HgRepositories.DEFAULT_BOOKMARKS]). 정한 북마크가 지워지면 다음 순서로 돌아간다.
 */
internal object HgDefaultBranch {

    private const val FILE = "yona-default-branch"

    private fun file(hg: Hg) = File(hg.repository.hgDir, FILE)

    /** 정해 둔 기본 북마크 이름이다. 정한 적이 없으면 `null`이다(그 북마크가 지금 있는지는 보지 않는다). */
    fun read(hg: Hg): String? = file(hg).takeIf { it.isFile }?.readText(Charsets.UTF_8)?.trim()?.ifEmpty { null }

    /** [bookmark]를 기본 북마크로 정해 적는다. */
    fun write(hg: Hg, bookmark: String) {
        file(hg).writeText(bookmark + "\n", Charsets.UTF_8)
    }

    /** 기본으로 삼을 이름표 후보이다. 정해 둔 것이 앞에 오고 그다음이 [HgRepositories.DEFAULT_BOOKMARKS]이다. */
    fun candidates(hg: Hg): List<String> = listOfNotNull(read(hg)) + HgRepositories.DEFAULT_BOOKMARKS

    /** [bookmarks] 가운데 기본 이름표로 쓸 것의 이름이다. 없으면 `null`이다. */
    fun resolve(hg: Hg, bookmarks: Map<String, String>): String? = candidates(hg).firstOrNull { it in bookmarks }
}
