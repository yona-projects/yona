package com.github.yonaprojects.yona.config.oauth2server

// yona-wiki P3-14 1라운드 — P3-07이 만든 인가 서버 인프라는 원래 `/mcp` 리소스 하나만 발급/검증할
// 수 있었다(ResourceIndicatorTokenCustomizer/ResourceServerConfig 둘 다 "$baseUrl/mcp" 문자열을
// 그대로 하드코딩). yona-wiki P3-14 티켓의 핵심 요구사항(제3자 앱이 REST API에도 위임 접근)을
// 지원하려면 발급 가능한 리소스가 최소 2종(mcp/api)이어야 해, 그 목록을 여기 한 곳에 모은다 —
// RFC8707(Resource Indicators)의 `resource` 파라미터로 클라이언트가 요청한 문자열을 이 목록과
// 대조해 유효성을 검사하고, 유효하면 그 리소스 전용 오디언스를 토큰에 스탬핑한다.
enum class ProtectedResource(private val path: String) {
    MCP("/mcp"),
    API("/api/v1");

    fun uri(baseUrl: String): String = "$baseUrl$path"

    companion object {
        fun fromUri(resourceUri: String?, baseUrl: String): ProtectedResource? =
            entries.find { it.uri(baseUrl) == resourceUri }
    }
}
