package com.github.yonaprojects.yona.domain.project

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

// yona models/TitleHead.java 대응 (P1-103). 이슈/게시글 제목이 "[Bug][UI] ..."처럼 대괄호로 시작하는
// 머리말(head keyword)로 시작하면, 프로젝트별로 그 키워드의 사용 빈도를 추적해 제목 자동완성/
// 중복이슈 제안에 쓴다.
@Entity
@Table(name = "title_head")
class TitleHead(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    @Column(nullable = false)
    var headKeyword: String = "",

    var frequency: Int = 0
)
