package com.github.yonaprojects.yona.domain.mention

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

// yona models/Mention.java 대응(사용자 지시로 yona의 로직·구조·한계를 그대로 포팅) — 리소스
// 하나당 (리소스, 멘션된 유저) 조합으로 한 행. 유니크 제약은 yona에도 없다(Mention.update()의
// diff-sync 로직이 중복 삽입을 막는다). yona도 리소스 삭제 시 이 행을 정리하는 코드가 전혀 없어
// (Mention.delete()/deleteBy() 호출 0건) 고아 행이 남는 한계를 그대로 가져왔다 — 조회 시 그냥
// 매치되지 않는 값이라 실질적 부작용은 없다.
@Entity
@Table(name = "mention")
class Mention(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    var resourceType: ResourceType = ResourceType.NOT_A_RESOURCE,

    @Column(name = "resource_id", nullable = false)
    var resourceId: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User
)
