package dev.yona.domain.misc

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 리소스의 구독(Watch)과 구독 해제(Unwatch)를 기록한다. v1.6 `Watch.watch`, `Watch.unwatch`와 `User.toggleFavoriteIssue`에 해당한다.
 * 구독 행과 해제 행은 서로 배타적이다. 한쪽을 만들면 다른 쪽을 지운다.
 */
@Service
class WatchService(
    private val watches: WatchRepository,
    private val unwatches: UnwatchRepository,
    private val favorites: FavoriteIssueRepository,
    private val issues: IssueRepository
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

    /**
     * 이슈 즐겨찾기를 뒤집는다 (v1.6 `User.toggleFavoriteIssue`).
     *
     * @return 즐겨찾기가 켜졌으면 `true`, 꺼졌으면 `false`
     */
    @Transactional
    fun toggleFavoriteIssue(user: User, issueId: Long): Boolean {
        val existing = favorites.findByUserIdAndIssueId(requireNotNull(user.id), issueId)
        if (existing.isNotEmpty()) { favorites.deleteAll(existing); return false }
        favorites.save(FavoriteIssue(user = user, issue = issues.findById(issueId).orElseThrow()))
        return true
    }
}
