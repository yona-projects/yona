package dev.yona.repository

import dev.yona.config.YonaProperties
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.storage.file.WindowCacheConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException

/**
 * 프로젝트 위키 저장소의 위치를 정하고 JGit으로 만들고 지우고 옮기고 연다 (X-17, D-60).
 *
 * 위키 페이지는 DB 엔티티가 아니라 프로젝트마다 하나씩 있는 별도 bare git 저장소 안의 마크다운 파일이다. 프로젝트의 VCS가
 * Subversion이어도 위키는 항상 git이다. 위치는 코드 저장소 바로 옆인 `<데이터 디렉터리>/repo/git/<소유자>/<이름>.wiki.git`이고
 * clone 주소는 Forgejo처럼 `/<소유자>/<이름>.wiki.git`이다. 이름이 `<이름>.wiki`인 프로젝트의 코드 저장소와 위치가 겹치므로 새 프로젝트
 * 이름에서 `.wiki` 끝맺음을 금지한다(`ProjectNameRules`). 그 규칙이 생기기 전에 만든 프로젝트가 있으면 위키 서비스가 만들기를 거절한다.
 *
 * 위키 백업은 사이트 데이터 내보내기와 별개이며 이 디렉터리 단위로 한다 (D-60).
 */
@Component
class WikiRepositories @Autowired constructor(private val dataDirectory: DataDirectory, private val properties: YonaProperties) {

    /** 단위 시험용이다. 기본 브랜치는 기본 설정(`main`)을 따른다. */
    constructor(dataDirectory: DataDirectory) : this(dataDirectory, YonaProperties())

    /** 위키 저장소 디렉터리이다. 소유자와 이름은 URL 디코딩이 끝난 값이다. */
    fun directoryOf(owner: String, name: String): File = File(File(dataDirectory.root(), REPO_PREFIX), "$owner/$name.wiki.git")

    /** 위키 저장소가 있는지 알려 준다. 프로젝트를 만들 때는 만들지 않고 첫 페이지를 저장할 때 만든다. */
    fun exists(owner: String, name: String): Boolean = directoryOf(owner, name).isDirectory

    /**
     * 작업 트리가 없는 bare 저장소를 만든다. 이미 있으면 아무 일도 하지 않는다. `HEAD`는 설정 `yona.git-default-branch`
     * (기본 `main`)를 가리킨다.
     */
    fun create(owner: String, name: String) {
        if (exists(owner, name)) return
        RepositoryBuilder().setGitDir(directoryOf(owner, name)).setBare().build().use {
            it.create(true)
            it.updateRef(Constants.HEAD).link(Constants.R_HEADS + properties.gitDefaultBranch)
        }
    }

    /** 위키 저장소를 연다. 호출한 쪽이 닫아야 한다. 저장소가 없으면 예외이다. */
    fun open(owner: String, name: String): Repository =
        RepositoryBuilder().setGitDir(directoryOf(owner, name)).setMustExist(true).build()

    /**
     * 위키 저장소 디렉터리를 지운다. 없으면 아무 일도 하지 않는다. 이미 열었던 저장소가 pack 파일을 붙들고 있을 수 있으므로
     * 코드 저장소처럼 JGit의 창 캐시를 다시 설치한 뒤 지운다.
     */
    fun delete(owner: String, name: String) {
        WindowCacheConfig().install()
        directoryOf(owner, name).deleteRecursively()
    }

    /**
     * 위키 저장소 디렉터리를 다른 소유자나 이름의 위치로 옮긴다. 옮길 위키가 없으면 성공으로 처리한다. 옮기지 못하면 `false`이다.
     */
    fun move(owner: String, name: String, newOwner: String, newName: String): Boolean {
        WindowCacheConfig().install()
        val source = directoryOf(owner, name)
        val target = directoryOf(newOwner, newName)
        return try {
            if (source.exists()) {
                target.parentFile.mkdirs()
                java.nio.file.Files.move(source.toPath(), target.toPath())
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    companion object {
        /** 데이터 디렉터리 아래 저장소의 상위 경로이다. 코드 저장소와 같다. */
        const val REPO_PREFIX = "repo/git"
    }
}
