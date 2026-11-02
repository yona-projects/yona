package dev.yona.web.site

import dev.yona.domain.lfs.LfsStorage
import dev.yona.domain.site.SiteSettingService
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.SiteManagerOnly
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 사이트 관리의 "Git LFS" 설정이다(추가 개발 항목 X-8, `decisions.md` D-79 5번, 6번). LFS 사용 여부, 객체 하나의 최대 크기(MiB), 프로젝트별 용량 한도(MiB, 0이면 없음)를 정하고
 * 사이트 전체의 저장 사용량을 보인다. 사이트 관리자만 쓴다.
 */
@Controller
@SiteManagerOnly
@AnonymousCheck
class SiteLfsController(private val settings: SiteSettingService, private val storage: LfsStorage) {

    @GetMapping("/sites/lfs")
    fun form(): ModelAndView {
        val usage = storage.siteUsage()
        return ModelAndView("site/lfs").addObject("title", "site.sidebar.lfs").addObject("enabled", settings.lfsEnabled())
            .addObject("maxObjectMb", settings.lfsMaxObjectSize() / MIB).addObject("quotaMb", settings.lfsProjectQuota() / MIB)
            .addObject("objectCount", usage.objects).addObject("usageText", readable(usage.bytes))
    }

    /** 설정을 저장하고 화면으로 돌아간다. 최대 크기는 1 MiB 이상, 용량 한도는 0 이상의 정수여야 하고 아니면 400이다. 체크하지 않은 사용 여부는 끈다. */
    @PostMapping("/sites/lfs")
    fun save(request: HttpServletRequest, response: HttpServletResponse): Any {
        val maxObject = request.getParameter("maxObjectMb")?.trim()?.toLongOrNull()?.takeIf { it >= 1 } ?: return ErrorViews.badRequest()
        val quota = request.getParameter("quotaMb")?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return ErrorViews.badRequest()
        settings.setLfsEnabled(request.getParameter("enabled") == "true")
        settings.setLfsMaxObjectSize(maxObject * MIB); settings.setLfsProjectQuota(quota * MIB)
        return RedirectSupport.see(request, response, "/sites/lfs", mapOf(RedirectSupport.SUCCESS to "site.lfs.saved"))
    }

    private fun readable(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble(); var unit = -1
        while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit])
    }

    private companion object {
        const val MIB = 1024L * 1024
    }
}
