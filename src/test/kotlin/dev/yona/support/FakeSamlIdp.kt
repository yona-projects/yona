package dev.yona.support

import dev.yona.domain.sso.Saml2Certificates
import net.shibboleth.shared.xml.SerializeSupport
import org.opensaml.core.xml.XMLObject
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport
import org.opensaml.core.xml.io.MarshallingException
import org.opensaml.core.xml.schema.XSString
import org.opensaml.saml.saml2.core.Assertion
import org.opensaml.saml.saml2.core.Attribute
import org.opensaml.saml.saml2.core.AttributeStatement
import org.opensaml.saml.saml2.core.Audience
import org.opensaml.saml.saml2.core.AudienceRestriction
import org.opensaml.saml.saml2.core.AuthnContext
import org.opensaml.saml.saml2.core.AuthnContextClassRef
import org.opensaml.saml.saml2.core.AuthnStatement
import org.opensaml.saml.saml2.core.Conditions
import org.opensaml.saml.saml2.core.Issuer
import org.opensaml.saml.saml2.core.NameID
import org.opensaml.saml.saml2.core.Response
import org.opensaml.saml.saml2.core.Status
import org.opensaml.saml.saml2.core.StatusCode
import org.opensaml.saml.saml2.core.Subject
import org.opensaml.saml.saml2.core.SubjectConfirmation
import org.opensaml.saml.saml2.core.SubjectConfirmationData
import org.opensaml.saml.saml2.encryption.Encrypter
import org.opensaml.security.credential.CredentialSupport
import org.opensaml.xmlsec.encryption.support.DataEncryptionParameters
import org.opensaml.xmlsec.encryption.support.EncryptionConstants
import org.opensaml.xmlsec.encryption.support.KeyEncryptionParameters
import org.opensaml.xmlsec.signature.Signature
import org.opensaml.xmlsec.signature.support.SignatureConstants
import org.opensaml.xmlsec.signature.support.Signer
import org.opensaml.xmlsec.signature.support.SignatureSupport
import org.opensaml.xmlsec.SignatureSigningParameters
import org.springframework.security.saml2.core.OpenSamlInitializationService
import java.io.ByteArrayInputStream
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.zip.Inflater
import javax.xml.namespace.QName

/**
 * 시험에서 SAML 2 신원 제공자(IdP)를 흉내 낸다. OpenSAML로 로그인 응답(Response)을 만들어 서명하거나 암호화한다. 서명 키(`key`)와 인증서는 시험마다 새로 만든다.
 * `Saml2SsoForm.idpCertificates`에는 [certificatePem]을 넣는다.
 */
class FakeSamlIdp {

    val entityId = "https://idp.corp.example/metadata"
    val ssoUrl = "https://idp.corp.example/sso"
    private val material = Saml2Certificates.generate("idp.corp.example")
    private val other = Saml2Certificates.generate("other.corp.example")
    val certificatePem: String get() = material.certificatePem
    private val certificate: X509Certificate = Saml2Certificates.parseCertificate(material.certificatePem)
    private val privateKey: PrivateKey = Saml2Certificates.parsePrivateKey(material.privateKeyPem)
    private val otherCertificate: X509Certificate = Saml2Certificates.parseCertificate(other.certificatePem)
    private val otherKey: PrivateKey = Saml2Certificates.parsePrivateKey(other.privateKeyPem)

    init { OpenSamlInitializationService.initialize() }

    /** SP가 보낸 AuthnRequest(리디렉션 바인딩)의 `ID`이다. */
    fun requestId(redirectLocation: String): String = Regex("""\bID="([^"]+)"""").find(requestXml(redirectLocation))!!.groupValues[1]

    /** SP가 보낸 AuthnRequest의 XML이다(리디렉션 바인딩: URL 인코딩, Base64, Deflate). */
    fun requestXml(redirectLocation: String): String {
        val raw = org.springframework.web.util.UriComponentsBuilder.fromUriString(redirectLocation).build().queryParams.getFirst("SAMLRequest")!!
        val compressed = Base64.getMimeDecoder().decode(java.net.URLDecoder.decode(raw, "UTF-8"))
        val inflater = Inflater(true).apply { setInput(compressed) }
        val out = ByteArray(65536); val n = inflater.inflate(out); inflater.end()
        return String(out, 0, n, Charsets.UTF_8)
    }

    /**
     * 로그인 응답을 만들어 Base64로 돌려준다.
     *
     * @param inResponseTo 요청 ID. `null`이면 IdP가 시작한 로그인(요청 없는 응답)이다
     * @param destination 응답이 도착할 주소(ACS)
     * @param audience 이 응답을 받을 SP의 Entity ID
     * @param signWith 서명 키. [wrongKey]를 주면 다른 키로 서명한다
     * @param signAssertion 어설션에 서명한다
     * @param signResponse 응답 전체에 서명한다
     * @param encryptFor 주어지면 어설션을 이 인증서로 암호화한다
     */
    fun response(
        inResponseTo: String?, destination: String, audience: String, nameId: String, attributes: Map<String, List<String>> = emptyMap(), issuer: String = entityId,
        wrongKey: Boolean = false, signAssertion: Boolean = true, signResponse: Boolean = false, notBefore: Instant = Instant.now().minusSeconds(30),
        notOnOrAfter: Instant = Instant.now().plusSeconds(300), recipient: String = destination, assertionId: String = "_" + UUID.randomUUID(), encryptFor: X509Certificate? = null,
        nameIdFormat: String? = null, statusValue: String = StatusCode.SUCCESS
    ): String {
        val assertion = build<Assertion>(Assertion.DEFAULT_ELEMENT_NAME).apply {
            id = assertionId; issueInstant = Instant.now()
            this.issuer = build<Issuer>(Issuer.DEFAULT_ELEMENT_NAME).apply { value = issuer }
            subject = build<Subject>(Subject.DEFAULT_ELEMENT_NAME).apply {
                this.nameID = build<NameID>(NameID.DEFAULT_ELEMENT_NAME).apply { value = nameId; nameIdFormat?.let { format = it } }
                subjectConfirmations.add(build<SubjectConfirmation>(SubjectConfirmation.DEFAULT_ELEMENT_NAME).apply {
                    method = SubjectConfirmation.METHOD_BEARER
                    subjectConfirmationData = build<SubjectConfirmationData>(SubjectConfirmationData.DEFAULT_ELEMENT_NAME).apply {
                        this.notOnOrAfter = notOnOrAfter; this.recipient = recipient; inResponseTo?.let { this.inResponseTo = it }
                    }
                })
            }
            conditions = build<Conditions>(Conditions.DEFAULT_ELEMENT_NAME).apply {
                this.notBefore = notBefore; this.notOnOrAfter = notOnOrAfter
                audienceRestrictions.add(build<AudienceRestriction>(AudienceRestriction.DEFAULT_ELEMENT_NAME).apply {
                    audiences.add(build<Audience>(Audience.DEFAULT_ELEMENT_NAME).apply { uri = audience })
                })
            }
            authnStatements.add(build<AuthnStatement>(AuthnStatement.DEFAULT_ELEMENT_NAME).apply {
                authnInstant = Instant.now()
                authnContext = build<AuthnContext>(AuthnContext.DEFAULT_ELEMENT_NAME).apply {
                    authnContextClassRef = build<AuthnContextClassRef>(AuthnContextClassRef.DEFAULT_ELEMENT_NAME).apply { uri = AuthnContext.PPT_AUTHN_CTX }
                }
            })
            if (attributes.isNotEmpty()) attributeStatements.add(build<AttributeStatement>(AttributeStatement.DEFAULT_ELEMENT_NAME).apply {
                attributes.forEach { (name, values) ->
                    this.attributes.add(build<Attribute>(Attribute.DEFAULT_ELEMENT_NAME).apply {
                        this.name = name
                        values.forEach { v -> attributeValues.add((XMLObjectProviderRegistrySupport.getBuilderFactory().getBuilder(XSString.TYPE_NAME) as org.opensaml.core.xml.schema.impl.XSStringBuilder).buildObject(org.opensaml.saml.saml2.core.AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME).apply { value = v }) }
                    })
                }
            })
        }
        val response = build<Response>(Response.DEFAULT_ELEMENT_NAME).apply {
            id = "_" + UUID.randomUUID(); issueInstant = Instant.now(); this.destination = destination
            inResponseTo?.let { this.inResponseTo = it }
            this.issuer = build<Issuer>(Issuer.DEFAULT_ELEMENT_NAME).apply { value = issuer }
            status = build<Status>(Status.DEFAULT_ELEMENT_NAME).apply { statusCode = build<StatusCode>(StatusCode.DEFAULT_ELEMENT_NAME).apply { value = statusValue } }
        }
        val signingKey = if (wrongKey) otherKey else privateKey; val signingCert = if (wrongKey) otherCertificate else certificate
        val signatures = mutableListOf<Signature>()
        if (signAssertion) assertion.signature = signature(signingKey, signingCert).also { signatures.add(it) }
        if (encryptFor == null) response.assertions.add(assertion)
        if (signResponse) response.signature = signature(signingKey, signingCert).also { signatures.add(it) }
        if (encryptFor == null) {
            marshall(response); signatures.forEach { Signer.signObject(it) }
        } else {
            // 어설션을 먼저 서명한 뒤 암호화해서 응답에 붙인다
            marshall(assertion); assertion.signature?.let { Signer.signObject(it) }
            val encrypter = Encrypter(
                DataEncryptionParameters().apply { algorithm = EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES128 },
                KeyEncryptionParameters().apply { encryptionCredential = CredentialSupport.getSimpleCredential(encryptFor, null); algorithm = EncryptionConstants.ALGO_ID_KEYTRANSPORT_RSAOAEP }
            ).also { it.keyPlacement = Encrypter.KeyPlacement.INLINE }
            response.encryptedAssertions.add(encrypter.encrypt(assertion))
            if (signResponse) { marshall(response); response.signature?.let { Signer.signObject(it) } } else marshall(response)
        }
        val xml = SerializeSupport.nodeToString(response.dom!!)
        return Base64.getEncoder().encodeToString(xml.toByteArray(Charsets.UTF_8))
    }

    private fun signature(key: PrivateKey, cert: X509Certificate): Signature {
        val signature = build<Signature>(Signature.DEFAULT_ELEMENT_NAME)
        SignatureSupport.prepareSignatureParams(signature, SignatureSigningParameters().apply {
            signingCredential = CredentialSupport.getSimpleCredential(cert, key)
            signatureAlgorithm = SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256; signatureReferenceDigestMethod = SignatureConstants.ALGO_ID_DIGEST_SHA256
            signatureCanonicalizationAlgorithm = SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
            keyInfoGenerator = org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory().apply { setEmitEntityCertificate(true) }.newInstance()
        })
        return signature
    }

    private fun marshall(xml: XMLObject) {
        try { XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(xml)!!.marshall(xml) } catch (e: MarshallingException) { throw IllegalStateException(e) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : XMLObject> build(name: QName): T = XMLObjectProviderRegistrySupport.getBuilderFactory().getBuilder(name)!!.buildObject(name) as T
}
