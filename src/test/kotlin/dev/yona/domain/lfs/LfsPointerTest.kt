package dev.yona.domain.lfs

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** LFS 포인터 파일 판별(X-8 단계 5)을 검증한다. 규격의 세 줄(`version`, `oid sha256:`, `size`)만 있는 작은 텍스트일 때만 포인터이다. */
class LfsPointerTest {

    private val oid = "4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393"

    @Test
    fun `규격의 포인터를 읽는다`() {
        val pointer = LfsPointer.parse("version https://git-lfs.github.com/spec/v1\noid sha256:$oid\nsize 12345\n")
        assertEquals(LfsPointer(oid, 12345), pointer)
        assertEquals(LfsPointer(oid, 0), LfsPointer.parse("version https://git-lfs.github.com/spec/v1\r\noid sha256:$oid\r\nsize 0"))
    }

    @Test
    fun `추가 키가 있어도 필수 값이 있으면 포인터이다`() {
        assertEquals(LfsPointer(oid, 5), LfsPointer.parse("version https://git-lfs.github.com/spec/v1\next-0-foo sha256:abc\noid sha256:$oid\nsize 5\n"))
    }

    @Test
    fun `포인터가 아닌 내용은 null이다`() {
        for (text in listOf("", "hello", "version https://git-lfs.github.com/spec/v1\noid sha256:$oid\n", "version https://git-lfs.github.com/spec/v1\noid sha256:xyz\nsize 1\n",
            "version https://git-lfs.github.com/spec/v1\noid sha256:$oid\nsize -1\n", "version https://example.com/other\noid sha256:$oid\nsize 1\n", "version https://git-lfs.github.com/spec/v1\noid sha1:$oid\nsize 1\n",
            "version https://git-lfs.github.com/spec/v1\noid sha256:$oid\nsize 1\n" + "x".repeat(2000))) assertNull(LfsPointer.parse(text), text.take(40))
    }
}
