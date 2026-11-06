package dev.yona.domain.vcs

/** 한 파일이 바뀐 방식이다. 저장소 종류와 무관하다. */
enum class FileChangeType { ADD, MODIFY, DELETE, RENAME, COPY }

/** 내용이 저장소가 읽어 줄 수 있는 크기(Git은 JGit의 큰 객체 한도)를 넘는다. 호출한 쪽은 그 파일의 내용을 싣지 않고 오류로 표시한다. */
class ContentTooLargeException : RuntimeException("파일이 너무 커서 읽지 않는다.")

/** 글 비교 알고리즘이다. Git은 저장소 설정 `diff.algorithm`으로 고르고 기본은 [HISTOGRAM]이다. */
enum class TextDiffAlgorithm { HISTOGRAM, MYERS }

/**
 * 한 파일의 변경 하나이다. 변경 목록은 저장소 종류별 구현([dev.yona.repository.DiffReader])이 구하고, 파일 내용은 필요할 때 읽는다.
 * 글로 비교하는 일(바뀐 곳 구하기, 헝크 만들기)은 종류와 무관하게 [dev.yona.repository.diff.DiffService]가 한다.
 *
 * @property type 바뀐 방식
 * @property pathA 바뀌기 전 경로. 추가한 파일이면 `null`
 * @property pathB 바뀐 뒤 경로. 지운 파일이면 `null`
 * @property oldMode 바뀌기 전 파일 모드 비트(예: `0x81A4`는 `100644`). 없으면 0
 * @property newMode 바뀐 뒤 파일 모드 비트. 없으면 0
 * @property contentA 바뀌기 전 내용을 읽는다. 앞쪽이 없으면 `null`이다. 너무 크면 [ContentTooLargeException]
 * @property contentB 바뀐 뒤 내용을 읽는다. 뒤쪽이 없으면 `null`이다. 너무 크면 [ContentTooLargeException]
 */
class FileChange(
    val type: FileChangeType, val pathA: String?, val pathB: String?, val oldMode: Int, val newMode: Int,
    val contentA: () -> ByteArray?, val contentB: () -> ByteArray?
)

/**
 * 두 커밋 사이의 변경 목록이다. 내용을 나중에 읽으므로 저장소를 연 채로 있다가 [close]에서 닫는다.
 *
 * @property commitA 바뀌기 전 커밋 번호. 없으면(첫 커밋) `null`
 * @property commitB 바뀐 뒤 커밋 번호
 * @property changes 바뀐 파일 목록(이름 바꿈과 복사를 찾은 뒤)
 * @property algorithm 글 비교에 쓸 알고리즘
 */
interface DiffSource : AutoCloseable {
    val commitA: String?
    val commitB: String?
    val changes: List<FileChange>
    val algorithm: TextDiffAlgorithm
}
