package dev.yona.markdown

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * 마크다운 렌더링 결과 캐시이다. v1.6 `CacheStore.renderedMarkdown`과 `ZipUtil`에 해당한다.
 *
 * 값은 메모리를 아끼려고 deflate로 압축해 보관하고, 항목이 [maximumSize]를 넘으면 오래 쓰지 않은 것부터 내보낸다.
 * 만료 시간은 없다(v1.6과 같다). 프로세스 안에서만 공유된다.
 *
 * v1.6은 원문의 `hashCode()`를 키로 써서 해시가 같은 서로 다른 원문의 결과가 뒤바뀔 수 있었다. 여기서는 원문의
 * SHA-256을 키로 써서 이를 피한다([ 결정 A-18 ]).
 *
 * @property maximumSize 보관할 최대 항목 수. v1.6의 기본값은 10000이다.
 */
class RenderedMarkdownCache(maximumSize: Long = DEFAULT_MAXIMUM_SIZE) {

    private val cache: Cache<String, ByteArray> = Caffeine.newBuilder().maximumSize(maximumSize).build()

    /** [source]의 결과가 있으면 돌려주고, 없으면 [render]로 만들어 보관한 뒤 돌려준다. */
    fun getOrRender(source: String, render: () -> String): String {
        val key = digest(source)
        cache.getIfPresent(key)?.let { return decompress(it) }
        val rendered = render()
        cache.put(key, compress(rendered))
        return rendered
    }

    /** 보관 중인 항목 수(근사값)이다. */
    fun size(): Long = cache.estimatedSize()

    /** 밀린 퇴출 작업을 즉시 처리한다. */
    fun cleanUp() = cache.cleanUp()

    private fun digest(source: String): String =
        MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun compress(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun decompress(bytes: ByteArray): String =
        InflaterInputStream(bytes.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) }

    companion object {
        /** v1.6 `MAXIMUM_CACHED_MARKDOWN_ENTRY`. */
        const val DEFAULT_MAXIMUM_SIZE = 10_000L
    }
}
