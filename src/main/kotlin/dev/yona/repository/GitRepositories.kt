package dev.yona.repository

import dev.yona.config.YonaProperties
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.storage.file.WindowCacheConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File

/**
 * Git 저장소의 위치를 정하고 JGit으로 만들고 지우고 연다. v1.6 `playRepository.GitRepository`의
 * `getGitDirectory`, `create`, `delete`, `buildGitRepository`에 해당한다 (D-15).
 *
 * 저장소는 작업 트리가 없는 bare 저장소이며 위치는 `<데이터 디렉터리>/repo/git/<소유자>/<이름>.git`이다.
 * v1.6의 병합용 저장소(`repo/git-merging/...`)는 만들지 않는다. 병합이 받는 저장소 안에서 일어난다(`PullRequestMerger`).
 */
@Component
class GitRepositories @Autowired constructor(private val dataDirectory: DataDirectory, private val properties: YonaProperties) {

    /** 단위 시험용이다. 기본 브랜치는 기본 설정(`main`)을 따른다. */
    constructor(dataDirectory: DataDirectory) : this(dataDirectory, YonaProperties())

    /** 저장소 디렉터리이다. 소유자와 이름은 URL 디코딩이 끝난 값이다. */
    fun directoryOf(owner: String, name: String): File = File(File(dataDirectory.root(), REPO_PREFIX), "$owner/$name.git")

    /**
     * 작업 트리가 없는 bare 저장소를 만든다. 이미 있으면 JGit이 예외를 던진다.
     * `HEAD`는 설정 `yona.git-default-branch`(기본 `main`)를 가리킨다. v1.6은 JGit 기본값인 `master`였다 (X-22, `decisions.md` A-187).
     */
    fun create(owner: String, name: String) {
        RepositoryBuilder().setGitDir(directoryOf(owner, name)).setBare().build().use {
            it.create(true)
            it.updateRef(Constants.HEAD).link(Constants.R_HEADS + properties.gitDefaultBranch)
        }
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

    /**
     * Git 스마트 HTTP용으로 저장소를 연다. 디렉터리가 없어도 예외 없이 저장소를 돌려주고 디렉터리를 만들지도 않는다.
     * v1.6 `GitRepository.buildGitRepository`가 `setMustExist`를 쓰지 않아, 프로젝트 행만 있고 디렉터리가 없을 때도 `info/refs`가
     * 빈 저장소와 같은 광고를 200으로 돌려주기 때문이다.
     */
    fun openForHttp(owner: String, name: String): Repository =
        RepositoryBuilder().setGitDir(directoryOf(owner, name)).build()

    /**
     * 저장소 디렉터리를 다른 소유자나 이름의 위치로 옮긴다 (v1.6 `GitRepository.move`). 원래 디렉터리가 없으면 옮길 것이 없다고
     * 보고 성공으로 처리한다. 옮기지 못하면 `false`이다.
     *
     * 이미 열었던 저장소가 파일을 붙들고 있을 수 있으므로 JGit의 창 캐시를 다시 설치한 뒤 옮긴다.
     */
    fun move(owner: String, name: String, newOwner: String, newName: String): Boolean {
        WindowCacheConfig().install()
        val source = directoryOf(owner, name)
        val target = directoryOf(newOwner, newName)
        source.setWritable(true)
        return try {
            if (source.exists()) {
                target.parentFile.mkdirs()
                java.nio.file.Files.move(source.toPath(), target.toPath())
            }
            true
        } catch (e: java.io.IOException) {
            false
        }
    }

    /**
     * 원본 저장소를 포크 저장소로 복제한다 (v1.6 `GitRepository.cloneLocalRepository`).
     *
     * v1.6은 먼저 객체 파일을 하드 링크로 거는 방법을 시도하지만, `Repository.create()`(= `create(false)`)가 이름이 `.git`으로 끝나지 않는
     * 디렉터리에서 작업 트리가 없어 항상 `NoWorkTreeException`을 던진다. 그래서 모든 포크는 실제로는 폴백인 복제(`Git.cloneRepository()`,
     * bare, 모든 브랜치)로 만들어진다. 결과는 `remote.origin`(원본의 canonical 절대 경로)과 원본 HEAD 브랜치의 `branch.<이름>` 설정이 붙은
     * bare 저장소이고, `refs/heads/` 아래의 브랜치와 태그만 복사한다. `refs/yobi/` 아래의 PR용 ref는 복사하지 않는다 (2026-10-27 P-10 결정 4, S1 명세 8번).
     * 하드 링크 시도는 실행된 적이 없는 코드이므로 옮기지 않는다.
     */
    fun cloneLocal(owner: String, name: String, forkOwner: String, forkName: String) {
        // 같은 이름의 프로젝트가 없는데 디렉터리가 남아 있으면 이전에 실패한 흔적이므로 지우고 시작한다 (`createRepository`와 같다).
        delete(forkOwner, forkName)
        // v1.6 `getGitDirectoryURL`은 file: URI가 아니라 canonical 절대 경로 문자열이다
        org.eclipse.jgit.api.Git.cloneRepository()
            .setURI(directoryOf(owner, name).canonicalPath).setDirectory(directoryOf(forkOwner, forkName))
            .setCloneAllBranches(true).setBare(true).call().close()
        // 빈 원본을 복제하면 JGit 7.x는 init.defaultBranch를 따를 수 있다. 원본 HEAD가 가리키는 브랜치를 그대로 쓴다.
        open(owner, name).use { origin ->
            val target = origin.exactRef("HEAD")?.takeIf { it.isSymbolic }?.target?.name ?: return
            open(forkOwner, forkName).use { forked ->
                if (forked.exactRef("HEAD")?.target?.name != target) forked.updateRef("HEAD").link(target)
            }
        }
    }

    /**
     * 다른 Git 저장소를 통째로(모든 브랜치) bare 저장소로 복제한다 (v1.6 `GitRepository.cloneRepository(gitUrl, project[, id, pw])`).
     * [authId]나 [authPw]가 있으면 사용자 이름과 비밀번호로 인증한다.
     */
    fun cloneRemote(owner: String, name: String, url: String, authId: String?, authPw: String?) {
        // 같은 이름의 프로젝트가 없는데 디렉터리가 남아 있으면 이전에 실패한 흔적이므로 지우고 시작한다 (`createRepository`와 같다).
        delete(owner, name)
        val command = org.eclipse.jgit.api.Git.cloneRepository().setURI(url).setDirectory(directoryOf(owner, name)).setCloneAllBranches(true).setBare(true)
        if (!authId.isNullOrEmpty() || !authPw.isNullOrEmpty()) {
            command.setCredentialsProvider(org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider(authId.orEmpty(), authPw.orEmpty()))
        }
        command.call().close()
    }

    /** 저장소의 브랜치, 태그, 원격 참조 이름을 이름순으로 돌려준다 (v1.6 `GitRepository.getRefNames`). */
    fun refNames(owner: String, name: String): List<String> = open(owner, name).use { repository ->
        repository.refDatabase.refs.map { it.name }.filter { ref -> WELL_KNOWN_PREFIXES.any { ref.startsWith(it) } }.sorted()
    }

    /** 저장소의 기본 브랜치(`HEAD`가 가리키는 참조) 이름이다. 예: `refs/heads/master`. */
    fun defaultBranch(owner: String, name: String): String = open(owner, name).use { it.findRef(Constants.HEAD).target.name }

    /** 저장소의 기본 브랜치를 [target]으로 바꾼다. 바꾸지 못하면 [java.io.IOException]이다. */
    fun setDefaultBranch(owner: String, name: String, target: String) {
        open(owner, name).use { repository ->
            val result = repository.updateRef(Constants.HEAD).link(target)
            if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FORCED && result != RefUpdate.Result.NO_CHANGE) {
                throw java.io.IOException("Failed to update symbolic ref, got: $result")
            }
        }
    }

    private companion object {
        /** 브랜치, 태그, 원격 참조의 접두어이다. */
        val WELL_KNOWN_PREFIXES = listOf(Constants.R_HEADS, Constants.R_TAGS, Constants.R_REMOTES)

        /** 데이터 디렉터리 아래의 Git 저장소 위치이다 (v1.6 `GitRepository.repoPrefix`). */
        const val REPO_PREFIX = "repo/git"
    }
}
