package com.github.yonaprojects.yona.domain.support

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder

// yona utils/CacheStore.java:15-26 CacheStore.renderedMarkdown 대응 (P2-43). Guava LRU 캐시(원본과 [GL-utils_CacheStore-002;GL-utils_CacheStore-003;GL-utils_CacheStore-004;GL-utils_CacheStore-005]
// 동일하게 maximumSize만 설정 — expireAfterWrite/expireAfterAccess 등 TTL은 원본에도 없다).
object MarkdownRenderCache {
    const val MAXIMUM_CACHED_MARKDOWN_ENTRY = 10000L

    val renderedMarkdown: Cache<Int, ByteArray> = CacheBuilder.newBuilder()
        .maximumSize(MAXIMUM_CACHED_MARKDOWN_ENTRY)
        .build()
}
