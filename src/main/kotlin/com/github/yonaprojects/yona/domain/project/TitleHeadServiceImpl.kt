package com.github.yonaprojects.yona.domain.project

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// yona models/TitleHead.java 대응.
@Service
@Transactional
class TitleHeadServiceImpl(
    private val titleHeadRepository: TitleHeadRepository
) : TitleHeadService {

    override fun saveTitleHeadKeyword(project: Project, title: String) {
        for (headKeyword in extractHeaderWordsInBrackets(title)) {
            val trimmed = headKeyword.trim()
            if (isSurroundedByBracket(trimmed)) {
                newHeadKeyword(project, removeBracket(trimmed))
            }
        }
    }

    override fun deleteTitleHeadKeyword(project: Project, title: String) {
        for (headKeyword in extractHeaderWordsInBrackets(title)) {
            val trimmed = headKeyword.trim()
            if (isSurroundedByBracket(trimmed)) {
                reduceHeadKeyword(project, removeBracket(trimmed))
            }
        }
    }

    override fun search(project: Project, query: String): List<TitleHead> {
        return titleHeadRepository.findByProjectIdAndHeadKeywordContainingIgnoreCase(project.id!!, query)
    }

    private fun newHeadKeyword(project: Project, headKeyword: String) {
        val found = titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, headKeyword)
        if (found != null) {
            found.frequency++
            titleHeadRepository.save(found)
        } else {
            titleHeadRepository.save(TitleHead(project = project, headKeyword = headKeyword, frequency = 1))
        }
    }

    // yona TitleHead.reduceHeadKeyword()의 "frequency == 0이면 삭제" 그대로 재현 — 생성/삭제 호출이
    // 짝을 이루지 않는 이례적 상황(예: 데이터 정합성 깨짐)에서 frequency가 음수로 남는 legacy의
    // 관찰 가능한 동작도 그대로 유지한다.
    private fun reduceHeadKeyword(project: Project, headKeyword: String) {
        val found = titleHeadRepository.findByProjectIdAndHeadKeyword(project.id!!, headKeyword) ?: return
        found.frequency--
        if (found.frequency == 0) {
            titleHeadRepository.delete(found)
        } else {
            titleHeadRepository.save(found)
        }
    }

    // yona TemplateHelper.extractHeaderWordsInBrackets()의 `title.split("(=\\[)|(?<=\\])")` 대응.
    // "=[" 리터럴 또는 "]" 직후 위치에서 분할 — 실질적으로는 제목 맨 앞부터 연달아 오는 "[xxx]" 형태의
    // 대괄호 구간만 분리해낸다(중간에 낀 대괄호는 앞의 일반 텍스트와 한 조각으로 묶여 isSurroundedByBracket에서 걸러짐).
    private fun extractHeaderWordsInBrackets(title: String): List<String> {
        return title.split(Regex("(=\\[)|(?<=\\])"))
    }

    private fun isSurroundedByBracket(trimmed: String): Boolean {
        return trimmed.indexOf("[") == 0 && trimmed.indexOf("]") == trimmed.length - 1 && trimmed.length > 2
    }

    private fun removeBracket(trimmed: String): String {
        return trimmed.substring(1, trimmed.length - 1)
    }
}
