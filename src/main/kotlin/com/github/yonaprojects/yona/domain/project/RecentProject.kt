package com.github.yonaprojects.yona.domain.project

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "recent_project",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "project_id"])]
)
class RecentProject(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0L,

    @Column(nullable = false)
    var owner: String = "",

    @Column(name = "project_id", nullable = false)
    var projectId: Long = 0L,

    @Column(name = "project_name", nullable = false)
    var projectName: String = "",

    @Column(name = "visited_date", nullable = false)
    var visitedDate: Instant = Instant.now()
)
