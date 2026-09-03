package com.github.yonaprojects.yona.domain.issue

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

// yona Project.delete()의 assignees 삭제 루프 대응 (P0-19). Issue.assignee가 cascade=ALL이라
// 이슈에 연결된 Assignee는 이슈 삭제 시 함께 지워지지만, 어떤 이슈에도 연결되지 않은 Assignee가
// 남아있을 가능성에 대비한 프로젝트 삭제 시점의 방어적 정리용.
@Repository
interface AssigneeRepository : JpaRepository<Assignee, Long> {
    fun findByProjectId(projectId: Long): List<Assignee>
}
