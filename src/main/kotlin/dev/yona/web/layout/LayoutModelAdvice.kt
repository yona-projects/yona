package dev.yona.web.layout

import dev.yona.security.SessionPermissions
import dev.yona.config.YonaProperties
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.user.UserSettingRepository
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.servlet.support.RequestContextUtils
import dev.yona.web.RedirectSupport
import dev.yona.web.TemplateHelper

/**
 * 모든 화면 템플릿이 공통으로 쓰는 모델 값을 만든다.
 *
 * | 모델 이름 | 내용 | v1.6의 대응 |
 * |---|---|---|
 * | `currentUser` | 현재 사용자 뷰 | `UserApp.currentUser()` |
 * | `site` | 사이트 설정 | `Application.*` 상수, `utils.Config` |
 * | `flashMessages` | 이전 요청이 남긴 일회성 메시지(번역 끝남) | `Http.Context.flash()` |
 * | `siteAdminLoggedIn` | 기본 관리자 계정으로 로그인했는지 | `UserApp.isSiteAdminLoggedInSession()` |
 * | `helper` | 표시 도우미(상대 시간, 날짜, 복수형) | `utils.TemplateHelper` |
 * | `requestPath` | 요청 경로 | `request().path()` |
 * | `requestUri` | 쿼리 문자열을 포함한 요청 주소 | `requestHeader.uri` |
 * | `isMac` | `User-Agent`에 `Macintosh`가 있는지 (단축키 안내가 쓴다) | `keymap`의 `isMac` |
 *
 * 컨트롤러가 그린 화면은 [LayoutModelAdvice]가, 예외 처리기가 그린 오류 화면은 [dev.yona.web.error.GlobalErrorHandler]가
 * 이 값을 채운다. `@ControllerAdvice`의 `@ModelAttribute`는 예외 처리기에는 적용되지 않기 때문이다.
 */
@Component
class LayoutModelFactory(
    private val currentUserProvider: CurrentUserProvider,
    private val properties: YonaProperties,
    private val permissions: SessionPermissions,
    private val issues: IssueRepository,
    private val avatars: AvatarService,
    private val userSettings: UserSettingRepository,
    private val flashTranslator: FlashMessageTranslator,
    private val templateHelper: dev.yona.web.TemplateHelper,
    private val messages: org.springframework.context.MessageSource,
    private val updates: dev.yona.domain.site.UpdateCheckService,
    private val socialProviders: dev.yona.domain.social.SocialProviders,
    private val socialSession: dev.yona.web.social.SocialSession
) {

    /** 업데이트 알림이다. [version]은 새 버전, [url]은 릴리스 페이지이다. */
    /** 로그인 화면의 소셜 로그인 버튼이다. [html]은 버튼 안에 넣을 로고와 문구이다. */
    data class SocialButton(val key: String, val html: String)

    data class UpdateNotice(val version: String, val url: String)

    /** 새 버전이 있고 알림을 끄지 않았으며 사이트 관리자일 때만 알림을 만든다 (v1.6 `partial_update_notification`). */
    private fun updateNotice(): UpdateNotice? {
        val version = updates.versionToUpdate
        if (version == null || !updates.shouldNotify()) return null
        val userId = currentUserProvider.get().user?.id ?: return null
        return if (permissions.siteManager(userId)) UpdateNotice(version, updates.releaseUrl(version)) else null
    }

    fun attributes(request: HttpServletRequest): Map<String, Any> = listOfNotNull<Pair<String, Any>>(
        updates.versionToUpdate?.let { "versionToUpdate" to it },
        updateNotice()?.let { "updateNotice" to it }
    ).toMap() + mapOf(
        "currentUser" to ViewerContext(
            currentUserProvider.get(), permissions, issues, avatars, userSettings,
            messages.getMessage("user.notExists.name", null, "user.notExists.name", org.springframework.context.i18n.LocaleContextHolder.getLocale()).orEmpty()
        ),
        "site" to properties,
        "siteAdminLoggedIn" to (currentUserProvider.get().user?.loginId == DEFAULT_ADMIN_LOGIN_ID),
        "flashMessages" to flashTranslator.translate(RequestContextUtils.getInputFlashMap(request).orEmpty()),
        "helper" to templateHelper,
        "socialProviders" to socialProviders.available().map { SocialButton(it, dev.yona.web.ProviderLogos.signIn(it, properties.social.githubName, request.contextPath)) },
        "socialLoginSupported" to socialProviders.available().isNotEmpty(),
        "socialLoggedIn" to socialSession.hasKeys(request),
        "requestPath" to request.requestURI,
        "isMac" to (request.getHeader("User-Agent")?.contains("Macintosh") == true),
        "requestUri" to listOfNotNull(request.requestURI, request.queryString).joinToString("?")
    )

    private companion object {
        /** 설치 직후 기본 관리자 계정의 로그인 ID이다 (v1.6 `SiteAdmin.SITEADMIN_DEFAULT_LOGINID`). */
        const val DEFAULT_ADMIN_LOGIN_ID = "admin"
    }
}

/** 컨트롤러가 화면을 그릴 때 [LayoutModelFactory]의 값을 모델에 채운다. */
@ControllerAdvice(annotations = [Controller::class])
class LayoutModelAdvice(private val layoutModelFactory: LayoutModelFactory) {

    @ModelAttribute
    fun layout(model: Model, request: HttpServletRequest) {
        model.addAllAttributes(layoutModelFactory.attributes(request))
    }
}

/**
 * 화면 맨 아래 스크립트가 알림으로 보여 줄 일회성 메시지이다. 번역은 끝난 상태이다.
 *
 * @property notices 짧게 나타났다 사라지는 알림(`$yobi.notify`)
 * @property alertTitle 있으면 확인 대화상자(`$yobi.alert`)로 보여 줄 제목
 * @property alertDescription 확인 대화상자의 설명
 */
data class FlashMessages(val notices: List<String>, val alertTitle: String?, val alertDescription: String)

/**
 * 일회성(flash) 메시지를 화면에 보일 문구로 바꾼다.
 *
 * v1.6 `common/scripts.scala.html`은 flash의 값을 메시지 키로 보고 번역한 뒤 HTML 이스케이프한다.
 * 키 `title`이 있으면 확인 대화상자(`$yobi.alert`)로, 나머지는 알림(`$yobi.notify`)으로 보여 준다.
 */
@Component
class FlashMessageTranslator(private val messages: MessageSource) {

    fun translate(flash: Map<String, Any?>): FlashMessages {
        val locale = LocaleContextHolder.getLocale()
        // v1.6 템플릿은 `@Messages(...)`의 출력을 HTML 이스케이프한다. 같은 결과를 내려고 미리 이스케이프한다.
        fun text(value: Any?): String =
            value?.toString()?.let { TemplateHelper.escapeHtml(messages.getMessage(it, null, it, locale) ?: it) } ?: ""
        // v1.6의 flash는 쿠키 하나라 넣은 순서가 그대로 보인다. 순서 정보가 있으면 그 순서를 따른다.
        val order = (flash[RedirectSupport.ORDER] as? String)?.split('\u0001').orEmpty()
        val keys = flash.keys.filter { it != TITLE && it != DESCRIPTION && it != RedirectSupport.ORDER }
            .sortedBy { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        return FlashMessages(
            notices = keys.map { text(flash[it]) },
            alertTitle = flash[TITLE]?.let { text(it) },
            alertDescription = text(flash[DESCRIPTION])
        )
    }

    private companion object {
        const val TITLE = "title"
        const val DESCRIPTION = "description"
    }
}
