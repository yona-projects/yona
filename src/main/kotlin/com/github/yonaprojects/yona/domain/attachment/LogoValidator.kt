package com.github.yonaprojects.yona.domain.attachment

// yona utils/LogoUtil.java 대응 (P1-124). 조직/프로젝트 로고 업로드 시 확장자·용량을 검증한다.
object LogoValidator {
    const val LOGO_FILE_LIMIT_SIZE: Long = 1024L * 1000L * 5L // 5M

    private val LOGO_TYPES = listOf("jpg", "jpeg", "png", "gif", "bmp")

    fun isImageFile(filename: String): Boolean {
        val lower = filename.lowercase()
        return LOGO_TYPES.any { lower.endsWith(it) }
    }
}
