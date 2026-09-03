package com.github.yonaprojects.yona

import java.security.MessageDigest
import java.util.Base64

fun main() {
    val password = "1234"
    val salt = "f322603b"
    
    val digest = MessageDigest.getInstance("SHA-256")
    digest.reset()
    digest.update(salt.toByteArray(Charsets.UTF_8))
    var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
    for (i in 1 until 1024) {
        digest.reset()
        hashed = digest.digest(hashed)
    }
    val result = Base64.getEncoder().encodeToString(hashed)
    println("GENERATED_PASSWORD_HASH:$result")
}
