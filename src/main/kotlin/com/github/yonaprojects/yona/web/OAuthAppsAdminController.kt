package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.oauth2server.OAuthAppRegistrationService
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClient
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

// 이 컨트롤러(`/site/oauth-apps`)는 한때 OAuth 앱
// 등록까지 사이트 관리자 전용으로 담당했다. 그런데 이 결정은 GitHub/Forgejo와 비교 검토한 결과가
// 아니라 `SsoAdminController`(사이트 전역 SSO IdP 설정)와 동일한 관리자 전용 패턴을 그대로 복사한
// 것이었다 — 실제로 GitHub(Settings > Developer settings > OAuth Apps)/Forgejo 둘 다 OAuth 앱
// 등록은 "사용자가 만드는 자원"으로서 사용자 계정에 종속된 셀프서비스 기능이다.
//
// 이후 이 컨트롤러의 역할을 "등록"에서 "사이트 전체 감사/오버사이트"로 축소했다 — 실제
// 등록/자기소유 삭제는 UserViewController(`/user/editform/oauth-apps-owned`)로 옮겼고, 여기는:
//  1. 사이트 전체에 등록된 앱(DCR 자동등록 + 사용자 셀프서비스 등록 전부)을 한눈에 조회 — 어떤
//     사용자가 등록했는지(ownerId → loginId)도 함께 보여준다. ownerId가 null이면 DCR/시스템 앱.
//  2. 사이트 관리자가 소유자와 무관하게 아무 앱이나 강제 삭제(override) — `checkAdmin`/
//     `isSiteManager`를 다른 곳에서도 전역 오버라이드 용도로 쓰는 것과 동일한 패턴.
// 등록 폼/POST .../register 라우트는 완전히 제거했다 — 관리자가 남의 앱을 대신 등록해줄 이유가 없다.
@Controller
@RequestMapping(value = ["/site/oauth-apps", "/sites/oauth-apps"])
class OAuthAppsAdminController(
    private val oAuthAppRegistrationService: OAuthAppRegistrationService,
    private val userRepository: UserRepository
) {

    // 관리자 감사 화면에서 앱 한 줄과 그 소유자 표시 정보를 함께 묶어 템플릿에 넘기기 위한 뷰
    // 모델. ownerLoginId가 null이면 DCR/시스템 소유(사람이 등록하지 않은 앱)라는 뜻이다.
    data class OAuthAppAdminRow(val app: OAuthRegisteredClient, val ownerLoginId: String?)

    private fun checkAdmin(authentication: Authentication?): User {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !loginUser.isSiteManager) {
            throw IllegalArgumentException("Unauthorized access")
        }
        return loginUser
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleUnauthorized(e: IllegalArgumentException): String {
        return "error/403"
    }

    @GetMapping
    fun view(authentication: Authentication?, model: Model): String {
        val currentUser = checkAdmin(authentication)
        model.addAttribute("currentUser", currentUser)

        val apps = oAuthAppRegistrationService.listAll()
        val ownerIds = apps.mapNotNull { it.ownerId }.distinct()
        val ownerLoginIdsById = if (ownerIds.isEmpty()) {
            emptyMap()
        } else {
            userRepository.findAllById(ownerIds).associate { it.id to it.loginId }
        }
        val rows = apps.map { app -> OAuthAppAdminRow(app, ownerLoginIdsById[app.ownerId]) }

        model.addAttribute("apps", rows)
        model.addAttribute("message", "title.siteSetting")
        return "site/oauth_apps"
    }

    // 사이트 관리자는 소유자가 누구든(본인이 아니어도, DCR 자동등록 앱이어도) 강제로 삭제할 수
    // 있다 — 이 저장소 전반의 "site manager global-override" 패턴(예: checkAdmin/isSiteManager로
    // 소유권 체크를 우회하는 다른 화면들)과 동일하다.
    @PostMapping("/{id}/delete")
    @Transactional
    fun delete(authentication: Authentication?, @PathVariable id: String): String {
        checkAdmin(authentication)
        val client = oAuthAppRegistrationService.findById(id) ?: return "redirect:/site/oauth-apps"

        oAuthAppRegistrationService.deleteClientAndRelatedRecords(client)
        return "redirect:/site/oauth-apps"
    }
}
