package dev.yona.repository.hg

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Mercurial 저장소 안 LFS 본문(`.hg/store/lfs/objects/<oid 앞 2자>/<나머지>`)을 한 저장소에서 다른 저장소로 옮긴다(X-7 9a, `decisions.md` D-90). Git LFS는 본문이 저장소
 * 밖에 있어 포크가 원본에서 빌려 읽지만, Mercurial의 본문은 저장소 안에 있고 체인지셋을 가져오는 일(`pull`, `bundle`)은 포인터만 옮기므로 포크와 풀 리퀘스트가
 * 본문을 함께 옮겨야 한다. 같은 디스크이면 하드 링크로(원본을 지워도 포크의 본문은 남는다), 아니면 복사한다.
 */
object HgLfsBlobs {

    /** 저장소 디렉터리 [repository]의 LFS 본문 폴더이다. */
    fun objectsDir(repository: File): File = File(repository, ".hg/store/lfs/objects")

    /** [source]의 모든 LFS 본문을 [target]으로 옮긴다. 이미 있는 것은 건너뛴다. 옮긴 개수를 돌려준다. */
    fun copyAll(source: File, target: File): Int {
        val root = objectsDir(source)
        if (!root.isDirectory) return 0
        return root.walkTopDown().filter { it.isFile }.count { copy(source, target, it.parentFile.name + it.name) }
    }

    /** [source] 저장소의 본문 [oid](64자)를 [target] 저장소로 옮긴다. 없거나 이미 있으면 `false`이다. */
    fun copy(source: File, target: File, oid: String): Boolean {
        if (oid.length != OID_LENGTH) return false
        val from = File(File(objectsDir(source), oid.substring(0, 2)), oid.substring(2))
        val to = File(File(objectsDir(target), oid.substring(0, 2)), oid.substring(2))
        if (!from.isFile || to.exists()) return false
        to.parentFile.mkdirs()
        try {
            Files.createLink(to.toPath(), from.toPath())
        } catch (e: Exception) {
            // 다른 디스크이거나 하드 링크를 못 만들면 복사한다
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
        }
        return true
    }

    private const val OID_LENGTH = 64
}
