package dev.yona.domain.pullrequest

/**
 * 풀 리퀘스트를 병합하는 방식이다(추가 개발 항목 X-28, `decisions.md` D-41). GitHub의 세 가지이다.
 *
 * - [MERGE]: 부모가 둘인 병합 커밋을 만든다(v1.6의 유일한 방식)
 * - [SQUASH]: 같은 결과 트리에 부모가 하나인 커밋 하나를 만든다
 * - [REBASE]: 원래 커밋을 받는 브랜치 위에 순서대로 다시 적용한다
 *
 * @property code 설정과 REST가 쓰는 이름이다
 */
enum class MergeMethod(val code: String) {
    MERGE("merge"), SQUASH("squash"), REBASE("rebase");

    companion object {
        /** [code]에 해당하는 방식이다. 모르는 값이면 `null`이다. */
        fun of(code: String?): MergeMethod? = entries.firstOrNull { it.code == code }
    }
}
