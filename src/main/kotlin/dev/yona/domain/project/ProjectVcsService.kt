package dev.yona.domain.project

import dev.yona.domain.board.PostingRepository
import dev.yona.repository.ProjectReadmeService
import dev.yona.repository.RepositoryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 프로젝트의 저장소 종류를 바꾼다. v1.6 `ProjectApp.changeVCS`와 `Project.changeVCS`에 해당한다.
 *
 * v1.6 `changeVCS`는 트랜잭션이 없고 기존 저장소를 먼저 지웠다. 새 저장소 생성에 실패하면 README 표시 해제와 포크 분리가 남고
 * 기존 저장소도 사라졌다([refactoring-backlog.md] R-74). 지금은 변경을 한 트랜잭션으로 묶고, 종류별 경로가 달라 새 저장소를 먼저 만든다.
 * DB 커밋 뒤 기존 저장소를 지우고 롤백하면 새 저장소를 지운다. 커밋 뒤 삭제에 실패하면 오류를 기록하고 기존 디렉터리는 남는다.
 */
@Service
class ProjectVcsService(
    private val projects: ProjectRepository,
    private val postings: PostingRepository,
    private val postingService: dev.yona.domain.board.PostingService,
    private val readme: ProjectReadmeService,
    private val repositories: RepositoryService,
    private val audit: dev.yona.domain.audit.AuditRecorder,
    private val backends: dev.yona.repository.VcsBackends
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 저장소를 만들 수 있는 종류 전부이다. 화면은 이 전부를 보여 주고 지금 종류는 "현재"로 막는다. */
    fun types(): List<String> = backends.creatableTypes()

    /**
     * [project]의 저장소 종류로 바꿀 수 있는 종류이다: 저장소를 만들 수 있게 등록된 종류에서 지금 종류를 뺀 것이다. 둘뿐일 때는 하나이고
     * (v1.6처럼 번갈아 바꾼다), 종류가 셋 이상이면 화면이 고르게 한다(Mercurial 도입을 위한 공통화 4단계, D-86 6번).
     */
    fun targets(project: Project): List<String> = backends.creatableTypes().filter { it != project.vcs }

    /**
     * [project]의 저장소 종류를 [target]으로 바꾼다. [target]을 주지 않으면 v1.6처럼 번갈아 바꾼다([Project.nextVcs]): Git이면 Subversion, 아니면 Git이다(v1.6 `changeVCS`).
     *
     * SVNKit과 JGit이 던지는 체크 예외에도 DB 변경을 되돌린다.
     *
     * @throws IllegalArgumentException [target]이 지금 종류이거나 [targets]에 없을 때. 아무것도 바꾸지 않는다
     */
    @Transactional(rollbackFor = [Exception::class])
    fun change(project: Project, target: String? = null) {
        val chosen = if (target == null) project.nextVcs() else target.also { require(it in targets(project)) { "바꿀 수 없는 저장소 종류입니다: $it" } }
        val projectId = requireNotNull(project.id)
        if (readme.read(project) != null) {
            postings.findFirstByProjectIdAndReadmeTrue(projectId)?.let { postingService.unmarkReadmeOnVcsChange(it) }
        }

        projects.findByOriginalProjectId(projectId).forEach {
            it.originalProject = null
            projects.save(it)
        }

        val previous = project.vcs
        val owner = requireNotNull(project.owner) { "프로젝트의 소유자가 없습니다." }
        val name = requireNotNull(project.name) { "프로젝트의 이름이 없습니다." }
        project.vcs = chosen
        try {
            repositories.createRepository(project)
        } catch (e: Exception) {
            project.vcs = previous
            throw e
        }
        val synchronized = TransactionSynchronizationManager.isSynchronizationActive()
        if (synchronized) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                private fun delete(type: String?) {
                    try {
                        repositories.deleteRepository(type, owner, name)
                    } catch (e: Exception) {
                        log.error("저장소 삭제 실패: {}/{} ({})", owner, name, type, e)
                    }
                }

                override fun afterCommit() = delete(previous)

                override fun afterCompletion(status: Int) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) delete(chosen)
                }
            })
        }
        projects.save(project)
        if (!synchronized) repositories.deleteRepository(previous, owner, name)
        audit.record(dev.yona.domain.audit.auditProject("project.vcs_changed", project, mapOf("from" to previous, "to" to project.vcs)))
    }
}
