package com.github.yonaprojects.yona

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringExtension

object KotestProjectConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(SpringExtension)
}
