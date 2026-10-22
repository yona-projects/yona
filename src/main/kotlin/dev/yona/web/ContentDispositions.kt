package dev.yona.web

import java.net.URLEncoder

/**
 * 다운로드 파일 이름을 `Content-Disposition`에 넣을 형태로 바꾼다. v1.6 `utils.HttpUtil.encodeContentDisposition`이다.
 *
 * `:`, `\`, `/`, `{`, `?`는 밑줄로 바꾸고, UTF-8로 퍼센트 인코딩하며(공백은 `%20`) `filename*=UTF-8''`을 앞에 붙인다.
 */
object ContentDispositions {

    private val UNSAFE = Regex("[:\\x5c/{?]")

    fun encodeFilename(filename: String): String {
        val safe = UNSAFE.replace(filename, "_")
        return "filename*=UTF-8''" + URLEncoder.encode(safe, Charsets.UTF_8).replace("+", "%20")
    }
}
