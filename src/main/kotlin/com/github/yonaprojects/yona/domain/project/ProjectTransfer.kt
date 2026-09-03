package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "project_transfer")
class ProjectTransfer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    var sender: User,

    @Column(nullable = false)
    var destination: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    var requested: Instant = Instant.now(),

    @Column(nullable = false, length = 50)
    var confirmKey: String = "",

    var accepted: Boolean = false,

    var newProjectName: String = ""
)
