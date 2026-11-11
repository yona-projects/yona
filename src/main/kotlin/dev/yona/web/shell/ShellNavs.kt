package dev.yona.web.shell

import dev.yona.domain.site.SiteSettingService
import org.springframework.stereotype.Component

/**
 * 템플릿이 사이드바 항목을 받아 가는 창구이다. `${@shellNavs.siteAdmin(...)}`처럼 부른다.
 *
 * 항목의 판정은 [ShellNav]가 맡고, 이 빈은 템플릿에서 접근할 수 있게 감싸 줄 뿐이다. 맥락마다 필요한 값
 * (프로젝트 화면 문맥, 조직 관리 권한 등)은 맥락을 옮길 때 이 빈에 더한다.
 */
@Component("shellNavs")
class ShellNavs(private val siteSettings: SiteSettingService) {

    /** 사이트 관리 사이드바이다. [updateAvailable]이 `true`이면 업데이트 항목에 배지 1이 붙는다. */
    fun siteAdmin(updateAvailable: Boolean): NavMenu = ShellNav.siteAdmin(updateAvailable)

    /** 내 설정 사이드바이다. OAuth 앱 항목은 사용자 앱 사용 설정이 켜졌을 때만 보인다. */
    fun userSettings(): NavMenu = ShellNav.userSettings(siteSettings.oauthUserAppsEnabled())

    /** 조직 메인 사이드바이다. 설정 항목은 조직 관리자([canManage])에게만 보인다. */
    fun organization(name: String, canManage: Boolean): NavMenu = ShellNav.organization(name, canManage)

    /** 조직 설정 사이드바이다. 맨 위에 조직으로 돌아가는 단추가 붙는다. */
    fun organizationSettings(name: String): NavMenu = ShellNav.organizationSettings(name)
}
