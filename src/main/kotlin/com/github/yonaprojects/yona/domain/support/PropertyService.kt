package com.github.yonaprojects.yona.domain.support

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// yona models/Property.java의 get()/getLong()/set() 정적 메서드 대응 (P1-55).
@Service
class PropertyService(
    private val propertyRepository: PropertyRepository
) {
    @Transactional(readOnly = true)
    fun get(name: PropertyName): String? {
        return propertyRepository.findByName(name)?.value
    }

    @Transactional(readOnly = true)
    fun getLong(name: PropertyName): Long? {
        return get(name)?.toLong()
    }

    @Transactional
    fun set(name: PropertyName, value: String) {
        val property = propertyRepository.findByName(name) ?: Property(name = name)
        property.value = value
        propertyRepository.save(property)
    }

    @Transactional
    fun set(name: PropertyName, value: Long) {
        set(name, value.toString())
    }
}
