package dev.yona.repository

import dev.yona.domain.project.Project
import org.eclipse.jgit.lib.Repository
import org.springframework.stereotype.Service

/**
 * 프로젝트의 저장소를 만들고 지우고 여는 진입점이다. v1.6 `playRepository.RepositoryService`에 해당한다.
 *
 * 프로젝트의 `vcs` 값으로 저장소 종류를 고른다. 지금은 Git(`GIT`)만 지원한다. Subversion은 로드맵의
 * 마지막 단계에서 SVNKit으로 추가한다 (D-13).
 */
@Service
class RepositoryService(private val git: GitRepositories) {

    /**
     * 프로젝트의 저장소를 새로 만든다. 같은 위치에 이미 있으면 먼저 지운다 (v1.6 `createRepository`).
     *
     * @throws UnsupportedOperationException 지원하지 않는 VCS일 때
     */
    fun createRepository(project: Project) {
        deleteRepository(project)
        when (project.vcs) {
            VCS_GIT -> git.create(owner(project), name(project))
            else -> throw unsupported(project)
        }
    }

    /** 프로젝트의 저장소 디렉터리를 지운다. 없으면 아무 일도 하지 않는다. */
    fun deleteRepository(project: Project) {
        when (project.vcs) {
            VCS_GIT -> git.delete(owner(project), name(project))
            // Subversion 저장소는 아직 만들 수 없으므로 지울 것도 없다. Subversion을 옮길 때(로드맵 8단계) 실제로 지운다.
            VCS_SUBVERSION -> Unit
            else -> throw unsupported(project)
        }
    }

    /**
     * 프로젝트 저장소 디렉터리를 다른 소유자나 이름의 위치로 옮긴다. 옮기지 못하면 `false`이다
     * (v1.6 `PlayRepository.move`).
     */
    fun moveRepository(project: Project, newOwner: String, newName: String): Boolean {
        if (project.vcs != VCS_GIT) throw unsupported(project)
        return git.move(owner(project), name(project), newOwner, newName)
    }

    /** Git 프로젝트의 저장소를 연다. 호출한 쪽이 닫아야 한다. */
    fun openRepository(project: Project): Repository {
        if (project.vcs != VCS_GIT) throw unsupported(project)
        return git.open(owner(project), name(project))
    }

    /** 프로젝트 저장소 디렉터리를 새 이름으로 옮긴다. 옮기지 못하면 `false`이다 (v1.6 `PlayRepository.renameTo`). */
    fun renameRepository(project: Project, newName: String): Boolean {
        if (project.vcs != VCS_GIT) throw unsupported(project)
        return git.move(owner(project), name(project), owner(project), newName)
    }

    /** 저장소의 브랜치, 태그, 원격 참조 이름이다 (v1.6 `getRefNames`). */
    fun refNames(project: Project): List<String> {
        if (project.vcs != VCS_GIT) throw unsupported(project)
        return git.refNames(owner(project), name(project))
    }

    /** 저장소의 기본 브랜치를 [target]으로 바꾼다 (v1.6 `setDefaultBranch`). */
    fun setDefaultBranch(project: Project, target: String) {
        if (project.vcs != VCS_GIT) throw unsupported(project)
        git.setDefaultBranch(owner(project), name(project), target)
    }

    /**
     * 프로젝트 저장소의 기본 브랜치 이름이다. 예: `refs/heads/master`. 저장소를 열 수 없으면 `HEAD`이다
     * (v1.6 `Project.defaultBranch`).
     */
    fun defaultBranch(project: Project): String = try {
        openRepository(project).use { it.findRef(org.eclipse.jgit.lib.Constants.HEAD)?.target?.name ?: "HEAD" }
    } catch (e: Exception) {
        "HEAD"
    }

    private fun owner(project: Project) = requireNotNull(project.owner) { "프로젝트의 소유자가 없습니다." }

    private fun name(project: Project) = requireNotNull(project.name) { "프로젝트의 이름이 없습니다." }

    private fun unsupported(project: Project) =
        UnsupportedOperationException("지원하지 않는 VCS입니다: ${project.vcs}")

    companion object {
        /** `project.vcs`의 Git 값이다 (v1.6 `RepositoryService.VCS_GIT`). */
        const val VCS_GIT = "GIT"

        /** `project.vcs`의 Subversion 값이다 (v1.6 `RepositoryService.VCS_SUBVERSION`). */
        const val VCS_SUBVERSION = "Subversion"
    }
}
