package dev.yona.web.site

import dev.yona.domain.sso.Saml2Certificates
import dev.yona.domain.sso.Saml2MetadataFetcher
import dev.yona.domain.sso.Saml2MetadataImport
import dev.yona.domain.sso.Saml2SsoForm
import dev.yona.domain.sso.OidcSsoForm
import dev.yona.domain.sso.SsoSaveError
import dev.yona.domain.sso.SsoSaveResult
import dev.yona.domain.sso.SsoSettingsService
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.SiteManagerOnly
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView
import java.time.Duration
import java.time.Instant

/**
 * 사이트 관리의 "SSO" 화면이다(추가 개발 항목 X-16, `decisions.md` D-80). 엔터프라이즈 SSO(OIDC, SAML 2)를 설정하고, Yona가 IdP에 알릴 값(리디렉션 주소, ACS 주소, Entity ID, 메타데이터 주소)을
 * 복사하기 좋게 보이며, IdP 메타데이터를 가져오고, SAML SP 키쌍을 만들고 올리고 교체한다. 사이트 관리자만 쓴다. 클라이언트 비밀 값과 SP 비밀 키는 화면에 다시 보이지 않는다.
 */
@Controller
@SiteManagerOnly
@AnonymousCheck
class SiteSsoController(
    private val settings: SsoSettingsService,
    private val siteSettings: dev.yona.domain.site.SiteSettingService,
    private val ldapSettings: dev.yona.domain.ldap.LdapSettingsService,
    private val siteUrls: SiteUrls,
    private val fetcher: Saml2MetadataFetcher,
    private val currentUserProvider: CurrentUserProvider,
    private val users: dev.yona.domain.user.UserRepository
) {

    @GetMapping("/sites/sso")
    fun form(@org.springframework.web.bind.annotation.RequestParam(required = false) tab: String?): ModelAndView {
        val oidc = settings.oidc(); val saml2 = settings.saml2()
        val origin = siteUrls.origin()
        return ModelAndView("site/sso").addObject("title", "site.sidebar.sso").addObject("tab", tab?.takeIf { it in TABS } ?: "general").addObject("oidc", oidc).addObject("saml2", saml2)
            .addObject("ssoOnly", siteSettings.ssoOnly()).addObject("ldap", ldapSettings.effective()).addObject("ldapProtocol", ldapSettings.protocol()).addObject("ldapSaved", ldapSettings.isSaved())
            .addObject("ldapEnabled", ldapSettings.enabled()).addObject("oidcEnabled", oidc.enabled == true).addObject("saml2Enabled", saml2.enabled == true).addObject("oidcSecretSet", !oidc.clientSecret.isNullOrBlank())
            .addObject("redirectUri", origin + siteUrls.path("/sso/oidc/callback/oidc")).addObject("initiateUrl", origin + siteUrls.path("/sso/oidc/initiate"))
            .addObject("acsUrl", origin + siteUrls.path("/sso/saml2/acs")).addObject("entityId", saml2.spEntityId?.takeIf { it.isNotBlank() } ?: (origin + siteUrls.path("/sso/saml2/metadata/saml2")))
            .addObject("metadataUrl", origin + siteUrls.path("/sso/saml2/metadata/saml2"))
            .addObject("idpCertificates", settings.saml2IdpCertificates().map { describe(it) })
            .addObject("spCertificate", saml2.spCertificate?.takeIf { it.isNotBlank() }?.let { describe(Saml2Certificates.parseCertificate(it)) })
            .addObject("spPreviousCertificate", saml2.spPreviousCertificate?.takeIf { it.isNotBlank() }?.let { describe(Saml2Certificates.parseCertificate(it)) })
    }

    /** SSO 전용 모드를 바꾼다. 켜려면 켠 SSO가 하나 이상 있어야 한다. */
    @PostMapping("/sites/sso/mode")
    fun saveMode(request: HttpServletRequest, response: HttpServletResponse): Any =
        if (siteSettings.setSsoOnly(request.getParameter("ssoOnly") == "true")) RedirectSupport.see(request, response, "$PAGE?tab=general", mapOf(RedirectSupport.SUCCESS to "site.sso.saved"))
        else warn(request, response, "site.sso.only.needProvider", "general")

    /** LDAP 설정을 저장한다(D-82). 켜는데 서버 주소, 포트, 기준 DN이 맞지 않으면 저장하지 않고 이유를 경고로 보인다. */
    @PostMapping("/sites/sso/ldap")
    fun saveLdap(request: HttpServletRequest, response: HttpServletResponse): Any {
        fun text(name: String) = request.getParameter(name)?.trim().orEmpty()
        val form = dev.yona.domain.ldap.LdapSettingsForm(
            enabled = request.getParameter("enabled") == "true", protocol = text("protocol").ifEmpty { "ldap" }, host = text("host"), port = text("port"), baseDn = text("baseDn"),
            distinguishedNamePostfix = text("distinguishedNamePostfix"), loginProperty = text("loginProperty"), displayNameProperty = text("displayNameProperty"), userNameProperty = text("userNameProperty"),
            emailProperty = text("emailProperty"), useEmailBaseLogin = request.getParameter("useEmailBaseLogin") == "true", fallbackToLocalLogin = request.getParameter("fallbackToLocalLogin") == "true",
            englishNameAttributeName = text("englishNameAttributeName")
        )
        return result(request, response, ldapSettings.save(form, actor()), "ldap")
    }

    /** 저장된 LDAP 서버에 연결할 수 있는지 확인하고 결과를 알린다. */
    @PostMapping("/sites/sso/ldap/check")
    fun checkLdap(request: HttpServletRequest, response: HttpServletResponse): Any {
        val error = ldapSettings.check()
        return RedirectSupport.see(request, response, "$PAGE?tab=ldap", mapOf((if (error == null) RedirectSupport.SUCCESS else RedirectSupport.WARNING) to (error?.messageKey ?: "site.sso.checked")))
    }

    /** OIDC 설정을 저장한다. 사용을 켜는데 연결이나 값이 맞지 않으면 저장하지 않고 이유를 경고로 보인다. */
    @PostMapping("/sites/sso/oidc")
    fun saveOidc(request: HttpServletRequest, response: HttpServletResponse): Any {
        fun text(name: String) = request.getParameter(name)?.trim().orEmpty()
        fun flag(name: String) = request.getParameter(name) == "true"
        val form = OidcSsoForm(
            enabled = flag("enabled"), displayName = text("displayName"), issuerUri = text("issuerUri"), clientId = text("clientId"), clientSecret = request.getParameter("clientSecret"),
            scopes = text("scopes"), emailClaim = text("emailClaim"), nameClaim = text("nameClaim"), loginIdClaim = text("loginIdClaim"), autoCreate = flag("autoCreate"), linkByEmail = flag("linkByEmail"),
            allowUnverifiedEmail = flag("allowUnverifiedEmail"), nameSync = flag("nameSync"), initiateEnabled = flag("initiateEnabled"), clockSkewSeconds = text("clockSkewSeconds").toIntOrNull() ?: 60
        )
        return result(request, response, settings.saveOidc(form, actor()), "oidc")
    }

    /** 저장된 OIDC 설정의 연결을 다시 확인하고 결과를 알린다. */
    @PostMapping("/sites/sso/oidc/check")
    fun checkOidc(request: HttpServletRequest, response: HttpServletResponse): Any {
        val error = settings.checkOidc()
        return RedirectSupport.see(request, response, "$PAGE?tab=oidc", mapOf((if (error == null) RedirectSupport.SUCCESS else RedirectSupport.WARNING) to (error?.messageKey ?: "site.sso.checked")))
    }

    /** SAML 2 설정을 저장한다. */
    @PostMapping("/sites/sso/saml2")
    fun saveSaml2(request: HttpServletRequest, response: HttpServletResponse): Any {
        fun text(name: String) = request.getParameter(name)?.trim().orEmpty()
        fun flag(name: String) = request.getParameter(name) == "true"
        val form = Saml2SsoForm(
            enabled = flag("enabled"), displayName = text("displayName"), idpEntityId = text("idpEntityId"), idpSsoUrl = text("idpSsoUrl"), idpCertificates = request.getParameter("idpCertificates").orEmpty(),
            spEntityId = text("spEntityId"), nameIdFormat = text("nameIdFormat"), emailAttribute = text("emailAttribute"), nameAttribute = text("nameAttribute"), loginIdAttribute = text("loginIdAttribute"),
            autoCreate = flag("autoCreate"), linkByEmail = flag("linkByEmail"), allowUnverifiedEmail = flag("allowUnverifiedEmail"), nameSync = flag("nameSync"), initiateEnabled = flag("initiateEnabled"),
            signRequests = flag("signRequests"), clockSkewSeconds = text("clockSkewSeconds").toIntOrNull() ?: 60, idpSsoBinding = text("idpSsoBinding").ifEmpty { "REDIRECT" }
        )
        return result(request, response, settings.saveSaml2(form, actor()), "saml2")
    }

    /** IdP 메타데이터(붙여 넣은 XML 또는 https 주소)를 읽어 발급자, 로그인 주소, 인증서를 채운다. 사용 여부는 바꾸지 않는다. */
    @PostMapping("/sites/sso/saml2/metadata")
    fun importMetadata(request: HttpServletRequest, response: HttpServletResponse): Any {
        val xml = request.getParameter("metadataXml")?.takeIf { it.isNotBlank() } ?: request.getParameter("metadataUrl")?.takeIf { it.isNotBlank() }?.let { fetcher.fetch(it) }
        val imported = xml?.let { settings.importSaml2Metadata(it) }
        if (imported !is Saml2MetadataImport.Imported) return warn(request, response, (imported as? Saml2MetadataImport.Invalid)?.error?.messageKey ?: SsoSaveError.UNREACHABLE.messageKey, "saml2")
        return result(request, response, settings.applySaml2Metadata(imported, actor()), "saml2")
    }

    /** SP 키쌍을 새로 만든다. 이미 있으면 이전 키로 옮겨 함께 둔다. */
    @PostMapping("/sites/sso/saml2/keys/generate")
    fun generateKeys(request: HttpServletRequest, response: HttpServletResponse): Any =
        result(request, response, settings.generateSpKeys(java.net.URI.create(siteUrls.origin()).host ?: "yona", actor()), "saml2-keys")

    /** 외부에서 만든 SP 비밀 키(PKCS#8 PEM)와 인증서(PEM)를 올린다. */
    @PostMapping("/sites/sso/saml2/keys/upload")
    fun uploadKeys(request: HttpServletRequest, response: HttpServletResponse): Any =
        result(request, response, settings.uploadSpKeys(request.getParameter("privateKey").orEmpty(), request.getParameter("certificate").orEmpty(), actor()), "saml2-keys")

    /** 교체 중인 이전 SP 키를 지운다. */
    @PostMapping("/sites/sso/saml2/keys/remove-previous")
    fun removePrevious(request: HttpServletRequest, response: HttpServletResponse): Any {
        settings.removePreviousSpKeys(actor())
        return RedirectSupport.see(request, response, "$PAGE?tab=saml2-keys", mapOf(RedirectSupport.SUCCESS to "site.sso.saved"))
    }

    /** SP 인증서를 PEM 파일로 내려받는다. IdP 관리자에게 전달한다. 키가 없으면 404이다. */
    @GetMapping("/sites/sso/saml2/sp-certificate.pem")
    @ResponseBody
    fun spCertificate(): ResponseEntity<String> {
        val pem = settings.saml2().spCertificate?.takeIf { it.isNotBlank() } ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-pem-file")).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"yona-saml-sp.pem\"").body(pem)
    }

    private fun result(request: HttpServletRequest, response: HttpServletResponse, result: SsoSaveResult, tab: String): Any = when (result) {
        is SsoSaveResult.Saved -> RedirectSupport.see(request, response, "$PAGE?tab=$tab", mapOf(RedirectSupport.SUCCESS to "site.sso.saved"))
        is SsoSaveResult.Rejected -> warn(request, response, result.error.messageKey, tab)
    }

    private fun warn(request: HttpServletRequest, response: HttpServletResponse, key: String, tab: String = "general"): Any =
        RedirectSupport.see(request, response, "$PAGE?tab=$tab", mapOf(RedirectSupport.WARNING to key))

    private fun actor() = currentUserProvider.get().user?.id?.let { users.findById(it).orElse(null) }

    /** 화면에 보일 인증서 요약이다. 30일 안에 끝나거나 이미 끝났으면 경고를 붙인다. */
    private fun describe(certificate: java.security.cert.X509Certificate): Map<String, Any?> {
        val info = Saml2Certificates.describe(certificate)
        val now = Instant.now()
        return linkedMapOf("subject" to info.subject, "notAfter" to java.util.Date.from(info.notAfter), "fingerprint" to info.fingerprintSha256, "expired" to info.notAfter.isBefore(now),
            "expiring" to (info.notAfter.isBefore(now.plus(Duration.ofDays(EXPIRY_WARNING_DAYS)))))
    }

    private companion object {
        const val PAGE = "/sites/sso"
        val TABS = setOf("general", "ldap", "oidc", "saml2", "saml2-keys")
        const val EXPIRY_WARNING_DAYS = 30L
    }
}
