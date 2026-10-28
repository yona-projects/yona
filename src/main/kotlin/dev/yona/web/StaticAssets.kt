package dev.yona.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * `static/assets/` 아래 정적 자원을 v1.6 Play 2.3 `Assets.at`과 같은 응답으로 내보낸다.
 *
 * - 헤더는 `Cache-Control: public, max-age=3600`, 강한 `ETag`(`sha1("<Last-Modified> -> <자원 주소>")`), `Last-Modified`이다. `Accept-Ranges`는 없고 `Range`는 무시한다.
 * - `If-None-Match`가 ETag와 같으면 위 세 헤더만 담은 `304`이고, 없을 때 `If-Modified-Since`가 수정 시각 이후이면 헤더 없는 `304`이다.
 * - 없는 자원, 폴더, 상위 경로(`..`)는 본문도 헤더도 없는 `404`이다.
 * - 내용 형식은 Play의 확장자 표를 따른다. 글자 계열은 `; charset=utf-8`이 붙고 표에 없는 확장자는 `application/octet-stream`이다.
 */
@Component
class StaticAssets {

    /** [relativePath]는 `assets/` 아래의 경로이다(앞의 `/` 없음). */
    fun serve(relativePath: String, request: HttpServletRequest): ResponseEntity<ByteArray> {
        val clean = relativePath.removePrefix("/")
        if (clean.isEmpty() || clean.endsWith("/") || clean.split("/").any { it == ".." || it == "." || it.isEmpty() }) return notFound()
        val resource = ClassPathResource("static/assets/$clean")
        val bytes = try { if (resource.exists()) resource.inputStream.use { it.readBytes() } else return notFound() } catch (e: java.io.IOException) { return notFound() }

        val lastModifiedMillis = try { resource.lastModified() } catch (e: java.io.IOException) { 0L }
        val lastModified = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(lastModifiedMillis / 1000 * 1000).atOffset(ZoneOffset.UTC))
        val etag = "\"" + sha1("$lastModified -> ${resource.url.toExternalForm()}") + "\""
        val cacheHeaders = { builder: ResponseEntity.BodyBuilder ->
            builder.header("Cache-Control", "public, max-age=3600").header("ETag", etag).header("Last-Modified", lastModified)
        }

        val ifNoneMatch = request.getHeader("If-None-Match")
        if (ifNoneMatch != null) {
            if (ifNoneMatch.split(",").any { it.trim() == etag }) return cacheHeaders(ResponseEntity.status(HttpStatus.NOT_MODIFIED)).build()
        } else {
            val since = request.getDateHeader("If-Modified-Since")
            if (since != -1L && lastModifiedMillis / 1000 * 1000 <= since) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build()
        }
        return cacheHeaders(ResponseEntity.ok()).header("Content-Type", contentType(clean)).body(bytes)
    }

    private fun notFound(): ResponseEntity<ByteArray> = ResponseEntity.status(HttpStatus.NOT_FOUND).build()

    private fun sha1(text: String): String = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun contentType(path: String): String {
        val name = path.substringAfterLast('/')
        val extension = if (name.contains('.')) name.substringAfterLast('.').lowercase() else ""
        return TYPES[extension] ?: "application/octet-stream"
    }

    private companion object {
        val TYPES = mapOf(
            "css" to "text/css; charset=utf-8", "html" to "text/html; charset=utf-8", "htm" to "text/html; charset=utf-8", "js" to "application/javascript; charset=utf-8",
            "json" to "application/json; charset=utf-8", "txt" to "text/plain; charset=utf-8", "xml" to "text/xml; charset=utf-8",
            "png" to "image/png", "gif" to "image/gif", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "ico" to "image/x-icon", "svg" to "image/svg+xml",
            "eot" to "application/vnd.ms-fontobject", "ttf" to "application/x-font-ttf", "woff" to "application/font-woff", "swf" to "application/x-shockwave-flash",
            "map" to "application/x-navimap"
        )
    }
}
