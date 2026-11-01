package dev.yona.domain.wiki

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.repository.CommitInfo
import dev.yona.repository.WikiRepositories
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffConfig
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.revwalk.RenameCallback
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.revwalk.FollowFilter
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.treewalk.filter.PathFilterGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.Date

/** 위키 페이지 목록의 한 줄이다. [name]은 파일 이름에서 `.md`를 뺀 값(공백은 `-`)이고 [title]은 `-`를 공백으로 되돌린 표시용 제목이다. */
data class WikiPageSummary(val name: String, val title: String)

/** 위키 페이지 한 건이다. [lastCommit]은 이 내용을 마지막으로 바꾼 커밋이다. */
data class WikiPage(val name: String, val title: String, val content: String, val lastCommit: CommitInfo?)

/**
 * 프로젝트 위키의 페이지를 만들고 읽고 고치고 지운다 (X-17, D-60).
 *
 * 위키는 프로젝트마다 하나씩 있는 별도 bare git 저장소(`WikiRepositories`)이고, 페이지는 그 저장소 최상위의 `<이름>.md` 파일이다.
 * 제목의 공백은 파일 이름에서 `-`이다(GitHub와 같다). 하위 폴더, 마크다운이 아닌 파일은 1차에서 다루지 않으며 목록에서 숨긴다. 특수 페이지
 * `_Sidebar`와 `_Footer`는 목록에서 빼고 따로 읽는다.
 *
 * 쓰기는 모두 커밋 한 개이다. 이름을 바꿀 때도 옛 파일 삭제와 새 파일 추가가 한 커밋에 담긴다. 권한은 이 서비스가 아니라 호출한 계층(웹, REST)이 확인한다.
 *
 * @param clash 같은 소유자에 `<프로젝트 이름>.wiki`라는 프로젝트가 있어 위키 저장소 위치가 그 코드 저장소와 겹치는지 알려 주는 판정이다.
 */
@Service
class WikiService(
    private val wikis: WikiRepositories,
    private val clash: (owner: String, name: String) -> Boolean,
    private val enabledIn: (Project) -> Boolean = { true },
    private val maxUploadBytes: Long = DEFAULT_MAX_UPLOAD
) {

    @Autowired
    constructor(wikis: WikiRepositories, projects: ProjectRepository, menuSettings: ProjectMenuSettingRepository, properties: YonaProperties) :
        this(
            wikis,
            { owner, name -> projects.findByOwnerIgnoreCaseAndNameIgnoreCase(owner, "$name.wiki") != null },
            { project -> project.id?.let { menuSettings.findFirstByProjectId(it)?.wiki == true } == true },
            minOf(properties.maxFileSize, DEFAULT_MAX_UPLOAD)
        )

    /** 시험에서 커밋 직전에 다른 쓰기를 끼워 넣어 동시 수정 충돌을 재현하는 자리이다. */
    internal var beforeCommit: (() -> Unit)? = null

    /** 제목을 페이지 이름으로 바꾼다. 올바르지 않은 제목이면 `null`이다. 화면과 REST가 주소의 이름을 확인할 때 쓴다. */
    fun pageNameOrNull(title: String): String? = nameOrNull(title)

    /**
     * 프로젝트 설정에서 위키를 켜 두었는지이다(D-61). 끄면 메뉴, 화면, REST, git 복제와 푸시를 호출하는 쪽이 모두 막는다. 이 서비스의 읽기와 쓰기는 막지 않으며
     * 위키 저장소의 내용은 끄는 동안에도 보존된다.
     */
    fun enabled(project: Project): Boolean = enabledIn(project)

    /** 이 프로젝트의 위키를 쓸 수 있는지 알려 준다(저장소 위치가 다른 프로젝트의 코드 저장소와 겹치지 않는지). */
    fun available(project: Project): Boolean = !clash(owner(project), name(project))

    /** 페이지 목록이다. 제목순(대소문자 무시)이고 특수 페이지는 뺀다. 위키가 없거나 쓸 수 없으면 빈 목록이다. */
    fun pages(project: Project): List<WikiPageSummary> = readRepository(project, emptyList()) { repo ->
        val head = repo.resolve(HEAD_COMMIT) ?: return@readRepository emptyList()
        val result = mutableListOf<WikiPageSummary>()
        TreeWalk(repo).use { walk ->
            walk.addTree(RevWalk(repo).use { it.parseCommit(head).tree })
            while (walk.next()) {
                val file = walk.nameString
                if (walk.isSubtree || walk.fileMode != FileMode.REGULAR_FILE || !file.endsWith(EXTENSION)) continue
                val name = file.removeSuffix(EXTENSION)
                if (name in SPECIAL_NAMES) continue
                result += WikiPageSummary(name, titleOf(name))
            }
        }
        result.sortedWith(compareBy({ it.title.lowercase() }, { it.name }))
    }

    /** 제목에 [query]가 들어 있는 페이지이다(대소문자 무시). 비어 있으면 전부이다. */
    fun search(project: Project, query: String): List<WikiPageSummary> {
        val needle = query.trim().lowercase()
        return pages(project).filter { needle.isEmpty() || it.title.lowercase().contains(needle) || it.name.lowercase().contains(needle) }
    }

    /**
     * 페이지 한 건이다. [revision]을 주면 그 시점의 내용이다. 없거나 제목이 올바르지 않거나 위키를 쓸 수 없으면 `null`이다.
     * [title]은 공백 표기와 하이픈 표기를 모두 받는다.
     */
    fun page(project: Project, title: String, revision: String? = null): WikiPage? {
        val name = nameOrNull(title) ?: return null
        return readRepository(project, null) { repo ->
            val commit = (if (revision == null) repo.resolve(HEAD_COMMIT) else resolveCommit(repo, revision)) ?: return@readRepository null
            val bytes = RevWalk(repo).use { walk ->
                TreeWalk.forPath(repo, name + EXTENSION, walk.parseCommit(commit).tree)?.use { if (it.isSubtree) null else repo.open(it.getObjectId(0)).bytes }
            } ?: return@readRepository null
            WikiPage(name, titleOf(name), String(bytes, Charsets.UTF_8), lastCommitOf(repo, commit, name + EXTENSION))
        }
    }

    /** `_Sidebar` 페이지의 내용이다. 없으면 `null`이다. */
    fun sidebar(project: Project): String? = page(project, SIDEBAR)?.content

    /** `_Footer` 페이지의 내용이다. 없으면 `null`이다. */
    fun footer(project: Project): String? = page(project, FOOTER)?.content

    /**
     * 새 페이지를 만든다. 위키 저장소가 아직 없으면 이때 만든다. [message]가 비어 있으면 `Create <이름>`이다.
     *
     * @throws WikiTitleInvalidException 제목이 규칙을 어길 때
     * @throws WikiPageExistsException 같은 이름의 페이지가 있을 때
     * @throws WikiUnavailableException 위키를 쓸 수 없을 때
     */
    fun create(project: Project, actor: User, title: String, content: String, message: String?): WikiPageSummary {
        val name = nameOf(title)
        write(project, actor, createIfMissing = true) { files ->
            if (files.has(name)) throw WikiPageExistsException(name)
            Change(emptySet(), mapOf(name + EXTENSION to content.toByteArray(Charsets.UTF_8)), messageOrDefault(message, "Create $name"))
        }
        return WikiPageSummary(name, titleOf(name))
    }

    /**
     * 페이지를 고친다. [newTitle]이 있고 이름이 달라지면 이름 바꾸기를 같은 커밋에 담는다. 기본 메시지는 `Update <이름>` 또는
     * `Rename <옛 이름> to <새 이름>`이다.
     *
     * @throws WikiPageNotFoundException 고칠 페이지가 없을 때
     * @throws WikiPageExistsException 바꾸려는 이름의 페이지가 이미 있을 때
     */
    fun update(project: Project, actor: User, title: String, newTitle: String?, content: String, message: String?): WikiPageSummary {
        val oldName = nameOrNull(title) ?: throw WikiPageNotFoundException("위키 페이지를 찾을 수 없습니다: $title")
        val newName = if (newTitle.isNullOrBlank()) oldName else nameOf(newTitle)
        write(project, actor, createIfMissing = false) { files ->
            if (!files.has(oldName)) throw WikiPageNotFoundException("위키 페이지를 찾을 수 없습니다: $oldName")
            if (newName != oldName && files.has(newName)) throw WikiPageExistsException(newName)
            if (newName == oldName) {
                Change(emptySet(), mapOf(newName + EXTENSION to content.toByteArray(Charsets.UTF_8)), messageOrDefault(message, "Update $newName"))
            } else {
                Change(setOf(oldName + EXTENSION), mapOf(newName + EXTENSION to content.toByteArray(Charsets.UTF_8)), messageOrDefault(message, "Rename $oldName to $newName"))
            }
        }
        return WikiPageSummary(newName, titleOf(newName))
    }

    /** 페이지를 지운다. 기본 메시지는 `Delete <이름>`이다. 없으면 [WikiPageNotFoundException]이다. */
    fun delete(project: Project, actor: User, title: String, message: String?) {
        val name = nameOrNull(title) ?: throw WikiPageNotFoundException("위키 페이지를 찾을 수 없습니다: $title")
        write(project, actor, createIfMissing = false) { files ->
            if (!files.has(name)) throw WikiPageNotFoundException("위키 페이지를 찾을 수 없습니다: $name")
            Change(setOf(name + EXTENSION), emptyMap(), messageOrDefault(message, "Delete $name"))
        }
    }

    /**
     * 페이지 파일을 바꾼 커밋을 최신순으로 돌려준다. 지워진 페이지의 이력도 나오고, 이름을 바꾼 페이지는 바꾸기 전 이름의 커밋까지 따라간다
     * (`git log --follow`, D-61). 예전 이름으로 부르면 그 이름이 가리키던 때까지의 이력이다. [page]는 0부터 센다.
     */
    fun history(project: Project, title: String, page: Int, size: Int): List<CommitInfo> {
        val name = nameOrNull(title) ?: return emptyList()
        return readRepository(project, emptyList()) { repo ->
            followed(repo, name + EXTENSION).drop(page * size).take(size).map { it.info }
        }
    }

    /**
     * [commitId] 커밋이 이 페이지에 가한 변경을 통합 diff 글로 돌려준다. 첫 커밋이면 전부 추가이고, 이름을 바꾼 커밋은 이름 바꾸기로 보인다. 바꾸기 전 이름의 커밋도
     * 새 이름으로 볼 수 있다. 변경이 없으면 빈 글이다.
     */
    fun diff(project: Project, title: String, commitId: String): String {
        val name = nameOrNull(title) ?: throw WikiPageNotFoundException("위키 페이지를 찾을 수 없습니다: $title")
        return readRepository(project, null) { repo ->
            val commit = resolveCommit(repo, commitId) ?: throw WikiPageNotFoundException("리비전을 찾을 수 없습니다: $commitId")
            val parent = RevWalk(repo).use { walk -> walk.parseCommit(commit).parents.firstOrNull()?.let { walk.parseCommit(it).id } }
            val chain = followed(repo, name + EXTENSION)
            val index = chain.indexOfFirst { it.info.id == commit.name }
            val paths = if (index < 0) listOf(name + EXTENSION) else listOfNotNull(chain[index].path, chain.getOrNull(index + 1)?.path).distinct()
            formatDiff(repo, parent, commit, paths)
        } ?: throw WikiPageNotFoundException("리비전을 찾을 수 없습니다: $commitId")
    }

    /** [revA]에서 [revB]로 가며 이 페이지가 바뀐 내용을 통합 diff 글로 돌려준다. 그 사이에 이름이 바뀌었으면 이름 바꾸기로 보인다. */
    fun compare(project: Project, title: String, revA: String, revB: String): String {
        val name = nameOrNull(title) ?: throw WikiPageNotFoundException("위키 페이지를 찾을 수 없습니다: $title")
        return readRepository(project, null) { repo ->
            val a = resolveCommit(repo, revA) ?: throw WikiPageNotFoundException("리비전을 찾을 수 없습니다: $revA")
            val b = resolveCommit(repo, revB) ?: throw WikiPageNotFoundException("리비전을 찾을 수 없습니다: $revB")
            val paths = followed(repo, name + EXTENSION).map { it.path }.distinct().ifEmpty { listOf(name + EXTENSION) }
            formatDiff(repo, a, b, paths)
        } ?: throw WikiPageNotFoundException("리비전을 찾을 수 없습니다: $revA")
    }

    /** 첨부 파일 한 건이다. [path]는 위키 저장소 안의 경로(`uploads/<이름>`)이고 마크다운에는 이 경로를 적는다. */
    class WikiAttachment(val name: String, val path: String, val size: Long)

    /** 저장소에서 읽은 첨부 파일이다. */
    class WikiFile(val name: String, val bytes: ByteArray)

    /**
     * 첨부 파일을 위키 저장소의 `uploads/<이름>`에 커밋 한 개로 올린다(D-61). 위키 저장소가 없으면 만든다. 이름은 경로와 금지 문자(`\ / : * ? " < > |`, 제어 문자)를 정리하고,
     * 같은 이름이 있으면 `-1`, `-2`를 붙여 덮어쓰지 않는다. 기본 커밋 메시지는 `Upload <이름>`이다.
     *
     * @throws WikiFileInvalidException 정리한 이름이 비어 있거나 `.`으로 시작하거나 200자를 넘을 때
     * @throws WikiFileTooLargeException 크기가 한도(설정과 25MB 중 작은 값)를 넘을 때
     * @throws WikiUnavailableException 위키를 쓸 수 없을 때
     */
    fun upload(project: Project, actor: User, fileName: String, bytes: ByteArray, message: String?): WikiAttachment {
        if (bytes.size > maxUploadBytes) throw WikiFileTooLargeException(maxUploadBytes)
        val name = fileNameOf(fileName)
        var chosen = name
        write(project, actor, createIfMissing = true) { files ->
            var n = 0
            while (files.hasPath("$UPLOADS/$chosen")) chosen = numbered(name, ++n)
            Change(emptySet(), mapOf("$UPLOADS/$chosen" to bytes), messageOrDefault(message, "Upload $chosen"))
        }
        return WikiAttachment(chosen, "$UPLOADS/$chosen", bytes.size.toLong())
    }

    /** `uploads/<이름>`의 첨부 파일이다. 없거나 이름이 폴더를 벗어나려 하면 `null`이다. 위키를 쓸 수 없어도 `null`이다. */
    fun file(project: Project, name: String): WikiFile? {
        if (name.isEmpty() || name == ".." || name == "." || name.contains('/') || name.contains('\\')) return null
        return readRepository(project, null) { repo ->
            val head = repo.resolve(HEAD_COMMIT) ?: return@readRepository null
            val bytes = RevWalk(repo).use { walk ->
                TreeWalk.forPath(repo, "$UPLOADS/$name", walk.parseCommit(head).tree)?.use { if (it.isSubtree) null else repo.open(it.getObjectId(0)).bytes }
            } ?: return@readRepository null
            WikiFile(name, bytes)
        }
    }

    // --- 내부 ---

    private fun owner(project: Project) = requireNotNull(project.owner) { "프로젝트의 소유자가 없습니다." }

    private fun name(project: Project) = requireNotNull(project.name) { "프로젝트의 이름이 없습니다." }

    /** 위키를 쓸 수 있고 저장소가 있을 때만 [block]을 실행한다. 아니면 [fallback]이다. */
    private fun <T> readRepository(project: Project, fallback: T, block: (Repository) -> T): T {
        if (!available(project) || !wikis.exists(owner(project), name(project))) return fallback
        return wikis.open(owner(project), name(project)).use(block)
    }

    private fun resolveCommit(repo: Repository, revision: String): ObjectId? = try {
        repo.resolve("$revision^{commit}")
    } catch (e: Exception) {
        null
    }

    private fun formatDiff(repo: Repository, from: ObjectId?, to: ObjectId, paths: List<String>): String {
        val out = ByteArrayOutputStream()
        RevWalk(repo).use { walk ->
            DiffFormatter(out).use { formatter ->
                formatter.setRepository(repo)
                formatter.pathFilter = if (paths.size == 1) PathFilter.create(paths.single()) else PathFilterGroup.createFromStrings(paths)
                formatter.isDetectRenames = true
                val oldTree = if (from == null) EmptyTreeIterator() else CanonicalTreeParser().apply { reset(repo.newObjectReader(), walk.parseCommit(from).tree) }
                val newTree = CanonicalTreeParser().apply { reset(repo.newObjectReader(), walk.parseCommit(to).tree) }
                formatter.format(formatter.scan(oldTree, newTree))
            }
        }
        return out.toString(Charsets.UTF_8)
    }

    /** 이름 바꾸기를 따라간 이력의 한 줄이다. [path]는 그 커밋 시점의 파일 경로이다. */
    private class Followed(val info: CommitInfo, val path: String)

    /**
     * [file]을 바꾼 커밋을 최신순으로 모으되 이름 바꾸기를 따라간다(`git log --follow`). 이력은 한 줄기라고 본다. 따라가다 이름이 바뀌는 커밋을 만나면 그 이전 커밋의 경로는
     * 옛 이름이다.
     */
    private fun followed(repo: Repository, file: String): List<Followed> {
        val head = repo.resolve(HEAD_COMMIT) ?: return emptyList()
        val filter = FollowFilter.create(file, repo.config.get(DiffConfig.KEY))
        // 이름 바꾸기를 만나면 JGit이 따라갈 경로를 옛 이름으로 바꾸고 알려 준다(필터 객체의 경로는 처음 값 그대로이다)
        var path = file
        filter.renameCallback = object : RenameCallback() {
            override fun renamed(entry: DiffEntry) {
                path = entry.oldPath
            }
        }
        val result = mutableListOf<Followed>()
        RevWalk(repo).use { walk ->
            walk.treeFilter = filter
            walk.markStart(walk.parseCommit(head))
            // 반복자는 다음 커밋을 미리 읽어 이름 바꾸기 알림이 한 칸 먼저 오므로 `next()`를 직접 부른다
            while (true) {
                val before = path
                val commit = walk.next() ?: break
                result += Followed(info(commit), before)
            }
        }
        return result
    }

    private fun lastCommitOf(repo: Repository, at: ObjectId, path: String): CommitInfo? =
        Git(repo).log().add(at).addPath(path).setMaxCount(1).call().firstOrNull()?.let { info(it) }

    private fun info(commit: RevCommit) = CommitInfo(
        id = commit.name, shortId = commit.abbreviate(7).name(), shortMessage = commit.shortMessage, message = commit.fullMessage,
        authorName = commit.authorIdent.name, authorEmail = commit.authorIdent.emailAddress, authorDate = commit.authorIdent.`when`,
        committerName = commit.committerIdent.name, committerEmail = commit.committerIdent.emailAddress, commitTime = Date(commit.commitTime * 1000L), parentCount = commit.parentCount
    )

    /** 쓰기 계획이 읽는 현재 위키 파일 목록이다. */
    private class Files(private val names: Set<String>) {
        fun has(pageName: String) = (pageName + EXTENSION) in names

        /** 저장소에 그 경로의 파일이 있는지이다(첨부 파일처럼 페이지가 아닌 파일에 쓴다). */
        fun hasPath(path: String) = path in names
    }

    /** 한 커밋에 담을 변경이다. [remove]는 지울 경로, [put]은 쓸 경로와 내용이다. */
    private class Change(val remove: Set<String>, val put: Map<String, ByteArray>, val message: String)

    /**
     * 위키 저장소에 커밋 한 개를 만든다. 저장소를 연 시점의 `HEAD`를 기준으로 [plan]을 세우고, 갱신할 때 그 `HEAD`가 그대로인지 확인한다.
     * 그 사이 다른 커밋이 들어왔으면 [WikiConflictException]이다.
     */
    private fun write(project: Project, actor: User, createIfMissing: Boolean, plan: (Files) -> Change) {
        if (!available(project)) throw WikiUnavailableException("이 프로젝트의 위키 저장소는 같은 이름의 다른 프로젝트와 겹쳐 쓸 수 없습니다.")
        val owner = owner(project)
        val name = name(project)
        if (!wikis.exists(owner, name)) {
            // 없는 위키에는 읽을 파일이 없다. 만들지 않는 쓰기는 계획이 알아서 "없음"으로 실패한다.
            if (!createIfMissing) {
                plan(Files(emptySet()))
                return
            }
            wikis.create(owner, name)
        }
        wikis.open(owner, name).use { repo ->
            val head = repo.resolve(HEAD_COMMIT)
            val existing = rootFiles(repo, head)
            val change = plan(Files(existing.keys))
            beforeCommit?.invoke()
            val identity = PersonIdent(actor.name?.takeIf { it.isNotBlank() } ?: actor.loginId.orEmpty(), actor.email.orEmpty())
            repo.newObjectInserter().use { inserter ->
                val index = DirCache.newInCore()
                val builder = index.builder()
                existing.forEach { (path, entry) ->
                    if (path in change.remove || path in change.put) return@forEach
                    builder.add(DirCacheEntry(path).apply { fileMode = entry.mode; setObjectId(entry.id) })
                }
                change.put.forEach { (path, bytes) ->
                    builder.add(DirCacheEntry(path).apply {
                        setLength(bytes.size.toLong())
                        setLastModified(java.time.Instant.now())
                        fileMode = FileMode.REGULAR_FILE
                        setObjectId(inserter.insert(Constants.OBJ_BLOB, bytes))
                    })
                }
                builder.finish()
                val commitId = inserter.insert(org.eclipse.jgit.lib.CommitBuilder().apply {
                    author = identity
                    committer = identity
                    setEncoding(Charsets.UTF_8)
                    message = change.message
                    if (head != null) setParentId(head)
                    setTreeId(index.writeTree(inserter))
                })
                inserter.flush()
                val ref = repo.exactRef(Constants.HEAD)?.target?.name ?: (Constants.R_HEADS + Constants.MASTER)
                val update = repo.updateRef(ref).apply {
                    setExpectedOldObjectId(head ?: ObjectId.zeroId())
                    setNewObjectId(commitId)
                    setRefLogMessage("wiki: ${change.message.lineSequence().firstOrNull().orEmpty()}", false)
                }
                when (val result = update.forceUpdate()) {
                    RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.FAST_FORWARD -> Unit
                    else -> throw WikiConflictException("위키가 그 사이에 바뀌어 저장하지 못했습니다. 다시 불러온 뒤 고쳐 주세요. ($result)")
                }
            }
        }
    }

    private class Entry(val mode: FileMode, val id: ObjectId)

    /** [head] 시점의 트리 전체(하위 폴더 포함)를 경로별로 읽는다. 하위 폴더 파일도 새 커밋에 그대로 남기려고 전부 읽는다. */
    private fun rootFiles(repo: Repository, head: ObjectId?): Map<String, Entry> {
        if (head == null) return emptyMap()
        val result = linkedMapOf<String, Entry>()
        RevWalk(repo).use { walk ->
            TreeWalk(repo).use { tree ->
                tree.addTree(walk.parseCommit(head).tree)
                tree.isRecursive = true
                while (tree.next()) result[tree.pathString] = Entry(tree.fileMode, tree.getObjectId(0))
            }
        }
        return result
    }

    private fun messageOrDefault(message: String?, default: String) = message?.trim()?.takeIf { it.isNotEmpty() } ?: default

    companion object {
        private const val EXTENSION = ".md"
        private const val UPLOADS = "uploads"

        /** 첨부 파일의 기본 크기 한도이다(25MB). 설정의 첨부 한도보다 크면 설정을 따른다. 위키는 git 저장소라 파일이 커지면 clone이 느려진다(A-315). */
        const val DEFAULT_MAX_UPLOAD = 25L * 1024 * 1024
        private const val HEAD_COMMIT = "HEAD^{commit}"
        private const val SIDEBAR = "_Sidebar"
        private const val FOOTER = "_Footer"
        private val SPECIAL_NAMES = setOf(SIDEBAR, FOOTER)

        /** 제목에 쓸 수 없는 문자이다(GitHub 문서의 목록). */
        private val FORBIDDEN = Regex("[\\\\/:*?\"<>|]")
        private const val MAX_TITLE = 200

        private val FILE_FORBIDDEN = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

        /** 첨부 파일 이름을 정리한다: 경로를 떼고 금지 문자를 `_`로 바꾼다. 비었거나 `.`으로 시작하거나 200자를 넘으면 예외이다. */
        fun fileNameOf(raw: String): String {
            val name = FILE_FORBIDDEN.replace(raw.substringAfterLast('/').substringAfterLast('\\').trim(), "_").trim()
            if (name.isEmpty() || name.startsWith(".")) throw WikiFileInvalidException("올릴 수 없는 파일 이름입니다.")
            if (name.length > MAX_TITLE) throw WikiFileInvalidException("파일 이름은 ${MAX_TITLE}자를 넘을 수 없습니다.")
            return name
        }

        private fun numbered(name: String, n: Int): String {
            val dot = name.lastIndexOf('.')
            return if (dot > 0) name.substring(0, dot) + "-$n" + name.substring(dot) else "$name-$n"
        }

        /** 표시용 제목이다. 파일 이름의 `-`를 공백으로 되돌린다. */
        fun titleOf(name: String) = name.replace('-', ' ')

        /**
         * 제목을 파일 이름 부분(페이지 이름)으로 바꾼다. 앞뒤 공백을 지우고 공백 하나하나를 `-`로 바꾼다.
         *
         * @throws WikiTitleInvalidException 비어 있거나 금지 문자, 제어 문자가 있거나, `_Sidebar`와 `_Footer`가 아닌 `_`로 시작하거나,
         *   점만으로 되어 있거나, 200자를 넘을 때
         */
        fun nameOf(title: String): String {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) throw WikiTitleInvalidException("제목이 비어 있습니다.")
            if (trimmed.length > MAX_TITLE) throw WikiTitleInvalidException("제목은 ${MAX_TITLE}자를 넘을 수 없습니다.")
            if (FORBIDDEN.containsMatchIn(trimmed)) throw WikiTitleInvalidException("제목에 쓸 수 없는 문자가 있습니다: \\ / : * ? \" < > |")
            if (trimmed.any { it.isISOControl() }) throw WikiTitleInvalidException("제목에 제어 문자를 쓸 수 없습니다.")
            val name = trimmed.replace(Regex("\\s"), "-")
            if (name.all { it == '.' }) throw WikiTitleInvalidException("제목이 올바르지 않습니다.")
            if (name.startsWith("_") && name !in SPECIAL_NAMES) throw WikiTitleInvalidException("밑줄로 시작하는 제목은 예약되어 있습니다.")
            return name
        }

        private fun nameOrNull(title: String): String? = try {
            nameOf(title)
        } catch (e: WikiTitleInvalidException) {
            null
        }
    }
}
