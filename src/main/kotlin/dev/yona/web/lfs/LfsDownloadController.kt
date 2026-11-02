package dev.yona.web.lfs

import dev.yona.domain.lfs.LfsService
import dev.yona.domain.lfs.LfsStorage
import dev.yona.domain.project.ProjectService
import dev.yona.web.ContentDispositions
import dev.yona.web.access.CodeAccessCheck
import dev.yona.web.error.ErrorViews
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

/**
 * 코드 보기의 LFS 포인터 안내에서 객체를 내려받는 주소이다(추가 개발 항목 X-8, `decisions.md` D-79 10번). 브라우저의 웹 세션으로 쓰고, 코드를 읽을 수 있는 사용자만 받는다
 * (`rawcode`와 같은 검사). git-lfs 클라이언트가 쓰는 `/info/lfs` 경로와 달리 사람이 누르는 링크이다. 포크는 원본의 객체를 읽기로 빌려 쓴다.
 */
@Controller
class LfsDownloadController(private val projectService: ProjectService, private val lfs: LfsService, private val storage: LfsStorage) {

    @GetMapping("/{user}/{project}/lfs/{oid}")
    @CodeAccessCheck
    @Transactional(readOnly = true)
    fun download(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable oid: String, @RequestParam(name = "name", required = false) fileName: String?): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name)?.takeIf { lfs.available(it) } ?: return ErrorViews.notFound()
        if (!LfsStorage.validOid(oid)) return ErrorViews.notFound()
        val holder = lfs.locate(project, oid) ?: return ErrorViews.notFound()
        val file = storage.objectFile(holder, oid)
        val download = (fileName?.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains('\\') } ?: oid)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(file.length())
            .header(HttpHeaders.CONTENT_DISPOSITION, if (download.all { it.code in 0x20..0x7E && it != '"' }) "attachment; filename=\"$download\"" else "attachment; " + ContentDispositions.encodeFilename(download))
            .body(FileSystemResource(file))
    }
}
