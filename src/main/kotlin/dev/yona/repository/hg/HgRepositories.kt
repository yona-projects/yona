package dev.yona.repository.hg

import dev.yona.repository.DataDirectory
import io.github.search5.hg4j.api.Hg
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException

/**
 * Mercurial 저장소의 위치를 정하고 hg4j로 만들고 지우고 연다(X-7, D-86). Git의 [dev.yona.repository.GitRepositories], Subversion의
 * [dev.yona.repository.svn.SvnRepositories]에 해당한다.
 *
 * 저장소는 `<데이터 디렉터리>/repo/hg/<소유자>/<이름>` 디렉터리이고 그 안의 `.hg`가 실제 저장소이다. 서버 저장소라 작업 파일은 두지 않는다.
 *
 * Yona는 Mercurial의 이름표(bookmark)를 브랜치로 보인다(D-86 1번): 참조 이름은 이름표가 `refs/heads/<이름>`, 태그가 `refs/tags/<이름>`이다. 이름표가
 * 하나도 없는 저장소의 기본 위치는 `HEAD`이다([HgRevisions]가 `default` 브랜치의 끝으로 푼다).
 */
@Component
class HgRepositories(private val dataDirectory: DataDirectory) {

    /** 저장소 디렉터리이다. 소유자와 이름은 URL 디코딩이 끝난 값이다. */
    fun directoryOf(owner: String, name: String): File = File(File(dataDirectory.root(), REPO_PREFIX), "$owner/$name")

    /** 저장소를 만든다. 이미 있으면 hg4j가 예외를 던진다. */
    fun create(owner: String, name: String) {
        val directory = directoryOf(owner, name)
        directory.mkdirs()
        Hg.init().setDirectory(directory).call().close()
    }

    /** 저장소 디렉터리를 지운다. 없으면 아무 일도 하지 않는다. */
    fun delete(owner: String, name: String) {
        directoryOf(owner, name).deleteRecursively()
    }

    /** 저장소 디렉터리를 다른 소유자나 이름의 위치로 옮긴다. 원래 디렉터리가 없으면 성공이고, 옮기지 못하면 `false`이다. */
    fun move(owner: String, name: String, newOwner: String, newName: String): Boolean {
        val source = directoryOf(owner, name)
        val target = directoryOf(newOwner, newName)
        source.setWritable(true)
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

    /**
     * 저장소를 연다. 호출한 쪽이 닫아야 한다.
     *
     * @throws IOException 저장소가 없거나 열 수 없을 때
     */
    fun open(owner: String, name: String): Hg {
        val directory = directoryOf(owner, name)
        if (!File(directory, ".hg").isDirectory) throw IOException("Mercurial 저장소가 없습니다: ${directory.path}")
        return Hg.open(directory)
    }

    /** 이름표(`refs/heads/<이름>`)와 태그(`refs/tags/<이름>`)를 이름순으로 모은다. 가상 태그 `tip`은 뺀다. */
    fun refNames(owner: String, name: String): List<String> = open(owner, name).use { hg ->
        (HgRevisions.bookmarks(hg).keys.map { HEADS + it } + HgRevisions.tags(hg).keys.map { TAGS + it }).sorted()
    }

    /**
     * 기본 위치이다. `main`, `master`, `@` 이름표 가운데 먼저 있는 것의 `refs/heads/<이름>`이고, 없으면 `HEAD`이다
     * (Git의 `HEAD`가 가리키는 브랜치에 해당한다).
     */
    fun defaultBranch(owner: String, name: String): String = open(owner, name).use { hg ->
        val bookmarks = HgRevisions.bookmarks(hg)
        DEFAULT_BOOKMARKS.firstOrNull { it in bookmarks }?.let { HEADS + it } ?: "HEAD"
    }

    companion object {
        /** 데이터 디렉터리 아래의 Mercurial 저장소 위치이다. */
        const val REPO_PREFIX = "repo/hg"

        /** 이름표를 브랜치로 나타내는 접두어이다. */
        const val HEADS = "refs/heads/"

        /** 태그의 접두어이다. */
        const val TAGS = "refs/tags/"

        /** 기본 위치로 삼는 이름표이다. 앞에 있을수록 우선한다(`@`는 Mercurial의 관례적인 기본 이름표). */
        val DEFAULT_BOOKMARKS = listOf("main", "master", "@")
    }
}

/** [Hg]를 `use { }`로 쓰게 한다. hg4j의 `Hg`는 [AutoCloseable]이 아니라 `close()`만 있다. */
inline fun <T> Hg.use(block: (Hg) -> T): T = try { block(this) } finally { close() }
