package dev.yona.repository

import dev.yona.domain.project.Project
import org.springframework.stereotype.Service

/**
 * 프로젝트 저장소의 README를 읽는다. 종류별 읽기는 [TextReader]가 맡는다([VcsBackends]가 프로젝트의 종류로 고른다). v1.6은 저장소를 열지
 * 못하거나 파일을 읽지 못하면 모두 README가 없는 것으로 보므로 읽다가 생긴 오류는 `null`로 바꾼다.
 */
@Service
class ProjectReadmeService(private val backends: VcsBackends) {

    private fun <T> quietly(block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        // v1.6은 저장소를 열지 못하거나 파일을 읽지 못하면 모두 README가 없는 것으로 본다.
        null
    }

    /** README 파일 이름이다. 없으면 `null`이다. Subversion은 `trunk` 아래도 찾는다. */
    fun fileName(project: Project): String? = quietly { backends.text(project.vcs).readmeFileName(project) }

    /** README 내용이다. 파일이 없거나 읽을 수 없으면 `null`이다. */
    fun read(project: Project): String? = quietly { backends.text(project.vcs).readme(project) }

    /**
     * 게시글 화면이 README 글의 본문으로 보여 주는 README이다 (v1.6 `BareRepository.readREADME`). `HEAD` 최상위에서 트리 순서로
     * 처음 만나는 `README.md`, `readme.md`, `README.markdown`, `readme.markdown`을 읽는다. 없거나 읽을 수 없으면, 지원하지 않는 종류이면 `null`이다.
     * 인코딩은 설정한 문자 집합(UTF-8)으로 읽는다.
     */
    fun readFirstFound(project: Project): String? = quietly { backends.text(project.vcs).readmeForPosting(project) }
}
