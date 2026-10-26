package dev.yona.io

import java.io.InputStream

/**
 * 파일 내용이 이진인지 v1.6과 같은 규칙으로 판정한다.
 *
 * v1.6은 JGit 4.5의 `RawText.isBinary`를 쓰는데, 그 규칙은 "앞 8000바이트에 NUL(0x00)이 하나라도 있으면 이진"이 전부이다. v2가 쓰는
 * JGit 7.x는 앞 8192바이트를 보고 CR 다음에 LF가 아닌 문자가 오는 경우, 끝이 CR인 경우도 이진으로 본다. 그러면 옛 Mac 줄바꿈 파일이나
 * 홀로 CR이 든 텍스트가 v1.6과 달리 이진으로 보여 미리보기와 diff가 사라진다. 라이브러리는 건드리지 않고 이 함수로 v1.6 판정을 따른다
 * (P-10 결정 3).
 *
 * v1.6 호출처: Git 파일 보기(`GitRepository.java:345`), Git diff 양쪽(`:1649`, `:1671`), SVN 파일 보기(`SVNRepository.java:178`).
 */
object RawTextBinary {

    /** v1.6(JGit 4.5) `RawText.FIRST_FEW_BYTES`이다. */
    const val FIRST_FEW_BYTES = 8000

    /** 앞 [FIRST_FEW_BYTES]바이트(또는 [length]바이트)에 NUL이 있으면 이진이다. */
    fun isBinary(raw: ByteArray, length: Int = raw.size): Boolean {
        val max = minOf(length, raw.size, FIRST_FEW_BYTES)
        for (i in 0 until max) if (raw[i] == 0.toByte()) return true
        return false
    }

    /** 스트림에서 최대 [FIRST_FEW_BYTES]바이트를 읽어 같은 규칙으로 판정한다. */
    fun isBinary(input: InputStream): Boolean {
        val buffer = ByteArray(FIRST_FEW_BYTES)
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, buffer.size - count)
            if (read == -1) break
            count += read
        }
        return isBinary(buffer, count)
    }
}
