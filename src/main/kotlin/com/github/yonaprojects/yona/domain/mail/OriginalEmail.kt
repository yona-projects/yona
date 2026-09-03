package com.github.yonaprojects.yona.domain.mail

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import jakarta.persistence.*
import java.time.Instant

/**
 * yona의 models/OriginalEmail.java 대응.
 * 수신 이메일의 Message-ID를 그 이메일로 만들어진 리소스(이슈/댓글 등)와 연결한다.
 * 두 가지 용도로 쓰인다:
 *  1) 같은 이메일이 중복 수신됐을 때 이미 처리됐는지 확인(messageId로 조회)
 *  2) 답장(In-Reply-To/References)이 어떤 리소스를 가리키는지 찾기(messageId로 역조회)
 */
@Entity
@Table(
    name = "original_email",
    uniqueConstraints = [UniqueConstraint(columnNames = ["resource_type", "resource_id"])]
)
class OriginalEmail(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var messageId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    var resourceType: ResourceType = ResourceType.NOT_A_RESOURCE,

    @Column(name = "resource_id", nullable = false)
    var resourceId: String = "",

    var handledDate: Instant? = Instant.now()
)
