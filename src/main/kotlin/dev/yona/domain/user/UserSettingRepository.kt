package dev.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository

/** 사용자 설정 저장소이다. */
interface UserSettingRepository : JpaRepository<UserSetting, Long> {

    /** 사용자의 설정을 조회한다. 설정을 한 번도 저장하지 않았으면 `null`이다 (v1.6 `UserSetting.findByUser`). */
    fun findByUserId(userId: Long): UserSetting?
}
