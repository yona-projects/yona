package dev.yona.domain.misc

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 리소스 구독을 다룬다. v1.6 `models.Watch.watch`에 해당한다. */
@Service
@Transactional
class WatchService(
    private val watches: WatchRepository,
    private val unwatches: UnwatchRepository
) {

    /**
     * [user]가 리소스를 구독한다. 이미 구독 중이면 중복으로 만들지 않고, 구독 해제 기록은 지운다.
     *
     * @param resourceId 리소스 번호(문자열)
     */
    fun watch(user: User, resourceType: ResourceType, resourceId: String) {
        val userId = requireNotNull(user.id)
        if (!watches.existsByUserIdAndResourceTypeAndResourceId(userId, resourceType, resourceId)) {
            watches.save(Watch(user = user, resourceType = resourceType, resourceId = resourceId))
        }
        unwatches.findByUserIdAndResourceTypeAndResourceId(userId, resourceType, resourceId).forEach { unwatches.delete(it) }
    }
}
