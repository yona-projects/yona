package dev.yona.repository

import dev.yona.domain.project.Project
import org.springframework.stereotype.Service
import java.io.OutputStream

/**
 * 코드 브라우저가 보여 줄 폴더와 파일의 정보를 저장소에서 읽는다. v1.6 `RepositoryService.getMetaDataFromAncestorDirectories`
 * (`app/playRepository/RepositoryService.java`)와 각 저장소의 `getMetaDataFromPath`, `getRawFile`, `getArchive`에 해당한다.
 *
 * 저장소 종류별 읽기는 [CodeReader]가 맡고([VcsBackends]가 프로젝트의 종류로 고른다), 이 서비스는 지표와 상위 폴더 모으기를 맡는다.
 * 결과는 화면과 JSON 응답이 함께 쓰는 맵이다.
 */
@Service
class CodeBrowserService(
    private val backends: VcsBackends,
    private val metrics: dev.yona.observability.VcsMetrics
) {

    private fun reader(project: Project) = backends.code(project.vcs)

    /** 저장소에 읽을 내용이 하나도 없으면 `true`이다. Git은 브랜치, 태그, 원격 참조가 하나도 없을 때이다 (v1.6 `GitRepository.isEmpty`). */
    fun isEmpty(project: Project): Boolean = reader(project).isEmpty(project)

    /**
     * [path]의 폴더나 파일 정보이다. [branch]가 `null`이면 `HEAD`이다. 폴더는 `{"type": "folder", "data": {이름: {…}}}`, 파일은
     * `{"type": "file", …}`이다. 저장소가 비었거나 브랜치와 경로가 없으면, 또는 항목이 없는 폴더이면 `null`이다.
     */
    fun metaData(project: Project, branch: String?, path: String): Map<String, Any?>? =
        metrics.operation(project.vcs, "browse") { reader(project).metaData(project, branch, path) }

    /**
     * [path]까지의 상위 폴더를 차례로 모은 메타데이터이다. 맨 앞은 루트(`path` 빈 값), 마지막은 [path] 자신이다.
     * 비어 있는 중간 폴더(항목이 폴더 하나뿐)는 건너뛴다. 하나라도 읽지 못하면 `null`이다.
     */
    fun ancestors(project: Project, branch: String?, path: String): List<Map<String, Any?>>? {
        val chain = ArrayList<Map<String, Any?>>()
        chain += LinkedHashMap(metaData(project, branch, "") ?: return null).also { it["path"] = "" }
        val parts = path.split("/")
        val length = if (path.isEmpty()) 0 else parts.size
        var partial = ""
        for (i in 0 until length) {
            partial = if (partial.isEmpty()) parts[i] else partial + "/" + parts[i]
            // Subversion은 중간 폴더를 건너뛰지 않는다 (v1.6 `SVNRepository.isIntermediateFolder`).
            val intermediate = reader(project).isIntermediateFolder(project, partial)
            if (!intermediate) chain += LinkedHashMap(metaData(project, branch, partial) ?: return null).also { it["path"] = partial }
        }
        return chain
    }

    /** [revision]의 [path] 파일 내용이다. 폴더이거나 없으면 `null`이다. */
    fun rawFile(project: Project, revision: String, path: String): ByteArray? = metrics.operation(project.vcs, "raw_file") { reader(project).rawFile(project, revision, path) }

    /**
     * 파일이 큰 객체 한도(Git은 JGit의 `core.streamFileThreshold`, 50MB)를 넘으면 메모리에 올리지 않고 흘려보낼 수 있게 크기와 앞부분, 스트림 열기를 돌려준다.
     * 한도 이하이거나 파일이 없으면, 큰 파일을 따로 다루지 않는 종류(Subversion)이면 `null`이다(그때는 [rawFile]을 쓴다). v1.6은 `getBytes()`가
     * `LargeObjectException`을 던져 500이었다(R-115).
     */
    fun largeRawFile(project: Project, revision: String, path: String): LargeRawFile? = reader(project).largeRawFile(project, revision, path)

    /** [branch]의 내용을 zip으로 [out]에 쓴다. 브랜치를 찾지 못하면 아무것도 쓰지 않는다. Subversion은 최신 리비전의 내용을 쓴다(R-62-3). */
    fun archive(project: Project, branch: String, out: OutputStream) = metrics.operation(project.vcs, "archive") { reader(project).archive(project, branch, out) }
}
