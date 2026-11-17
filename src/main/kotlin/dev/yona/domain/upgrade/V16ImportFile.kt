package dev.yona.domain.upgrade

import dev.yona.domain.site.DataService
import org.springframework.stereotype.Component
import tools.jackson.core.JsonToken
import tools.jackson.databind.json.JsonMapper
import java.io.File

/**
 * 이관 명령이 데이터 가져오기 형식의 JSON 파일을 DB에 넣는 단계이다. v1.6이 H2를 쓰던 설치는 H2 2.x가 옛 파일(H2 1.3)을 읽지 못해 DB를 그대로 이어받지 못한다.
 * 그래서 `tools/h2-export`가 옛 H2 파일을 읽어 `/sites/export`와 같은 모양의 JSON으로 바꾸고, 이 단계가 그 파일을 사이트 관리의 데이터 가져오기와 같은 코드([DataService.import])로 넣는다.
 * 이 뒤에 이어지는 데이터 보정([V16DataMigration])은 그대로이다.
 *
 * 가져오기는 대상 테이블을 비우고 파일의 행으로 교체한다. 테이블마다 따로 되돌리므로 일부 테이블만 실패할 수 있고, 그때는 [V16ImportException]으로 어느 테이블인지 알린다.
 */
@Component
class V16ImportFile(private val data: DataService) {

    /**
     * [path]의 파일을 가져온다.
     *
     * @throws V16ImportException 파일이 없거나 읽을 수 없을 때, 또는 가져오지 못한 테이블이 있을 때
     */
    fun import(path: String): V16ImportReport {
        val file = File(path)
        if (!file.isFile) throw V16ImportException("가져오기 파일이 없습니다: $path")
        val result = file.inputStream().buffered().use { data.import(it) }
        if (result.failed.isNotEmpty()) {
            throw V16ImportException("가져오지 못한 테이블이 있습니다: ${result.failed.joinToString(", ")}", result.failed)
        }
        // v2가 모르는 테이블(옛 DB의 스키마 변경 기록, 쓰이지 않는 연결 테이블 등)은 가져오지 않는다. 행이 있는 것은 조용히 버리지 않고 알린다
        val known = result.imported.map { it.uppercase() }.toSet()
        val ignored = tableRowCounts(file).filter { (name, rows) -> rows > 0 && name.uppercase() !in known }
        return V16ImportReport(result.imported, ignored)
    }

    /** 파일의 최상위 테이블 이름과 행 수이다(시퀀스 필드 `<이름>_SEQ`는 뺀다). 행의 내용은 읽지 않고 건너뛰어 큰 파일도 메모리를 쓰지 않는다. */
    private fun tableRowCounts(file: File): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        JsonMapper.builder().build().createParser(file).use { parser ->
            if (parser.nextToken() != JsonToken.START_OBJECT) return counts
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                val name = parser.currentName()
                if (parser.nextToken() == JsonToken.START_ARRAY) {
                    var rows = 0
                    while (parser.nextToken() != JsonToken.END_ARRAY) { rows++; parser.skipChildren() }
                    counts[name] = rows
                } else parser.skipChildren()
            }
        }
        return counts
    }
}

/**
 * 가져오기 파일 단계의 결과이다.
 *
 * @property imported 가져온 테이블
 * @property ignored v2가 모르는 테이블 중 **행이 있어 가져오지 않은 것**과 그 행 수(옛 DB에만 있던 데이터이다. 비어 있는 테이블은 담지 않는다)
 */
data class V16ImportReport(val imported: List<String>, val ignored: Map<String, Int>)

/** 가져오기 파일 단계의 실패이다. [failed]는 가져오지 못한 테이블이다(파일이 없는 경우는 비어 있다). */
class V16ImportException(message: String, val failed: List<String> = emptyList()) : RuntimeException(message)
