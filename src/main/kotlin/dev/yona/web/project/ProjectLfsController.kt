package dev.yona.web.project

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.lfs.LfsLockService
import dev.yona.domain.lfs.LfsService
import dev.yona.domain.lfs.LfsStorage
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsOnlyGitAvailable
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 프로젝트 설정의 "Git LFS" 화면이다(추가 개발 항목 X-8, `decisions.md` D-79). LFS 저장 사용량(객체 수와 용량, 사이트의 한도)과 현재 잠금 목록을 보이고, 잠근 사람이
 * 떠난 잠금을 프로젝트 관리자가 강제로 풀게 한다. 설정을 고칠 수 있는 사용자(프로젝트 관리자)만 쓰고 Git 프로젝트에서만 동작한다.
 */
@Controller
@IsOnlyGitAvailable
@AnonymousCheck
class ProjectLfsController(
    private val projectService: ProjectService,
    private val storage: LfsStorage,
    private val locks: LfsLockService,
    private val lfs: LfsService,
    private val settings: SiteSettingService,
    private val users: UserRepository,
    private val views: ProjectViewFactory,
    private val helper: TemplateHelper,
    private val currentUserProvider: CurrentUserProvider
) {

    /** 잠금 목록의 한 줄이다. */
    data class LockRow(val id: Long, val path: String, val owner: String, val lockedText: String)

    @GetMapping("/{user}/{project}/setting/lfs")
    @IsAllowed(Operation.UPDATE)
    @Transactional(readOnly = true)
    fun form(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        val usage = storage.usage(project); val quota = settings.lfsProjectQuota()
        val rows = locks.list(project, null, null, null, LfsLockService.MAX_LIMIT).locks.map { LockRow(requireNotNull(it.id), it.path.orEmpty(), it.ownerUserId?.let { id -> users.findById(id).orElse(null)?.loginId }.orEmpty(), helper.dateString(it.lockedDate)) }
        return ModelAndView("project/lfs").addObject("title", "project.lfs").addObject("projectView", views.of(project))
            .addObject("enabled", lfs.available(project)).addObject("objectCount", usage.objects).addObject("usageText", readable(usage.bytes))
            .addObject("quotaText", if (quota > 0) readable(quota) else null).addObject("maxObjectText", readable(settings.lfsMaxObjectSize())).addObject("locks", rows)
    }

    /** 잠금을 강제로 풀고 화면으로 돌아간다. 이 프로젝트의 잠금이 아니거나 없으면 404이다. */
    @PostMapping("/{user}/{project}/setting/lfs/locks/{id}/delete")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun release(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        val actor = currentUserProvider.get().user?.id?.let { users.findById(it).orElse(null) } ?: return ErrorViews.forbidden()
        return when (locks.unlock(project, actor, id, force = true, mayForce = true)) {
            is LfsLockService.Released -> RedirectSupport.see(request, response, url(project), mapOf(RedirectSupport.SUCCESS to "lfs.lock.released"))
            else -> ErrorViews.notFound("error.notfound")
        }
    }

    private fun url(project: Project) = "/${project.owner}/${project.name}/setting/lfs"

    private fun readable(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble(); var unit = -1
        while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit])
    }
}
