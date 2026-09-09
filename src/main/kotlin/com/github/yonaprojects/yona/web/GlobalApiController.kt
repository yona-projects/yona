package com.github.yonaprojects.yona.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// yona controllers/api/GlobalApi.java hello() 대응
@RestController
class GlobalApiController {

    @GetMapping("/-_-api/v1/hello")
    fun hello(): Map<String, Any> {
        return mapOf("message" to "I'm alive!", "ok" to true)
    }
}
