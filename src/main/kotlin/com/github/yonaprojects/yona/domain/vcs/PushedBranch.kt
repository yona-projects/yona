package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.project.Project
import jakarta.persistence.*
import java.time.Instant

// yona models/PushedBranch.java 대응 (P1-24). yona는 name에 전체 ref(refs/heads/foo)를
// 저장하지만, 이 저장소는 P0-12(GitPushHooks)에서 이미 짧은 브랜치 이름을 기준으로
// PullRequest.fromBranch/findRelatedPullRequests를 다뤄왔으므로 그 관례를 그대로 따라
// 짧은 브랜치 이름으로 저장한다.
@Entity
@Table(name = "project_pushed_branch")
class PushedBranch(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    var pushedDate: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project? = null
)
