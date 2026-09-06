package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.GitTag
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.lib.Repository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// yona-wiki P3-10 — `yona tag list/create/delete` CLI 및 REST 클라이언트용 신규 JSON API
// (`/api/v1/projects/{owner}/{project}/tags`). Issue/PR REST API와 달리 위임할 만한 기존
// 세션/폼 컨트롤러가 없어(완전 신규 기능) BranchApiController/TagViewController와 동일한
// AccessControl 판정을 이 컨트롤러가 직접 수행한다(ProjectRestApiController.create()/fork()가
// 직접 로직을 갖는 것과 같은 패턴).
//
// 권한: 목록 조회는 Operation.READ, 생성/삭제는 각각 Operation.UPDATE/DELETE — 브랜치 관리
// (BranchApiController.setAsDefault()/deleteBranch())와 정확히 같은 문턱이다. PROJECT 리소스는
// AccessControl.isAllowed()에서 UPDATE/DELETE가 둘 다 "매니저 또는 조직관리자 전용"으로 같은
// 문턱이라(계획 문서 "권한" 절 참고) 실질적으로 생성/삭제 모두 매니저 전용이다.
//
// ApiTokenAuthenticationFilter의 resourceSegmentToResourceType에 "tags" -> ResourceType.CODE
// (CODE 그룹) 매핑을 추가해뒀다 — 코드 브라우저/브랜치와 같은 그룹으로 취급한다.
@RestController
@RequestMapping("/api/v1/projects/{owner}/{project}/tags")
class TagRestApiController(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun findProject(owner: String, project: String): Project? {
        return projectRepository.findByOwnerAndNameOrPreviousPlace(owner, project).orElse(null)
    }

    private fun isGitProject(project: Project): Boolean = project.vcs?.uppercase() == "GIT"

    @GetMapping
    fun list(
        @PathVariable owner: String,
        @PathVariable project: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = findProject(owner, project) ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!accessControl.isAllowed(user, found, Operation.READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        if (!isGitProject(found)) {
            return ResponseEntity.badRequest().body(mapOf("error" to "This project is not a git repository."))
        }

        val repository = repositoryService.getRepository(found)
        val tags = repository.getTags().sortedByDescending { it.targetCommit.getCommitterDate() }
        return ResponseEntity.ok(tags.map { toTagNode(it) })
    }

    @PostMapping
    fun create(
        @PathVariable owner: String,
        @PathVariable project: String,
        @RequestBody request: CreateTagRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = findProject(owner, project) ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!accessControl.isAllowed(user, found, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        if (!isGitProject(found)) {
            return ResponseEntity.badRequest().body(mapOf("error" to "This project is not a git repository."))
        }

        val tagName = request.name.trim().removePrefix("refs/tags/")
        // JGit의 자체 ref 이름 검증 규칙을 그대로 재사용한다 — "..", 선행/후행 "/", 제어문자, 공백 등
        // 경로 탈출/인젝션에 쓰일 수 있는 문자가 전부 여기서 걸러진다(리소스 삭제/생성 API가 임의
        // 파일 경로에 영향을 주지 않는지 자체 보안 리뷰한 근거).
        if (tagName.isBlank() || !Repository.isValidRefName("refs/tags/$tagName")) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Invalid tag name: ${request.name}"))
        }

        val repository = repositoryService.getRepository(found)
        val target = request.target?.trim()?.takeIf { it.isNotEmpty() } ?: "HEAD"
        val message = request.message?.trim()?.takeIf { it.isNotEmpty() }

        return try {
            repository.createTag(tagName, target, message, user?.name, user?.email)
            val created = repository.getTags().firstOrNull { it.shortName == tagName }
                ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "Tag was created but could not be read back."))
            ResponseEntity.status(HttpStatus.CREATED).body(toTagNode(created))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: RefAlreadyExistsException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "Tag already exists: $tagName"))
        } catch (e: GitAPIException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @DeleteMapping("/{tag}")
    fun delete(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable tag: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = findProject(owner, project) ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!accessControl.isAllowed(user, found, Operation.DELETE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        if (!isGitProject(found)) {
            return ResponseEntity.badRequest().body(mapOf("error" to "This project is not a git repository."))
        }

        val decodedTagName = URLDecoder.decode(tag.trimStart('/'), StandardCharsets.UTF_8.name())
        // 코디네이터 push 전 리뷰(2026-09-07) — create()는 Repository.isValidRefName()으로 경로
        // 탈출/인젝션 문자를 걸러내는데 delete()는 그 검증 없이 곧바로 deleteTag()를 호출하고
        // 있었다. JGit의 ref 업데이트가 실제로 임의 파일에 영향을 줄 가능성은 낮아 보이지만(존재
        // 확인 후 파싱 검증을 거쳐야 삭제로 이어짐), 검증 비대칭을 남겨둘 이유가 없어 동일하게
        // 방어한다 — 방어 심층화(defense in depth), 관대한 실패(존재하지 않는 태그로 취급)로
        // 일관성 유지.
        if (decodedTagName.isBlank() || !Repository.isValidRefName("refs/tags/$decodedTagName")) {
            return ResponseEntity.notFound().build()
        }

        val repository = repositoryService.getRepository(found)
        repository.deleteTag(decodedTagName)
        return ResponseEntity.noContent().build()
    }

    private fun toTagNode(tag: GitTag): Map<String, Any?> = mapOf(
        "name" to tag.shortName,
        "targetCommitId" to tag.targetCommit.getId(),
        "annotated" to tag.annotated,
        "message" to tag.message,
        "tagger" to tag.tagger?.loginId
    )

    data class CreateTagRequest(
        val name: String,
        val target: String? = null,
        val message: String? = null
    )
}
