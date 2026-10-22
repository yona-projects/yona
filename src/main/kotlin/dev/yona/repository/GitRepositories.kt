package dev.yona.repository

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.storage.file.WindowCacheConfig
import org.springframework.stereotype.Component
import java.io.File

/**
 * Git 저장소의 위치를 정하고 JGit으로 만들고 지우고 연다. v1.6 `playRepository.GitRepository`의
 * `getGitDirectory`, `create`, `delete`, `buildGitRepository`에 해당한다 (D-15).
 *
 * 저장소는 작업 트리가 없는 bare 저장소이며 위치는 `<데이터 디렉터리>/repo/git/<소유자>/<이름>.git`이다.
 * 포크와 풀 리퀘스트 병합용 저장소(`repo/git-merging/...`)는 해당 기능을 옮길 때 추가한다.
 */
@Component
class GitRepositories(private val dataDirectory: DataDirectory) {

    /** 저장소 디렉터리이다. 소유자와 이름은 URL 디코딩이 끝난 값이다. */
    fun directoryOf(owner: String, name: String): File = File(File(dataDirectory.root(), REPO_PREFIX), "$owner/$name.git")

    /** 작업 트리가 없는 bare 저장소를 만든다. 이미 있으면 JGit이 예외를 던진다. */
    fun create(owner: String, name: String) {
        RepositoryBuilder().setGitDir(directoryOf(owner, name)).setBare().build().use { it.create(true) }
    }

    /**
     * 저장소 디렉터리를 지운다. 없으면 아무 일도 하지 않는다.
     *
     * 이미 열었던 저장소가 pack 파일을 붙들고 있으면 지워지지 않으므로, v1.6처럼 JGit의 창 캐시를 다시 설치해
     * 참조를 비운 뒤 지운다.
     */
    fun delete(owner: String, name: String) {
        WindowCacheConfig().install()
        directoryOf(owner, name).deleteRecursively()
    }

    /** 저장소를 연다. 호출한 쪽이 닫아야 한다. 저장소가 없으면 예외이다. */
    fun open(owner: String, name: String): Repository =
        RepositoryBuilder().setGitDir(directoryOf(owner, name)).setMustExist(true).build()

    private companion object {
        /** 데이터 디렉터리 아래의 Git 저장소 위치이다 (v1.6 `GitRepository.repoPrefix`). */
        const val REPO_PREFIX = "repo/git"
    }
}
