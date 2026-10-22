package dev.yona.web

import org.springframework.core.io.ClassPathResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import java.time.Duration

/** 루트의 `/favicon.ico`를 제공한다. v1.6 `GET /favicon.ico`는 `public/images/favicon.ico`에 연결된다. */
@Controller
class FaviconController {

    @GetMapping("/favicon.ico")
    fun favicon(): ResponseEntity<ByteArray> =
        ResponseEntity.ok().contentType(MediaType.parseMediaType("image/x-icon"))
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
            .body(ClassPathResource("static/assets/images/favicon.ico").inputStream.use { it.readBytes() })
}
