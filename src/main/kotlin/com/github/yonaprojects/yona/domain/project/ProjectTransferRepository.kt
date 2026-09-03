package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.domain.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional

@Repository
interface ProjectTransferRepository : JpaRepository<ProjectTransfer, Long> {
    fun findByProjectAndSenderAndDestination(project: Project, sender: User, destination: String): Optional<ProjectTransfer>
    fun findByProjectId(projectId: Long): List<ProjectTransfer>
    fun findByIdAndAcceptedAndRequestedAfter(id: Long, accepted: Boolean, requestedLimit: Instant): Optional<ProjectTransfer>
}
