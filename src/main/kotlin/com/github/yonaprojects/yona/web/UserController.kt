package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.YonaAuthenticationProvider
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.user.Email
import com.github.yonaprojects.yona.domain.user.EmailDomainValidator
import com.github.yonaprojects.yona.domain.user.ReservedWordsValidator
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserService
import com.github.yonaprojects.yona.domain.user.UserSetting
import com.github.yonaprojects.yona.domain.user.UserSettingRepository
import com.github.yonaprojects.yona.domain.user.UserState
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.HtmlUtils
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

@RestController
class UserController(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val recentIssueService: RecentIssueService,
    private val userSettingRepository: UserSettingRepository,
    private val organizationRepository: OrganizationRepository,
    private val yonaAuthenticationProvider: YonaAuthenticationProvider,
    @Value("\${yona.signup.allowed-email-domains:}")
    private val allowedEmailDomains: String,
    @Value("\${yona.signup.require-admin-confirm:false}")
    private val requireAdminConfirm: Boolean
) {

    private fun getLoginUserId(authentication: Authentication?): Long {
        if (authentication == null) throw IllegalArgumentException("Unauthorized")
        val user = userRepository.findByLoginId(authentication.name)
            .orElseThrow { IllegalArgumentException("User not found") }
        return user.id!!
    }

    private fun getServerUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort
        return if (serverPort == 80 || serverPort == 443) {
            "$scheme://$serverName"
        } else {
            "$scheme://$serverName:$serverPort"
        }
    }

    // yona User.getVisitedIssues() / RecentIssue.getRecentIssues 대응.
    @GetMapping("/api/users/me/recent-issues")
    fun getRecentIssues(authentication: Authentication?): ResponseEntity<List<Map<String, Any?>>> {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val user = userRepository.findByLoginId(authentication.name).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val recentIssues = recentIssueService.getRecentIssues(user).map {
            mapOf(
                "title" to it.title,
                "url" to it.url,
                "createdDate" to it.createdDate.toString()
            )
        }
        return ResponseEntity.ok(recentIssues)
    }

    @GetMapping("/api/users")
    fun searchUsers(
        @RequestParam("query") query: String,
        request: HttpServletRequest
    ): ResponseEntity<List<Map<String, String>>> {
        val referer = request.getHeader("referer") ?: ""
        // JSON API 형식 검증
        if (query.isEmpty()) {
            return ResponseEntity.ok(emptyList())
        }

        val users = userRepository.findAll().filter {
            (it.loginId.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)) &&
                    it.state != UserState.DELETED
        }.take(10)

        val result = users.map { user ->
            val avatarUrl = "/images/default-avatar-128.png"
            val sb = StringBuilder()
            sb.append("<img class='mention_image' src='$avatarUrl'>")
            sb.append("<b class='mention_name'>${user.name}</b>")
            sb.append("<span class='mention_username'> @${user.loginId}</span>")

            mapOf(
                "info" to sb.toString(),
                "loginId" to user.loginId
            )
        }

        return ResponseEntity.ok(result)
    }

    @PostMapping("/api/users/emails")
    fun addEmail(
        @RequestParam("email") email: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val userId = getLoginUserId(authentication)
            val savedEmail = userService.addEmail(userId, email)
            ResponseEntity.ok(mapOf("status" to "success", "emailId" to (savedEmail.id ?: 0L)))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to add email")))
        }
    }

    @DeleteMapping("/api/users/emails/{emailId}")
    fun deleteEmail(
        @PathVariable emailId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            userService.deleteEmail(userId, emailId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to delete email")))
        }
    }

    @PostMapping("/api/users/emails/{emailId}/send-verification")
    fun sendValidationEmail(
        @PathVariable emailId: Long,
        request: HttpServletRequest,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            val serverUrl = getServerUrl(request)
            userService.sendValidationEmail(userId, emailId, serverUrl)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to send verification email")))
        }
    }

    // yona UserApp.isUsed() 대응. 회원가입 폼의 아이디 실시간 중복확인에 사용된다.
    @GetMapping("/user/isUsed")
    fun isUsed(@RequestParam("name") name: String): ResponseEntity<Map<String, Boolean>> {
        val isExist = userService.isLoginIdExist(name) || organizationRepository.findByName(name).isPresent
        return ResponseEntity.ok(
            mapOf(
                "isExist" to isExist,
                "isReserved" to ReservedWordsValidator.isReserved(name)
            )
        )
    }

    // yona UserApp.isEmailExist() 대응. 회원가입 폼의 이메일 실시간 중복확인에 사용된다.
    @GetMapping("/user/isEmailExist")
    fun isEmailExist(@RequestParam("email") email: String): ResponseEntity<Map<String, Boolean>> {
        return ResponseEntity.ok(mapOf("isExist" to userService.isEmailExist(email)))
    }

    @PostMapping("/api/users/emails/{emailId}/set-main")
    fun setAsMainEmail(
        @PathVariable emailId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            userService.setAsMainEmail(userId, emailId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to set main email")))
        }
    }

    @PostMapping("/api/users/profile/update")
    fun updateProfile(
        @RequestParam("name") name: String,
        @RequestParam("email") email: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
            
            if (name.isEmpty()) {
                return ResponseEntity.badRequest().body(mapOf("error" to "이름은 필수 항목입니다."))
            }

            if (user.email != email && userService.isEmailExist(email)) {
                return ResponseEntity.badRequest().body(mapOf("error" to "이미 사용 중인 이메일 주소입니다."))
            }

            val escapedName = HtmlUtils.htmlEscape(name.trim())
            user.name = escapedName
            user.email = email.trim()
            userRepository.save(user)

            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to update profile")))
        }
    }

    @PostMapping("/api/users/token/reset")
    fun resetApiToken(
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
            
            // 신규 API 토큰 생성 (Sha256)
            val rawToken = LocalDateTime.now().toString() + user.loginId
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
            val newToken = Base64.getEncoder().encodeToString(hash)
            
            user.token = newToken
            userRepository.save(user)
            
            ResponseEntity.ok(mapOf("status" to "success", "token" to newToken))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to reset token")))
        }
    }

    @PostMapping("/api/users/password/change")
    fun changePassword(
        @RequestBody request: ChangePasswordRequest,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

            // 현재 비밀번호 검증
            val hashedOld = hashPassword(request.oldPassword, user.passwordSalt ?: "")
            if (user.password != hashedOld) {
                return ResponseEntity.badRequest().body(mapOf("error" to "현재 비밀번호가 일치하지 않습니다."))
            }

            // 새 비밀번호 일치 확인
            if (request.password != request.retypedPassword) {
                return ResponseEntity.badRequest().body(mapOf("error" to "입력한 새 비밀번호가 일치하지 않습니다."))
            }

            if (request.password.length < 4) {
                return ResponseEntity.badRequest().body(mapOf("error" to "비밀번호는 4자 이상이어야 합니다."))
            }

            // 비밀번호 재설정
            val newSalt = UUID.randomUUID().toString().substring(0, 8)
            val newHashed = hashPassword(request.password, newSalt)
            
            user.passwordSalt = newSalt
            user.password = newHashed
            userRepository.save(user)

            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to change password")))
        }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(Charsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return Base64.getEncoder().encodeToString(hashed)
    }

    data class ChangePasswordRequest(
        val oldPassword: String,
        val password: String,
        val retypedPassword: String
    )

    // yona UserApi.newUser() 대응. 사이트관리자 전용 벌크 사용자 생성 —
    // 비로그인 상태에서도 호출 가능한 API이므로 권한 검사는 세션/토큰 인증을 거친 currentUser로 직접
    // 판단한다(스프링 시큐리티 인가 규칙이 아닌 컨트롤러 내부 판단인 것도 legacy와 동일).
    //
    // 원본 legacy 경로는 `-_-api/v1/users`다 — 포팅 당시 "Play 프레임워크 라우팅 아티팩트라 이식
    // 대상 아님"으로 잘못 판단해 한동안 `/api/users`로 옮겨져 있었으나(다른 35개 legacy Open API
    // 엔드포인트는 전부 경로까지 원본 그대로 이식한 것과 모순), `/api/users`를 실제로 쓰는 곳이
    // 내부에 전혀 없어 잘못됐던 경로는 걷어내고 원본 경로만 남긴다.
    @PostMapping("/-_-api/v1/users")
    fun newUser(
        @RequestBody request: NewUsersRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val currentUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (currentUser == null || !currentUser.isSiteManager) {
            return ResponseEntity.badRequest()
                .body(mapOf("message" to "User creation with api is allowed by Site admin only."))
        }

        val createdUsers = request.users.map { createUserNode(it) }
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUsers)
    }

    // yona UserApi.createUserNode() 대응. yona는 벌크 생성된 사용자의 비밀번호를
    // SecureRandomNumberGenerator로 생성한 불투명한 값으로 채운 뒤, 그 값을 "평문"으로 취급해 다시
    // salt+해시하는(UserApp.createNewUser) 절차를 그대로 거친다 — 즉 결과적으로 어떤 실제 비밀번호로도
    // 로그인할 수 없는 계정이 되며, 별도의 비밀번호 재설정 절차(신규 요청 범위 밖)를 거쳐야 한다.
    private fun createUserNode(item: NewUserItem): Map<String, Any?> {
        if (!EmailDomainValidator.isAllowed(item.email, allowedEmailDomains)) {
            return mapOf(
                "status" to 403, "reason" to "Forbidden",
                "message" to "허용되지 않은 이메일 도메인입니다.", "user" to item
            )
        }
        if (userRepository.findByEmail(item.email).isPresent) {
            return mapOf("status" to 409, "reason" to "Conflict", "message" to "Already exists!", "user" to item)
        }

        val opaqueRandomPassword = Base64.getEncoder().encodeToString(SecureRandom().generateSeed(20))
        val salt = UUID.randomUUID().toString().substring(0, 8)
        val user = User(
            loginId = item.loginId,
            name = item.name,
            email = item.email,
            password = hashPassword(opaqueRandomPassword, salt),
            passwordSalt = salt
        )
        if (requireAdminConfirm) {
            user.state = UserState.LOCKED
        }
        val created = userService.createUser(user)

        return mapOf(
            "status" to 201, "reason" to "Created",
            "user" to mapOf(
                "id" to created.id, "loginId" to created.loginId,
                "name" to created.name, "email" to created.email
            )
        )
    }

    data class NewUsersRequest(val users: List<NewUserItem>)
    data class NewUserItem(val loginId: String, val name: String, val email: String)

    // yona UserApi.newToken() 대응. 세션 없이 아이디(또는 이메일)+비밀번호로
    // API 액세스 토큰을 발급한다. 비밀번호 검증 자체는 YonaAuthenticationProvider에 위임해 LDAP
    // 활성화 여부/계정 잠금 상태 처리를 로그인 폼과 동일하게 재사용한다.
    @PostMapping("/-_-api/v1/users/token")
    fun newToken(@RequestBody request: NewTokenRequest): ResponseEntity<Map<String, String>> {
        val user = userRepository.findByLoginId(request.id).orElse(null)
            ?: userRepository.findByEmail(request.id).orElse(null)

        if (user == null || user.state == UserState.LOCKED || user.state == UserState.DELETED) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("message" to "No valid user by id"))
        }

        return try {
            yonaAuthenticationProvider.authenticate(UsernamePasswordAuthenticationToken(user.loginId, request.password))

            val rawToken = LocalDateTime.now().toString() + user.loginId
            val digest = MessageDigest.getInstance("SHA-256")
            val newToken = Base64.getEncoder().encodeToString(digest.digest(rawToken.toByteArray(Charsets.UTF_8)))

            user.token = newToken
            userRepository.save(user)

            ResponseEntity.ok(mapOf("access_token" to newToken))
        } catch (e: AuthenticationException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("message" to "No user by id and password"))
        }
    }

    data class NewTokenRequest(val id: String, val password: String)

    // yona UserApi.users() 대응. 사이트관리자 전용, ACTIVE 사용자 전체 목록.
    @GetMapping("/-_-api/v1/admin/users")
    fun listAllUsersForAdmin(authentication: Authentication?): ResponseEntity<Any> {
        val currentUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (currentUser == null || !currentUser.isSiteManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val users = userRepository.findByState(UserState.ACTIVE)
        val result = users.map { u ->
            mapOf(
                "id" to u.id, "login_id" to u.loginId, "name" to u.name,
                "email" to u.email, "state" to u.state.name, "is_guest" to u.isGuest
            )
        }
        return ResponseEntity.ok(result)
    }

    // yona UserApi.updateUserState() 대응. 사이트관리자 전용, 사이트관리자
    // 상태(SITE_ADMIN)로의 변경은 이 API로 금지한다(별도 절차 필요 — legacy와 동일한 제약).
    @PatchMapping("/-_-api/v1/admin/users/{loginId}")
    fun updateUserStateByAdmin(
        @PathVariable loginId: String,
        @RequestBody request: UpdateUserStateRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val currentUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (currentUser == null || !currentUser.isSiteManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val user = userRepository.findByLoginId(loginId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val state = UserState.of(request.state) ?: return ResponseEntity.badRequest().build()
        if (state == UserState.SITE_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        user.state = state
        userRepository.save(user)

        return ResponseEntity.ok(mapOf("id" to user.id, "login_id" to user.loginId, "state" to user.state.name))
    }

    data class UpdateUserStateRequest(val state: String)

    // yona UserApp.setDefaultLoginPage() 대응(레거시 Open API 경로 별칭 포함). 로그인 후 사이트
    // 루트로 접속했을 때 이동할 "기본 페이지"를 사용자별로 저장한다(리다이렉트 소비는
    // IndexController). legacy 경로 `/-_-api/v1/user/defultLoginPage`는 legacy 자체 오타(defult)까지
    // 그대로 유지한 별칭 — 기존 `/user/setDefaultLoginPage`도 계속 지원한다.
    @PostMapping(value = ["/user/setDefaultLoginPage", "/-_-api/v1/user/defultLoginPage"])
    fun setDefaultLoginPage(
        @RequestParam(required = false) path: String?,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String?>> {
        if (authentication == null) throw IllegalArgumentException("Unauthorized")
        val user = userRepository.findByLoginId(authentication.name)
            .orElseThrow { IllegalArgumentException("User not found") }

        val userSetting = userSettingRepository.findByUserId(user.id!!).orElseGet { UserSetting(user = user) }
        userSetting.loginDefaultPage = path
        userSettingRepository.save(userSetting)

        return ResponseEntity.ok(mapOf("defaultLoginPage" to userSetting.loginDefaultPage))
    }
}
