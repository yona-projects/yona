package com.github.yonaprojects.yona.domain.project

import jakarta.persistence.*

@Entity
@Table(
    name = "label",
    uniqueConstraints = [UniqueConstraint(columnNames = ["category", "name"])]
)
class Label(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var category: String = "",

    @Column(nullable = false)
    var name: String = "",

    @ManyToMany(mappedBy = "labels")
    var projects: MutableSet<Project> = mutableSetOf()
)
