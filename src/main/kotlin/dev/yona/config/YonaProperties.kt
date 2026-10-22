package dev.yona.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Yona의 애플리케이션 설정이다. v1.6의 `conf/application.conf`에서 `application.*` 키로 설정하던 값을
 * `yona.*` 키로 옮겼다. 키 대응표는 `yona-v2-wiki/architecture/configuration-keys.md`를 본다.
 *
 * @property siteName 사이트 이름. 화면 제목과 환영 문구에 쓰인다 (`application.siteName`)
 * @property feedbackUrl 상단 메뉴의 피드백 링크. 비우면 링크를 숨긴다 (`application.feedback.url`)
 * @property hideProjectListing 익명 사용자와 게스트에게 프로젝트 목록을 숨긴다 (`application.hide.project.listing`)
 * @property sendYonaUsage 사용 통계(Google Analytics)를 보낸다 (`application.send.yona.usage`)
 * @property showUserEmail 사용자 이메일을 화면에 보여 준다 (`application.show.user.email`)
 * @property navbarCustomLinkName 상단 메뉴에 추가할 링크 이름 (`application.navbar.custom.link.name`)
 * @property navbarCustomLinkUrl 상단 메뉴에 추가할 링크 주소 (`application.navbar.custom.link.url`)
 * @property loginPageLoginIdPlaceholder 로그인 화면 아이디 입력란의 안내 문구 (`application.login.page.loginId.placeholder`)
 * @property loginPagePasswordPlaceholder 로그인 화면 비밀번호 입력란의 안내 문구 (`application.login.page.password.placeholder`)
 * @property displayPrivateRepositories 프로젝트 목록에 비공개 프로젝트도 보여 준다. 읽을 수 없으면 흐리게 표시한다 (`application.displayPrivateRepositories`)
 * @property dataDir 저장소와 첨부 파일을 둘 데이터 디렉터리. 비우면 JVM 시스템 속성 `yona.data`, 그것도 없으면 작업 디렉터리를 쓴다 (D-16)
 * @property hostname 서버 이름. 비우면 요청의 Host 헤더를 쓴다 (`application.hostname`)
 * @property port 서버 포트. 80이면 주소에 쓰지 않는다 (`application.port`)
 * @property scheme `http` 또는 `https`. 비우면 요청의 스킴을 쓴다 (`application.scheme`)
 * @property allowsAnonymousAccess 익명 사용자의 접근을 허용한다. 끄면 `@AnonymousCheck` 화면이 모두 로그인을 요구한다 (`application.allowsAnonymousAccess`)
 * @property socialLoginOnly 소셜 로그인만 허용한다 (`application.use.social.login.only`)
 * @property maxFileSize 첨부 파일의 최대 크기(바이트) (`application.maxFileSize`)
 */
@ConfigurationProperties(prefix = "yona")
data class YonaProperties(
    val siteName: String = "Yona",
    val feedbackUrl: String = "https://github.com/yona-projects/yona/issues",
    val hideProjectListing: Boolean = false,
    val sendYonaUsage: Boolean = true,
    val showUserEmail: Boolean = true,
    val navbarCustomLinkName: String = "",
    val navbarCustomLinkUrl: String = "",
    val loginPageLoginIdPlaceholder: String = "",
    val loginPagePasswordPlaceholder: String = "",
    val displayPrivateRepositories: Boolean = false,
    val dataDir: String = "",
    val hostname: String = "",
    val port: Int? = null,
    val scheme: String = "",
    val allowsAnonymousAccess: Boolean = true,
    val socialLoginOnly: Boolean = false,
    val maxFileSize: Long = 2147483454L,
    val signup: Signup = Signup(),
    val gravatar: Gravatar = Gravatar()
) {
    /** 가입 정책 설정이다. */
    data class Signup(
        /** 가입 승인제: 신규 계정을 관리자가 승인하기 전까지 잠가 둔다 (`signup.require.admin.confirm`). */
        val requireAdminConfirm: Boolean = false
    )

    /** 아바타 서비스(Gravatar) 설정이다. */
    data class Gravatar(
        /**
         * 기동할 때 Gravatar 서버에 접속할 수 있는지 확인한다. v1.6은 항상 확인한다
         * (`Config.isConnectableToGravatar`). 끄면 접속할 수 없는 것으로 보고 기본 아바타를 쓴다.
         */
        val check: Boolean = true
    )
}
