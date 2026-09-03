package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.project.Project
import jakarta.persistence.*
import java.time.Instant

@MappedSuperclass
abstract class AbstractPosting(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var title: String = "",

    @Column(length = 1_000_000)
    var body: String? = null,

    @Column(length = 1_000_000)
    var history: String? = null,

    var createdDate: Instant? = null,
    var updatedDate: Instant? = null,

    var authorId: Long? = null,
    var authorLoginId: String? = null,
    var authorName: String? = null,

    // yona AbstractPosting.updatedByAuthorId 대응 (P2-02) — 편집자(마지막 수정자) 정보.
    // yona는 id만 저장하고 뷰에서 User.find.byId(...)로 매번 조회하지만, yona는 이미 author*
    // 필드들을 엔티티에 비정규화해 두는 기존 컨벤션(authorId/authorLoginId/authorName)을 그대로
    // 따라 updatedBy도 동일하게 비정규화한다(조회 왕복 없이 템플릿에서 바로 사용 가능).
    var updatedByAuthorId: Long? = null,
    var updatedByAuthorLoginId: String? = null,
    var updatedByAuthorName: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    var number: Long? = null,

    var numOfComments: Int = 0
)
