package dev.yona.repository

import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.RefUpdate
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
     * 원본 저장소를 포크 저장소로 복제한다 (v1.6 `GitRepository.cloneLocalRepository`). 먼저 객체 파일을 하드 링크로 걸어 디스크를 아끼고
     * 참조를 옮겨 오며, 하드 링크를 만들 수 없으면(파일 시스템이 다른 경우 등) 만들던 저장소를 지우고 전체를 복사해 복제한다.
     *
     * v1.6은 `info/alternates`만 복사하려고 비교했으나 비교 대상이 절대 경로로 풀려 늘 같지 않았다. 그래서 그 파일도 하드 링크로 걸린다.
     * 같은 결과를 내려고 그대로 따른다.
     */
    fun cloneLocal(owner: String, name: String, forkOwner: String, forkName: String) {
        try {
            cloneHardLinked(owner, name, forkOwner, forkName)
        } catch (e: Exception) {
            delete(forkOwner, forkName)
            org.eclipse.jgit.api.Git.cloneRepository()
                .setURI(directoryOf(owner, name).toURI().toString()).setDirectory(directoryOf(forkOwner, forkName))
                .setCloneAllBranches(true).setBare(true).call().close()
        }
    }

    private fun cloneHardLinked(owner: String, name: String, forkOwner: String, forkName: String) {
        open(owner, name).use { origin ->
            RepositoryBuilder().setGitDir(directoryOf(forkOwner, forkName)).setBare().build().use { forked ->
                forked.create(true)
                val originObjects = java.nio.file.Paths.get(File(origin.directory, "objects").absolutePath)
                val forkedObjects = java.nio.file.Paths.get(File(forked.directory, "objects").absolutePath)
                java.nio.file.Files.walkFileTree(originObjects, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                    override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                        val target = forkedObjects.resolve(originObjects.relativize(file.toAbsolutePath()))
                        target.parent.toFile().mkdirs()
                        java.nio.file.Files.deleteIfExists(target)
                        java.nio.file.Files.createLink(target, file)
                        return java.nio.file.FileVisitResult.CONTINUE
                    }
                })
                for ((refName, ref) in origin.allRefs) {
                    val update = forked.updateRef(refName)
                    if (ref.isSymbolic) {
                        update.link(ref.target.name)
                    } else {
                        update.setNewObjectId(ref.objectId)
                        update.update()
                    }
                }
            }
        }
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
