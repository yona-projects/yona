package dev.yona.domain.vcs

/**
 * 푸시로 한 ref(브랜치, 태그 등 이름 붙은 참조)가 바뀐 방식이다. 저장소 종류와 무관하다. Git은 `ReceiveCommand`의 종류에서, Mercurial은 이름표
 * (bookmark) 이동과 체인지그룹 도착에서 바꿔 온다.
 */
enum class RefUpdateType {
    /** ref를 새로 만들었다. */
    CREATE,

    /** 빨리 감기로 갱신했다(이전 끝이 새 끝의 조상이다). */
    UPDATE,

    /** 빨리 감기가 아닌 갱신(강제 푸시)이다. */
    FORCE_UPDATE,

    /** ref를 지웠다. */
    DELETE
}

/**
 * 푸시 한 번에서 ref 하나가 바뀐 일이다. 푸시 전 검사(보호 규칙, 서명 확인)와 푸시 뒤 처리(알림, 웹훅, 이슈 참조, 풀 리퀘스트 검사)가 종류와 무관하게
 * 이 값만 받는다(Mercurial 도입을 위한 공통화 3단계, D-86).
 *
 * 브랜치는 `refs/heads/<이름>`, 태그는 `refs/tags/<이름>` 꼴이다. Mercurial은 이름표(bookmark)를 `refs/heads/<이름>`으로 나타낸다.
 *
 * @property ref ref 전체 이름
 * @property old 바뀌기 전 끝 커밋 번호(40자 16진수). 만들기이면 [NULL_ID]
 * @property new 바뀐 뒤 끝 커밋 번호. 지우기이면 [NULL_ID]
 * @property type 바뀐 방식
 */
data class RefUpdate(val ref: String, val old: String, val new: String, val type: RefUpdateType) {

    /** 새로 만든 ref이면 `true`이다. */
    val isCreate: Boolean get() = type == RefUpdateType.CREATE

    /** 지운 ref이면 `true`이다. */
    val isDelete: Boolean get() = type == RefUpdateType.DELETE

    companion object {
        /** 없음을 뜻하는 커밋 번호(영 40개)이다. Git의 영 번호이고 Mercurial의 null 노드도 같은 모양이다. */
        val NULL_ID: String = "0".repeat(40)
    }
}

/** 푸시로 바뀐 ref 목록에서 ref 이름을 종류별로 모은다. v1.6 `playRepository.hooks.ReceiveCommandUtil`에 해당한다. */
object RefUpdates {

    /** 새로 만들었거나 갱신(빨리 감기가 아닌 갱신 포함)한 ref 이름이다. */
    fun pushed(updates: Collection<RefUpdate>): Set<String> = refNames(updates, RefUpdateType.CREATE, RefUpdateType.UPDATE, RefUpdateType.FORCE_UPDATE)

    /** 지운 ref 이름이다. */
    fun deleted(updates: Collection<RefUpdate>): Set<String> = refNames(updates, RefUpdateType.DELETE)

    /** 갱신한 ref 이름이다. */
    fun updated(updates: Collection<RefUpdate>): Set<String> = refNames(updates, RefUpdateType.UPDATE, RefUpdateType.FORCE_UPDATE)

    private fun refNames(updates: Collection<RefUpdate>, vararg types: RefUpdateType): Set<String> =
        updates.filter { it.type in types }.mapTo(LinkedHashSet()) { it.ref }
}
