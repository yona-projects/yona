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

/**
 * 개인용 API 토큰 서비스이다(추가 개발 항목 X-5, `decisions.md` D-36). 새 토큰은 `yona_pat_` + 임의 40자(`SecureRandom`)이고 해시만 저장한다.
 * v1.6의 사용자 토큰은 [migrateLegacyTokens]가 일반 Classic 형태로 옮긴다.
 */
@Service
class ApiTokenService(
    private val tokens: ApiTokenRepository,
    private val users: UserRepository,
    private val entityManager: EntityManager
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    companion object {
        /** 새 토큰 값의 접두사이다. 비밀번호와 구분하고 목록에 보인다. */
        const val TOKEN_PREFIX = "yona_pat_"

        /** 사용자당 토큰 최대 개수이다(GitHub Fine-grained 한도와 같다). */
        const val MAX_TOKENS = 50

        const val NAME_MAX = 100

        /** 옛 URL `POST /-_-api/v1/users/token`이 교체해 만드는 토큰의 이름이다. */
        const val LOGIN_TOKEN_NAME = "API login"

        /** v1.6 사용자 토큰을 옮긴 행의 이름이다. */
        const val LEGACY_TOKEN_NAME = "이전 토큰"

        /** 마지막 사용 시각을 다시 쓰기까지의 간격(밀리초)이다. */
        private const val TOUCH_INTERVAL_MILLIS = 60_000L

        /** 로그인 토큰의 만료까지 일수이다. */
        const val LOGIN_TOKEN_DAYS = 30

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
        if (trimmed.isEmpty() || trimmed.length > NAME_MAX) return TokenCreation.Rejected("apiToken.error.name")
        if (scopes.any { it !in ApiScopes.selectable(siteManager) }) return TokenCreation.Rejected("apiToken.error.scope")
        if (expiresDate != null && !expiresDate.after(now)) return TokenCreation.Rejected("apiToken.error.expires")
        if (maxDays > 0 && (expiresDate == null || expiresDate.time > now.time + maxDays * DAY_MILLIS)) return TokenCreation.Rejected("apiToken.error.expires")
        if (tokens.countByUserId(requireNotNull(user.id)) >= MAX_TOKENS) return TokenCreation.Rejected("apiToken.error.limit")
        val value = TOKEN_PREFIX + randomString()
        val saved = tokens.save(
            ApiToken(
                user = user, name = trimmed, tokenHash = TokenHasher.sha256Hex(value), tokenPrefix = value.take(NEW_PREFIX_LENGTH),
                scopes = ApiScopes.format(scopes), isLegacy = false, createdDate = now, expiresDate = expiresDate
            )
        )
        return TokenCreation.Created(value, saved)
    }

    /**
     * 옛 URL(`POST /-_-api/v1/users/token`)이 돌려줄 토큰을 만든다. 이름이 [LOGIN_TOKEN_NAME]인 이전 토큰 하나를 지우고 새로 만든다(다른 토큰은 건드리지 않는다).
     * 범위는 사이트 관리자를 뺀 모든 범위, 만료는 [LOGIN_TOKEN_DAYS]일이다.
     */
    @Transactional
    fun issueLoginToken(user: User): TokenCreation.Created {
        tokens.deleteAll(tokens.findByUserIdAndName(requireNotNull(user.id), LOGIN_TOKEN_NAME))
        tokens.flush()
        val expires = Date(System.currentTimeMillis() + LOGIN_TOKEN_DAYS * DAY_MILLIS)
        return create(user, LOGIN_TOKEN_NAME, ApiScopes.selectable(false).toSet(), expires) as? TokenCreation.Created
            ?: throw IllegalStateException("로그인 토큰을 만들지 못했다")
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
