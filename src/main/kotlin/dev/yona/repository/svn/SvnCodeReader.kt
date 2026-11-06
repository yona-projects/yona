package dev.yona.repository.svn

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.VcsType
import dev.yona.repository.CodeReader
import org.springframework.stereotype.Component
import java.io.OutputStream

/**
 * Subversion 저장소의 폴더와 파일 정보를 읽는 [CodeReader] 구현이다. 폴더와 파일 정보는 [SvnCodeBrowser]가 만들고, 원본 내용과 내려받기는
 * [SvnRepositories]가 읽는다. 중간 폴더를 건너뛰지 않고(v1.6 `SVNRepository.isIntermediateFolder`) 큰 파일을 따로 흘려보내지도 않는다.
 */
@Component
class SvnCodeReader(private val svn: SvnRepositories, private val browser: SvnCodeBrowser) : CodeReader {

    override val type: VcsType get() = VcsType.SUBVERSION

    override fun isEmpty(project: Project): Boolean = svn.isEmpty(project.owner.orEmpty(), project.name.orEmpty())

    override fun metaData(project: Project, revision: String?, path: String): Map<String, Any?>? = browser.metaData(project, revision, path)

    override fun rawFile(project: Project, revision: String, path: String): ByteArray? {
        val number = if (revision == "HEAD") revision else revision.toLongOrNull()?.toString() ?: return null
        return svn.rawFile(project.owner.orEmpty(), project.name.orEmpty(), number, path)
    }

    /** v1.6 `SVNRepository.getArchive`는 아무것도 쓰지 않아 빈 파일이 내려갔다. 최신 리비전의 내용을 zip으로 만든다(R-62-3). */
    override fun archive(project: Project, branch: String, out: OutputStream) = svn.exportZip(project.owner.orEmpty(), project.name.orEmpty(), out)
}
