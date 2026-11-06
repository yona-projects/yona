package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.domain.vcs.VcsType
import dev.yona.io.CharsetDetection
import dev.yona.repository.CodeReader
import dev.yona.security.AvatarService
import dev.yona.util.SiteUrls
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.api.ManifestCommand
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.io.File
import java.io.OutputStream
import java.util.SortedMap
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Mercurial 저장소의 폴더와 파일 정보를 hg4j로 읽는 [CodeReader] 구현이다(X-7, D-86). 결과는 Git의 [dev.yona.repository.GitCodeReader]와 같은 맵 모양이라
 * 코드 화면과 JSON 응답이 그대로 쓴다.
 *
 * - 폴더는 리비전의 매니페스트(파일 목록)를 폴더 단위로 묶는다. 항목마다 마지막으로 바꾼 커밋은 리비전의 조상을 최근 순으로 걸으며 그 항목의 파일을
 *   바꾼 첫 체인지셋을 찾는다(체인지셋의 바뀐 파일 목록으로 판정한다). 병합 체인지셋은 바꾼 파일 목록이 거의 비어 있어 병합에서 합쳐진 변경은 그 이전의
 *   체인지셋으로 보인다.
 * - 폴더 하나만 담은 폴더는 건너뛰어 `one/two`처럼 합친 이름으로 나온다(Git과 같다). 이 판단은 보고 있는 리비전의 파일로 한다.
 * - 항목의 커밋 번호는 12자 짧은 노드이고, 파일 화면의 커밋 번호는 보고 있는 리비전의 40자 노드이다.
 * - 압축은 hg4j의 zip을 받아 저장소 이름 폴더와 `.hg_archival.txt`를 뺀다(Git의 내려받기와 같은 내용만 담는다).
 */
@Component
class HgCodeReader(
    private val hg: HgRepositories,
    private val userLookup: dev.yona.domain.user.UserLookup,
    private val avatars: AvatarService,
    private val messages: MessageSource,
    private val properties: YonaProperties,
    private val siteUrls: SiteUrls
) : CodeReader {

    override val type: VcsType get() = VcsType.MERCURIAL

    private fun open(project: Project) = hg.open(requireNotNull(project.owner), requireNotNull(project.name))

    /** 커밋이 하나도 없으면 비어 있다. */
    override fun isEmpty(project: Project): Boolean = open(project).use { HgRevisions.tip(it) == null }

    override fun metaData(project: Project, revision: String?, path: String): Map<String, Any?>? = open(project).use { h ->
        val node = HgRevisions.resolve(h, revision) ?: return null
        val files = manifest(h, node)
        val clean = path.trim('/')
        if (clean.isEmpty()) return folder(h, project, node, files, "")
        files.firstOrNull { it.path == clean }?.let { return file(h, project, node, it) }
        if (files.any { it.path.startsWith("$clean/") }) folder(h, project, node, files, clean) else null
    }

    override fun isIntermediateFolder(project: Project, path: String): Boolean = open(project).use { h ->
        val clean = path.trim('/')
        if (clean.isEmpty()) return false
        val node = HgRevisions.head(h) ?: return false
        children(manifest(h, node), clean).let { it.size == 1 && it.values.single() }
    }

    override fun rawFile(project: Project, revision: String, path: String): ByteArray? = open(project).use { h ->
        val node = HgRevisions.resolve(h, revision) ?: return null
        if (manifest(h, node).none { it.path == path }) return null
        h.cat().setFile(path).setRevision(node).call()
    }

    override fun archive(project: Project, branch: String, out: OutputStream) {
        open(project).use { h ->
            val node = HgRevisions.resolve(h, branch) ?: return
            val temp = java.nio.file.Files.createTempDirectory("yona-hg-archive")
            try {
                val zip = File(temp.toFile(), "a.zip")
                // hg4j는 항목마다 `a/`(받는 파일 이름) 접두어를 붙이고 `.hg_archival.txt`를 더한다. 둘 다 빼서 다시 쓴다
                h.archive().setRevision(node).setDestination(zip).setType("zip").call()
                val target = ZipOutputStream(out)
                ZipFile(zip).use { source ->
                    for (entry in source.entries().asSequence()) {
                        val name = entry.name.removePrefix("a/")
                        if (entry.isDirectory || name == ARCHIVAL) continue
                        target.putNextEntry(ZipEntry(name).also { it.time = entry.time })
                        source.getInputStream(entry).use { it.copyTo(target) }
                        target.closeEntry()
                    }
                }
                target.finish()
            } finally {
                temp.toFile().deleteRecursively()
            }
        }
    }

    // --- 폴더 ---

    private fun manifest(h: Hg, node: String): List<ManifestCommand.ManifestEntry> = h.manifest().setRevision(node).call().sortedBy { it.path }

    /** [base] 폴더의 직접 항목이다. 이름에서 폴더이면 `true`이다. */
    private fun children(files: List<ManifestCommand.ManifestEntry>, base: String): SortedMap<String, Boolean> {
        val prefix = if (base.isEmpty()) "" else "$base/"
        val result = TreeMap<String, Boolean>()
        for (entry in files) {
            if (!entry.path.startsWith(prefix)) continue
            val rest = entry.path.substring(prefix.length)
            val name = rest.substringBefore('/')
            result[name] = (result[name] ?: false) || rest.contains('/')
        }
        return result
    }

    private fun folder(h: Hg, project: Project, node: String, files: List<ManifestCommand.ManifestEntry>, base: String): Map<String, Any?>? {
        val names = children(files, base)
        if (names.isEmpty()) return null
        val changelog = HgRevisions.changelog(h)
        val head = HgChangesets.revOf(changelog, node)
        // 항목마다 마지막으로 바꾼 체인지셋을 찾는다: 조상을 최근 순으로 걸으며 항목의 파일을 바꾼 첫 체인지셋이다
        val remaining = LinkedHashMap<String, String>()
        for ((name, isFolder) in names) remaining[name] = (if (base.isEmpty()) name else "$base/$name").let { if (isFolder) "$it/" else it }
        val lastChange = HashMap<String, HgChangeset>()
        for (rev in HgChangesets.ancestors(changelog, head).sortedDescending()) {
            if (remaining.isEmpty()) break
            val changeset = HgChangesets.read(changelog, rev) ?: continue
            val touched = remaining.entries.filter { (_, target) -> changeset.files.any { file -> if (target.endsWith("/")) file.startsWith(target) else file == target } }
            for ((name, _) in touched) { lastChange[name] = changeset; remaining.remove(name) }
        }
        val found: SortedMap<String, Map<String, Any?>> = TreeMap()
        for ((name, isFolder) in names) {
            val changeset = lastChange[name] ?: continue
            val entry = linkedMapOf<String, Any?>("type" to if (isFolder) "folder" else "file")
            describe(entry, changeset, project)
            found[if (isFolder) extendPath(files, base, name) else name] = entry
        }
        if (found.isEmpty()) return null
        return linkedMapOf("type" to "folder", "data" to found)
    }

    /** 폴더 항목에 마지막으로 바꾼 체인지셋의 정보를 채운다. */
    private fun describe(entry: MutableMap<String, Any?>, changeset: HgChangeset, project: Project) {
        val (name, email) = HgAuthors.parse(changeset.author)
        val user = userLookup.findByEmail(email.ifEmpty { null })
        val shortId = changeset.node.take(SHORT_ID)
        entry["msg"] = changeset.message.substringBefore("\n\n").replace('\n', ' ').trim()
        entry["avatar"] = avatar(user, email)
        entry["userName"] = user?.name ?: messages.getMessage("user.notExists.name", null, LocaleContextHolder.getLocale())
        entry["userLoginId"] = user?.loginId.orEmpty()
        entry["createdDate"] = changeset.timestamp * 1000L
        entry["author"] = name
        entry["commitId"] = shortId
        entry["commitUrl"] = siteUrls.path(dev.yona.util.UrlPaths.projectPath(project.owner, project.name) + "/commit/$shortId")
    }

    /**
     * 작성자 아바타 주소이다. 등록하지 않은 커미터는 **커밋 이메일**의 Gravatar(크기 34)이고, 등록한 사용자는 업로드한 아바타 또는 그 사용자 이메일의
     * Gravatar이다(Git과 같다).
     */
    private fun avatar(user: dev.yona.domain.user.User?, commitEmail: String?): String =
        if (user == null) avatars.gravatarUrl(commitEmail, AVATAR_SIZE) else avatars.avatarUrl(user)

    /** 폴더 하나만 담은 폴더를 건너뛰어 이어 붙인 이름이다. 예: `one`이 `two`만 담고 `two`가 `c.txt`를 담으면 `one/two`("folder jumping"). */
    private fun extendPath(files: List<ManifestCommand.ManifestEntry>, base: String, name: String): String {
        var path = name
        while (true) {
            val full = if (base.isEmpty()) path else "$base/$path"
            val inside = children(files, full)
            if (inside.size != 1 || !inside.values.single()) return path
            path = path + "/" + inside.keys.single()
        }
    }

    // --- 파일 ---

    private fun file(h: Hg, project: Project, node: String, entry: ManifestCommand.ManifestEntry): Map<String, Any?> {
        val changelog = HgRevisions.changelog(h)
        val head = HgChangesets.revOf(changelog, node)
        val changeset = HgChangesets.ancestors(changelog, head).sortedDescending().asSequence()
            .mapNotNull { HgChangesets.read(changelog, it) }.firstOrNull { entry.path in it.files } ?: requireNotNull(HgChangesets.read(changelog, head))
        val (authorName, email) = HgAuthors.parse(changeset.author)
        val user = userLookup.findByEmail(email.ifEmpty { null })
        val time = changeset.timestamp * 1000L
        val short = changeset.message.substringBefore("\n\n").replace('\n', ' ').trim()
        val result = linkedMapOf<String, Any?>("type" to "file", "msg" to short)
        result["author"] = authorName
        result["avatar"] = avatar(user, email)
        result["userName"] = user?.name ?: messages.getMessage("user.notExists.name", null, LocaleContextHolder.getLocale())
        result["userLoginId"] = user?.loginId.orEmpty()
        result["createdDate"] = time
        result["commitMessage"] = short
        result["commiter"] = authorName
        result["commitDate"] = time
        // Git처럼 파일 화면의 커밋 번호는 보고 있는 리비전의 전체 번호이다
        result["commitId"] = node
        val bytes = h.cat().setFile(entry.path).setRevision(node).call()
        result["size"] = bytes.size.toLong()
        val binary = dev.yona.io.RawTextBinary.isBinary(bytes)
        result["isBinary"] = binary
        if (!binary && bytes.size <= properties.codeBrowserMaxFileSize) result["data"] = String(bytes, charset(CharsetDetection.detect(bytes)))
        result["mimeType"] = dev.yona.io.MediaTypes.detectRaw(bytes.copyOf(minOf(bytes.size, MIME_SNIFF_BYTES)), entry.path.substringAfterLast('/'))
        return result
    }

    private companion object {
        const val SHORT_ID = 12
        const val AVATAR_SIZE = 34

        /** 파일 종류를 감지할 때 읽는 앞부분의 바이트 수이다. */
        const val MIME_SNIFF_BYTES = 65536

        /** Mercurial이 압축에 더하는 메타데이터 파일 이름이다. */
        const val ARCHIVAL = ".hg_archival.txt"
    }
}
