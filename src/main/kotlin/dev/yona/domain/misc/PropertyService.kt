package dev.yona.domain.misc

import dev.yona.domain.enumeration.PropertyName
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 시스템 속성(`property` 테이블)을 읽고 쓴다. v1.6 `models.Property`의 `get`, `getLong`, `set`에 해당한다. */
@Service
class PropertyService(private val properties: PropertyRepository) {

    /** 속성 값이다. 같은 이름이 여럿이면 첫 값이고 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun get(name: PropertyName): String? = properties.findByName(name).firstOrNull()?.value

    /** 숫자 속성 값이다. 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun getLong(name: PropertyName): Long? = get(name)?.toLong()

    /** 속성 값을 저장한다. 없으면 만든다. */
    @Transactional
    fun set(name: PropertyName, value: String) {
        val property = properties.findByName(name).firstOrNull() ?: Property(name = name)
        property.value = value
        properties.save(property)
    }

    /** 숫자 속성 값을 저장한다. */
    fun set(name: PropertyName, value: Long) = set(name, value.toString())
}
