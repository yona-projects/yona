package dev.yona.markdown

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.repository.RepositoryService
import org.springframework.stereotype.Service

/**
 * 프로젝트 문맥에서 마크다운을 렌더링한다. v1.6 `utils.Markdown`의 `render(source, project)`,
 * `renderFileInReadme`, `renderFileInCodeBrowser`에 해당한다.
 *
 * 프로젝트 문맥이란 README의 상대 경로 변환, 이슈 주소 링크 변환, `#이슈`와 `@사용자` 같은 참조의 자동 링크처럼
 * 어느 프로젝트의 글인지 알아야 하는 후처리를 뜻한다. 프로젝트와 무관한 변환은 [MarkdownRenderer]가 맡는다.
 *
 * 처리 순서는 v1.6과 같다. 변환 -> `javascript:` 링크 제거 -> `noreferrer` -> 이슈 주소 링크 변환 -> 정화(여기까지 캐시)
 * -> 자동 링크(캐시 바깥, 매번 수행).
 */
@Service
class ProjectMarkdownRenderer(
    private val renderer: MarkdownRenderer,
    private val links: LinkPostProcessor,
    private val issueLinks: IssueLinkTransformer,
    private val autoLinks: AutoLinkRenderer,
    private val repositories: RepositoryService,
    private val properties: YonaProperties,
    private val currentUserProvider: dev.yona.security.CurrentUserProvider
) {

    /**
     * 프로젝트 안의 글(게시글, 코멘트, 메일 등)을 렌더링한다.
     *
     * @param lang 자동 링크의 사용자 이름을 고를 언어. 없으면 현재 사용자의 언어를 쓴다
     */
    fun render(source: String, project: Project, lang: String? = null): String = autoLinks.render(highlight(source, project), project, lang)

    /** 프로젝트 홈에 보여 줄 README를 렌더링한다. 상대 경로 이미지와 링크를 저장소 주소로 바꾼다. */
    fun renderFileInReadme(source: String, project: Project): String =
        autoLinks.render(highlight(rewriter(project).forReadme(source), project), project, null)

    /** 코드 브라우저에서 `.md` 파일을 렌더링한다. 상대 경로 이미지를 저장소 주소로 바꾼다. */
    fun renderFileInCodeBrowser(source: String, project: Project): String =
        autoLinks.render(highlight(rewriter(project).forCodeBrowser(source), project), project, null)

    /** 이슈 링크 변환은 보는 사람의 읽기 권한에 따라 달라지므로 프로젝트와 보는 사람을 보관 키에 넣는다(R-16). */
    private fun highlight(source: String, project: Project): String =
        renderer.renderWithPipeline(source, "p${project.id}:u${currentUserProvider.get().user?.id ?: 0}") { html ->
        issueLinks.transform(links.checkReferrer(links.removeJavascriptInHref(html)))
    }

    private fun rewriter(project: Project) = RelativeLinkRewriter(
        owner = project.owner.orEmpty(),
        name = project.name.orEmpty(),
        defaultBranch = repositories.defaultBranch(project),
        contextPath = properties.applicationContext
    )
}
