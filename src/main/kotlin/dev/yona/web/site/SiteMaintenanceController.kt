package dev.yona.web.site

import dev.yona.config.YonaProperties
import dev.yona.domain.site.DiagnosticService
import dev.yona.domain.site.UpdateCheckService
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.SiteManagerOnly
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 사이트 관리의 업데이트 확인과 진단이다. v1.6 `SiteApp.update`, `unwatchUpdate`, `diagnose`(`app/controllers/SiteApp.java:318-350`)에 해당한다.
 * 사이트 관리자만 쓴다.
 */
@Controller
// v1.6 SiteApp: @With(SiteManagerAuthAction) 다음 @AnonymousCheck 순서라 익명은 관리자 검사의 403을 먼저 받는다
@SiteManagerOnly
@AnonymousCheck
class SiteMaintenanceController(
    private val updates: UpdateCheckService,
    private val diagnostics: DiagnosticService,
    private val properties: YonaProperties
) {

    /**
     * 업데이트 확인 화면이다. 확인을 켜 두었으면(`yona.update.check`) 요청 안에서 저장소를 읽어 새 버전을 찾고, 읽지 못하면 오류를 화면에 보인다.
     * 꺼 두었으면 현재 버전을 알리지 않고 백그라운드가 마지막에 찾은 값만 보여 준다.
     */
    @GetMapping("/sites/update")
    fun update(): ModelAndView {
        var currentVersion: String? = null
        var failure: Exception? = null
        if (properties.update.check) {
            currentVersion = updates.currentVersion
            try {
                updates.refresh()
            } catch (e: Exception) {
                failure = e
            }
        }
        val version = updates.versionToUpdate
        return ModelAndView("site/update")
            .addObject("title", "title.siteSetting")
            .addObject("currentVersion", currentVersion)
            .addObject("noticeOn", updates.noticeOn)
            .addObject("versionToUpdate", version)
            .addObject("releaseUrl", version?.let { updates.releaseUrl(it) })
            .addObject("failure", failure?.stackTraceToString())
    }

    /** 새 버전 알림을 다시 켠다. 업데이트 화면의 "알림 켜기" 폼이 보내고, 끝나면 업데이트 화면으로 돌아간다(site-F6). */
    @PostMapping("/sites/watchUpdate")
    fun watchUpdate(): ResponseEntity<Void> {
        updates.watch()
        return ResponseEntity.status(org.springframework.http.HttpStatus.SEE_OTHER).header(org.springframework.http.HttpHeaders.LOCATION, "/sites/update").build()
    }

    /** 새 버전 알림을 끈다. 서버 전체에 적용되고 서버를 다시 시작해도 꺼져 있다(다시 켜려면 업데이트 화면의 "알림 켜기", v1.6은 다시 시작하면 켜졌다). */
    @PostMapping("/sites/unwatchUpdate")
    fun unwatchUpdate(): ResponseEntity<Void> {
        updates.unwatch()
        return ResponseEntity.ok().build()
    }

    @GetMapping("/sites/diagnostic")
    fun diagnose(): ModelAndView = ModelAndView("site/diagnostic")
        .addObject("title", "title.siteSetting")
        .addObject("errors", diagnostics.checkAll())
}
