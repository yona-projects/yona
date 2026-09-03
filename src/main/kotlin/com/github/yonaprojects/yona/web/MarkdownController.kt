package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.support.MarkdownService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody

data class MarkdownRenderRequest(
    val body: String,
    val breaks: Boolean = true
)

@Controller
class MarkdownController(
    private val projectRepository: ProjectRepository,
    private val markdownService: MarkdownService
) {

    @PostMapping("/markdown/{owner}/{projectName}", produces = ["text/html;charset=UTF-8"])
    @ResponseBody
    fun render(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestBody request: MarkdownRenderRequest
    ): ResponseEntity<String> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val html = markdownService.render(request.body, request.breaks, project)
        return ResponseEntity.ok(html)
    }
}

