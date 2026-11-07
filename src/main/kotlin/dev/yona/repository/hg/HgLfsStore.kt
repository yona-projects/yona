package dev.yona.repository.hg

import io.github.search5.hg4j.lfs.HgLfsFiles
import org.springframework.stereotype.Component
import java.io.File

/**
 * Mercurial 저장소 안 LFS 저장소(`.hg/store/lfs/objects`)의 사용량과 LFS 파일 목록을 읽는다(X-7 9a의 LFS 화면, `decisions.md` D-90). Mercurial의 큰 파일 본문은
 * Yona의 Git LFS 저장소가 아니라 저장소 안에 hg4j가 둔다. 그래서 용량 제한과 잠금은 없고 이 읽기만 있다.
 */
@Component
class HgLfsStore(private val repositories: HgRepositories) {

    /** LFS 저장소의 사용량이다. @property objects 본문 개수 @property bytes 본문 바이트 합계 */
    data class Usage(val objects: Int, val bytes: Long)

    /** 기본 위치의 머리에서 LFS로 저장된 파일 한 개이다. @property oid 본문의 SHA-256(64자) @property size 본문 바이트 */
    data class TrackedFile(val path: String, val oid: String, val size: Long)

    /** [owner]/[name] 저장소의 LFS 저장소 사용량이다. 저장소나 폴더가 없으면 0이다. */
    fun usage(owner: String, name: String): Usage {
        val root = File(repositories.directoryOf(owner, name), ".hg/store/lfs/objects")
        if (!root.isDirectory) return Usage(0, 0)
        val files = root.walkTopDown().filter { it.isFile }.toList()
        return Usage(files.size, files.sumOf { it.length() })
    }

    /** 사이트 전체 Mercurial 저장소의 LFS 저장소 사용량이다(`repo/hg/<소유자>/<이름>/.hg/store/lfs/objects` 아래 본문). 저장소가 없으면 0이다. */
    fun siteUsage(): Usage {
        val root = repositories.rootDirectory()
        if (!root.isDirectory) return Usage(0, 0)
        val files = root.walkTopDown().onEnter { it == root || it.name != ".git" }.filter { it.isFile && it.parentFile.parentFile.invariantSeparatorsPath.endsWith(".hg/store/lfs/objects") }.toList()
        return Usage(files.size, files.sumOf { it.length() })
    }

    /** 기본 위치(이름표 `main` 등)의 머리에서 LFS로 저장된 파일을 경로순으로 돌려준다. 커밋이 없으면 비어 있다. */
    fun trackedFiles(owner: String, name: String): List<TrackedFile> = repositories.open(owner, name).use { hg ->
        val head = HgRevisions.head(hg) ?: return emptyList()
        val rev = HgChangesets.revOf(HgRevisions.changelog(hg), head)
        if (rev < 0) return emptyList()
        HgLfsFiles.inManifest(hg.repository, rev).map { (path, pointer) -> TrackedFile(path, pointer.oid, pointer.size) }
    }
}
