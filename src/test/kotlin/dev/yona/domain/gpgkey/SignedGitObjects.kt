package dev.yona.domain.gpgkey

import dev.yona.domain.project.Project
import dev.yona.repository.RepositoryService
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.GpgSignature
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.TagBuilder
import org.eclipse.jgit.lib.TreeFormatter
import java.util.Date
import java.util.TimeZone

/**
 * 시험에서 서명된 커밋과 주석 태그를 저장소에 직접 만든다(X-4). 실제 `gpg` 없이 [TestGpgKey]로 서명한다.
 * 커밋은 [commit]의 `parent`를 주지 않으면 새 뿌리 커밋이다.
 */
object SignedGitObjects {

    private val UTC = TimeZone.getTimeZone("UTC")

    /**
     * [project] 저장소에 [key]로 서명한 커밋을 만들어 [branch]가 가리키게 한다. 커밋 번호를 돌려준다.
     *
     * @param signerKeyId 서명할 키 ID. 비우면 주 키이다
     * @param sign 거짓이면 서명하지 않은 커밋을 만든다
     * @param parent 부모 커밋 번호. 비우면 뿌리 커밋이다
     * @param tamper 참이면 서명한 뒤 메시지를 바꿔 서명이 맞지 않게 한다
     */
    fun commit(
        repositories: RepositoryService, project: Project, key: TestGpgKey?, committerEmail: String, message: String = "signed commit", branch: String = "master",
        signerKeyId: Long? = null, tamper: Boolean = false, time: Long = 1_800_000_000_000L, parent: String? = null
    ): String = repositories.openRepository(project).use { repository ->
        val inserter = repository.newObjectInserter()
        val tree = TreeFormatter().apply { append("a.txt", FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, "a".toByteArray())) }
        val ident = PersonIdent("Committer", committerEmail, Date(time), UTC)
        val builder = CommitBuilder().apply {
            setTreeId(inserter.insert(tree)); author = ident; committer = ident; this.message = message
            parent?.let { setParentId(org.eclipse.jgit.lib.ObjectId.fromString(it)) }
        }
        if (key != null) {
            builder.setGpgSignature(GpgSignature(key.sign(builder.build(), signerKeyId).toByteArray()))
            if (tamper) builder.message = "$message (바뀜)"
        }
        val id = inserter.insert(builder)
        inserter.flush()
        repository.updateRef("refs/heads/$branch").apply { setNewObjectId(id); setForceUpdate(true) }.update()
        if (branch == "master") repository.updateRef(Constants.HEAD, false).link("refs/heads/master")
        id.name
    }

    /** [project] 저장소의 [commitId]에 [key]로 서명한 주석 태그 [name]을 만든다. 태그 개체 번호를 돌려준다. [key]가 `null`이면 서명 없는 주석 태그이다. */
    fun annotatedTag(repositories: RepositoryService, project: Project, key: TestGpgKey?, name: String, taggerEmail: String, commitId: String, time: Long = 1_800_000_100_000L): String =
        repositories.openRepository(project).use { repository ->
            val inserter = repository.newObjectInserter()
            val builder = TagBuilder().apply {
                tag = name; tagger = PersonIdent("Tagger", taggerEmail, Date(time), UTC); message = "릴리스 $name\n"
                setObjectId(repository.resolve(commitId), Constants.OBJ_COMMIT)
            }
            val unsigned = builder.build()
            val bytes = if (key == null) unsigned else unsigned + key.sign(unsigned).toByteArray()
            val id = inserter.insert(Constants.OBJ_TAG, bytes)
            inserter.flush()
            repository.updateRef("refs/tags/$name").apply { setNewObjectId(id); setForceUpdate(true) }.update()
            id.name
        }
}
