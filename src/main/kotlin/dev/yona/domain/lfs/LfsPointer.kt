package dev.yona.domain.lfs

/**
 * Git LFS 포인터 파일이다(추가 개발 항목 X-8). 저장소에는 큰 파일 대신 이 작은 텍스트가 들어간다: `version https://git-lfs.github.com/spec/v1`,
 * `oid sha256:<64자 16진수>`, `size <바이트>` 세 줄(그 밖의 `확장 키`는 있어도 된다). 코드 보기가 포인터 파일을 알아보는 데 쓴다.
 *
 * @property oid 내용의 SHA-256
 * @property size 실제 파일의 바이트 수
 */
data class LfsPointer(val oid: String, val size: Long) {

    companion object {
        private const val VERSION = "https://git-lfs.github.com/spec/v1"

        /** 포인터는 아주 작다. 이보다 크면 포인터로 보지 않는다. */
        private const val MAX_LENGTH = 1024

        /** [text]가 포인터이면 읽은 값을, 아니면 `null`을 돌려준다. */
        fun parse(text: String): LfsPointer? {
            if (text.length > MAX_LENGTH) return null
            val lines = text.split("\r\n", "\n").filter { it.isNotEmpty() }
            if (lines.firstOrNull() != "version $VERSION") return null
            var oid: String? = null; var size: Long? = null
            for (line in lines.drop(1)) {
                val key = line.substringBefore(' '); val value = line.substringAfter(' ', "")
                when (key) {
                    "oid" -> oid = value.removePrefix("sha256:").takeIf { value.startsWith("sha256:") && LfsStorage.validOid(it) }
                    "size" -> size = value.toLongOrNull()?.takeIf { it >= 0 }
                }
            }
            return if (oid != null && size != null) LfsPointer(oid, size) else null
        }
    }
}
