package dev.yona.markdown

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.repository.RepositoryService
import org.springframework.stereotype.Service

/**
 * 프로젝트 문맥에서 마크다운을 렌더링한다. v1.6 `utils.Markdown`의 `render(source, project)`,
 * `renderFileInReadme`, `renderFileInCodeBrowser`에 해당한다.
 *
 * 프로젝트 문맥이란 README의 상대 경로 변환처럼 어느 프로젝트의 글인지 알아야 하는 후처리를 뜻한다.
 * 프로젝트와 무관한 변환은 [MarkdownRenderer]가 맡는다.
 */
@Service
class ProjectMarkdownRenderer(
    private val renderer: MarkdownRenderer,
    private val repositories: RepositoryService,
    private val properties: YonaProperties
) {

    /** 프로젝트 안의 글(게시글, 코멘트 등)을 렌더링한다. */
    fun render(source: String, project: Project): String = renderer.render(source)

    /** 프로젝트 홈에 보여 줄 README를 렌더링한다. 상대 경로 이미지와 링크를 저장소 주소로 바꾼다. */
    fun renderFileInReadme(source: String, project: Project): String =
        renderer.render(rewriter(project).forReadme(source))

    /** 코드 브라우저에서 `.md` 파일을 렌더링한다. 상대 경로 이미지를 저장소 주소로 바꾼다. */
    fun renderFileInCodeBrowser(source: String, project: Project): String =
        renderer.render(rewriter(project).forCodeBrowser(source))

    private fun rewriter(project: Project) = RelativeLinkRewriter(
        owner = project.owner.orEmpty(),
        name = project.name.orEmpty(),
        defaultBranch = repositories.defaultBranch(project),
        contextPath = properties.applicationContext
    )
}
