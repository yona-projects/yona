package dev.yona.domain.gpgkey

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * git 커밋과 주석 태그에서 서명과 서명 대상 바이트를 가려내는 규칙을 검증한다(X-4 1단계).
 * git은 커밋의 `gpgsig` 머리글을 뺀 나머지를, 태그는 서명 블록 앞까지를 서명 대상으로 한다.
 */
class GitSignaturesTest {

    private val payload = "tree 4b825dc642cb6eb9a060e54bf8d69288fbee4904\nauthor A <a@example.com> 1700000000 +0900\ncommitter A <a@example.com> 1700000000 +0900\n\n메시지\n".toByteArray()
    private val armored = "-----BEGIN PGP SIGNATURE-----\n\niQEzBAABCAAdFiEE\n=abcd\n-----END PGP SIGNATURE-----\n"

    /** git이 하는 대로 서명을 머리글 `gpgsig`로 끼워 넣은 커밋 원문을 만든다. 빈 줄은 공백 하나로 잇는다. */
    private fun commitWith(signature: String): ByteArray {
        val text = payload.toString(Charsets.UTF_8)
        val split = text.indexOf("\n\n")
        val header = text.substring(0, split + 1)
        val folded = signature.trimEnd('\n').split("\n").mapIndexed { index, line -> if (index == 0) "gpgsig $line" else " $line" }.joinToString("\n")
        return (header + folded + "\n" + text.substring(split + 1)).toByteArray()
    }

    @Test
    fun `커밋에서 gpgsig 머리글을 빼면 서명 대상이 되고 서명은 접힘이 풀린다`() {
        val extracted = assertNotNull(GitSignatures.ofCommit(commitWith(armored)))
        assertContentEquals(payload, extracted.data)
        assertEquals(armored.trimEnd('\n'), extracted.signature.toString(Charsets.UTF_8).trimEnd('\n'))
    }

    @Test
    fun `서명이 없는 커밋은 null이다`() {
        assertNull(GitSignatures.ofCommit(payload))
    }

    @Test
    fun `메시지에 서명처럼 보이는 글이 있어도 머리글만 본다`() {
        val tricky = (payload.toString(Charsets.UTF_8) + "gpgsig -----BEGIN PGP SIGNATURE-----\n").toByteArray()
        assertNull(GitSignatures.ofCommit(tricky))
    }

    @Test
    fun `주석 태그는 서명 블록 앞까지가 서명 대상이다`() {
        val tag = "object 4b825dc642cb6eb9a060e54bf8d69288fbee4904\ntype commit\ntag v1\ntagger A <a@example.com> 1700000000 +0900\n\n릴리스\n".toByteArray()
        val extracted = assertNotNull(GitSignatures.ofTag(tag + armored.toByteArray()))
        assertContentEquals(tag, extracted.data)
        assertTrue(extracted.signature.toString(Charsets.UTF_8).startsWith("-----BEGIN PGP SIGNATURE-----"))
        assertNull(GitSignatures.ofTag(tag))
    }
}
