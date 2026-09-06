package com.github.yonaprojects.yona.domain.branchprotection

import org.springframework.data.jpa.repository.JpaRepository

interface ProtectedBranchRepository : JpaRepository<ProtectedBranch, Long> {
    fun findByProjectId(projectId: Long): List<ProtectedBranch>
}
