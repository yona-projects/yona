package dev.yona.domain.oauth2server

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date

/** OAuth 앱 만들기 결과이다. [Created.secret]은 기밀 앱의 비밀 값이며 **이때만** 알 수 있다. */
sealed interface ClientCreation {
    data class Created(val client: OAuthClient, val secret: String?) : ClientCreation
    data class Rejected(val messageKey: String) : ClientCreation
}

/** OAuth 앱 고치기 결과이다. */
sealed interface ClientEdit {
    data class Done(val client: OAuthClient) : ClientEdit
    data class Rejected(val messageKey: String) : ClientEdit
}

/** OAuth 앱이 요청할 수 있는 범위이다(`decisions.md` D-71 9, 11번). */
object OAuthScopes {

    /** OAuth 앱에 **주지 않는** 범위이다. 사이트 관리자 범위는 외부 앱에 사이트 전체 권한을 위임하는 사고를 막으려고 뺀다. */
    val BLOCKED: Set<String> = setOf(ApiScopes.SITE_ADMIN)

    /** 앱이 고를 수 있는 범위(화면에 보이는 순서)이다. */
    fun grantable(): List<String> = ApiScopes.selectable(true).filter { it !in BLOCKED }

    /** [scopes]가 비어 있지 않고 모두 [grantable]이면 `true`이다. */
    fun valid(scopes: Collection<String>): Boolean = scopes.isNotEmpty() && scopes.all { it in grantable() }
}

/**
 * 사용자가 만드는 OAuth 앱(클라이언트)이다(추가 개발 항목 X-1, `decisions.md` D-71). 만들기, 고치기, 비밀 값 다시 만들기, 지우기는 소유자만 하고, 관리자는 사이트 전체 목록을 보고 지울 수 있다.
 * 기밀 앱의 비밀 값은 `yona_cs_`로 시작하는 무작위 값이고 SHA-256만 저장한다(개인용 토큰과 같은 방식).
 */
@Service
class OAuthClientService(private val clients: OAuthClientRepository) {

    private val random = SecureRandom()

    @Transactional
    fun create(
        owner: User, name: String, description: String, homeUrl: String, redirectUris: String, confidential: Boolean, scopes: Set<String>, now: Date = Date()
    ): ClientCreation {
        val ownerId = requireNotNull(owner.id)
        invalid(name, description, homeUrl, redirectUris, scopes)?.let { return ClientCreation.Rejected(it) }
        if (clients.countByOwnerTypeAndOwnerId(OAuthClient.USER, ownerId) >= MAX_PER_OWNER) return ClientCreation.Rejected("oauth.error.limit")
        val secret = if (confidential) SECRET_PREFIX + randomString(SECRET_LENGTH) else null
        val client = clients.save(
            OAuthClient(
                clientId = CLIENT_ID_PREFIX + randomString(CLIENT_ID_LENGTH), name = name.trim(), description = description.trim(), homeUrl = homeUrl.trim(),
                redirectUris = RedirectUris.format(RedirectUris.parse(redirectUris)), confidential = confidential, secretHash = secret?.let { TokenHasher.sha256Hex(it) },
                allowedScopes = ApiScopes.format(scopes), registration = OAuthClient.USER, ownerType = OAuthClient.USER, ownerId = ownerId, createdDate = now
            )
        )
        return ClientCreation.Created(client, secret)
    }

    /** 소유자의 앱 목록이다. 최근에 만든 순서이다. */
    @Transactional(readOnly = true)
    fun list(owner: User): List<OAuthClient> = clients.findByOwnerTypeAndOwnerIdOrderByCreatedDateDescIdDesc(OAuthClient.USER, requireNotNull(owner.id))

    /** 소유자의 앱 하나이다. 남의 앱이거나 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun find(owner: User, id: Long): OAuthClient? = owned(owner, id)

    /** 앱의 이름, 설명, 홈 주소, 리디렉션 주소, 허용 범위를 고친다. 클라이언트 ID와 공개 또는 기밀 구분은 바뀌지 않는다. */
    @Transactional
    fun update(owner: User, id: Long, name: String, description: String, homeUrl: String, redirectUris: String, scopes: Set<String>, now: Date = Date()): ClientEdit {
        val client = owned(owner, id) ?: return ClientEdit.Rejected("oauth.error.notFound")
        invalid(name, description, homeUrl, redirectUris, scopes)?.let { return ClientEdit.Rejected(it) }
        client.name = name.trim(); client.description = description.trim(); client.homeUrl = homeUrl.trim()
        client.redirectUris = RedirectUris.format(RedirectUris.parse(redirectUris)); client.allowedScopes = ApiScopes.format(scopes); client.updatedDate = now
        return ClientEdit.Done(clients.save(client))
    }

    /** 기밀 앱의 비밀 값을 새로 만든다. 새 값을 돌려주고 이전 값은 쓸 수 없다. 남의 앱이거나 공개 앱이면 `null`이다. */
    @Transactional
    fun regenerateSecret(owner: User, id: Long, now: Date = Date()): String? {
        val client = owned(owner, id)?.takeIf { it.confidential == true } ?: return null
        val secret = SECRET_PREFIX + randomString(SECRET_LENGTH)
        client.secretHash = TokenHasher.sha256Hex(secret); client.updatedDate = now
        clients.save(client)
        return secret
    }

    /** 소유자가 앱을 지운다. 남의 앱이거나 없으면 `false`이다. */
    @Transactional
    fun delete(owner: User, id: Long): Boolean {
        val client = owned(owner, id) ?: return false
        clients.delete(client)
        return true
    }

    /** 비밀 값이 [clientId] 기밀 앱의 것이면 `true`이다(상수 시간 비교). 공개 앱이거나 앱이 없으면 `false`이다. */
    @Transactional(readOnly = true)
    fun verifySecret(clientId: String, secret: String): Boolean {
        val stored = clients.findByClientId(clientId)?.takeIf { it.confidential == true }?.secretHash ?: return false
        return MessageDigest.isEqual(stored.toByteArray(), TokenHasher.sha256Hex(secret).toByteArray())
    }

    /** 사이트 전체의 앱이다(관리자 화면). */
    @Transactional(readOnly = true)
    fun listAll(): List<OAuthClient> = clients.findAllByOrderByCreatedDateDescIdDesc()

    /** 관리자가 앱을 지운다. 없으면 `false`이다. */
    @Transactional
    fun deleteAsAdmin(id: Long): Boolean {
        val client = clients.findById(id).orElse(null) ?: return false
        clients.delete(client)
        return true
    }

    /** 사용자가 가진 앱을 모두 지운다(사용자 삭제 처리). */
    @Transactional
    fun deleteOwnedBy(owner: User) = clients.deleteByOwnerTypeAndOwnerId(OAuthClient.USER, requireNotNull(owner.id))

    private fun owned(owner: User, id: Long): OAuthClient? =
        clients.findById(id).orElse(null)?.takeIf { it.ownerType == OAuthClient.USER && it.ownerId == owner.id }

    private fun invalid(name: String, description: String, homeUrl: String, redirectUris: String, scopes: Set<String>): String? {
        if (name.trim().isEmpty() || name.trim().length > NAME_MAX) return "oauth.error.name"
        if (description.trim().length > DESCRIPTION_MAX) return "oauth.error.description"
        if (homeUrl.isNotBlank() && !validHomeUrl(homeUrl.trim())) return "oauth.error.homeUrl"
        val uris = RedirectUris.parse(redirectUris)
        if (uris.isEmpty() || uris.size > MAX_REDIRECTS || uris.any { RedirectUris.problem(it) != null }) return "oauth.error.redirect"
        if (!OAuthScopes.valid(scopes)) return "oauth.error.scope"
        return null
    }

    private fun validHomeUrl(value: String): Boolean = value.length <= HOME_URL_MAX && try {
        val uri = URI(value); uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty() && uri.userInfo == null
    } catch (e: Exception) { false }

    private fun randomString(length: Int) = buildString(length) { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    companion object {
        const val CLIENT_ID_PREFIX = "yona_app_"
        const val SECRET_PREFIX = "yona_cs_"
        private const val CLIENT_ID_LENGTH = 20
        private const val SECRET_LENGTH = 40
        const val MAX_PER_OWNER = 20
        const val MAX_REDIRECTS = 10
        const val NAME_MAX = 100
        const val DESCRIPTION_MAX = 500
        const val HOME_URL_MAX = 500
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}
