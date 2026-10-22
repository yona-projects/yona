package dev.yona.web.layout

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
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.servlet.support.RequestContextUtils
import org.springframework.web.util.HtmlUtils

/**
 * 모든 화면 템플릿이 공통으로 쓰는 모델 값을 채운다.
 *
 * | 모델 이름 | 내용 | v1.6의 대응 |
 * |---|---|---|
 * | `currentUser` | 현재 사용자 뷰 | `UserApp.currentUser()` |
 * | `site` | 사이트 설정 | `Application.*` 상수, `utils.Config` |
 * | `flash` | 이전 요청이 남긴 일회성 메시지(번역 전 키) | `Http.Context.flash()` |
 * | `siteAdminLoggedIn` | 기본 관리자 계정으로 로그인했는지 | `UserApp.isSiteAdminLoggedInSession()` |
 */
@ControllerAdvice(annotations = [Controller::class])
class LayoutModelAdvice(
    private val currentUserProvider: CurrentUserProvider,
    private val properties: YonaProperties,
    private val siteAdmins: SiteAdminRepository,
    private val issues: IssueRepository,
    private val avatars: AvatarService,
    private val organizationUsers: OrganizationUserRepository,
    private val userSettings: UserSettingRepository,
    private val flashTranslator: FlashMessageTranslator
) {

    @ModelAttribute("currentUser")
    fun currentUser(): ViewerContext = ViewerContext(currentUserProvider.get(), siteAdmins, issues, avatars, organizationUsers, userSettings)

    @ModelAttribute("site")
    fun site(): YonaProperties = properties

    @ModelAttribute("siteAdminLoggedIn")
    fun siteAdminLoggedIn(): Boolean = currentUserProvider.get().user?.loginId == DEFAULT_ADMIN_LOGIN_ID

    @ModelAttribute("flashMessages")
    fun flashMessages(request: HttpServletRequest): FlashMessages =
        flashTranslator.translate(RequestContextUtils.getInputFlashMap(request).orEmpty())

    /** 현재 요청의 경로이다. 문서 머리의 `og:url` 등에 쓴다 (v1.6 `request().path()`). */
    @ModelAttribute("requestPath")
    fun requestPath(request: HttpServletRequest): String = request.requestURI

    private companion object {
        /** 설치 직후 기본 관리자 계정의 로그인 ID이다 (v1.6 `SiteAdmin.SITEADMIN_DEFAULT_LOGINID`). */
        const val DEFAULT_ADMIN_LOGIN_ID = "admin"
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
            value?.toString()?.let { HtmlUtils.htmlEscape(messages.getMessage(it, null, it, locale) ?: it) } ?: ""
        return FlashMessages(
            notices = flash.filterKeys { it != TITLE && it != DESCRIPTION }.values.map { text(it) },
            alertTitle = flash[TITLE]?.let { text(it) },
            alertDescription = text(flash[DESCRIPTION])
        )
    }

    private companion object {
        const val TITLE = "title"
        const val DESCRIPTION = "description"
    }
}
