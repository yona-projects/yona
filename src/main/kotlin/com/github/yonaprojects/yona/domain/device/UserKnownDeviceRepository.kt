package com.github.yonaprojects.yona.domain.device

import org.springframework.data.jpa.repository.JpaRepository

interface UserKnownDeviceRepository : JpaRepository<UserKnownDevice, Long> {
    fun findByUserIdAndDeviceTokenHash(userId: Long, deviceTokenHash: String): UserKnownDevice?
    fun findByUserId(userId: Long): List<UserKnownDevice>
}
