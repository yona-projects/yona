package dev.yona.domain.project

import dev.yona.domain.board.PostingRepository
import dev.yona.repository.ProjectReadmeService
import dev.yona.repository.RepositoryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 프로젝트의 저장소 종류를 바꾼다. v1.6 `ProjectApp.changeVCS`와 `Project.changeVCS`에 해당한다.
 *
 * 순서: README 게시글 표시를 끄고(저장소에 README가 있을 때) -> 포크의 원본 연결을 끊고 -> 기존 저장소를 지우고 -> 종류를
 * 바꿔 새 저장소를 만든다. 중간에 실패하면 DB 변경은 취소되지만 이미 지운 저장소 디렉터리는 돌아오지 않는다(v1.6도 같다).
 */
@Service
@Transactional
class ProjectVcsService(
    private val projects: ProjectRepository,
    private val postings: PostingRepository,
    private val readme: ProjectReadmeService,
    private val repositories: RepositoryService
) {

    /** [project]의 저장소 종류를 다음 종류로 바꾼다. */
    fun change(project: Project) {
        val projectId = requireNotNull(project.id)
        if (readme.read(project) != null) {
            postings.findFirstByProjectIdAndReadmeTrue(projectId)?.let {
                it.readme = false
                postings.save(it)
            }
        }

        projects.findByOriginalProjectId(projectId).forEach {
            it.originalProject = null
            projects.save(it)
        }

        val previous = project.vcs
        repositories.deleteRepository(project)
        project.vcs = project.nextVcs()
        try {
            repositories.createRepository(project)
        } catch (e: Exception) {
            project.vcs = previous
            throw e
        }
        projects.save(project)
    }
}
