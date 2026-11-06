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
        enableLfs(directory)
    }

    /**
     * 저장소의 `.hg/hgrc`에 lfs 확장을 켠다(X-7 9a, D-90). 서버 쪽 hg4j(`HgHttpWireServer`)는 이 확장이 켜진 저장소의 LFS 요청(배치와 객체)만 처리하고,
     * 저장소에 LFS 파일이 없으면 클라이언트가 확장을 켜지 않아도 아무 영향이 없다. 포크와 가져오기도 [create]를 거치므로 같은 설정을 받는다.
     *
     * 사용자 캐시(`lfs.usercache`)는 끈다(`experimental.lfs.disableusercache`). hg4j 서버는 객체가 있는지를 저장소의 LFS 저장소와 함께 이 캐시도 보는데,
     * 캐시는 컴퓨터의 모든 저장소가 공유하므로 켜 두면 한 프로젝트가 올린 큰 파일 본문이 다른 프로젝트에도 "이미 있음"으로 보이고(저장소 사이에 새는 것),
     * 올린 본문이 그 저장소의 저장소 안에 들어가지 않는다.
     *
     * `lfs.url = null://`은 서버 저장소에 올려 보낼 원격이 없다는 뜻이다. hg4j `BundleCommand`는 원본 hg처럼 번들에 담는 체인지셋의 본문을 `[lfs] url`에 먼저 올리는데,
     * 풀 리퀘스트 가져오기가 번들을 만들 때 원격이 없다는 오류가 나지 않게 하고 본문은 [HgLfsBlobs]가 직접 옮긴다(D-90).
     */
    private fun enableLfs(directory: File) {
        File(directory, ".hg/hgrc").appendText("[extensions]\nlfs =\n\n[experimental]\nlfs.disableusercache = true\n\n[lfs]\nurl = null://\n")
    }

    /**
     * 원본 저장소를 포크 저장소로 복제한다(X-7 7a). 빈 저장소를 만들어 원본에서 체인지셋을 가져온다: 이름표는 함께 오고 태그는 `.hgtags`로 오며, 원본의
     * secret 체인지셋은 hg4j가 원본을 읽을 때 숨기므로 오지 않는다(풀 리퀘스트가 가져온 숨은 체인지셋, D-87). `hg init` 뒤 `hg pull`은 작업 파일과 dirstate를 만들지 않는 원본 hg의
     * 서버 저장소 만드는 방식이다(hg4j `PullCommand`는 원본처럼 dirstate를 건드리지 않는다, hg4j 수정 `c583a0c`).
     *
     * 복제하다 실패하면 만들다 만 포크 디렉터리를 지운다.
     *
     * @throws IOException 원본이 없거나 복제하지 못했을 때
     */
    fun cloneLocal(owner: String, name: String, forkOwner: String, forkName: String) {
        val source = directoryOf(owner, name)
        if (!File(source, ".hg").isDirectory) throw IOException("Mercurial 저장소가 없습니다: ${source.path}")
        create(forkOwner, forkName)
        try {
            // LFS 본문은 저장소 안에 있고 체인지셋을 가져오는 일은 포인터만 옮기므로 본문을 먼저 옮긴다(D-90)
            HgLfsBlobs.copyAll(source, directoryOf(forkOwner, forkName))
            open(forkOwner, forkName).use { fork ->
                io.github.search5.hg4j.api.PullCommand(fork.repository).setSource(source.canonicalPath).call()
            }
        } catch (e: Exception) {
            delete(forkOwner, forkName)
            throw if (e is IOException) e else IOException("포크하지 못했습니다: ${e.message}", e)
        }
    }

    /**
     * 다른 Mercurial 저장소를 주소로 가져와 복제한다(X-7 7b). [cloneLocal]과 같이 빈 저장소를 만들어 가져오고(작업 파일 없음), 인증 정보는
     * 있을 때만 쓴다. 같은 이름의 디렉터리가 남아 있으면 이전에 실패한 흔적이므로 지우고 시작하며, 실패하면 만들다 만 디렉터리를 지운다.
     *
     * @throws io.github.search5.hg4j.errors.HgAuthException 인증이 필요하거나 인증 정보가 틀렸을 때
     * @throws IOException 주소를 읽을 수 없거나 저장소가 아니거나 전송에 실패했을 때
     */
    fun cloneRemote(owner: String, name: String, url: String, authId: String?, authPw: String?) {
        delete(owner, name)
        create(owner, name)
        try {
            open(owner, name).use { hg ->
                val pull = io.github.search5.hg4j.api.PullCommand(hg.repository).setSource(url)
                if (!authId.isNullOrEmpty() || !authPw.isNullOrEmpty()) {
                    pull.setCredentialsProvider(io.github.search5.hg4j.transport.UsernamePasswordCredentialsProvider(authId.orEmpty(), authPw.orEmpty()))
                }
                pull.call()
            }
        } catch (e: Exception) {
            delete(owner, name)
            throw e
        }
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

        /** 풀 리퀘스트의 숨은 참조로 예약한 이름표 접두어이다(D-87). 브랜치 목록과 이름 풀이에서 빼고 푸시를 거부한다. Git의 `refs/yobi/`에 해당한다. */
        const val RESERVED_BOOKMARK_PREFIX = "yobi/"

        /** 기본 위치로 삼는 이름표이다. 앞에 있을수록 우선한다(`@`는 Mercurial의 관례적인 기본 이름표). */
        val DEFAULT_BOOKMARKS = listOf("main", "master", "@")
    }
}

/** [Hg]를 `use { }`로 쓰게 한다. hg4j의 `Hg`는 [AutoCloseable]이 아니라 `close()`만 있다. */
inline fun <T> Hg.use(block: (Hg) -> T): T = try { block(this) } finally { close() }
