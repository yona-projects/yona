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
import dev.yona.domain.vcs.VcsCapability
import dev.yona.web.access.RequiresVcs
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
 * 떠난 잠금을 프로젝트 관리자가 강제로 풀게 한다. 설정을 고칠 수 있는 사용자(프로젝트 관리자)만 쓴다. 같은 주소(`/setting/lfs`)가 Mercurial이면 저장소 안 LFS(hg4j 내장)의 사용량과 파일 목록 화면이다(D-90, 잠금과 용량 제한은 Git LFS에만 있다).
 */
@Controller
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
    private val currentUserProvider: CurrentUserProvider,
    private val hgLfs: dev.yona.repository.hg.HgLfsStore
) {

    /** 잠금 목록의 한 줄이다. */
    data class LockRow(val id: Long, val path: String, val owner: String, val lockedText: String)

    /** Mercurial 화면의 LFS 파일 한 줄이다. */
    data class HgFileRow(val path: String, val sizeText: String, val oid: String)

    @GetMapping("/{user}/{project}/setting/lfs")
    @IsAllowed(Operation.UPDATE)
    @RequiresVcs(VcsCapability.LFS_SCREEN)
    @Transactional(readOnly = true)
    fun form(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound("error.notfound")
        // Mercurial은 큰 파일 본문을 저장소 안에 hg4j가 두므로 용량 제한과 잠금이 없다. 같은 주소에서 사용량과 파일 목록만 보인다(D-90)
        if (!dev.yona.domain.vcs.VcsCapabilities.supports(project.vcs, VcsCapability.LFS)) return hgForm(project)
        val usage = storage.usage(project); val quota = settings.lfsProjectQuota()
        val rows = locks.list(project, null, null, null, LfsLockService.MAX_LIMIT).locks.map { LockRow(requireNotNull(it.id), it.path.orEmpty(), it.ownerUserId?.let { id -> users.findById(id).orElse(null)?.loginId }.orEmpty(), helper.dateString(it.lockedDate)) }
        return ModelAndView("project/lfs").addObject("title", "project.lfs").addObject("projectView", views.of(project))
            .addObject("enabled", lfs.available(project)).addObject("objectCount", usage.objects).addObject("usageText", readable(usage.bytes))
            .addObject("quotaText", if (quota > 0) readable(quota) else null).addObject("maxObjectText", readable(settings.lfsMaxObjectSize())).addObject("locks", rows)
    }

    /** Mercurial의 LFS 화면이다(D-90): 저장 사용량, 기본 위치 머리의 LFS 파일 목록, 클라이언트 설정 안내. */
    private fun hgForm(project: Project): Any {
        val owner = requireNotNull(project.owner); val name = requireNotNull(project.name)
        val usage = hgLfs.usage(owner, name)
        val files = hgLfs.trackedFiles(owner, name).map { HgFileRow(it.path, readable(it.size), it.oid) }
        return ModelAndView("project/lfs-hg").addObject("title", "project.lfs.hg").addObject("projectView", views.of(project))
            .addObject("objectCount", usage.objects).addObject("usageText", readable(usage.bytes)).addObject("files", files)
    }

    /** 잠금을 강제로 풀고 화면으로 돌아간다. 이 프로젝트의 잠금이 아니거나 없으면 404이다. */
    @PostMapping("/{user}/{project}/setting/lfs/locks/{id}/delete")
    @IsAllowed(Operation.UPDATE)
    @RequiresVcs(VcsCapability.LFS)
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
