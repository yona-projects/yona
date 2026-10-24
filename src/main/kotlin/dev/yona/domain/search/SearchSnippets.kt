package dev.yona.domain.search

/**
 * 검색 결과에 보여 줄 본문 발췌를 만든다. v1.6 `SearchResult.makeSnippets`(`app/models/SearchResult.java:54-107`)에 해당한다.
 *
 * 대소문자를 구분하지 않고 겹치지 않게 키워드가 나오는 곳을 모두 찾아 앞뒤 [threshold]자를 한 조각으로 만든다. 앞 조각과 이어지거나
 * 겹치면 하나로 합친다. 조각은 원문의 대소문자를 그대로 쓴다.
 */
object SearchSnippets {

    /**
     * @param contents 본문. `null`이면 조각이 없다(v1.6은 오류가 난다)
     * @param keyword 검색어
     * @param threshold 키워드 앞뒤로 포함할 글자 수
     * @return 본문 순서대로의 조각. 키워드가 없으면 빈 목록이다
     */
    fun make(contents: String?, keyword: String, threshold: Int): List<String> {
        if (contents == null) return emptyList()
        val lowerContents = contents.lowercase()
        val lowerKeyword = keyword.lowercase()
        val ranges = ArrayList<IntArray>()
        var index = lowerContents.indexOf(lowerKeyword)
        while (index != -1) {
            val begin = if (index < threshold) 0 else index - threshold
            val end = minOf(index + lowerKeyword.length + threshold, lowerContents.length)
            val latest = ranges.lastOrNull()
            if (latest != null && latest[1] >= begin) latest[1] = end else ranges += intArrayOf(begin, end)
            index = lowerContents.indexOf(lowerKeyword, index + lowerKeyword.length)
        }
        return ranges.map { contents.substring(it[0], it[1]) }
    }
}
