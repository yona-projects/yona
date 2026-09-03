package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.project.Project
import jakarta.persistence.*

@Entity
@Table(name = "favorite_project")
class FavoriteProject(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    var owner: String = "",
    var projectName: String = ""
) {
    constructor(user: User, project: Project) : this(
        id = null,
        user = user,
        project = project,
        owner = project.owner ?: "",
        projectName = project.name ?: ""
    )
}
