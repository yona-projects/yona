package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.notification.MentionRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.persistence.EntityManager
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

/**
 * 외부용 사용자 API이다. v1.6 `UserApi`(`app/controllers/api/UserApi.java`)의 사용자 만들기, 토큰 받기, 내 이슈, 통계, 관리자용 목록과 상태 바꾸기에 해당한다.
 * 토큰 인증은 `Authorization: token <값>` 헤더이다.
 */
@Controller
@ResponseBody
class ExternalUserApiController(
    private val requests: ApiRequests,
    private val users: UserRepository,
    private val apiUsers: ApiUsers,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val mentions: MentionRepository,
    private val issueJson: IssueJson,
    private val properties: YonaProperties,
    private val entityManager: EntityManager,
    private val issueSearch: dev.yona.domain.issue.IssueSearchService,
    private val loginSession: dev.yona.security.LoginSessionService
) {
    private val random = SecureRandom()

    // --- 사용자 만들기 ---

    /**
     * 사용자 여러 명을 만든다(사이트 관리자만). 본문 `{"users": [{"loginId", "name", "email"}]}`. 응답은 사용자마다 `{status, reason, user}`이고
     * 허용하지 않는 메일 도메인은 `403`, 이미 있는 이메일(이나 아이디)은 `409`로 알린다. 비밀번호는 무작위이다.
     */
    @PostMapping("/-_-api/v1/users")
    @Transactional
    fun newUser(request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        if (!accessControl.isSiteManager(currentUserProvider.get().user)) return requests.message(HttpStatus.BAD_REQUEST, "User creation with api is allowed by Site admin only.")
        val list = json.findValue("users")
        if (list == null || !list.isArray) return requests.message(HttpStatus.BAD_REQUEST, "No users key exists or value must be array!")
        val created: List<Map<String, Any?>> = list.toList().map { node ->
            apiUsers.create(node.findValue("loginId")?.asString("").orEmpty(), node.findValue("name")?.asString("").orEmpty(), node.findValue("email")?.asString("").orEmpty())
        }
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(created)
    }


    // --- 토큰 ---

    /** 아이디나 이메일과 비밀번호로 새 토큰을 만들어 `{"access_token"}`으로 돌려준다. 잠기거나 지워진 사용자는 거절한다. */
    @PostMapping("/-_-api/v1/users/token")
    @Transactional
    fun newToken(request: HttpServletRequest, response: jakarta.servlet.http.HttpServletResponse): Any {
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Empty request data")
        val id = json.findValue("id")?.asString("").orEmpty()
        val password = json.findValue("password")?.asString("").orEmpty()
        val user = users.findByLoginKey(id)
        if (user == null || user.state == UserState.LOCKED || user.state == UserState.DELETED) return requests.message(HttpStatus.UNAUTHORIZED, "No valid user by id")
        if (user.password != dev.yona.security.LegacyPasswordHasher.hash(password, user.passwordSalt.orEmpty())) return requests.message(HttpStatus.UNAUTHORIZED, "No user by id and password")
        // v1.6 `SHA256Util.hashBasedNow()`: 지금 시각 문자열의 SHA-256을 Base64로 쓴다.
        val token = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(LocalDateTime.now().toString().toByteArray()))
        user.token = token
        users.save(user)
        // v1.6 UserApi.newToken은 토큰을 만들기 전에 addUserInfoToSession으로 세션 로그인도 한다 (쿠키를 유지하는 클라이언트는 사이트에도 로그인된다)
        loginSession.start(request, response, user)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mapOf("access_token" to token))
    }

    // --- 내 이슈 ---

    /**
     * 토큰 사용자의 이슈를 필터(`assigned`, `created`, `mentioned`, `favorite`, `all`)별로 최근 수정 순서로 돌려준다.
     * `page`는 1부터, `pageNum`은 한 쪽의 개수(기본 10)이다. 모르는 필터는 `400`이다.
     */
    @GetMapping("/-_-api/v1/user/issues")
    @Transactional(readOnly = true)
    fun issuesByUser(
        request: HttpServletRequest, @RequestParam(defaultValue = "assigned") filter: String,
        @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "10") pageNum: Int
    ): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val userId = requireNotNull(user.id)
        // v1.6 Mention.getMentioningIssueIds: 본문에서 멘션된 이슈와 멘션된 댓글이 달린 이슈를 모두 모은다
        val mentioned = { issueSearch.mentioningIssueIds(userId).toList().ifEmpty { listOf(-1L) } }
        val favorite = "select f.issue.id from FavoriteIssue f where f.user.id = :user"
        val (where, params) = when (filter) {
            "assigned" -> "a.user.id = :user" to mapOf<String, Any>("user" to userId)
            "created" -> "i.authorId = :user" to mapOf("user" to userId)
            "mentioned" -> "i.id in :mentioned" to mapOf("mentioned" to mentioned())
            "favorite" -> "i.id in ($favorite)" to mapOf("user" to userId)
            "all" -> "(i.authorId = :user or a.user.id = :user or i.id in :mentioned or i.id in ($favorite))" to mapOf("user" to userId, "mentioned" to mentioned())
            else -> return ResponseEntity.badRequest().build<Void>()
        }
        val query = entityManager.createQuery("select i from Issue i left join i.assignee a where $where order by i.updatedDate desc, i.id desc", Issue::class.java)
        params.forEach { (k, v) -> query.setParameter(k, v) }
        val items = query.setFirstResult(maxOf(page - 1, 0) * pageNum).setMaxResults(pageNum).resultList
        val base = properties.hostname.ifEmpty { "http://localhost" }
        val result = items.map { issue ->
            val project = requireNotNull(issue.project)
            linkedMapOf<String, Any?>(
                "id" to issue.id, "number" to issue.number, "state" to issue.state?.name, "title" to issue.title,
                "createdDate" to issueJson.iso(issue.createdDate), "updatedDate" to issueJson.iso(issue.updatedDate),
                "author" to linkedMapOf("id" to issue.authorId, "loginId" to issue.authorLoginId, "name" to issue.authorName),
                "assignee" to (issue.assignee?.let { linkedMapOf("id" to it.id, "loginId" to it.user?.loginId, "name" to it.user?.name) } ?: linkedMapOf<String, Any?>()),
                "project" to linkedMapOf("id" to project.id, "name" to project.name), "owner" to project.owner,
                "refUrl" to "$base/${project.owner}/${project.name}/issue/${issue.number}"
            )
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mapOf("result" to result))
    }

    // --- 통계 ---

    /** 사용자의 글, 댓글, 담당, 추천 개수이다. 없는 사용자는 모두 0이다. 로그인해야 한다. */
    @GetMapping("/-_-api/v1/users/{user}/statistics")
    @AnonymousCheck(requiresLogin = true)
    @Transactional(readOnly = true)
    fun statistics(@PathVariable("user") loginId: String): Any {
        val target = users.findByLoginIdIgnoreCase(loginId)
        val count = { jpql: String -> target?.id?.let { entityManager.createQuery(jpql, java.lang.Long::class.java).setParameter("id", it).singleResult.toLong() } ?: 0L }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
            linkedMapOf(
                "issue" to count("select count(i) from Issue i where i.authorId = :id"), "posting" to count("select count(p) from Posting p where p.authorId = :id"),
                "assignedIssue" to count("select count(i) from Issue i where i.assignee.user.id = :id"),
                "issueComment" to count("select count(c) from IssueComment c where c.authorId = :id"), "postingComment" to count("select count(c) from PostingComment c where c.authorId = :id"),
                "issueVoter" to count("select count(i) from Issue i join i.voters v where v.id = :id"), "issueCommentVoter" to count("select count(c) from IssueComment c join c.voters v where v.id = :id")
            )
        )
    }

    // --- 관리자 ---

    /** 활성 사용자 목록이다(사이트 관리자만). */
    @GetMapping("/-_-api/v1/admin/users")
    @Transactional(readOnly = true)
    fun adminUsers(): Any {
        if (!accessControl.isSiteManager(currentUserProvider.get().user)) return ErrorViews.forbidden("error.auth.unauthorized.waringMessage")
        val list = entityManager.createQuery("select u from User u where u.state = :state order by u.id", User::class.java).setParameter("state", UserState.ACTIVE).resultList
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
            list.map { linkedMapOf("id" to it.id, "login_id" to it.loginId, "name" to it.name, "email" to it.email, "state" to it.state?.name, "is_guest" to it.isGuest) }
        )
    }

    /** 사용자의 상태를 바꾼다(사이트 관리자만). 본문 `{"state": "locked"}`. `SITE_ADMIN`으로는 바꿀 수 없다(`403`). */
    @PatchMapping("/-_-api/v1/admin/users/{user}")
    @Transactional
    fun updateUserState(@PathVariable("user") loginId: String, request: HttpServletRequest): Any {
        if (!accessControl.isSiteManager(currentUserProvider.get().user)) return ErrorViews.forbidden("error.auth.unauthorized.waringMessage")
        val target = users.findByLoginIdIgnoreCase(loginId) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Void>()
        val json = requests.json(request) ?: return ResponseEntity.badRequest().body("Empty json body")
        val state = UserState.entries.firstOrNull { it.name.equals(json.findValue("state")?.asString("").orEmpty(), ignoreCase = true) } ?: return ResponseEntity.badRequest().build<Void>()
        if (state == UserState.SITE_ADMIN) return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()
        target.state = state
        users.save(target)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("id" to target.id, "login_id" to target.loginId, "state" to target.state?.name))
    }
}
