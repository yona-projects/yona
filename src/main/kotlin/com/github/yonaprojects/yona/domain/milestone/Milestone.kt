package com.github.yonaprojects.yona.domain.milestone

import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.enumeration.State
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "milestone",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "title"])]
)
class Milestone(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var title: String = "",

    var dueDate: Instant? = null,

    @Column(length = 1_000_000)
    var contents: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: State = State.OPEN,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonIgnore
    var project: Project
)
