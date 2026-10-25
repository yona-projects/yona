package dev.yona.web

/**
 * 소셜 로그인 제공자의 로고 HTML이다. v1.6 `utils.TemplateHelper.GithubLogo`, `GoogleLogo`(`app/utils/TemplateHelper.scala:43-49`)이다.
 *
 * 사용자 페이지의 "연결된 소셜 로그인"에서 쓴다. 알 수 없는 제공자는 로고가 없다.
 */
object ProviderLogos {

    /** GitHub 로고이다. */
    const val GITHUB: String = """<span class="github"><svg aria-hidden="true" height="24" version="1.1" viewBox="0 0 16 16" width="19"><path
  d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59 0.4 0.07 0.55-0.17 0.55-0.38 0-0.19-0.01-0.82-0.01-1.49-2.01 0.37-2.53-0.49-2.69-0.94-0.09-0.23-0.48-0.94-0.82-1.13-0.28-0.15-0.68-0.52-0.01-0.53 0.63-0.01 1.08 0.58 1.23 0.82 0.72 1.21 1.87 0.87 2.33 0.66 0.07-0.52 0.28-0.87 0.51-1.07-1.78-0.2-3.64-0.89-3.64-3.95 0-0.87 0.31-1.59 0.82-2.15-0.08-0.2-0.36-1.02 0.08-2.12 0 0 0.67-0.21 2.2 0.82 0.64-0.18 1.32-0.27 2-0.27 0.68 0 1.36 0.09 2 0.27 1.53-1.04 2.2-0.82 2.2-0.82 0.44 1.1 0.16 1.92 0.08 2.12 0.51 0.56 0.82 1.27 0.82 2.15 0 3.07-1.87 3.75-3.65 3.95 0.29 0.25 0.54 0.73 0.54 1.48 0 1.07-0.01 1.93-0.01 2.2 0 0.21 0.15 0.46 0.55 0.38C13.71 14.53 16 11.53 16 8 16 3.58 12.42 0 8 0z"></path></svg></span>"""

    /** Google 로고이다. [contextPath]는 정적 자원 주소의 앞에 붙는다. */
    fun google(contextPath: String = ""): String = """<span class="google"><img src="$contextPath/assets/images/provider-logo/btn_google_light_normal_ios.svg"></span>"""

    /** 제공자 키(`github`, `google`; 대소문자 무시)의 로고 HTML이다. 그 밖의 제공자는 빈 문자열이다. */
    fun of(provider: String, contextPath: String = ""): String = when {
        provider.equals("github", ignoreCase = true) -> GITHUB
        provider.equals("google", ignoreCase = true) -> google(contextPath)
        else -> ""
    }

    /**
     * 로그인 화면의 소셜 로그인 버튼 안에 넣을 HTML이다 (v1.6 `TemplateHelper.providerWithLogo`). 알 수 없는 제공자는 빈 문자열이다.
     *
     * @param githubName GitHub 버튼에 쓰는 이름 (`application.social.login.github.name`)
     */
    fun signIn(provider: String, githubName: String, contextPath: String = ""): String = when (provider) {
        "github" -> """<span class="auth-provider-logo">$GITHUB <span class="provider-name">Sign in with $githubName</span></span>"""
        "google" -> """<span class="auth-provider-logo"><img src="$contextPath/assets/images/provider-logo/btn_google_light_normal_ios.svg" alt="login with Google"> Sign in with Google</span>"""
        else -> ""
    }
}
