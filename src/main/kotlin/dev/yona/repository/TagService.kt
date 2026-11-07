package dev.yona.repository

import dev.yona.domain.project.Project
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
 * @property taggerEmail 주석 태그를 단 사람의 이메일. 없으면 `null`
 * @property signature 주석 태그의 GPG 서명과 서명 대상 바이트(X-4). 서명이 없으면 `null`
 */
data class TagInfo(
    val name: String, val shortName: String, val annotated: Boolean, val message: String?, val taggerName: String?, val taggerDate: Date?,
    val commitId: String?, val commitDate: Date?, val taggerEmail: String? = null, val signature: dev.yona.domain.gpgkey.SignedPayload? = null
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
 * 태그 목록과 만들기, 삭제를 맡는다(`decisions.md` D-30, D-52). 읽기와 쓰기는 [RefReader]가 맡고([VcsBackends]가 프로젝트의 종류로 고른다),
 * 이 서비스는 지표를 더한다. 태그가 없는 종류(Subversion)는 지원하지 않는 VCS이다.
 */
@Service
class TagService(private val backends: VcsBackends, private val metrics: dev.yona.observability.VcsMetrics) {

    private fun refs(project: Project) = backends.refs(project.vcs)

    /**
     * 저장소의 태그를 태그 시각이 최근인 순서로 모은다. 같으면 이름순이고, 시각을 알 수 없는 태그(커밋이 아닌 대상을 가리키는 경량 태그)는 맨 뒤이다.
     * 주석 태그가 다른 태그를 가리키면(태그의 태그) 끝까지 벗겨 최종 대상을 본다. 커밋이 아닌 대상을 가리켜도 실패하지 않는다.
     */
    fun tags(project: Project): List<TagInfo> = metrics.operation(project.vcs, "tags") { refs(project).tags(project) }

    /** 이름으로 태그 하나를 찾는다. 없으면 `null`이다. */
    fun find(project: Project, shortName: String): TagInfo? = refs(project).findTag(project, shortName)

    /**
     * 태그를 만든다(외부 REST, X-37c). [target]은 브랜치, 태그, 커밋 번호(앞부분만 줘도 된다)이고 없으면 `HEAD`(기본 브랜치의 끝)이다. [message]가 있으면 주석 태그이고
     * 없으면 경량 태그이다. 이름이 참조 이름 규칙(공백, `..` 등)을 어기면 [TagCreateError.INVALID_NAME], 같은 이름이 있으면 [TagCreateError.ALREADY_EXISTS],
     * 대상을 찾지 못하거나 커밋이 아니면 [TagCreateError.NO_TARGET], 커밋이 하나도 없는 저장소의 `HEAD`이면 [TagCreateError.EMPTY_REPOSITORY]이다.
     * 주석 태그의 태그 단 사람은 [tagger]이다.
     */
    fun create(project: Project, name: String, target: String?, message: String?, tagger: TagAuthor): TagCreation = refs(project).createTag(project, name, target, message, tagger)

    /** 태그를 지운다. 그런 태그가 없으면 `false`이다. 브랜치나 다른 참조는 이름이 같아도 지우지 않는다. */
    fun delete(project: Project, shortName: String, remover: TagAuthor? = null): Boolean = refs(project).deleteTag(project, shortName, remover)
}
