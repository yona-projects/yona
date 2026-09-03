package com.github.yonaprojects.yona.domain.support

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

// yona models/Property.java 대응 (P1-55). 이름-값 형태의 사이트 전역 설정을 저장하는 범용 엔티티지만,
// 실제로는 IMAP 메일함 폴링 워터마크(MAILBOX_LAST_SEEN_UID/MAILBOX_LAST_UID_VALIDITY) 용도로만
// 쓰인다 — yona 원본도 이 두 값 외에는 쓰지 않는다("Add property you need here"라는 주석만 있음).
enum class PropertyName {
    MAILBOX_LAST_SEEN_UID,
    MAILBOX_LAST_UID_VALIDITY
}

@Entity
@Table(name = "property", uniqueConstraints = [UniqueConstraint(columnNames = ["name"])])
class Property(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var name: PropertyName,

    @Column(length = 4000)
    var value: String? = null
)
