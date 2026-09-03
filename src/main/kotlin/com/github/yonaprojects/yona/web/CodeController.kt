package com.github.yonaprojects.yona.web

import tools.jackson.databind.node.ObjectNode
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/vcs/{owner}/{projectName}")
class CodeController(
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService
) {

    @GetMapping("/meta")
    fun getMetaData(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "HEAD") branch: String,
        @RequestParam(required = false, defaultValue = "") path: String
    ): ResponseEntity<ObjectNode> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val repository = repositoryService.getRepository(project)
        val metaData = repository.getMetaDataFromPath(branch, path)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return ResponseEntity.ok(metaData)
    }

    @GetMapping("/meta/ancestors")
    fun getMetaDataFromAncestors(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "HEAD") branch: String,
        @RequestParam(required = false, defaultValue = "") path: String
    ): ResponseEntity<List<ObjectNode>> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val repository = repositoryService.getRepository(project)
        val recursiveData = repositoryService.getMetaDataFromAncestorDirectories(repository, branch, path)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        return ResponseEntity.ok(recursiveData)
    }

    @GetMapping("/raw")
    fun showRawFile(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam revision: String,
        @RequestParam path: String
    ): ResponseEntity<ByteArray> {
        val rawData = repositoryService.getFileAsRaw(owner, projectName, revision, path)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_OCTET_STREAM
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${path.substringAfterLast('/')}\"")

        return ResponseEntity(rawData, headers, HttpStatus.OK)
    }
}
