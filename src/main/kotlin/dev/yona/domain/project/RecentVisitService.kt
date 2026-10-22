package dev.yona.domain.project

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자가 최근 방문한 프로젝트를 기록한다. v1.6 `User.visits(Project)`와 `RecentProject.addNew`에 해당한다.
 *
 * v1.6의 `User`는 마지막으로 방문한 프로젝트 번호(`lastVisitedProjectId`)를 메모리의 사용자 캐시에 들고 있다가
 * 같은 프로젝트를 연달아 방문하면 기록을 건너뛴다. v2는 요청마다 사용자를 새로 읽으므로 같은 규모(2000명)의
 * 메모리 캐시에 사용자별 마지막 방문 번호를 보관한다. 서버를 다시 시작하면 비워지는 점도 v1.6과 같다.
 */
@Service
@Transactional
class RecentVisitService(private val recentProjects: RecentProjectRepository) {

    private val lastVisited: Cache<Long, Long> = Caffeine.newBuilder().maximumSize(MAX_TRACKED_USERS).build()

    /**
     * [user]가 [project]를 방문했음을 기록한다. 익명이거나 직전에 방문한 프로젝트와 같으면 아무것도 하지 않는다.
     */
    fun visits(user: User?, project: Project) {
        val userId = user?.id ?: return
        val projectId = requireNotNull(project.id)
        if (lastVisited.getIfPresent(userId) == projectId) return
        lastVisited.put(userId, projectId)
        record(user, project)
    }

    /**
     * 최근 방문한 프로젝트에 기록한다. 같은 프로젝트의 이전 기록은 지우고, 사용자당 [MAX_RECENT_PROJECTS]개를 넘으면
     * 가장 오래된 것부터 지운다 (v1.6 `RecentProject.addVisitHistory`).
     *
     * v1.6은 이 작업을 비동기로 실행했다. v2는 요청 트랜잭션 안에서 처리한다([decisions.md] A-15).
     */
    fun record(user: User, project: Project) {
        val userId = requireNotNull(user.id)
        recentProjects.findByUserIdAndProjectId(userId, requireNotNull(project.id)).forEach { recentProjects.delete(it) }
        recentProjects.flush()
        recentProjects.save(RecentProject(userId = userId, owner = project.owner, project = project, projectName = project.name))
        val all = recentProjects.findByUserIdOrderByIdDesc(userId)
        all.drop(MAX_RECENT_PROJECTS).forEach { recentProjects.delete(it) }
    }

    companion object {
        /** 사용자당 보관하는 최근 방문 프로젝트의 최대 개수이다 (v1.6 `RecentProject.MAX_RECENT_LIST_PER_USER`). */
        const val MAX_RECENT_PROJECTS = 30

        /** 마지막 방문 번호를 기억하는 최대 사용자 수이다 (v1.6 `CacheStore.yonaUsers`의 크기). */
        private const val MAX_TRACKED_USERS = 2000L
    }
}
