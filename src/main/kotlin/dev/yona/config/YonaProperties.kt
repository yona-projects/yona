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
 * @property backgroundAsync 풀 리퀘스트 병합 검사와 푸시 뒤 알림처럼 요청과 별개로 도는 일을 별개의 스레드에서 한다 (v1.6의 Akka 액터). 끄면 요청 안에서 바로 한다. 테스트가 결과를 기다리지 않도록 끌 수 있다
 * @property slack 슬랙 웹훅의 첨부에 붙일 색이다. 키는 이벤트 종류 이름(`NEW_ISSUE` 등)이고 값은 색(`good`, `#ff0000`)이다 (`slack.<이벤트 종류>`)
 * @property notificationDraftTime 같은 사용자가 같은 리소스에 같은 종류의 알림을 이 시간(밀리초) 안에 연달아 만들면 하나로 합친다 (`application.notification.draft-time`, 기본 30초)
 * @property notificationKeepTime 알림 이벤트를 보관하는 일수. 0 이하이면 지우지 않는다 (`application.notification.keep-time`, 기본 -1)
 * @property notificationMailEnabled 알림 메일을 보낸다. 끄면 발송 스케줄러를 시작하지 않는다 (`notification.bymail.enabled`, 기본 켬)
 * @property notificationMailHideAddress 받는 사람 주소를 서로 숨긴다. 켜면 받는 사람(To)에는 사이트 발신 주소를 쓰고 사용자는 숨은참조(Bcc)에 넣는다 (`application.notification.bymail.hideAddress`, 기본 켬)
 * @property notificationMailRecipientLimit 메일 한 통에 넣을 수 있는 받는 사람 수. 0이면 제한이 없다 (`application.notification.bymail.recipientLimit`, 기본 0)
 * @property notificationMailInitDelay 기동 후 첫 발송까지 기다리는 시간(밀리초) (`application.notification.bymail.initdelay`, 기본 5초)
 * @property notificationMailInterval 발송 주기(밀리초) (`application.notification.bymail.interval`, 기본 60초)
 * @property notificationMailDelay 알림 이벤트를 만든 뒤 메일로 보내기까지 기다리는 시간(밀리초). 그 사이 연달아 일어난 알림을 묶을 수 있다 (`application.notification.bymail.delay`, 기본 180초)
 * @property smtp 보내는 메일 주소 설정이다. SMTP 접속 정보는 `spring.mail.*`로 설정한다
 * @property easymail 가입 안내 메일의 보내는 사람이다 (`play-easymail.from.*`)
 * @property codeBrowserMaxFileSize 코드 브라우저가 내용을 보여 주는 텍스트 파일의 최대 크기(바이트). 넘으면 원본 보기 링크만 준다 (`application.codeBrowser.viewer.maxFileSize`, 기본 1MB)
 * @property issueEventDraftTime 같은 사용자가 같은 종류의 이슈 변경을 이 시간(밀리초) 안에 연달아 하면 하나로 합친다 (`application.issue-event.draft-time`, 기본 30초)
 * @property translationApi 번역 API 주소. 비우면 번역 단추를 숨긴다 (`application.extras.translation.api`)
 * @property translationHeaderKey 번역 API 요청에 붙일 인증 헤더 이름 (`application.extras.translation.headerKey`)
 * @property translationHeaderValue 그 헤더의 값 (`application.extras.translation.headerValue`)
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
    val codeBrowserMaxFileSize: Long = 1024L * 1024,
    val notificationMailEnabled: Boolean = true,
    val notificationMailHideAddress: Boolean = true,
    val notificationMailRecipientLimit: Int = 0,
    val notificationMailInitDelay: Long = 5_000L,
    val notificationMailInterval: Long = 60_000L,
    val notificationMailDelay: Long = 180_000L,
    val smtp: Smtp = Smtp(),
    val easymail: Easymail = Easymail(),
    val notificationDraftTime: Long = 30_000L,
    val backgroundAsync: Boolean = true,
    val slack: Map<String, String> = emptyMap(),
    val notificationKeepTime: Int = -1,
    val translationApi: String = "",
    val translationHeaderKey: String = "",
    val translationHeaderValue: String = "",
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
    /** 번역 API가 설정되어 있으면 `true`이다. 화면의 번역 단추를 보일지 정한다. */
    val translationApiConfigured: Boolean get() = translationApi.isNotBlank()

    /**
     * 보내는 메일 주소 설정이다 (v1.6 `smtp.user`, `smtp.domain`과 `Config.getEmailFromSmtp()`).
     * 주소는 `user`에 `@`가 있으면 그 값이고, 없으면 `user@domain`이다. `domain`이 비면 사이트 호스트 이름을 쓴다.
     */
    data class Smtp(
        val user: String = "",
        val domain: String = ""
    )

    /**
     * 가입 안내 메일의 보내는 사람이다 (v1.6 `play-easymail.from.email`, `play-easymail.from.name`).
     * `fromEmail`을 비우면 알림 메일과 같은 보내는 주소(`smtp`)를 쓴다.
     */
    data class Easymail(
        val fromEmail: String = "",
        val fromName: String = "yona-no-reply"
    )

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
