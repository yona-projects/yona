package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserSettingRepository
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.support.YonaUpdateService
import com.github.yonaprojects.yona.config.TemplateHelper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class GlobalModelAttributeAdvice(
    private val userRepository: UserRepository,
    private val markdownService: MarkdownService,
    private val templateHelper: TemplateHelper,
    private val yonaUpdateService: YonaUpdateService,
    private val issueRepository: IssueRepository,
    private val userSettingRepository: UserSettingRepository,
    // yona application.conf의 "application.sendYonaUsage"(Application.SEND_YONA_USAGE) 대응.
    @Value("\${yona.analytics.send-usage:false}") private val sendYonaUsage: Boolean,
    // yona controllers/Application.java:35 HIDE_PROJECT_LISTING 대응 (P0-23). 기존 컨트롤러들과 동일 키 재사용.
    @Value("\${yona.application.hide-project-listing:false}") private val hideProjectListing: Boolean,
    // yona controllers/Application.java:42-43 NAVBAR_CUSTOM_LINK_NAME/URL 대응 — common/usermenu.scala.html:80-82. [GL-controllers_Application-007]
    @Value("\${yona.application.navbar.custom-link.name:}") private val navbarCustomLinkName: String,
    @Value("\${yona.application.navbar.custom-link.url:}") private val navbarCustomLinkUrl: String,
    // yona controllers/UserApp.java:79-80 useSocialLoginOnly("application.use.social.login.only") 대응 [GL-controllers_UserApp-011]
    // (P-템플릿 #15) — true면 로그인/회원가입/loginDialog에서 아이디·비밀번호 폼을 숨기고 소셜 로그인만 노출.
    @Value("\${yona.application.use-social-login-only:false}") private val useSocialLoginOnly: Boolean
) {

    @ModelAttribute("currentUser")
    fun currentUser(): User? {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication.name == "anonymousUser") {
            return null
        }
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    @ModelAttribute("markdownService")
    fun markdownService(): MarkdownService {
        return markdownService
    }

    @ModelAttribute("templateHelper")
    fun templateHelper(): TemplateHelper {
        return templateHelper
    }

    @ModelAttribute("yonaUpdateService")
    fun yonaUpdateService(): YonaUpdateService {
        return yonaUpdateService
    }

    @ModelAttribute("currentRequestPath")
    fun currentRequestPath(request: HttpServletRequest): String {
        return request.requestURI
    }

    @ModelAttribute("sendYonaUsage")
    fun sendYonaUsage(): Boolean {
        return sendYonaUsage
    }

    @ModelAttribute("hideProjectListing")
    fun hideProjectListing(): Boolean {
        return hideProjectListing
    }

    // yona Issue.countOpenIssuesByUser(User) 대응.
    @ModelAttribute("myOpenIssueCount")
    fun myOpenIssueCount(): Long {
        val user = currentUser() ?: return 0
        return issueRepository.countByAssigneeAndState(user.id!!, State.OPEN)
    }

    // yona UserSetting.findByUser(id).loginDefaultPage 대응.
    @ModelAttribute("loginDefaultPage")
    fun loginDefaultPage(): String? {
        val user = currentUser() ?: return null
        return userSettingRepository.findByUserId(user.id!!).orElse(null)?.loginDefaultPage
    }

    @ModelAttribute("navbarCustomLinkName")
    fun navbarCustomLinkName(): String = navbarCustomLinkName

    @ModelAttribute("navbarCustomLinkUrl")
    fun navbarCustomLinkUrl(): String = navbarCustomLinkUrl

    @ModelAttribute("useSocialLoginOnly")
    fun useSocialLoginOnly(): Boolean = useSocialLoginOnly
}
