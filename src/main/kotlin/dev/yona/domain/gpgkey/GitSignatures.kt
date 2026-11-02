package dev.yona.domain.gpgkey

/**
 * 서명 한 개와 그 서명이 덮는 바이트이다.
 *
 * @property data 서명 대상 바이트
 * @property signature ASCII armor 서명 글(`-----BEGIN PGP SIGNATURE-----` 또는 SSH 서명 `-----BEGIN SSH SIGNATURE-----`로 시작)
 */
class SignedPayload(val data: ByteArray, val signature: ByteArray)

/**
 * git 개체 원문에서 서명과 서명 대상을 가려낸다(X-4). git이 서명을 만들고 검증할 때와 같은 규칙이다.
 *
 * - 커밋: 머리글 `gpgsig`(이어지는 줄은 공백 하나로 시작)가 서명이고, 그 머리글을 뺀 원문이 서명 대상이다.
 * - 주석 태그: 원문 끝에 붙은 `-----BEGIN PGP SIGNATURE-----`(SSH 서명은 `-----BEGIN SSH SIGNATURE-----`) 블록이 서명이고, 그 앞까지가 서명 대상이다.
 */
object GitSignatures {

    /** 서명 블록이 시작하는 줄이다. OpenPGP 서명과 SSH 서명(`gpg.format=ssh`, D-68)이다. */
    private val BEGIN_MARKERS = listOf("-----BEGIN PGP SIGNATURE-----", "-----BEGIN SSH SIGNATURE-----")
    private val GPGSIG = "gpgsig ".toByteArray()

    /** 커밋 원문 [raw]에서 서명을 가려낸다. 서명이 없으면 `null`이다. 머리글 영역(첫 빈 줄 앞)만 본다. */
    fun ofCommit(raw: ByteArray): SignedPayload? {
        val headerEnd = indexOf(raw, byteArrayOf('\n'.code.toByte(), '\n'.code.toByte()), 0).let { if (it < 0) raw.size else it + 1 }
        var position = 0
        var start = -1
        var end = -1
        val signature = StringBuilder()
        while (position < headerEnd) {
            val lineEnd = raw.indexOf('\n'.code.toByte(), position).let { if (it < 0 || it >= headerEnd) headerEnd else it }
            val inSignature = start >= 0 && end < 0
            if (inSignature) {
                if (raw[position] == ' '.code.toByte()) {
                    signature.append('\n').append(String(raw, position + 1, lineEnd - position - 1, Charsets.UTF_8))
                } else {
                    end = position
                }
            } else if (start < 0 && startsWith(raw, position, GPGSIG)) {
                start = position
                signature.append(String(raw, position + GPGSIG.size, lineEnd - position - GPGSIG.size, Charsets.UTF_8))
            }
            position = lineEnd + 1
        }
        if (start < 0) return null
        if (end < 0) end = minOf(position, headerEnd + 1).coerceAtMost(raw.size)
        val data = raw.copyOfRange(0, start) + raw.copyOfRange(end, raw.size)
        return SignedPayload(data, (signature.toString() + "\n").toByteArray(Charsets.UTF_8))
    }

    /** 주석 태그 원문 [raw]에서 서명을 가려낸다(OpenPGP 또는 SSH). 서명이 없으면 `null`이다. */
    fun ofTag(raw: ByteArray): SignedPayload? {
        val index = BEGIN_MARKERS.maxOf { lastLineStart(raw, it.toByteArray()) }
        if (index < 0) return null
        return SignedPayload(raw.copyOfRange(0, index), raw.copyOfRange(index, raw.size))
    }

    /** [marker]로 시작하는 줄 가운데 마지막 줄의 시작 위치이다. 없으면 -1이다. */
    private fun lastLineStart(raw: ByteArray, marker: ByteArray): Int {
        var index = raw.size
        while (true) {
            index = lastIndexOf(raw, marker, index - 1)
            if (index < 0) return -1
            if (index == 0 || raw[index - 1] == '\n'.code.toByte()) return index
        }
    }

    private fun startsWith(raw: ByteArray, at: Int, prefix: ByteArray) = at + prefix.size <= raw.size && prefix.indices.all { raw[at + it] == prefix[it] }

    private fun ByteArray.indexOf(value: Byte, from: Int): Int {
        for (i in from until size) if (this[i] == value) return i
        return -1
    }

    private fun indexOf(raw: ByteArray, needle: ByteArray, from: Int): Int {
        var i = from
        while (i + needle.size <= raw.size) {
            if (startsWith(raw, i, needle)) return i
            i++
        }
        return -1
    }

    private fun lastIndexOf(raw: ByteArray, needle: ByteArray, from: Int): Int {
        var i = minOf(from, raw.size - needle.size)
        while (i >= 0) {
            if (startsWith(raw, i, needle)) return i
            i--
        }
        return -1
    }
}
