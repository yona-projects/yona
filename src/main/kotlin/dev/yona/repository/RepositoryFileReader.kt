package dev.yona.repository

import dev.yona.domain.project.Project
import org.springframework.stereotype.Component

/**
 * 저장소의 `HEAD`에 있는 파일을 문자열로 읽는다. 이슈 템플릿(`ISSUE_TEMPLATE.md`)처럼 저장소 파일을 한 번 읽어 쓰는 곳에 쓴다.
 * v1.6 `PlayRepository.getRawFile("HEAD", path)`와 `FileUtil.detectCharset`에 해당한다. 종류별 읽기는 [TextReader]가 맡는다
 * ([VcsBackends]가 프로젝트의 종류로 고른다).
 */
@Component
class RepositoryFileReader(private val backends: VcsBackends) {

    /**
     * [path] 파일의 내용을 읽는다. 저장소를 열 수 없거나 파일이 없거나 디렉터리이면 `null`이다.
     * v1.6처럼 읽는 중 생긴 모든 오류는 파일이 없는 것으로 본다.
     */
    fun readText(project: Project, path: String): String? = try {
        backends.text(project.vcs).readText(project, path)
    } catch (e: Exception) {
        null
    }
}
