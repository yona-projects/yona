package dev.yona.repository.hg

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.VcsType
import dev.yona.io.CharsetDetection
import dev.yona.repository.TextReader
import org.springframework.stereotype.Component

/**
 * Mercurial 저장소의 파일을 기본 위치(이름표 `main`, `master`, `@`, 없으면 `default` 브랜치의 끝) 기준으로 텍스트로 읽는 [TextReader] 구현이다.
 * README는 최상위의 `README.md`, `readme.md` 순이고, 게시글용 README는 최상위에서 정렬 순서로 처음 만나는 네 가지 이름 중 하나이다(Git과 같다).
 */
@Component
class HgTextReader(private val hg: HgRepositories) : TextReader {

    override val type: VcsType get() = VcsType.MERCURIAL

    private fun open(project: Project) = hg.open(requireNotNull(project.owner), requireNotNull(project.name))

    override fun readText(project: Project, path: String): String? = open(project).use { h ->
        val node = HgRevisions.head(h) ?: return null
        if (h.manifest().setRevision(node).call().none { it.path == path }) return null
        decode(h.cat().setFile(path).setRevision(node).call())
    }

    override fun readmeFileName(project: Project): String? = open(project).use { h ->
        val node = HgRevisions.head(h) ?: return null
        val paths = h.manifest().setRevision(node).call().map { it.path }.toSet()
        CANDIDATES.firstOrNull { it in paths }
    }

    override fun readme(project: Project): String? = open(project).use { h ->
        val node = HgRevisions.head(h) ?: return null
        val paths = h.manifest().setRevision(node).call().map { it.path }.toSet()
        val name = CANDIDATES.firstOrNull { it in paths } ?: return null
        decode(h.cat().setFile(name).setRevision(node).call())
    }

    /** 게시글 화면이 README 글의 본문으로 보여 주는 README이다. 인코딩은 UTF-8로 읽는다. */
    override fun readmeForPosting(project: Project): String? = open(project).use { h ->
        val node = HgRevisions.head(h) ?: return null
        val top = h.manifest().setRevision(node).call().map { it.path }.filter { !it.contains('/') }.sorted()
        val name = top.firstOrNull { it in ANY_README } ?: return null
        String(h.cat().setFile(name).setRevision(node).call(), Charsets.UTF_8)
    }

    private fun decode(bytes: ByteArray): String = String(bytes, java.nio.charset.Charset.forName(CharsetDetection.detect(bytes)))

    private companion object {
        val CANDIDATES = listOf("README.md", "readme.md")
        val ANY_README = setOf("README.md", "readme.md", "README.markdown", "readme.markdown")
    }
}
