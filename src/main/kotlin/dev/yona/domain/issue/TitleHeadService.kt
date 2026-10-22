package dev.yona.domain.issue

import dev.yona.domain.project.Project
import dev.yona.domain.project.TitleHead
import dev.yona.domain.project.TitleHeadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 글 제목 앞의 `[머리말]` 키워드를 세어 둔다. 제목 입력란의 자동 완성이 쓴다.
 * v1.6 `TitleHead`(`app/models/TitleHead.java`)에 해당한다.
 */
@Service
class TitleHeadService(private val titleHeads: TitleHeadRepository) {

    /** 제목의 머리말마다 개수를 하나 늘린다. 처음 보는 키워드는 1로 만든다. */
    @Transactional
    fun save(project: Project, title: String?) {
        keywords(title).forEach { keyword ->
            val found = titleHeads.findFirstByProjectIdAndHeadKeyword(requireNotNull(project.id), keyword)
            if (found != null) {
                found.frequency = (found.frequency ?: 0) + 1
                titleHeads.save(found)
            } else {
                titleHeads.save(TitleHead(project = project, headKeyword = keyword, frequency = 1))
            }
        }
    }

    /** 제목의 머리말마다 개수를 하나 줄이고 0이 되면 지운다. */
    @Transactional
    fun delete(project: Project, title: String?) {
        keywords(title).forEach { keyword ->
            val found = titleHeads.findFirstByProjectIdAndHeadKeyword(requireNotNull(project.id), keyword) ?: return@forEach
            val frequency = (found.frequency ?: 0) - 1
            if (frequency == 0) titleHeads.delete(found) else { found.frequency = frequency; titleHeads.save(found) }
        }
    }

    /** 프로젝트의 키워드 중 [query]가 들어 있는 것이다. */
    @Transactional(readOnly = true)
    fun search(project: Project, query: String): List<TitleHead> = titleHeads.search(requireNotNull(project.id), query)

    /** 제목에서 대괄호로 감싼 머리말(`[버그]`의 `버그`)만 뽑는다. 비어 있는 `[]`는 뺀다. */
    private fun keywords(title: String?): List<String> = TitleHeadWords.extract(title.orEmpty()).map { it.trim() }
        .filter { it.indexOf("[") == 0 && it.indexOf("]") == it.length - 1 && it.length > 2 }
        .map { it.substring(1, it.length - 1) }
}

/** 제목을 `=[`와 `]` 뒤에서 잘라 머리말 후보로 나눈다 (v1.6 `TemplateHelper.extractHeaderWordsInBrackets`). */
object TitleHeadWords {
    private val SPLITTER = Regex("(=\\[)|(?<=\\])")

    fun extract(title: String): List<String> = title.split(SPLITTER)
}
