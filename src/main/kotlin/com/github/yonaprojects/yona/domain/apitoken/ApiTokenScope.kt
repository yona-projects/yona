package com.github.yonaprojects.yona.domain.apitoken

import jakarta.persistence.*

// ApiToken 1개가 ApiTokenScopeGroup(issues/pull-requests/code/...)별로 서로 다른 permission을
// 가질 수 있어(예: issues는 write, code는 read-only) 조인테이블이 아닌 별도 엔티티로 분리했다 —
// ProjectUser가 (project, user) 조합에 role이라는 부가 값을 얹는 것과 같은 패턴.
@Entity
@Table(name = "api_token_scope")
class ApiTokenScope(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_token_id", nullable = false)
    var apiToken: ApiToken? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_group", nullable = false)
    var scopeGroup: ApiTokenScopeGroup = ApiTokenScopeGroup.ISSUES,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var permission: ApiTokenPermission = ApiTokenPermission.NONE
)
