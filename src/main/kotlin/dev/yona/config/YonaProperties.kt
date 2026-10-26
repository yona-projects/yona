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
 * @property scheme `http` 또는 `https`. 비우면 `http`이다. v1.6은 요청이 https인지나 `X-Forwarded-Proto`를 보지 않으므로, HTTPS 뒤에서 운영하면 `https`로 설정해야
 *   메일과 웹훅의 주소가 `https://`가 된다 (`application.scheme`)
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
 * @property update 업데이트 확인 설정이다
 * @property github 이전(Migration) 화면의 GitHub 연동 설정이다
 * @property social 소셜 로그인(GitHub, Google) 설정이다
 * @property imap 메일 수신(IMAP) 설정이다
 * @property mailboxPollingInterval IDLE을 쓸 수 없을 때 새 메일을 확인하는 주기(밀리초) (`application.mailbox.polling.interval`, 기본 5분)
 * @property ldap LDAP 로그인 설정이다
 * @property protocol LDAP 접속 프로토콜(`ldap` 또는 `ldaps`). v1.6은 `ldap.protocol`이 아니라 접두어 없는 최상위 `protocol` 키를 읽는다. 같게 한다(R-60)
 * @property secret 사이트 비밀 키이다. 기본값이거나 비어 있으면 처음 기동한 것으로 보고 모든 요청에서 기본 관리자 계정을 정하는 화면을 보인다 (`application.secret`)
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
    /** SVNKit WebDAV 오류 본문의 `<address>`에 쓰는 서버 이름이다 (`application.server`, v1.6 기본 설정 파일의 값 `Play/2.3`). `Server` 헤더에는 쓰지 않는다. */
    val server: String = "Play/2.3",
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
    val github: Github = Github(),
    val social: Social = Social(),
    val imap: Imap = Imap(),
    val mailboxPollingInterval: Long = 5 * 60 * 1000L,
    val ldap: Ldap = Ldap(),
    val protocol: String = "ldap",
    val secret: String = DEFAULT_SECRET,
    val smtp: Smtp = Smtp(),
    val update: Update = Update(),
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
    companion object {
        /** 설치 직후 설정 예시에 들어 있는 사이트 비밀 키이다. 이 값이면 아직 정하지 않은 것이다 (v1.6 `Global.DEFAULT_SECRET`). */
        const val DEFAULT_SECRET = "VA2v:_I=h9>?FYOH:@ZhW]01P<mWZAKlQ>kk>Bo`mdCiA>pDw64FcBuZdDh<47Ew"
    }

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
     * 메일 수신(IMAP) 설정이다 (v1.6 `imap.*`). 호스트, 사용자, 비밀번호가 모두 있어야 시작한다.
     *
     * @property use 메일 수신을 쓴다. 호스트를 설정해도 끌 수 있다 (`imap.use`, 기본 켬)
     * @property host IMAP 서버 호스트. 비우면 시작하지 않는다
     * @property user IMAP 사용자. `@`가 있으면 이 값이 곧 사이트의 수신 주소이다
     * @property password IMAP 비밀번호
     * @property domain 수신 주소의 도메인. `user`에 `@`가 없을 때 `user@domain`으로 쓰며 비우면 사이트 호스트 이름을 쓴다 (`imap.domain`)
     * @property address 수신 주소. 비우면 `user`와 `domain`으로 만든다 (`imap.address`)
     * @property port IMAP 서버 포트. 비우면 프로토콜의 기본 포트이다. v1.6에는 없는 설정으로 테스트와 비표준 포트용이다(A-97)
     * @property ssl SSL로 연결한다 (`imap.ssl`)
     * @property folder 받은 편지함 이름 (`imap.folder`, 기본 `inbox`)
     */
    data class Imap(
        val use: Boolean = true,
        val host: String = "",
        val user: String = "",
        val password: String = "",
        val domain: String = "",
        val address: String = "",
        val port: Int? = null,
        val ssl: Boolean = false,
        val folder: String = "inbox"
    )

    /**
     * LDAP 로그인 설정이다 (v1.6 `application.use.ldap.login.supoort`(키의 오타가 그대로 실제 키이다)와 `ldap.*`).
     *
     * @property enabled LDAP으로 로그인한다
     * @property host LDAP 서버 호스트
     * @property port LDAP 서버 포트(문자열)
     * @property baseDn 사용자를 검색할 기준 DN
     * @property distinguishedNamePostfix 로그인 이름으로 바인드 DN을 만들 때 뒤에 붙일 부분. `CN=이름,OU=user,DC=abc,DC=com`이면 `OU=user,DC=abc,DC=com`
     * @property loginProperty 로그인 이름을 담은 속성
     * @property displayNameProperty 표시 이름 속성
     * @property userNameProperty 바인드 DN의 첫 속성 이름
     * @property emailProperty 이메일 속성
     * @property useEmailBaseLogin 이메일로 인증한다. 로그인 ID를 입력하면 그 사용자의 이메일로 바꿔 인증하고, 새 사용자의 로그인 ID는 이메일의 앞부분으로 만든다 (`ldap.options.useEmailBaseLogin`)
     * @property fallbackToLocalLogin LDAP 서버에 연결하지 못하거나 인증에 실패하면 로컬 비밀번호로 다시 시도한다 (`ldap.options.fallbackToLocalLogin`)
     * @property englishNameAttributeName 영문 이름 속성. 비우면 읽지 않는다 (`ldap.options.englishNameAttributeName`)
     */
    data class Ldap(
        val enabled: Boolean = false,
        val host: String = "127.0.0.1",
        val port: String = "389",
        val baseDn: String = "",
        val distinguishedNamePostfix: String = "",
        val loginProperty: String = "sAMAccountName",
        val displayNameProperty: String = "displayName",
        val userNameProperty: String = "CN",
        val emailProperty: String = "mail",
        val useEmailBaseLogin: Boolean = false,
        val fallbackToLocalLogin: Boolean = false,
        val englishNameAttributeName: String = ""
    )

    /**
     * 소셜 로그인 설정이다. v1.6 `application.social.login.*`와 `conf/social-login.conf`(play-authenticate)에 해당한다.
     * v1.6의 기본 설정 파일에 들어 있던 시험용 클라이언트 ID와 비밀은 옮기지 않는다. 쓰려면 직접 발급해 넣는다(A-95).
     *
     * @property loginSupport 로그인 화면에 보이고 쓸 수 있는 제공자를 쉼표로 이은 값. 예: `github, google`. 비우면 쓰지 않는다 (`application.social.login.support`)
     * @property nameSync 로그인할 때마다 소셜 계정의 이름으로 사용자 이름을 고친다 (`application.use.social.login.name.sync`)
     * @property githubName 로그인 화면의 GitHub 버튼에 쓰는 이름 (`application.social.login.github.name`)
     * @property github GitHub 제공자 설정이다. GitHub Enterprise는 주소 세 개를 바꿔 쓴다
     * @property google Google 제공자 설정이다
     */
    data class Social(
        val loginSupport: String = "",
        val nameSync: Boolean = false,
        val githubName: String = "github",
        val github: OAuthProvider = OAuthProvider(),
        val google: OAuthProvider = OAuthProvider()
    ) {
        /** 허용된 제공자 키 목록이다. 공백은 무시한다 (v1.6 `TemplateHelper.isAllowedOAuthProvider`). */
        fun allowedProviders(): List<String> = loginSupport.replace(" ", "").split(",").filter { it.isNotEmpty() }
    }

    /**
     * OAuth 제공자 한 곳의 설정이다 (v1.6 `play-authenticate.<제공자>`).
     *
     * @property clientId 발급받은 클라이언트 ID
     * @property clientSecret 발급받은 클라이언트 비밀
     * @property scope 요청할 권한 범위
     * @property authorizationUrl 사용자를 보낼 인증 화면 주소
     * @property accessTokenUrl 인증 코드를 접근 토큰으로 바꾸는 주소
     * @property userInfoUrl 접근 토큰으로 사용자 정보를 읽는 주소
     * @property redirectSecure 돌아올 주소(redirect_uri)를 `https`로 만든다. 기본은 `http`이다 (`redirectUri.secure`)
     * @property redirectHost 돌아올 주소의 호스트. 비우면 요청의 호스트를 쓴다 (`redirectUri.host`)
     */
    data class OAuthProvider(
        val clientId: String = "",
        val clientSecret: String = "",
        val scope: String = "",
        val authorizationUrl: String = "",
        val accessTokenUrl: String = "",
        val userInfoUrl: String = "",
        val redirectSecure: Boolean = false,
        val redirectHost: String = ""
    )

    /**
     * 이전(Migration) 화면의 GitHub 연동 설정이다 (v1.6 `github.*`).
     *
     * @property allowMigration 이전 화면(`GET /migration`)을 연다 (`github.allow.migration`, 기본 꺼짐). 내보내기 JSON 주소는 이 설정과 관계없이 열려 있다
     * @property clientId GitHub OAuth 앱의 클라이언트 ID (`github.client.id`)
     * @property clientSecret GitHub OAuth 앱의 비밀 (`github.client.secret`)
     * @property accessTokenUrl 인증 코드를 토큰으로 바꾸는 주소
     */
    data class Github(
        val allowMigration: Boolean = false,
        val clientId: String = "TYPE YOUR GITHUB CILENT ID",
        val clientSecret: String = "TYPE YOUR GITHUB CILENT SECRET",
        val accessTokenUrl: String = "https://github.com/login/oauth/access_token"
    )

    /**
     * 업데이트 확인 설정이다 (v1.6 `application.update.*`).
     *
     * @property check 업데이트 화면에서 확인을 수행한다 (`application.update.check.use`)
     * @property initDelay 서버를 시작한 뒤 첫 확인까지 기다리는 시간(밀리초) (`application.update.notification.initdelay`, 기본 5초)
     * @property interval 확인 주기(밀리초). 0 이하이면 백그라운드 확인을 하지 않는다 (`application.update.notification.interval`, 기본 1시간)
     * @property repositoryUrl 태그를 읽을 Git 저장소 (`application.update.repositoryUrl`)
     * @property releaseUrlFormat 릴리스 페이지 주소 형식. `%s`가 버전이다 (`application.update.releaseUrlFormat`)
     */
    data class Update(
        val check: Boolean = true,
        val initDelay: Long = 5_000L,
        val interval: Long = 3_600_000L,
        val repositoryUrl: String = "http://repo.yona.io/yona/yona",
        val releaseUrlFormat: String = "https://github.com/yona-projects/yona/releases/tag/v%s"
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
