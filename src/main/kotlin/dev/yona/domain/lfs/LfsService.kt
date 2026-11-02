package dev.yona.domain.lfs

import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.site.SiteSettingService
import org.springframework.stereotype.Service

/** batch 요청의 객체 하나이다. */
data class LfsObjectRequest(val oid: String, val size: Long)

/** batch가 객체 하나에 내린 판단이다. */
sealed interface LfsPlan {
    val request: LfsObjectRequest

    /** 내려받을 수 있다. */
    data class Download(override val request: LfsObjectRequest) : LfsPlan

    /** 올려야 한다(서버에 없다). */
    data class Upload(override val request: LfsObjectRequest) : LfsPlan

    /** 올릴 필요가 없다(이미 있다). */
    data class Present(override val request: LfsObjectRequest) : LfsPlan

    /** 이 객체는 처리할 수 없다. [code]는 HTTP 상태와 같은 숫자이다. */
    data class Failure(override val request: LfsObjectRequest, val code: Int, val message: String) : LfsPlan
}

/**
 * LFS batch 판단과 한도를 맡는다(추가 개발 항목 X-8, `decisions.md` D-79). 프로젝트와 oid만 다루며 저장소 종류와 무관하다.
 * 내려받기는 있는 객체만, 올리기는 없는 객체만 동작을 준다. 객체 하나의 최대 크기와 프로젝트의 용량 한도를 넘는 올리기는 그 객체만 413 오류이다.
 */
@Service
class LfsService(private val storage: LfsStorage, private val settings: SiteSettingService, private val projects: ProjectRepository) {

    /** LFS를 쓸 수 있는 프로젝트이면 `true`이다(사이트 설정이 켜져 있고 저장소 종류가 지원된다). */
    fun available(project: Project): Boolean = settings.lfsEnabled() && LfsSupport.supports(project)

    /**
     * [oid] 객체가 있는 프로젝트를 찾는다. 자기 저장소를 먼저 보고, 없으면 포크의 원본, 그 원본의 원본 순으로 올라가며 읽기로 빌려 쓴다(D-79 4번). 어디에도 없으면 `null`이다.
     * 순환이나 지나치게 깊은 연결은 [MAX_FORK_DEPTH]에서 멈춘다.
     */
    fun locate(project: Project, oid: String): Project? {
        var current: Project? = project
        var depth = 0
        while (current != null && depth <= MAX_FORK_DEPTH) {
            if (storage.exists(current, oid)) return current
            current = current.originalProject
            depth++
        }
        return null
    }

    /** [oid] 객체의 크기이다. 자기 저장소나 원본 쪽에서 찾고 없으면 `null`이다. */
    fun sizeOf(project: Project, oid: String): Long? = locate(project, oid)?.let { storage.sizeOf(it, oid) }

    /** 원본 프로젝트를 지우기 전에 부른다. 이 프로젝트를 원본으로 둔 포크마다 이 프로젝트의 객체를 포크로 가져가게 한다(D-79 4번). */
    fun adoptForForks(project: Project) {
        val id = project.id ?: return
        projects.findByOriginalProjectId(id).forEach { storage.adopt(project, it) }
    }

    /** [operation](`download` 또는 `upload`)의 [objects]를 판단한다. 같은 batch 안의 올리기는 용량 한도에 누적해서 센다. */
    fun plan(project: Project, operation: String, objects: List<LfsObjectRequest>): List<LfsPlan> {
        val maxObject = settings.lfsMaxObjectSize()
        val quota = settings.lfsProjectQuota()
        var committed = if (operation == UPLOAD && quota > 0) storage.usage(project).bytes else 0L
        return objects.map { item ->
            when {
                !LfsStorage.validOid(item.oid) -> LfsPlan.Failure(item, 422, "Invalid oid")
                item.size < 0 -> LfsPlan.Failure(item, 422, "Invalid size")
                operation == DOWNLOAD -> {
                    val stored = sizeOf(project, item.oid)
                    when {
                        stored == null -> LfsPlan.Failure(item, 404, "Object does not exist")
                        stored != item.size -> LfsPlan.Failure(item, 422, "Object size does not match")
                        else -> LfsPlan.Download(item)
                    }
                }
                locate(project, item.oid) != null -> LfsPlan.Present(item)
                item.size > maxObject -> LfsPlan.Failure(item, 413, "Object exceeds the maximum size")
                quota > 0 && committed + item.size > quota -> LfsPlan.Failure(item, 413, "Project exceeds the LFS storage quota")
                else -> { committed += item.size; LfsPlan.Upload(item) }
            }
        }
    }

    /** 올릴 수 있는 최대 바이트이다. 객체 크기 한도와 남은 프로젝트 용량 가운데 작은 값이다. */
    fun maxUploadBytes(project: Project): Long {
        val quota = settings.lfsProjectQuota()
        val maxObject = settings.lfsMaxObjectSize()
        return if (quota > 0) minOf(maxObject, (quota - storage.usage(project).bytes).coerceAtLeast(0)) else maxObject
    }

    companion object {
        /** 포크 연결을 따라 올라가는 최대 깊이이다. */
        const val MAX_FORK_DEPTH = 16
        const val DOWNLOAD = "download"
        const val UPLOAD = "upload"
    }
}
