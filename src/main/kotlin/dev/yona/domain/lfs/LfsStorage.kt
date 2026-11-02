package dev.yona.domain.lfs

import dev.yona.domain.project.Project
import dev.yona.repository.DataDirectory
import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Git LFS 객체를 파일 시스템에 두는 저장소이다(추가 개발 항목 X-8, `decisions.md` D-79).
 *
 * 객체는 프로젝트마다 `<데이터 디렉터리>/repo/lfs/<소유자>/<이름>/objects/<oid 앞 2자>/<다음 2자>/<oid>`에 둔다. `repo/git`, `repo/svn`과 같은
 * `repo/<종류>/<소유자>/<이름>` 규칙을 따르는 형제 디렉터리이고, 저장소 종류와 무관하게 프로젝트와 oid만 안다(Mercurial을 붙일 때 그대로 쓴다).
 * oid는 내용의 SHA-256 소문자 16진수 64자이다. 올릴 때는 임시 파일에 받으면서 해시와 길이를 세고, 맞을 때만 제자리로 옮겨 놓는다(원자적 이동).
 */
@Component
class LfsStorage(private val dataDirectory: DataDirectory) {

    /** [store]의 결과이다. */
    sealed interface StoreResult {
        /** 저장했다. [created]가 거짓이면 이미 있던 객체이다. */
        data class Stored(val size: Long, val created: Boolean) : StoreResult

        /** 내용의 SHA-256이 oid와 다르다. */
        data object HashMismatch : StoreResult

        /** 내용이 허용한 최대 크기를 넘었다. */
        data object TooLarge : StoreResult
    }

    /** 프로젝트의 사용량이다. */
    data class Usage(val objects: Long, val bytes: Long)

    /** 프로젝트의 LFS 루트 디렉터리이다(없을 수 있다). */
    fun directoryOf(project: Project): File = directoryOf(requireNotNull(project.owner), requireNotNull(project.name))

    /** 소유자와 이름으로 구한 LFS 루트 디렉터리이다. 이름이 저장소 밖을 가리키지 않는지 확인한다. */
    fun directoryOf(owner: String, name: String): File {
        val base = File(dataDirectory.root(), "repo/lfs").canonicalFile
        val directory = File(base, "$owner/$name").canonicalFile
        require(directory.path.startsWith(base.path + File.separator)) { "LFS 디렉터리가 저장소 밖을 가리킵니다: $owner/$name" }
        return directory
    }

    /** [oid] 객체의 파일이다(없을 수 있다). [oid]가 올바르지 않으면 [IllegalArgumentException]이다. */
    fun objectFile(project: Project, oid: String): File {
        require(validOid(oid)) { "올바르지 않은 oid입니다" }
        return File(directoryOf(project), "objects/${oid.substring(0, 2)}/${oid.substring(2, 4)}/$oid")
    }

    /** 객체가 있으면 `true`이다. */
    fun exists(project: Project, oid: String): Boolean = validOid(oid) && objectFile(project, oid).isFile

    /** 객체의 크기(바이트)이다. 없으면 `null`이다. */
    fun sizeOf(project: Project, oid: String): Long? = if (validOid(oid)) objectFile(project, oid).takeIf { it.isFile }?.length() else null

    /**
     * [input]을 [oid] 객체로 저장한다. 읽으면서 SHA-256과 길이를 세고, 길이가 [maxBytes]를 넘으면 곧바로 멈춘다([StoreResult.TooLarge]).
     * 해시가 [oid]와 다르면 [StoreResult.HashMismatch]이다. 어느 경우에도 임시 파일은 남기지 않는다. 이미 있는 객체는 다시 쓰지 않는다.
     */
    fun store(project: Project, oid: String, input: InputStream, maxBytes: Long): StoreResult {
        val target = objectFile(project, oid)
        if (target.isFile) return StoreResult.Stored(target.length(), created = false)
        val temporary = File(directoryOf(project), "tmp/${UUID.randomUUID()}").also { it.parentFile.mkdirs() }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    size += read
                    if (size > maxBytes) return StoreResult.TooLarge
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
            if (digest.digest().joinToString("") { "%02x".format(it) } != oid) return StoreResult.HashMismatch
            target.parentFile.mkdirs()
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return StoreResult.Stored(size, created = true)
        } finally {
            temporary.delete()
        }
    }

    /**
     * 프로젝트의 LFS 디렉터리를 새 소유자와 이름 위치로 옮긴다(개명, 이전). 옮길 것이 없으면 `true`이다. 새 위치에 이미 있으면 `false`이다.
     * 저장소 디렉터리를 옮기는 곳([dev.yona.repository.RepositoryService.moveRepository])에서 함께 부른다.
     */
    fun move(oldOwner: String, oldName: String, newOwner: String, newName: String): Boolean {
        val source = directoryOf(oldOwner, oldName)
        if (!source.exists()) return true
        val target = directoryOf(newOwner, newName)
        if (target.exists()) return false
        target.parentFile.mkdirs()
        Files.move(source.toPath(), target.toPath())
        return true
    }

    /** 프로젝트의 LFS 디렉터리를 지운다. 없으면 아무 일도 하지 않는다. */
    fun delete(project: Project) { directoryOf(project).deleteRecursively() }

    /**
     * [from]의 객체 가운데 [to]에 없는 것을 [to]로 가져간다. 같은 파일 시스템이면 하드 링크로 디스크를 더 쓰지 않고, 안 되면 복사한다.
     * 원본 프로젝트를 지울 때 포크가 빌려 쓰던 객체를 포크가 갖게 한다(`decisions.md` D-79 4번).
     */
    fun adopt(from: Project, to: Project) {
        val objects = File(directoryOf(from), "objects")
        if (!objects.isDirectory) return
        objects.walkTopDown().filter { it.isFile }.forEach { source ->
            val target = objectFile(to, source.name)
            if (target.exists()) return@forEach
            target.parentFile.mkdirs()
            try { Files.createLink(target.toPath(), source.toPath()) } catch (e: Exception) { Files.copy(source.toPath(), target.toPath()) }
        }
    }

    /** 사이트 전체의 객체 수와 바이트이다(`repo/lfs` 아래 모든 프로젝트). */
    fun siteUsage(): Usage {
        val root = File(dataDirectory.root(), "repo/lfs")
        if (!root.isDirectory) return Usage(0, 0)
        val files = root.walkTopDown().filter { it.isFile && "/objects/" in it.path.replace(File.separatorChar, '/') }.toList()
        return Usage(files.size.toLong(), files.sumOf { it.length() })
    }

    /** 프로젝트의 객체 수와 바이트이다. */
    fun usage(project: Project): Usage {
        val objects = File(directoryOf(project), "objects")
        if (!objects.isDirectory) return Usage(0, 0)
        val files = objects.walkTopDown().filter { it.isFile }.toList()
        return Usage(files.size.toLong(), files.sumOf { it.length() })
    }

    companion object {
        private const val BUFFER = 64 * 1024
        private val OID = Regex("^[0-9a-f]{64}$")

        /** [oid]가 SHA-256 소문자 16진수 64자이면 `true`이다. */
        fun validOid(oid: String): Boolean = OID.matches(oid)
    }
}
