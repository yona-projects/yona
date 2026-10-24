package dev.yona.domain.site

/**
 * 의미 있는 버전(semver) 값이다. 업데이트 확인이 현재 버전과 저장소의 태그를 비교할 때 쓴다 (v1.6은 `com.github.zafarkhaja.semver.Version`).
 *
 * 비교는 semver 규칙을 따른다: 주, 부, 수 번호를 숫자로 비교하고, 프리릴리스가 있는 버전은 같은 번호의 정식 버전보다 낮으며,
 * 프리릴리스끼리는 점으로 나눈 식별자를 차례로 비교한다(숫자는 숫자끼리 크기, 숫자는 문자보다 낮음, 앞부분이 같으면 짧은 쪽이 낮음).
 * 빌드 메타데이터(`+`)는 비교에 쓰지 않는다.
 */
class Semver private constructor(
    private val numbers: List<Long>,
    private val preRelease: List<String>
) : Comparable<Semver> {

    override fun compareTo(other: Semver): Int {
        for (i in 0..2) numbers[i].compareTo(other.numbers[i]).let { if (it != 0) return it }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1
        for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
            val a = preRelease[i]
            val b = other.preRelease[i]
            val numeric = a.all { it.isDigit() } to b.all { it.isDigit() }
            val result = when {
                numeric.first && numeric.second -> a.toLong().compareTo(b.toLong())
                numeric.first -> -1
                numeric.second -> 1
                else -> a.compareTo(b)
            }
            if (result != 0) return result
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    override fun equals(other: Any?): Boolean = other is Semver && compareTo(other) == 0

    override fun hashCode(): Int = numbers.hashCode() * 31 + preRelease.hashCode()

    override fun toString(): String = numbers.joinToString(".") + if (preRelease.isEmpty()) "" else "-" + preRelease.joinToString(".")

    companion object {
        private val PATTERN = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        /**
         * 태그나 버전 문자열을 semver 모양으로 바꾼다 (v1.6 `Config.semverize`). 앞의 `v`를 떼고, `.<숫자가 아닌 글자>`는 `-`로 바꾸며,
         * `숫자.숫자`로 끝나거나 그 뒤가 `-`이면 `.0`을 끼운다(`0.5` -> `0.5.0`, `0.4.0.pre` -> `0.4.0-pre`).
         */
        fun semverize(text: String): String {
            var version = text.removePrefix("v")
            version = version.replace(Regex("""\.([^0-9])"""), "-$1")
            return version.replace(Regex("""^(\d+\.\d+)($|-)"""), "$1.0$2")
        }

        /** @throws IllegalArgumentException semver 모양이 아닐 때 */
        fun parse(text: String): Semver {
            val match = requireNotNull(PATTERN.matchEntire(text)) { "버전의 모양이 올바르지 않습니다: $text" }
            return Semver(
                listOf(match.groupValues[1].toLong(), match.groupValues[2].toLong(), match.groupValues[3].toLong()),
                match.groupValues[4].takeIf { it.isNotEmpty() }?.split(".").orEmpty()
            )
        }
    }
}
