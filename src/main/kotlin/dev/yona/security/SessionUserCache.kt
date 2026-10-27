package dev.yona.security

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 로그인한 사용자를 서버에 기억하는 캐시이다. v1.6 `CacheStore.yonaUsers`(`app/utils/CacheStore.java`, 최대 2000명)의 대응이다.
 *
 * v1.6은 세션 쿠키에 사용자 번호만 담고 사용자 객체는 이 캐시에서 찾는다(`UserApp.getUserFromSession`).
 * 다음 두 가지 동작을 그대로 따른다.
 *
 * - **캐시에 없는 사용자의 세션은 무효이다.** 항목이 밀려나거나(최대 개수 초과) 무효화되면 다음 요청의 세션이 비워져 다시 로그인해야 한다.
 *   예를 들어 사이트 관리자가 다른 사용자의 사이트 관리자 권한을 바꾸면 그 사용자는 로그아웃된다(`SiteApp.toggleAccountState` 근처의 `invalidate`).
 * - **항목에는 판정이 기억된다.** 사용자 객체가 사이트 관리자 여부, 프로젝트 매니저 여부, 조직 소속 판정을 처음 계산한 값으로 기억하므로(`User.isSiteManager`,
 *   `isManagerOf`, `isMemberOf(Organization, RoleType)`), 권한이 바뀌어도 항목이 교체되거나 무효화될 때까지 이전 판정이 유지된다.
 *   로그인, 프로필 수정, 프로젝트와 조직 가입 신청처럼 v1.6이 `put`을 부르는 시점에 [remember]로 항목을 새로 만들어 판정을 비운다.
 *
 * API 토큰으로 식별한 사용자는 v1.6에서도 요청마다 DB에서 새로 읽으므로 이 캐시를 거치지 않는다.
 *
 * @param maximumSize 기억할 최대 사용자 수이다. v1.6 `MAXIMUM_CACHED_YONA_USER_ENTRY`와 같은 2000이다.
 */
@Component
class SessionUserCache(maximumSize: Long = MAXIMUM_ENTRIES) {

    private val entries: Cache<Long, SessionUserMemo> = Caffeine.newBuilder().maximumSize(maximumSize).build()

    /** 사용자를 새로 기억한다. 이미 있으면 항목을 교체해 기억한 판정을 비운다 (v1.6 `CacheStore.yonaUsers.put`). */
    fun remember(userId: Long) {
        entries.put(userId, SessionUserMemo())
    }

    /** 사용자를 잊는다. 그 사용자의 세션은 다음 요청에서 무효가 된다 (v1.6 `CacheStore.yonaUsers.invalidate`). */
    fun invalidate(userId: Long) {
        entries.invalidate(userId)
    }

    /** 사용자가 기억되어 있으면 `true`이다. */
    fun contains(userId: Long): Boolean = entries.getIfPresent(userId) != null

    /** 사용자의 판정 기억이다. 기억되어 있지 않으면 `null`이다. */
    fun memoOf(userId: Long): SessionUserMemo? = entries.getIfPresent(userId)

    /** 밀려난 항목을 즉시 정리한다. 시험에서 최대 개수 동작을 확인할 때 쓴다. */
    fun cleanUp() {
        entries.cleanUp()
    }

    companion object {
        /** 기억할 최대 사용자 수이다 (v1.6 `CacheStore.MAXIMUM_CACHED_YONA_USER_ENTRY`). */
        const val MAXIMUM_ENTRIES = 2000L
    }
}

/**
 * 한 사용자에 대해 처음 계산한 권한 판정을 기억한다. v1.6 `User`의 `siteManager`, `projectManagerMemo`, `orgMembersMemo` 필드에 해당한다.
 *
 * 프로젝트 멤버 여부(`isMemberOf(Project)`)는 v1.6에서도 기억하지 않으므로 여기에 두지 않는다.
 */
class SessionUserMemo {

    @Volatile
    private var siteManager: Boolean? = null
    private val projectManagers = ConcurrentHashMap<Long, Boolean>()
    private val orgMembers = ConcurrentHashMap<String, Boolean>()

    /** 사이트 관리자 여부이다. 처음 부를 때만 [compute]로 계산한다. */
    fun siteManager(compute: () -> Boolean): Boolean = siteManager ?: compute().also { siteManager = it }

    /** 프로젝트 매니저 여부이다. 프로젝트마다 처음 부를 때만 [compute]로 계산한다. */
    fun projectManager(projectId: Long, compute: () -> Boolean): Boolean =
        projectManagers[projectId] ?: compute().also { projectManagers[projectId] = it }

    /** 조직의 해당 역할 소속 여부이다. (조직, 역할)마다 처음 부를 때만 [compute]로 계산한다. */
    fun orgMember(organizationId: Long, roleType: String, compute: () -> Boolean): Boolean {
        val key = "$organizationId:$roleType"
        return orgMembers[key] ?: compute().also { orgMembers[key] = it }
    }
}
