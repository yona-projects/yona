package dev.yona.domain.apitoken

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Date

/** 토큰 만들기 결과이다. */
sealed interface TokenCreation {
    /** 만들었다. [value]는 이 한 번만 돌려주는 원문이다. */
    data class Created(val value: String, val token: ApiToken) : TokenCreation

    /** 거절했다. [messageKey]는 사용자에게 보일 메시지 키이다. */
    data class Rejected(val messageKey: String) : TokenCreation
}

/** 토큰 편집 결과이다. */
sealed interface TokenEdit {
    /** 저장했다. */
    data class Done(val token: ApiToken) : TokenEdit

    /** 거절했다. [messageKey]는 사용자에게 보일 메시지 키이다. */
    data class Rejected(val messageKey: String) : TokenEdit

    /** 없거나 다른 사용자의 토큰이다. */
    data object NotFound : TokenEdit
}

/**
 * 개인용 API 토큰 서비스이다(추가 개발 항목 X-5, `decisions.md` D-36). 새 토큰은 `yona_pat_` + 임의 40자(`SecureRandom`)이고 해시만 저장한다.
 * v1.6의 사용자 토큰은 [migrateLegacyTokens]가 일반 Classic 형태로 옮긴다.
 */
@Service
class ApiTokenService(
    private val tokens: ApiTokenRepository,
    private val users: UserRepository,
    private val entityManager: EntityManager,
    private val audit: dev.yona.domain.audit.AuditRecorder
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    companion object {
        /** 새 토큰 값의 접두사이다. 비밀번호와 구분하고 목록에 보인다. */
        const val TOKEN_PREFIX = "yona_pat_"

        /** 사용자당 토큰 최대 개수이다(GitHub Fine-grained 한도와 같다). */
        const val MAX_TOKENS = 50

        const val NAME_MAX = 100

        /** v1.6 사용자 토큰을 옮긴 행의 이름이다. */
        const val LEGACY_TOKEN_NAME = "이전 토큰"

        /** 마지막 사용 시각을 다시 쓰기까지의 간격(밀리초)이다. */
        private const val TOUCH_INTERVAL_MILLIS = 60_000L

        private const val DAY_MILLIS = 86_400_000L
        private const val RANDOM_LENGTH = 40
        private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        private const val NEW_PREFIX_LENGTH = 13
        private const val LEGACY_PREFIX_LENGTH = 4
        private const val UNUSED_DAYS = 365L
    }

    /**
     * 토큰을 만든다. 이름은 필수이고 100자까지, 범위는 [ApiScopes]에 있는 것(`site_admin`은 [siteManager]일 때만), 만료는 오늘 이후여야 한다.
     * [maxDays]가 0보다 크면 만료 없음은 받지 않고 그 일수 안이어야 한다. 사용자당 [MAX_TOKENS]개까지이다.
     */
    @Transactional
    fun create(user: User, name: String, scopes: Set<String>, expiresDate: Date?, siteManager: Boolean = false, maxDays: Int = 0, now: Date = Date()): TokenCreation {
        val trimmed = name.trim()
        invalidName(trimmed)?.let { return TokenCreation.Rejected(it) }
        if (scopes.any { it !in ApiScopes.selectable(siteManager) }) return TokenCreation.Rejected("apiToken.error.scope")
        invalidExpiry(expiresDate, maxDays, now)?.let { return TokenCreation.Rejected(it) }
        if (tokens.countByUserId(requireNotNull(user.id)) >= MAX_TOKENS) return TokenCreation.Rejected("apiToken.error.limit")
        val value = TOKEN_PREFIX + randomString()
        val saved = tokens.save(
            ApiToken(
                user = user, name = trimmed, tokenHash = TokenHasher.sha256Hex(value), tokenPrefix = value.take(NEW_PREFIX_LENGTH),
                scopes = ApiScopes.format(scopes), isLegacy = false, createdDate = now, expiresDate = expiresDate
            )
        )
        audit.record(tokenEvent("token.created", saved, mapOf("scopes" to ApiScopes.parse(saved.scopes).sorted(), "expires" to saved.expiresDate?.toInstant()?.toString())))
        return TokenCreation.Created(value, saved)
    }

    /** 사용자 자신의 토큰 하나를 읽는다. 없거나 다른 사용자의 토큰이면 `null`이다. */
    @Transactional(readOnly = true)
    fun find(user: User, id: Long): ApiToken? = tokens.findById(id).orElse(null)?.takeIf { it.user?.id == user.id }

    /**
     * 토큰의 이름과 범위를 바꾼다(GitHub Classic의 편집과 같다). 값, 만료, 마지막 사용 시각은 그대로이다. 이미 가진 범위는 사용자가 더 고를 수 없는 범위(사이트 관리자 범위 등)여도
     * 그대로 둘 수 있다. 만료는 값이 새로 나가는 [regenerate]로만 바꾼다. 유출된 토큰의 수명을 값 교체 없이 늘리는 것을 막기 위해서이다.
     */
    @Transactional
    fun update(user: User, id: Long, name: String, scopes: Set<String>, siteManager: Boolean = false): TokenEdit {
        val token = find(user, id) ?: return TokenEdit.NotFound
        val trimmed = name.trim()
        invalidName(trimmed)?.let { return TokenEdit.Rejected(it) }
        val allowed = ApiScopes.selectable(siteManager) + ApiScopes.parse(token.scopes)
        if (scopes.any { it !in allowed }) return TokenEdit.Rejected("apiToken.error.scope")
        val before = mapOf("name" to token.name, "scopes" to ApiScopes.parse(token.scopes).sorted())
        token.name = trimmed
        token.scopes = ApiScopes.format(scopes)
        val saved = tokens.save(token)
        audit.record(tokenEvent("token.updated", saved, mapOf("name" to mapOf("from" to before["name"], "to" to saved.name), "scopes" to mapOf("from" to before["scopes"], "to" to ApiScopes.parse(saved.scopes).sorted()))))
        return TokenEdit.Done(saved)
    }

    /**
     * 새 값을 발급하고 만료를 새로 정한다. 옛 값은 즉시 무효이다. 이름과 범위는 그대로이고 마지막 사용 시각은 지운다. 옛 토큰(추측 가능한 값)도 새 값을 받으면 일반 토큰이 된다.
     * 만료 검사는 [create]와 같다. 없거나 다른 사용자의 토큰이면 `null`이다.
     */
    @Transactional
    fun regenerate(user: User, id: Long, expiresDate: Date?, maxDays: Int = 0, now: Date = Date()): TokenCreation? {
        val token = find(user, id) ?: return null
        invalidExpiry(expiresDate, maxDays, now)?.let { return TokenCreation.Rejected(it) }
        val value = TOKEN_PREFIX + randomString()
        token.tokenHash = TokenHasher.sha256Hex(value)
        token.tokenPrefix = value.take(NEW_PREFIX_LENGTH)
        token.isLegacy = false
        token.expiresDate = expiresDate
        token.lastUsedDate = null
        val saved = tokens.save(token)
        audit.record(tokenEvent("token.regenerated", saved, mapOf("expires" to saved.expiresDate?.toInstant()?.toString())))
        return TokenCreation.Created(value, saved)
    }

    /** 토큰 이벤트이다. 토큰 값과 해시는 넣지 않고 이름, 번호, 범위만 남긴다. */
    private fun tokenEvent(action: String, token: ApiToken, details: Map<String, Any?>) =
        dev.yona.domain.audit.AuditEvent(action, targetType = "token", targetId = token.id?.toString(), targetLabel = token.name, details = details)

    private fun invalidName(trimmed: String): String? = if (trimmed.isEmpty() || trimmed.length > NAME_MAX) "apiToken.error.name" else null

    private fun invalidExpiry(expiresDate: Date?, maxDays: Int, now: Date): String? = when {
        expiresDate != null && !expiresDate.after(now) -> "apiToken.error.expires"
        maxDays > 0 && (expiresDate == null || expiresDate.time > now.time + maxDays * DAY_MILLIS) -> "apiToken.error.expires"
        else -> null
    }

    /**
     * 토큰 값으로 토큰을 찾는다. 해시가 맞고 만료되지 않았고 주인이 잠기거나 삭제되지 않았으면 돌려준다. 마지막 사용 시각은 1분에 한 번만 갱신한다.
     */
    @Transactional
    fun authenticate(value: String): ApiToken? {
        if (value.isEmpty()) return null
        val token = tokens.findByTokenHash(TokenHasher.sha256Hex(value)) ?: return null
        val now = Date()
        if (token.expiresDate != null && !token.expiresDate!!.after(now)) return null
        val owner = token.user ?: return null
        if (owner.state == UserState.LOCKED || owner.state == UserState.DELETED) return null
        val last = token.lastUsedDate
        if (last == null || now.time - last.time >= TOUCH_INTERVAL_MILLIS) {
            token.lastUsedDate = now
            tokens.save(token)
        }
        return token
    }

    @Transactional(readOnly = true)
    fun list(user: User): List<ApiToken> = tokens.findByUserIdOrderByCreatedDateDescIdDesc(requireNotNull(user.id))

    /** 사용자 자신의 토큰만 지운다. 다른 사용자의 토큰이면 `false`이다. */
    @Transactional
    fun delete(user: User, id: Long): Boolean {
        val token = tokens.findById(id).orElse(null) ?: return false
        if (token.user?.id != user.id) return false
        tokens.delete(token)
        audit.record(tokenEvent("token.deleted", token, mapOf("scopes" to ApiScopes.parse(token.scopes).sorted())))
        return true
    }

    /** 1년 넘게 쓰지 않은 토큰을 지운다. 이관한 이전 토큰은 기존 사용자를 위해 지우지 않는다. 지운 개수를 돌려준다. */
    @Transactional
    fun deleteUnused(): Int {
        val cutoff = Date(System.currentTimeMillis() - UNUSED_DAYS * DAY_MILLIS)
        entityManager.flush()
        return tokens.deleteUnusedSince(cutoff).also { entityManager.clear() }
    }

    /**
     * v1.6의 사용자 토큰(`n4user.token`)을 일반 Classic 형태의 행으로 옮긴다(`decisions.md` D-36 5번). 값을 해시해 이름 [LEGACY_TOKEN_NAME], 모든 범위, 만료 없음으로
     * 옮기므로 같은 값이 계속 동작한다. 사용자 열은 비운다. 이미 옮긴 행이 있는 사용자는 열만 비운다. 멱등하다. 옮긴 개수를 돌려준다.
     */
    @Transactional
    fun migrateLegacyTokens(): Int {
        val owners = entityManager.createQuery("select u from User u where u.token is not null and u.token <> ''", User::class.java).resultList
        var migrated = 0
        for (owner in owners) {
            val value = requireNotNull(owner.token)
            if (!tokens.existsByUserIdAndIsLegacyTrue(requireNotNull(owner.id))) {
                tokens.save(
                    ApiToken(
                        user = owner, name = LEGACY_TOKEN_NAME, tokenHash = TokenHasher.sha256Hex(value), tokenPrefix = value.take(LEGACY_PREFIX_LENGTH),
                        scopes = ApiScopes.format(ApiScopes.selectable(siteManager = true)), isLegacy = true, createdDate = Date()
                    )
                )
                migrated++
            }
            owner.token = null
            users.save(owner)
        }
        if (migrated > 0) log.info("Migrated {} legacy user tokens to the api_token table", migrated)
        return migrated
    }

    private fun randomString(): String = buildString(RANDOM_LENGTH) { repeat(RANDOM_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}
