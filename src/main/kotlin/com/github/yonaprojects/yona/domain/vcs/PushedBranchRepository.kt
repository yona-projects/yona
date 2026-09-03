package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.project.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional

@Repository
interface PushedBranchRepository : JpaRepository<PushedBranch, Long> {
    fun findByProjectAndName(project: Project, name: String): Optional<PushedBranch>
    fun findByProjectAndPushedDateAfter(project: Project, cutoff: Instant): List<PushedBranch>
    fun findByProjectAndPushedDateBefore(project: Project, cutoff: Instant): List<PushedBranch>

    // legacy PushedBranch.findByOwnerAndOriginalProject(User owner, Project originalProject) 대응
    // (그룹11 #167/#180) — originalProject의 fork들 중 owner(currentUser) 소유의 fork에 최근(1시간
    // 이내) push된 브랜치를 모두 모은다. legacy는 Project.findByOwnerAndOriginalProject()로 fork
    // 목록을 구한 뒤 각 fork.getRecentlyPushedBranches()를 합치는 2단계였지만, 동일한 결과를 단일
    // JPQL 조인으로 표현한다.
    @Query(
        """
        SELECT pb FROM PushedBranch pb
        WHERE pb.project.originalProject = :originalProject
        AND pb.project.owner = :ownerLoginId
        AND pb.pushedDate > :cutoff
        """
    )
    fun findByOriginalProjectAndOwnerAndPushedDateAfter(
        @Param("originalProject") originalProject: Project,
        @Param("ownerLoginId") ownerLoginId: String,
        @Param("cutoff") cutoff: Instant
    ): List<PushedBranch>
}
