package dev.yona.domain.project

import dev.yona.domain.board.PostingRepository
import dev.yona.repository.ProjectReadmeService
import dev.yona.repository.RepositoryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 프로젝트의 저장소 종류를 바꾼다. v1.6 `ProjectApp.changeVCS`와 `Project.changeVCS`에 해당한다.
 *
 * 순서: README 게시글 표시를 끄고(저장소에 README가 있을 때) -> 포크의 원본 연결을 끊고 -> 새 종류의 저장소를 만들고 -> 종류를 저장하고 -> 기존
 * 저장소를 지운다. v1.6 `changeVCS`는 트랜잭션이 없고 기존 저장소를 먼저 지워서, 새 저장소 생성이 실패하면 저장한 README 표시 해제와 포크 분리가 남고
 * 기존 저장소도 돌아오지 않았다([refactoring-backlog.md] R-74). 지금은 변경을 트랜잭션으로 묶어 DB 변경이 함께 되돌아가고, 되돌릴 수 없는 저장소 삭제를
 * 마지막으로 미뤄 새 저장소 생성이 실패해도 기존 저장소가 남는다. 저장소 종류별 경로가 달라 새 저장소를 먼저 만들 수 있다.
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

    /**
     * [project]의 저장소 종류로 바꿀 수 있는 종류이다: 저장소를 만들 수 있게 등록된 종류에서 지금 종류를 뺀 것이다. 둘뿐일 때는 하나이고
     * (v1.6처럼 번갈아 바꾼다), 종류가 셋 이상이면 화면이 고르게 한다(Mercurial 도입을 위한 공통화 4단계, D-86 6번).
     */
    fun targets(project: Project): List<String> = backends.creatableTypes().filter { it != project.vcs }

    /**
     * [project]의 저장소 종류를 [target]으로 바꾼다. [target]을 주지 않으면 v1.6처럼 번갈아 바꾼다([Project.nextVcs]): Git이면 Subversion, 아니면 Git이다(v1.6 `changeVCS`).
     *
     * @throws IllegalArgumentException [target]이 지금 종류이거나 [targets]에 없을 때. 아무것도 바꾸지 않는다
     */
    @Transactional
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
        project.vcs = chosen
        try {
            repositories.createRepository(project)
        } catch (e: Exception) {
            project.vcs = previous
            throw e
        }
        val next = project.vcs
        // 기존 저장소는 이전 종류로 찾아 지운다. 지우지 못하면 새로 만든 저장소도 되돌려 예외를 던진다(트랜잭션이 DB를 되돌린다)
        project.vcs = previous
        try {
            repositories.deleteRepository(project)
        } catch (e: Exception) {
            project.vcs = next
            runCatching { repositories.deleteRepository(project) }
            project.vcs = previous
            throw e
        }
        project.vcs = next
        projects.save(project)
        audit.record(dev.yona.domain.audit.auditProject("project.vcs_changed", project, mapOf("from" to previous, "to" to project.vcs)))
    }
}
