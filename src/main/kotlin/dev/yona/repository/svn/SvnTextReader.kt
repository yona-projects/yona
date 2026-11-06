package dev.yona.repository.svn

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.VcsType
import dev.yona.io.CharsetDetection
import dev.yona.repository.TextReader
import org.springframework.stereotype.Component

/**
 * Subversion 저장소의 파일을 최신(`HEAD`) 리비전 기준으로 텍스트로 읽는 [TextReader] 구현이다. README는 `README.md`, `readme.md`,
 * `/trunk/README.md`, `/trunk/readme.md` 순이다(v1.6 `getReadmeFileName`). 게시글용 README는 Subversion에 없다.
 */
@Component
class SvnTextReader(private val svn: SvnRepositories) : TextReader {

    override val type: VcsType get() = VcsType.SUBVERSION

    override fun readText(project: Project, path: String): String? =
        svn.rawFile(project.owner.orEmpty(), project.name.orEmpty(), "HEAD", path)?.let { String(it, java.nio.charset.Charset.forName(CharsetDetection.detect(it))) }

    override fun readmeFileName(project: Project): String? {
        val owner = project.owner.orEmpty()
        val name = project.name.orEmpty()
        return (CANDIDATES + CANDIDATES.map { "/trunk/$it" }).firstOrNull { svn.isFile(owner, name, it) }
    }

    override fun readme(project: Project): String? {
        val fileName = readmeFileName(project) ?: return null
        val bytes = svn.rawFile(project.owner.orEmpty(), project.name.orEmpty(), "HEAD", fileName) ?: return null
        return String(bytes, java.nio.charset.Charset.forName(CharsetDetection.detect(bytes)))
    }

    private companion object {
        val CANDIDATES = listOf("README.md", "readme.md")
    }
}
