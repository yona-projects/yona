package com.github.yonaprojects.yona.domain.project

import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "project")
class Project(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(length = 1_000_000)
    var overview: String? = null,

    var vcs: String? = null,
    var siteurl: String? = null,
    var owner: String? = null,

    var createdDate: Instant? = null,

    // yona Project.java:131-133 previousOwnerLoginId/previousName/previousNameChangedTime 대응 [GL-models_Project-026]
    // (P1-76) — 이전(transfer)/이름 변경 시의 예전 위치를 기록해, 예전 owner/name으로 들어온 요청도
    // (git remote 등) 계속 이 프로젝트로 폴백 조회될 수 있게 한다.
    var previousOwnerLoginId: String? = null,
    var previousName: String? = null,
    var previousNameChangedTime: Instant? = null,

    var lastIssueNumber: Long = 0,
    var lastPostingNumber: Long = 0,

    var isCodeAccessibleMemberOnly: Boolean = false,

    var lastPushedDate: Instant? = null,

    var defaultReviewerCount: Int = 1,
    var isUsingReviewerCount: Boolean = false,

    var isCodeEnabled: Boolean = true,
    var isIssueEnabled: Boolean = true,
    var isPullRequestEnabled: Boolean = true,
    var isReviewEnabled: Boolean = true,
    var isMilestoneEnabled: Boolean = true,
    var isBoardEnabled: Boolean = true,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    var organization: Organization? = null,

    @Enumerated(EnumType.STRING)
    var projectScope: ProjectScope = ProjectScope.PRIVATE,

    @OneToMany(mappedBy = "project", cascade = [CascadeType.ALL], orphanRemoval = true)
    var projectUsers: MutableList<ProjectUser> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_project_id")
    var originalProject: Project? = null,

    // yona Project.deleteFork()/deleteOriginal() 대응 (P0-19에서 발견·수정): CascadeType.ALL은
    // REMOVE를 포함해 원본 프로젝트 삭제 시 모든 fork까지 함께 삭제해버렸다 — legacy는 fork를
    // 삭제하지 않고 originalProject 연결만 끊는다(ProjectServiceImpl.deleteProject() 참고).
    @OneToMany(mappedBy = "originalProject", cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    var forkingProjects: MutableList<Project> = mutableListOf(),

    @ManyToMany(mappedBy = "enrolledProjects")
    var enrolledUsers: MutableList<User> = mutableListOf(),

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "project_label",
        joinColumns = [JoinColumn(name = "project_id")],
        inverseJoinColumns = [JoinColumn(name = "label_id")]
    )
    var labels: MutableSet<Label> = mutableSetOf()
) {
    val isPrivate: Boolean
        get() = projectScope == ProjectScope.PRIVATE

    val isPublic: Boolean
        get() = projectScope == ProjectScope.PUBLIC

    val isProtected: Boolean
        get() = projectScope == ProjectScope.PROTECTED

    // legacy Project.java:589 isForkedFromOrigin()/593 hasForks() 대응 (그룹11 #167 pullrequest/list
    // 포팅 과정에서 추가) — 단순 null/size 체크라 별도 서비스 없이 엔티티 계산 프로퍼티로 이식한다.
    val isForkedFromOrigin: Boolean
        get() = originalProject != null

    val hasForks: Boolean
        get() = forkingProjects.isNotEmpty()


    // yona Project.java:850 getAssociationProjects() 대응(그룹11 #168) — 이 프로젝트 자신 + 자신에서
    // 포크해간 프로젝트들 + (자신이 포크라면) 원본 프로젝트(코드/PR 메뉴가 둘 다 켜져 있을 때만)를
    // cross-fork PR의 from/to 프로젝트 후보로 반환한다. 자기 자신을 포함하는 리스트라 Jackson이
    // JSON 직렬화 시 무한 재귀에 빠지지 않도록 @JsonIgnore(뷰 모델 전용, REST 응답에는 노출 안 함).
    @get:JsonIgnore
    val associationProjects: List<Project>
        get() {
            val projects = mutableListOf(this)
            projects.addAll(forkingProjects)
            val origin = originalProject
            if (isForkedFromOrigin && origin != null && origin.isCodeEnabled && origin.isPullRequestEnabled) {
                projects.add(origin)
            }
            return projects
        }
}
