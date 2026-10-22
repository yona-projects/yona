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
 * @property projectDefaultScope 프로젝트 생성 화면에서 기본으로 고르는 공개 범위. `private`이면 비공개를 고른다 (`project.default.scope.when.create`)
 * @property projectDefaultMenus 프로젝트 생성 화면에서 기본으로 켜는 메뉴를 쉼표로 이은 값 (`project.default.menus.when.create`)
 * @property displayPrivateRepositories 프로젝트 목록에 비공개 프로젝트도 보여 준다. 읽을 수 없으면 흐리게 표시한다 (`application.displayPrivateRepositories`)
 * @property dataDir 저장소와 첨부 파일을 둘 데이터 디렉터리. 비우면 JVM 시스템 속성 `yona.data`, 그것도 없으면 작업 디렉터리를 쓴다 (D-16)
 * @property hostname 서버 이름. 비우면 요청의 Host 헤더를 쓴다 (`application.hostname`)
 * @property port 서버 포트. 80이면 주소에 쓰지 않는다 (`application.port`)
 * @property scheme `http` 또는 `https`. 비우면 요청의 스킴을 쓴다 (`application.scheme`)
 * @property useEmailVerification 가입한 사용자를 인증 메일의 링크를 열 때까지 잠가 둔다 (`application.use.email.verification`)
 * @property allowedSendingMailDomains 가입과 메일 발송을 허용할 이메일 도메인을 쉼표로 이은 값. 비우면 모두 허용한다 (`application.allowed.sending.mail.domains`)
 * @property temporaryFilesKeepUpTime 임시 첨부를 정리하는 주기(밀리초). 기본 하루 (`application.temporaryfiles.keep-up.time`)
 * @property parsersTextMaxLength 폼(`application/x-www-form-urlencoded`) 본문의 최대 바이트. 넘으면 폼 값을 읽지 못한 것으로 처리한다 (`parsers.text.maxLength`, 기본 102400)
 * @property issueEventDraftTime 같은 사용자가 같은 종류의 이슈 변경을 이 시간(밀리초) 안에 연달아 하면 하나로 합친다 (`application.issue-event.draft-time`, 기본 30초)
 * @property noreferrer 외부 링크에 `rel="noreferrer"`를 붙인다 (`application.noreferrer`)
 * @property applicationContext 서버를 하위 경로에 둘 때의 경로. 앞뒤 슬래시 없이 쓴다 (`application.context`)
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
    val projectDefaultScope: String = "public",
    val projectDefaultMenus: String = "code, issue, pullRequest, review, milestone, board",
    val displayPrivateRepositories: Boolean = false,
    val dataDir: String = "",
    val hostname: String = "",
    val port: Int? = null,
    val scheme: String = "",
    val applicationContext: String = "",
    val parsersTextMaxLength: Long = 102_400L,
    val issueEventDraftTime: Long = 30_000L,
    val noreferrer: Boolean = false,
    val temporaryFilesKeepUpTime: Long = 86_400_000L,
    val useEmailVerification: Boolean = false,
    val allowedSendingMailDomains: String = "",
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
