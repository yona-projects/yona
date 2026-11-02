package dev.yona.domain.lfs

import dev.yona.domain.audit.AuditActor
import dev.yona.domain.audit.AuditActorType
import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PersistenceContext
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.Date

/**
 * LFS 파일 잠금이다(추가 개발 항목 X-8, `decisions.md` D-79 2번). 프로젝트와 경로가 유일하다. 경로는 길 수 있어 인덱스에는 SHA-256 해시([pathHash])를 쓴다
 * (데이터베이스마다 인덱스 키 길이 제한이 달라서이다).
 *
 * @property projectId 프로젝트 번호. 개명과 이전에 영향이 없다
 * @property path 잠근 파일의 저장소 안 경로
 * @property ownerUserId 잠근 사용자 번호
 * @property refName 잠글 때 알린 참조 이름(선택)
 */
@Entity
@Table(
    name = "lfs_lock",
    indexes = [Index(name = "ix_lfs_lock_owner", columnList = "owner_user_id")],
    uniqueConstraints = [UniqueConstraint(name = "uq_lfs_lock_path", columnNames = ["project_id", "path_hash"])]
)
class LfsLock(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "project_id")
    var projectId: Long? = null,
    @Column(length = 2000)
    var path: String? = null,
    @Column(name = "path_hash", length = 64)
    var pathHash: String? = null,
    @Column(name = "owner_user_id")
    var ownerUserId: Long? = null,
    @Column(name = "ref_name", length = 255)
    var refName: String? = null,
    @Column(name = "locked_date")
    var lockedDate: Date? = null
)

/** [LfsLock] 저장소이다. */
interface LfsLockRepository : JpaRepository<LfsLock, Long> {

    fun findByProjectIdAndPathHash(projectId: Long, pathHash: String): LfsLock?

    fun findByIdAndProjectId(id: Long, projectId: Long): LfsLock?

    /**
     * 번호가 [after]보다 큰 잠금을 번호 순으로 가져온다. [path]가 빈 문자열이면 경로로 거르지 않고, [id]가 0이면 번호로 거르지 않는다
     * (널 매개변수는 데이터베이스마다 자료형 추론이 달라 쓰지 않는다).
     */
    @Query("select l from LfsLock l where l.projectId = :projectId and l.id > :after and (:path = '' or l.path = :path) and (:id = 0 or l.id = :id) order by l.id asc")
    fun page(@Param("projectId") projectId: Long, @Param("after") after: Long, @Param("path") path: String, @Param("id") id: Long, pageable: org.springframework.data.domain.Pageable): List<LfsLock>

    @Modifying
    @Query("delete from LfsLock l where l.ownerUserId = :userId")
    fun deleteByOwner(@Param("userId") userId: Long): Int

    @Modifying
    @Query("delete from LfsLock l where l.projectId = :projectId")
    fun deleteByProject(@Param("projectId") projectId: Long): Int
}

/**
 * LFS 파일 잠금을 만들고, 찾고, 푼다(X-8 단계 3). 규칙은 규격(`git-lfs`의 파일 잠금 API)을 따른다: 이미 잠긴 경로는 만들 수 없고(기존 잠금을 알려 준다),
 * 푸는 것은 잠근 사람이며 `force`는 [unlock]을 부르는 쪽이 허용한 사람([mayForce], 프로젝트 관리자)만 한다. 잠그고 푸는 일은 감사 기록을 남긴다.
 */
@Service
class LfsLockService(private val locks: LfsLockRepository, private val audit: AuditRecorder) {

    sealed interface CreateResult
    data class Created(val lock: LfsLock) : CreateResult
    data class Conflict(val existing: LfsLock) : CreateResult
    data class Invalid(val message: String) : CreateResult

    sealed interface UnlockResult
    data class Released(val lock: LfsLock) : UnlockResult
    data object NotFound : UnlockResult
    data object Forbidden : UnlockResult

    /** 잠금 목록의 한 쪽이다. [nextCursor]가 `null`이면 마지막 쪽이다. */
    data class LockPage(val locks: List<LfsLock>, val nextCursor: String?)

    /** `verify`의 결과이다. [ours]는 호출한 사용자의 잠금, [theirs]는 남의 잠금이다. */
    data class Verification(val ours: List<LfsLock>, val theirs: List<LfsLock>, val nextCursor: String?)

    @PersistenceContext
    private lateinit var em: EntityManager

    /** [project]의 [rawPath] 파일을 [user]가 잠근다. */
    @Transactional
    fun create(project: Project, user: User, rawPath: String, refName: String?): CreateResult {
        val path = normalize(rawPath) ?: return Invalid("Invalid path")
        val projectId = requireNotNull(project.id); val hash = hashOf(path)
        locks.findByProjectIdAndPathHash(projectId, hash)?.let { return Conflict(it) }
        val saved = locks.saveAndFlush(LfsLock(projectId = projectId, path = path, pathHash = hash, ownerUserId = user.id, refName = refName?.take(255), lockedDate = Date()))
        audit.record(event("lfs.lock_created", project, user, saved, mapOf("path" to path)))
        return Created(saved)
    }

    /** 잠금 [id]를 푼다. 잠근 사람이 아니면 [force]이고 [mayForce]일 때만 풀린다. */
    @Transactional
    fun unlock(project: Project, user: User, id: Long, force: Boolean, mayForce: Boolean): UnlockResult {
        val lock = locks.findByIdAndProjectId(id, requireNotNull(project.id)) ?: return NotFound
        val own = lock.ownerUserId == user.id
        if (!own && !(force && mayForce)) return Forbidden
        locks.delete(lock); locks.flush()
        audit.record(event("lfs.lock_released", project, user, lock, mapOf("path" to lock.path, "force" to (!own), "owner_id" to lock.ownerUserId)))
        return Released(lock)
    }

    /** 잠금 목록이다. [path]나 [id]로 거르고, [cursor](앞 쪽의 마지막 번호)부터 [limit]개를 돌려준다. */
    @Transactional(readOnly = true)
    fun list(project: Project, path: String?, id: Long?, cursor: String?, limit: Int): LockPage {
        val projectId = requireNotNull(project.id)
        val size = limit.coerceIn(1, MAX_LIMIT)
        val found = locks.page(projectId, cursor?.toLongOrNull() ?: 0L, path.orEmpty(), id ?: 0L, org.springframework.data.domain.PageRequest.ofSize(size + 1))
        val pageItems = found.take(size)
        return LockPage(pageItems, if (found.size > size) pageItems.last().id.toString() else null)
    }

    /** 푸시 전에 클라이언트가 부르는 `verify`이다. 잠금을 호출한 [user]의 것과 남의 것으로 나눈다. */
    @Transactional(readOnly = true)
    fun verify(project: Project, user: User, cursor: String?, limit: Int): Verification {
        val page = list(project, null, null, cursor, limit)
        val (ours, theirs) = page.locks.partition { it.ownerUserId == user.id }
        return Verification(ours, theirs, page.nextCursor)
    }

    /** 사용자의 잠금을 모두 지운다(사용자 삭제 처리). */
    @Transactional
    fun deleteOfUser(user: User) { locks.deleteByOwner(requireNotNull(user.id)) }

    /** 프로젝트의 잠금을 모두 지운다(프로젝트 삭제 처리). */
    @Transactional
    fun deleteOfProject(project: Project) { locks.deleteByProject(requireNotNull(project.id)) }

    private fun event(action: String, project: Project, user: User, lock: LfsLock, details: Map<String, Any?>) = AuditEvent(
        action, targetType = "lfs_lock", targetId = lock.id?.toString(), targetLabel = lock.path?.take(255), organizationId = project.organization?.id, projectId = project.id, details = details,
        actor = AuditActor(AuditActorType.USER, user.id, user.loginId)
    )

    /** 경로를 정리한다. 비었거나, 절대 경로이거나, `..` 구간이 있거나, 너무 길면 `null`이다. 앞뒤 공백과 `\`는 정리한다. */
    private fun normalize(raw: String): String? {
        val path = raw.trim().replace('\\', '/')
        if (path.isEmpty() || path.length > MAX_PATH || path.startsWith("/")) return null
        if (path.split('/').any { it == ".." }) return null
        return path
    }

    private fun hashOf(path: String) = MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_LIMIT = 100
        const val MAX_PATH = 1000
    }
}
