package dev.yona.domain.site

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 관리자 화면에서 바꾸는 사이트 설정 한 개이다(추가 개발 항목 X-2, `decisions.md` D-69). 설정 파일이 아니라 데이터베이스에 두어 재시작 없이 바꾼다. v1.6에는 없는 테이블이다.
 *
 * @property key 설정 이름(예: `security.two-factor-enforcement`)
 * @property value 설정 값
 */
@Entity
@Table(name = "site_setting")
class SiteSetting(
    @Id @Column(name = "setting_key", length = 100)
    var key: String? = null,
    @Column(name = "setting_value", length = 1000)
    var value: String? = null
)

/** [SiteSetting] 저장소이다. */
interface SiteSettingRepository : JpaRepository<SiteSetting, String>

/** 2단계 인증을 누구에게 강제하는지이다. */
enum class TwoFactorEnforcement {
    /** 강제하지 않는다(기본값). */
    OFF,

    /** 사이트 관리자에게만 강제한다. */
    ADMINS,

    /** 모든 사용자에게 강제한다. */
    ALL
}
