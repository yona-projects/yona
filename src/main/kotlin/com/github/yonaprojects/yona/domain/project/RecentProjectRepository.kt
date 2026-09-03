package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.domain.user.User
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional

interface RecentProjectRepository : JpaRepository<RecentProject, Long> {
    fun findByUserId(userId: Long): List<RecentProject>
    fun findByUserIdOrderByVisitedDateDesc(userId: Long): List<RecentProject>
    fun findByUserIdAndProjectId(userId: Long, projectId: Long): Optional<RecentProject>
    fun deleteByUserIdAndProjectId(userId: Long, projectId: Long)

    // yona User.visits(Project)/RecentProject.addNew() 대응 (P2-09). 웹 컨트롤러
    // (ProjectViewController)뿐 아니라 git 프로토콜 진입점(GitServletConfig)에서도
    // 동일하게 최근 방문 프로젝트를 기록할 수 있도록 저장소 계층 공용 메서드로 승격.
    fun recordVisit(user: User, project: Project) {
        try {
            val userId = user.id ?: return
            val projectId = project.id ?: return

            // deleteByUserIdAndProjectId()의 DELETE와 뒤이은 save()의 INSERT가 같은 트랜잭션
            // 안에서 (userId, projectId) unique 제약을 두고 순서가 꼬이지 않도록 즉시 flush한다.
            deleteByUserIdAndProjectId(userId, projectId)
            flush()

            save(
                RecentProject(
                    userId = userId,
                    owner = project.owner ?: "",
                    projectId = projectId,
                    projectName = project.name ?: "",
                    visitedDate = Instant.now()
                )
            )

            val recentList = findByUserIdOrderByVisitedDateDesc(userId)
            if (recentList.size > 30) {
                deleteAll(recentList.subList(30, recentList.size))
            }
        } catch (e: Exception) {
            // NOOP — 방문 기록 실패가 git/웹 요청 자체를 막아서는 안 됨(yona RecentProject.addVisitHistory()도
            // OptimisticLockException을 무시하는 것과 동일한 취지)
        }
    }
}
