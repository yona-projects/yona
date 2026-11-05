package dev.yona.domain.migration

import dev.yona.config.YonaProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

/** GitHub OAuth 인증 코드를 접근 토큰으로 바꾼다. 테스트에서는 바꿔 끼운다. */
fun interface GithubTokenClient {
    /** @return 접근 토큰. 응답에서 찾지 못하면 빈 문자열이다 */
    fun exchange(code: String): String
}

/**
 * GitHub의 토큰 주소로 `client_id`, `client_secret`, `code`를 보내 토큰을 받는다. v1.6 `MigrationApp.getOAuthToken`
 * (`app/controllers/MigrationApp.java:67-91`)과 같다.
 *
 * 본문은 URL 인코딩 없이 이어 붙이고 `redirect_uri`를 보내지 않는다. v1.6은 응답을 정규식 `access_token=([^&]+)`로만 읽고 상태 코드도 보지 않아,
 * GitHub가 JSON으로 응답하면 토큰이 비었고 오류 응답의 본문에서도 토큰을 찾으려 했다(R-58-1). 성공(2xx) 응답만 읽고, JSON이면 `access_token`
 * 값을, 폼 형식이면 정규식으로 토큰을 쓴다. 못 찾으면 빈 문자열이다. v1.6과 달리 토큰을 로그에 남기지 않는다(A-94).
 */
@Component
class RestGithubTokenClient(private val properties: YonaProperties) : GithubTokenClient {

    override fun exchange(code: String): String {
        val github = properties.github
        val response = RestClient.create().post().uri(github.accessTokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header("Accept", "application/json,application/x-www-form-urlencoded,text/html,*/*")
            .body("client_id=${github.clientId}&client_secret=${github.clientSecret}&code=$code")
            .retrieve().onStatus({ true }, { _, _ -> }).toEntity(String::class.java)
        if (!response.statusCode.is2xxSuccessful) return ""
        val body = response.body.orEmpty()
        if (body.trimStart().startsWith("{")) {
            return try {
                JsonMapper.builder().build().readTree(body).path("access_token").asString("")
            } catch (e: Exception) {
                ""
            }
        }
        return Regex("access_token=([^&]+)").find(body)?.groupValues?.get(1).orEmpty()
    }
}
