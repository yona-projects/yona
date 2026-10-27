package dev.yona.domain.misc

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 리소스의 구독(Watch)과 구독 해제(Unwatch)를 기록한다. v1.6 `Watch.watch`, `Watch.unwatch`에 해당한다.
 * 구독 행과 해제 행은 서로 배타적이다. 한쪽을 만들면 다른 쪽을 지운다.
 */
@Service
class WatchService(
    private val watches: WatchRepository,
    private val unwatches: UnwatchRepository
) {

    /** [user]가 리소스를 구독하게 한다. 이미 구독 중이면 그대로 두고, 해제 기록은 지운다. */
    @Transactional
    fun watch(user: User, type: ResourceType, resourceId: String) {
        val userId = requireNotNull(user.id)
        if (!watches.existsByUserIdAndResourceTypeAndResourceId(userId, type, resourceId)) {
            watches.save(Watch(user = user, resourceType = type, resourceId = resourceId))
        }
        unwatches.deleteAll(unwatches.findByUserIdAndResourceTypeAndResourceId(userId, type, resourceId))
    }

    /**
     * 리소스를 지울 때 그 리소스의 구독 행과 해제 행을 모두 지운다 (v1.6 `ResourcePersistAdapter.postDelete`는 `ResourceConvertible`을
     * 지울 때 같은 트랜잭션에서 지운다).
     */
    @Transactional
    fun removeAll(type: ResourceType, resourceId: String) {
        watches.deleteAll(watches.findByResourceTypeAndResourceId(type, resourceId))
        unwatches.deleteAll(unwatches.findByResourceTypeAndResourceId(type, resourceId))
    }

    /**
     * [user]의 구독을 해제한다. 해제 기록을 만들고 구독 행은 지운다.
     *
     * @return 이미 해제한 적이 있었으면 `false`, 이번에 처음 해제했으면 `true`
     */
    @Transactional
    fun unwatch(user: User, type: ResourceType, resourceId: String): Boolean {
        val userId = requireNotNull(user.id)
        val first = !unwatches.existsByUserIdAndResourceTypeAndResourceId(userId, type, resourceId)
        if (first) unwatches.save(Unwatch(user = user, resourceType = type, resourceId = resourceId))
        watches.deleteAll(watches.findByUserIdAndResourceTypeAndResourceId(userId, type, resourceId))
        return first
    }
}
