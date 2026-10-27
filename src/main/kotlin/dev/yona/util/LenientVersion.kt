package dev.yona.util

/**
 * 세 자리 버전을 앞에서부터 읽는 관대한 파서이다. v1.6이 쓰던 `java-semver` 0.7.2의 `VersionParser`를 옮겼다.
 *
 * 세 자리(`major.minor.patch`) 뒤에 `-`나 `+`가 아닌 글자가 오면 그 뒤는 읽지 않고 버린다(`2.20.1 (Apple Git-117)`은 `2.20.1`이다).
 * 숫자 식별자는 앞에 0을 둘 수 없고, 정수 범위를 넘으면 읽기에 실패한다. 읽지 못하면 [parse]는 `null`을 돌려준다
 * (v1.6은 예외를 잡아 거짓으로 처리했다).
 */
class LenientVersion private constructor(
    private val numbers: IntArray,
    private val preRelease: List<String>?
) : Comparable<LenientVersion> {

    /** 세 자리 숫자를 비교하고, 같으면 사전 배포판(`-rc1`)이 정식 배포판보다 낮다. 사전 배포판끼리는 식별자를 차례로 비교한다. */
    override fun compareTo(other: LenientVersion): Int {
        for (i in 0 until 3) if (numbers[i] != other.numbers[i]) return numbers[i].compareTo(other.numbers[i])
        val a = preRelease
        val b = other.preRelease
        if (a == null && b == null) return 0
        if (a == null) return 1
        if (b == null) return -1
        for (i in 0 until minOf(a.size, b.size)) {
            val r = compareIdentifier(a[i], b[i])
            if (r != 0) return r
        }
        return a.size.compareTo(b.size)
    }

    private fun compareIdentifier(a: String, b: String): Int {
        val x = a.toIntOrNull()
        val y = b.toIntOrNull()
        return if (x != null && y != null) x.compareTo(y) else a.compareTo(b)
    }

    companion object {

        /** 읽을 수 있으면 버전을, 아니면 `null`을 돌려준다. */
        fun parse(text: String): LenientVersion? = try {
            Reader(text).read()
        } catch (e: IllegalStateException) {
            null
        } catch (e: NumberFormatException) {
            null
        }

        fun of(major: Int, minor: Int, patch: Int) = LenientVersion(intArrayOf(major, minor, patch), null)
    }

    private class Reader(private val s: String) {
        private var o = 0

        private fun la(k: Int = 1): Char? = (o + k - 1).let { if (it < s.length) s[it] else null }
        private fun digit(c: Char?) = c != null && c in '0'..'9'
        private fun letter(c: Char?) = c != null && (c in 'a'..'z' || c in 'A'..'Z')
        private fun hyphen(c: Char?) = c == '-'

        private fun consume(test: (Char?) -> Boolean): Char {
            val c = la()
            check(test(c)) { "예상하지 못한 글자이다" }
            o++
            return c!!
        }

        private fun numeric(): String {
            val a = la()
            check(a != null) { "끝이다" }
            check(!(a == '0' && digit(la(2)))) { "앞자리 0이다" }
            return digits()
        }

        private fun digits(): String {
            val sb = StringBuilder().append(consume(::digit))
            while (digit(la())) sb.append(s[o++])
            return sb.toString()
        }

        private fun alnum(): String {
            val t = { c: Char? -> digit(c) || letter(c) || hyphen(c) }
            val sb = StringBuilder().append(consume(t))
            while (t(la())) sb.append(s[o++])
            return sb.toString()
        }

        /** [before]를 만나기 전에 [any]가 나오는지 앞을 내다본다. */
        private fun beforeFind(before: (Char?) -> Boolean, any: (Char?) -> Boolean): Boolean {
            for (i in 1..s.length) {
                val c = la(i)
                if (before(c)) break
                if (any(c)) return true
            }
            return false
        }

        private fun meta(pre: Boolean): List<String> {
            val plus = { c: Char? -> c == '+' }
            val eol = { c: Char? -> c == null }
            val dot = { c: Char? -> c == '.' }
            val end = if (pre) (if (beforeFind(eol, plus)) plus else eol) else eol
            var before = if (beforeFind(end, dot)) dot else end
            val ids = mutableListOf<String>()
            while (!end(la())) {
                check(!(before === dot && dot(la()))) { "빈 식별자이다" }
                ids += if (beforeFind(before) { letter(it) || hyphen(it) }) alnum() else if (pre) numeric() else digits()
                if (before === dot) o++
                before = if (beforeFind(end, dot)) dot else end
            }
            return ids
        }

        fun read(): LenientVersion {
            val n = IntArray(3)
            n[0] = numeric().toInt(); consume { it == '.' }
            n[1] = numeric().toInt(); consume { it == '.' }
            n[2] = numeric().toInt()
            var pre: List<String>? = null
            if (hyphen(la())) { o++; pre = meta(true) }
            if (la() == '+') { o++; meta(false) }
            return LenientVersion(n, pre)
        }
    }
}
