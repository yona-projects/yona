package dev.yona.repository

import dev.yona.domain.project.Project
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk
import org.springframework.stereotype.Service
import java.util.Date

/**
 * 태그 한 줄이다. v1.6에 없는 전용 화면의 값이다(로드맵 X-18, `decisions.md` D-30).
 *
 * @property name 참조 전체 이름(`refs/tags/v1.0`)
 * @property shortName 화면에 보여 줄 이름. `refs/tags/` 접두어를 뗀다. 슬래시가 들어 있을 수 있다(`release/1.0`)
 * @property annotated 주석 태그이면 `true`, 경량 태그이면 `false`
 * @property message 주석 태그의 메시지 전체. 경량 태그는 `null`
 * @property taggerName 주석 태그를 단 사람. 없으면 `null`
 * @property taggerDate 주석 태그를 단 시각. 없으면 `null`
 * @property commitId 태그가 (주석 태그를 모두 벗긴 끝에) 가리키는 커밋 번호. 커밋이 아니면 `null`
 * @property commitDate 그 커밋의 커밋 시각. 커밋이 아니면 `null`
 */
data class TagInfo(
    val name: String, val shortName: String, val annotated: Boolean, val message: String?, val taggerName: String?, val taggerDate: Date?,
    val commitId: String?, val commitDate: Date?
) {
    /** 7자리 커밋 번호이다. */
    val commitShortId: String? get() = commitId?.take(7)

    /** 커밋이 아닌 대상(트리, 블롭)을 가리키는 태그이면 `true`이다. */
    val notCommit: Boolean get() = commitId == null

    /** 메시지의 첫 줄이다. 메시지가 없으면 `null`이다. */
    val shortMessage: String? get() = message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()

    /** 정렬에 쓰는 시각이다. 주석 태그는 태그를 단 시각, 없으면 커밋 시각이다. */
    val sortDate: Date? get() = taggerDate ?: commitDate
}

/** 태그를 만들지 못한 이유이다(외부 REST, X-37c). */
enum class TagCreateError { INVALID_NAME, ALREADY_EXISTS, NO_TARGET, EMPTY_REPOSITORY }

/** 태그 만들기 결과이다. 성공하면 [tag]가 있고 실패하면 [error]가 있다. */
data class TagCreation(val tag: TagInfo?, val error: TagCreateError?)

/**
 * 태그 목록과 만들기, 삭제를 맡는다(`decisions.md` D-30, D-52). 참조를 읽는 부분은 [BranchService]와 같은 방식이며 종류만 다르다.
 */
@Service
class TagService(private val repositories: RepositoryService) {

    /**
     * 저장소의 태그를 태그 시각이 최근인 순서로 모은다. 같으면 이름순이고, 시각을 알 수 없는 태그(커밋이 아닌 대상을 가리키는 경량 태그)는 맨 뒤이다.
     * 주석 태그가 다른 태그를 가리키면(태그의 태그) 끝까지 벗겨 최종 대상을 본다. 커밋이 아닌 대상을 가리켜도 실패하지 않는다.
     */
    fun tags(project: Project): List<TagInfo> = repositories.openRepository(project).use { repository ->
        RevWalk(repository).use { walk ->
            repository.refDatabase.refs.filter { it.name.startsWith(Constants.R_TAGS) && it.objectId != null }
                .mapNotNull { ref -> runCatching { info(walk, ref.name, ref.objectId) }.getOrNull() }
                .sortedWith(compareBy<TagInfo>({ it.sortDate == null }, { -(it.sortDate?.time ?: 0L) }, { it.shortName }))
        }
    }

    /** 이름으로 태그 하나를 찾는다. 없으면 `null`이다. */
    fun find(project: Project, shortName: String): TagInfo? = repositories.openRepository(project).use { repository ->
        val ref = repository.exactRef(Constants.R_TAGS + shortName) ?: return null
        val id = ref.objectId ?: return null
        RevWalk(repository).use { walk -> runCatching { info(walk, ref.name, id) }.getOrNull() }
    }

    /**
     * 태그를 만든다(외부 REST, X-37c). [target]은 브랜치, 태그, 커밋 번호(앞부분만 줘도 된다)이고 없으면 `HEAD`(기본 브랜치의 끝)이다. [message]가 있으면 주석 태그이고
     * 없으면 경량 태그이다. 이름이 참조 이름 규칙(공백, `..` 등)을 어기면 [TagCreateError.INVALID_NAME], 같은 이름이 있으면 [TagCreateError.ALREADY_EXISTS],
     * 대상을 찾지 못하거나 커밋이 아니면 [TagCreateError.NO_TARGET], 커밋이 하나도 없는 저장소의 `HEAD`이면 [TagCreateError.EMPTY_REPOSITORY]이다.
     * 주석 태그의 태그 단 사람은 [tagger]이다.
     */
    fun create(project: Project, name: String, target: String?, message: String?, tagger: org.eclipse.jgit.lib.PersonIdent): TagCreation = repositories.openRepository(project).use { repository ->
        if (!org.eclipse.jgit.lib.Repository.isValidRefName(Constants.R_TAGS + name)) return TagCreation(null, TagCreateError.INVALID_NAME)
        if (repository.exactRef(Constants.R_TAGS + name) != null) return TagCreation(null, TagCreateError.ALREADY_EXISTS)
        val id = runCatching { repository.resolve(target ?: Constants.HEAD) }.getOrNull()
            ?: return TagCreation(null, if (target == null) TagCreateError.EMPTY_REPOSITORY else TagCreateError.NO_TARGET)
        val commit = RevWalk(repository).use { walk -> runCatching { walk.parseCommit(id) }.getOrNull() } ?: return TagCreation(null, TagCreateError.NO_TARGET)
        Git(repository).tag().setName(name).setObjectId(commit).setAnnotated(message != null).apply { if (message != null) { setMessage(message); setTagger(tagger) } }.call()
        TagCreation(find(project, name), null)
    }

    /** 태그를 지운다. 그런 태그가 없으면 `false`이다. 브랜치나 다른 참조는 이름이 같아도 지우지 않는다. */
    fun delete(project: Project, shortName: String): Boolean = repositories.openRepository(project).use { repository ->
        if (repository.exactRef(Constants.R_TAGS + shortName) == null) return false
        Git(repository).tagDelete().setTags(Constants.R_TAGS + shortName).call()
        true
    }

    private fun info(walk: RevWalk, name: String, id: org.eclipse.jgit.lib.ObjectId): TagInfo {
        var target = walk.parseAny(id)
        var first: RevTag? = null
        while (target is RevTag) {
            if (first == null) first = target
            target = walk.parseAny(target.`object`)
        }
        val commit = target as? RevCommit
        return TagInfo(
            name = name, shortName = name.removePrefix(Constants.R_TAGS), annotated = first != null, message = first?.fullMessage,
            taggerName = first?.taggerIdent?.name, taggerDate = first?.taggerIdent?.`when`, commitId = commit?.name, commitDate = commit?.committerIdent?.`when`
        )
    }
}
