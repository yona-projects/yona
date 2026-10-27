package dev.yona.web.api

import dev.yona.domain.misc.FavoriteIssueRepository
import dev.yona.domain.misc.FavoriteOrganizationRepository
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.misc.FavoriteService
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody

/**
 * 이슈 즐겨찾기 API이다. v1.6 `UserApi.toggleFoveriteIssue`, `getFoveriteIssues`(`app/controllers/api/UserApi.java:92-126`)에 해당한다.
 * 익명은 `403`, 숫자가 아닌 번호는 `400`이다(v1.6은 예외로 `500`이었으나 화면에서 닿지 않는다).
 */
@Controller
class FavoriteApiController(
    private val favoriteService: FavoriteService,
    private val favorites: FavoriteIssueRepository,
    private val favoriteProjects: FavoriteProjectRepository,
    private val favoriteOrganizations: FavoriteOrganizationRepository,
    private val users: UserRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val messages: MessageSource
) {

    @PostMapping("/-_-api/v1/favoriteIssues/{issueId}")
    @ResponseBody
    @Transactional
    fun toggle(@PathVariable issueId: String): ResponseEntity<Any> {
        val user = currentUserProvider.get().user ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val id = issueId.toLongOrNull() ?: return ResponseEntity.badRequest().body("Wrong issue id")
        val favored = try { favoriteService.toggleIssue(user, id) } catch (e: NoSuchElementException) { return ResponseEntity.badRequest().body("Wrong issue id") }
        val key = if (favored) "issue.favorite.added" else "issue.favorite.deleted"
        return ResponseEntity.ok(linkedMapOf("issueId" to issueId, "favored" to favored, "message" to messages.getMessage(key, null, key, LocaleContextHolder.getLocale())))
    }

    /** 내 즐겨찾기 이슈의 번호, 제목, 작성자 이름을 돌려준다. */
    @GetMapping("/-_-api/v1/favoriteIssues")
    @ResponseBody
    @Transactional(readOnly = true)
    fun list(): ResponseEntity<Any> {
        // v1.6 익명의 `favoriteIssues`는 null이라 `NullPointerException`으로 500이다(공식 v1.16.0으로 확인, 프로젝트와 조직 목록은 다르다)
        val user = currentUserProvider.get().user ?: throw NullPointerException("favoriteIssues of the anonymous user")
        val rows = favorites.findByUserIdOrderByIdAsc(requireNotNull(user.id)).mapNotNull { it.issue }
        val items = rows.map { issue ->
            val author = issue.authorId?.let { users.findById(it).orElse(null) }
            linkedMapOf("issueId" to issue.id, "issueTitle" to issue.title, "issueAuthorName" to author?.let { UserDisplay.pureName(it, user.lang) })
        }
        return ResponseEntity.ok(linkedMapOf("projectIds" to rows.map { it.id }, "projects" to items))
    }

    /** 프로젝트 즐겨찾기를 뒤집고 `{projectId, favored}`를 돌려준다. 끄면 그 프로젝트의 최근 방문 기록도 지운다. */
    @PostMapping("/-_-api/v1/favoriteProjects/{projectId}")
    @ResponseBody
    @Transactional
    fun toggleProject(@PathVariable projectId: String): ResponseEntity<Any> {
        val user = currentUserProvider.get().user ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val id = projectId.toLongOrNull() ?: return ResponseEntity.badRequest().body("Wrong project id")
        val favored = try { favoriteService.toggleProject(user, id) } catch (e: NoSuchElementException) { return ResponseEntity.badRequest().body("Wrong project id") }
        return ResponseEntity.ok(linkedMapOf("projectId" to projectId, "favored" to favored))
    }

    /** 내 즐겨찾기 프로젝트의 번호, 이름, 소유자를 돌려준다. */
    @GetMapping("/-_-api/v1/favoriteProjects")
    @ResponseBody
    @Transactional(readOnly = true)
    fun projects(): ResponseEntity<Any> {
        // 익명이면 빈 목록이다(v1.6 `currentUser()`는 즐겨찾기가 빈 NullUser)
        val user = currentUserProvider.get().user ?: return ResponseEntity.ok(linkedMapOf("projectIds" to emptyList<Long>(), "projects" to emptyList<Any>()))
        val rows = favoriteProjects.findByUserIdOrderByIdAsc(requireNotNull(user.id))
        val items = rows.map { linkedMapOf("projectId" to it.project?.id, "projectName" to it.projectName, "owner" to it.owner) }
        return ResponseEntity.ok(linkedMapOf("projectIds" to rows.map { it.project?.id }, "projects" to items))
    }

    /** 조직 즐겨찾기를 뒤집고 `{organizationId, favored}`를 돌려준다. */
    @PostMapping("/-_-api/v1/favoriteOrganizations/{organizationId}")
    @ResponseBody
    @Transactional
    fun toggleOrganization(@PathVariable organizationId: String): ResponseEntity<Any> {
        val user = currentUserProvider.get().user ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val id = organizationId.toLongOrNull() ?: return ResponseEntity.badRequest().body("Wrong organization id")
        val favored = try { favoriteService.toggleOrganization(user, id) } catch (e: NoSuchElementException) { return ResponseEntity.badRequest().body("Wrong organization id") }
        return ResponseEntity.ok(linkedMapOf("organizationId" to organizationId, "favored" to favored))
    }

    /** 내 즐겨찾기 조직의 번호와 이름을 돌려준다. */
    @GetMapping("/-_-api/v1/favoriteOrganizations")
    @ResponseBody
    @Transactional(readOnly = true)
    fun organizations(): ResponseEntity<Any> {
        val user = currentUserProvider.get().user ?: return ResponseEntity.ok(linkedMapOf("organizationIds" to emptyList<Long>(), "organizations" to emptyList<Any>()))
        val rows = favoriteOrganizations.findByUserIdOrderByIdAsc(requireNotNull(user.id))
        val items = rows.map { linkedMapOf("organizationId" to it.organization?.id, "organizationName" to it.organizationName) }
        return ResponseEntity.ok(linkedMapOf("organizationIds" to rows.map { it.organization?.id }, "organizations" to items))
    }
}
