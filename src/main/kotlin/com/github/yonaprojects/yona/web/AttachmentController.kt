package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.Principal
import java.text.Normalizer
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository

@RestController
class AttachmentController(
    private val attachmentService: AttachmentService,
    private val attachmentRepository: AttachmentRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val milestoneRepository: MilestoneRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val accessControl: AccessControl
) {

    private fun findUploader(authorEmail: String?, authorLoginId: String?, principal: Principal?): User {
        if (!authorEmail.isNullOrBlank()) {
            val u = userRepository.findByEmail(authorEmail)
            if (u.isPresent && u.get().loginId != "anonymous") {
                return u.get()
            }
        }
        if (!authorLoginId.isNullOrBlank()) {
            val u = userRepository.findByLoginId(authorLoginId)
            if (u.isPresent && u.get().loginId != "anonymous") {
                return u.get()
            }
        }
        if (principal != null) {
            val u = userRepository.findByLoginId(principal.name)
            if (u.isPresent) return u.get()
        }
        return User(loginId = "anonymous", name = "Anonymous")
    }

    @PostMapping("/files")
    fun uploadFile(
        @RequestParam("filePath") file: MultipartFile,
        @RequestParam(value = "authorEmail", required = false) authorEmail: String?,
        @RequestParam(value = "authorLoginId", required = false) authorLoginId: String?,
        principal: Principal?
    ): ResponseEntity<Map<String, String>> {
        val uploader = findUploader(authorEmail, authorLoginId, principal)
        if (uploader.loginId == "anonymous") {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // NFC 파일명 정규화
        val normalizedFilename = Normalizer.normalize(file.originalFilename ?: "unknown", Normalizer.Form.NFC)

        // yona AttachmentApp.java:84-85 attach.store(...)의 반환값(isCreated) 대응 (P2-24) —
        // AttachmentService.store()가 dedup 여부를 직접 반환하므로 사후에 existsByHash로 추정할
        // 필요가 없다.
        val (attach, isNew) = attachmentService.store(
            inputStream = file.inputStream,
            name = normalizedFilename,
            containerType = ResourceType.NOT_A_RESOURCE,
            containerId = "",
            ownerLoginId = uploader.loginId ?: "anonymous"
        )

        val fileUrl = "/files/${attach.id}"
        val body = mapOf(
            "id" to attach.id.toString(),
            "mimeType" to (attach.mimeType ?: ""),
            "name" to attach.name,
            "url" to fileUrl,
            "size" to (attach.size?.toString() ?: "0")
        )

        val responseStatus = if (isNew) HttpStatus.CREATED else HttpStatus.OK

        return ResponseEntity.status(responseStatus)
            .header(HttpHeaders.LOCATION, fileUrl)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(body)
    }

    @GetMapping("/files/{id}")
    fun getFile(
        @PathVariable("id") id: Long,
        @RequestParam(value = "action", required = false) action: String?,
        request: HttpServletRequest,
        principal: Principal?
    ): ResponseEntity<Resource> {
        val attachment = attachmentRepository.findById(id).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        // yona AccessControl.java:255-259 ATTACHMENT READ(컨테이너의 READ 권한으로 위임) 대응 (P1-96,
        // 보안). 다운로드/인라인 조회 둘 다 이 게이트를 거친다 — 이전에는 권한 체크 자체가 없었다.
        val loginUser = principal?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowedAttachment(loginUser, attachment, Operation.READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val dispositionType = if (action == "download") "attachment" else "inline"
        val eTag = "\"${attachment.hash}-$dispositionType\""

        val ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH)
        if (ifNoneMatch != null && ifNoneMatch == eTag) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .header(HttpHeaders.ETAG, eTag)
                .build()
        }

        val file = attachmentService.getFile(attachment)
        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        val resource = FileSystemResource(file)
        
        val contentDisposition = ContentDisposition.builder(dispositionType)
            .filename(attachment.name, StandardCharsets.UTF_8)
            .build()
            .toString()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, attachment.mimeType ?: "application/octet-stream")
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
            .header(HttpHeaders.ETAG, eTag)
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
            .body(resource)
    }

    @PostMapping("/files/{id}")
    fun deleteFile(
        @PathVariable("id") id: Long,
        @RequestParam(value = "_method", required = false) method: String?,
        principal: Principal?
    ): ResponseEntity<String> {
        if (method?.lowercase() != "delete") {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("_method must be 'delete'.")
        }

        val loginUser = principal?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val attachment = attachmentRepository.findById(id).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        // 권한 체크: 컨테이너 리소스의 수정 권한이 있는지 혹은 업로드 사용자 본인인지 체크
        val isAllowed = when (attachment.containerType) {
            ResourceType.USER, ResourceType.NOT_A_RESOURCE -> {
                attachment.ownerLoginId == loginUser.loginId || loginUser.isSiteManager
            }
            ResourceType.ISSUE_POST -> {
                attachment.containerId.toLongOrNull()?.let { issueId ->
                    val issue = issueRepository.findById(issueId).orElse(null)
                    issue?.let {
                        accessControl.isAllowedToUpdateIssue(loginUser, it.project, it.authorLoginId)
                    }
                } ?: false
            }
            ResourceType.BOARD_POST -> {
                attachment.containerId.toLongOrNull()?.let { postingId ->
                    val posting = postingRepository.findById(postingId).orElse(null)
                    posting?.let {
                        accessControl.isAllowedToUpdatePosting(loginUser, it.project, it.authorLoginId)
                    }
                } ?: false
            }
            ResourceType.MILESTONE -> {
                attachment.containerId.toLongOrNull()?.let { milestoneId ->
                    val milestone = milestoneRepository.findById(milestoneId).orElse(null)
                    milestone?.let {
                        accessControl.isAllowedToUpdateMilestone(loginUser, it.project)
                    }
                } ?: false
            }
            // yona AccessControl.java:250-263 isProjectResourceAllowed()의 ATTACHMENT 케이스
            // (컨테이너의 UPDATE 권한으로 위임) 대응 (P1-130). 업로더 본인 전용으로 과잉 제한하던
            // catch-all에서 COMMIT_COMMENT/REVIEW_COMMENT를 분리 — AccessControl.isAllowedAttachment()가
            // 이미 이 두 타입을 정확히 컨테이너(커밋/리뷰 댓글)의 UPDATE 권한(프로젝트 멤버 누구나)으로
            // 위임하도록 구현돼 있어(getFile()의 READ 체크가 이미 재사용 중) 그대로 재사용한다.
            ResourceType.COMMIT_COMMENT, ResourceType.REVIEW_COMMENT -> {
                accessControl.isAllowedAttachment(loginUser, attachment, Operation.UPDATE)
            }
            else -> {
                attachment.ownerLoginId == loginUser.loginId || loginUser.isSiteManager
            }
        }

        if (!isAllowed) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        attachmentService.delete(attachment)

        val remainingExists = attachmentRepository.existsByHash(attachment.hash)
        val msg = if (remainingExists) {
            "The attachment is removed successfully, but its origin file still exists."
        } else {
            "Both the attachment and its origin file are removed successfully."
        }

        return ResponseEntity.ok(msg)
    }

    @GetMapping("/files")
    fun getFileList(
        @RequestParam(value = "containerType", required = false) containerType: String?,
        @RequestParam(value = "containerId", required = false) containerId: String?,
        principal: Principal?
    ): ResponseEntity<Map<String, Any>> {
        if (containerType.isNullOrBlank() || containerId.isNullOrBlank()) {
            return ResponseEntity.ok(mapOf("attachments" to emptyList<Any>()))
        }

        val resType = try {
            ResourceType.valueOf(containerType)
        } catch (e: Exception) {
            ResourceType.NOT_A_RESOURCE
        }

        // yona AccessControl.java:255-259 ATTACHMENT READ 대응 (P1-96, 보안). 목록 조회는 특정 첨부가
        // 아니라 컨테이너 단위 질의이므로, containerType/containerId만으로 동일한 컨테이너 권한 위임
        // 로직(isAllowedAttachment)을 재사용한다(id/name/hash 등은 이 판정에 쓰이지 않아 더미로 둬도 안전).
        val loginUser = principal?.let { userRepository.findByLoginId(it.name).orElse(null) }
        val containerProbe = Attachment(containerType = resType, containerId = containerId)
        if (!accessControl.isAllowedAttachment(loginUser, containerProbe, Operation.READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(resType, containerId)
        val list = attachments.map { attach ->
            mapOf(
                "id" to (attach.id?.toString() ?: ""),
                "mimeType" to (attach.mimeType ?: ""),
                "name" to attach.name,
                "url" to "/files/${attach.id}",
                "size" to (attach.size?.toString() ?: "0")
            )
        }

        return ResponseEntity.ok(mapOf("attachments" to list))
    }
}
