package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.repository.svn.SvnRepositories
import org.eclipse.jgit.lib.Repository
import org.springframework.stereotype.Service

/**
 * 프로젝트의 저장소를 만들고 지우고 여는 진입점이다. v1.6 `playRepository.RepositoryService`에 해당한다.
 *
 * 프로젝트의 `vcs` 값(`GIT`, `Subversion`)으로 저장소 종류를 고른다. Subversion은 SVNKit으로 처리한다 (D-13). 포크, 복제, 가져오기,
 * 저장소 열기는 Git만 지원한다.
 */
@Service
class RepositoryService(private val git: GitRepositories, private val svn: SvnRepositories) {

    /**
     * 프로젝트의 저장소를 새로 만든다. 같은 위치에 이미 있으면 먼저 지운다 (v1.6 `createRepository`).
     *
     * @throws UnsupportedOperationException 지원하지 않는 VCS일 때
     */
    fun createRepository(project: Project) {
        deleteRepository(project)
        when (project.vcs) {
            VCS_GIT -> git.create(owner(project), name(project))
            VCS_SUBVERSION -> svn.create(owner(project), name(project))
            else -> throw unsupported(project)
        }
    }

    /** 프로젝트의 저장소 디렉터리를 지운다. 없으면 아무 일도 하지 않는다. */
    fun deleteRepository(project: Project) {
        when (project.vcs) {
            VCS_GIT -> git.delete(owner(project), name(project))
            VCS_SUBVERSION -> svn.delete(owner(project), name(project))
            else -> throw unsupported(project)
        }
    }

    /**
     * 프로젝트 저장소 디렉터리를 다른 소유자나 이름의 위치로 옮긴다. 옮기지 못하면 `false`이다
     * (v1.6 `PlayRepository.move`).
     */
    fun moveRepository(project: Project, newOwner: String, newName: String): Boolean = when (project.vcs) {
        VCS_GIT -> git.move(owner(project), name(project), newOwner, newName)
        VCS_SUBVERSION -> svn.move(owner(project), name(project), newOwner, newName)
        else -> throw unsupported(project)
    }

    /** Git 프로젝트의 저장소를 연다. 호출한 쪽이 닫아야 한다. */
    fun openRepository(project: Project): Repository {
        if (project.vcs != VCS_GIT) throw unsupported(project)
        return git.open(owner(project), name(project))
    }

    /** Git 스마트 HTTP용으로 저장소를 연다. 디렉터리가 없어도 예외가 없다 ([GitRepositories.openForHttp]). */
    fun openRepositoryForHttp(project: Project): Repository {
        if (project.vcs != VCS_GIT) throw unsupported(project)
        return git.openForHttp(owner(project), name(project))
    }

    /**
     * [original]의 Git 저장소를 [fork]의 위치로 복제한다 (v1.6 `GitRepository.cloneLocalRepository`).
     *
     * @throws UnsupportedOperationException Git이 아닌 프로젝트일 때
     */
    fun cloneRepository(original: Project, fork: Project) {
        if (original.vcs != VCS_GIT) throw unsupported(original)
        if (fork.vcs != VCS_GIT) throw unsupported(fork)
        git.cloneLocal(owner(original), name(original), owner(fork), name(fork))
    }

    /** 다른 Git 저장소를 [project]의 저장소로 복제해 가져온다 (v1.6 `GitRepository.cloneRepository`). */
    fun importRepository(project: Project, url: String, authId: String?, authPw: String?) {
        git.cloneRemote(owner(project), name(project), url, authId, authPw)
    }

    /** 프로젝트 저장소 디렉터리를 새 이름으로 옮긴다. 옮기지 못하면 `false`이다 (v1.6 `PlayRepository.renameTo`). */
    fun renameRepository(project: Project, newName: String): Boolean = moveRepository(project, owner(project), newName)

    /** 저장소의 브랜치, 태그, 원격 참조 이름이다. Subversion은 `HEAD` 하나이다 (v1.6 `getRefNames`). */
    fun refNames(project: Project): List<String> = when (project.vcs) {
        VCS_GIT -> git.refNames(owner(project), name(project))
        VCS_SUBVERSION -> svn.refNames()
        else -> throw unsupported(project)
    }

    /** 저장소의 기본 브랜치를 [target]으로 바꾼다 (v1.6 `setDefaultBranch`). */
    fun setDefaultBranch(project: Project, target: String) {
        when (project.vcs) {
            VCS_GIT -> git.setDefaultBranch(owner(project), name(project), target)
            // Subversion은 기본 브랜치를 바꿀 수 없다(v1.6 `setDefaultBranch`는 아무 일도 하지 않는다).
            VCS_SUBVERSION -> Unit
            else -> throw unsupported(project)
        }
    }

    /**
     * 프로젝트 저장소의 기본 브랜치 이름이다. 예: `refs/heads/master`. 저장소를 열 수 없으면 `HEAD`이다
     * (v1.6 `Project.defaultBranch`).
     */
    fun defaultBranch(project: Project): String = if (project.vcs == VCS_SUBVERSION) svn.defaultBranch() else try {
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
