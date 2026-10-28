package dev.yona.domain.pullrequest

/**
 * 풀 리퀘스트 병합 때 자동으로 닫을 이슈 번호를 글에서 뽑는다. v1.6에 없는 추가 항목이다 (로드맵 X-11, `decisions.md` A-188).
 *
 * `close`, `fix`, `resolve`와 그 활용형(`closes`, `closed`, `fixes`, `fixed`, `resolves`, `resolved`) 뒤에 공백과 `#번호`가 붙은 것만
 * 닫는다는 뜻으로 읽는다. 대소문자는 가리지 않는다. 그냥 언급한 `#번호`나 `closing #1` 같은 말은 세지 않는다.
 * `next`의 정규식은 문자 집합 표기 실수(`fix[e[s|d]]?`) 때문에 `fixes`, `fixed`를 알아보지 못해 의도한 활용형으로 바로잡았다.
 */
object PullRequestIssueReference {

    private val pattern = Regex("""(?<![A-Za-z])(?:close[sd]?|fix(?:es|ed)?|resolve[sd]?)\s+#(\d+)""", RegexOption.IGNORE_CASE)

    /** [texts]에서 닫는 말로 가리킨 이슈 번호를 모두 모은다. 같은 번호는 한 번만 담고, 숫자가 아주 커서 `Long`에 맞지 않는 번호는 건너뛴다. */
    fun closingNumbers(texts: Iterable<String?>): Set<Long> =
        texts.filterNotNull().flatMap { text -> pattern.findAll(text).mapNotNull { it.groupValues[1].toLongOrNull() }.toList() }.toSet()
}
