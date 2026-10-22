package dev.yona.web

import dev.yona.domain.project.ProjectService
import dev.yona.markdown.MarkdownRenderer
import dev.yona.markdown.ProjectMarkdownRenderer
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import tools.jackson.databind.JsonNode

/**
 * 편집기의 미리보기가 쓰는 마크다운 변환 요청이다. v1.6 `MarkdownApp.render`에 해당한다.
 *
 * 요청 본문은 `{"body": "마크다운", "breaks": true}` 형태의 JSON이고, 응답은 변환한 HTML 조각이다.
 * `breaks`는 v1.6에서도 변환에 쓰이지 않고 줄바꿈은 항상 `<br>`로 바뀐다.
 */
@Controller
class MarkdownController(
    private val projectService: ProjectService,
    private val renderer: MarkdownRenderer,
    private val projectRenderer: ProjectMarkdownRenderer
) {

    @PostMapping("/markdown/{user}/{project}")
    fun render(
        @PathVariable("user") ownerName: String,
        @PathVariable("project") projectName: String,
        @RequestBody request: JsonNode
    ): ResponseEntity<String> {
        val body = request.path("body").takeIf { it.isString }?.stringValue().orEmpty()
        val project = projectService.findByOwnerAndProjectName(ownerName, projectName)
        val html = if (project != null) projectRenderer.render(body, project) else renderer.render(body)
        return ResponseEntity.ok().contentType(MediaType("text", "html", Charsets.UTF_8)).body(html)
    }
}
