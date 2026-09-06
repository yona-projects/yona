package com.github.yonaprojects.yona.domain.gpgkey

import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.util.RawParseUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

/**
 * yona-wiki P3-03 Step8 — 커밋의 GPG 서명을 실제로 암호학적으로 검증한다(서명이 "있다"만
 * 확인하는 것이 아니라 BouncyCastle로 공개키에 대해 실제 서명 검증을 수행한다 — 보안 리뷰 항목).
 *
 * 정책(계획 문서 "리스크/미결정 사항"에서 착수 시 확정): 미서명 커밋도 push는 항상 허용되고
 * 배지만 표시되지 않는다(UNSIGNED). author 이메일과 서명 키의 (계정 소유로 인증된) UID
 * 이메일이 일치해야만 VERIFIED, 서명은 있으나 암호학적 검증 실패거나 이메일이 일치하지 않으면
 * UNVERIFIED — 이 서비스는 push를 거부하는 훅이 아니라 순수 판정 로직이다(GitPushHooks의
 * PreReceiveHook과 달리 결과를 반환만 한다 — Commit.getGpgVerificationStatus()가 커밋 목록/
 * 상세 조회 시점에 즉시 호출해 화면에 표시하므로 별도의 DB 캐시 테이블이 필요 없다. 커밋은
 * 이 앱에서 애초에 JPA 엔티티로 미러링되지 않고 항상 git 객체 저장소에서 그때그때 읽어오므로
 * "Commit 모델에 검증결과 필드 추가"는 domain/vcs/Commit.kt에 새 추상 메서드를 추가하는
 * 형태로 구현했다 — GitCommit.kt/GitRepository.kt 참고).
 */
@Service
class GpgSignatureVerifier(
    private val gpgKeyRepository: GpgKeyRepository
) {
    private val logger = LoggerFactory.getLogger(GpgSignatureVerifier::class.java)

    fun verify(commit: RevCommit): GpgVerificationStatus {
        val rawSignature = commit.rawGpgSignature ?: return GpgVerificationStatus.UNSIGNED

        val signature = try {
            parseSignature(rawSignature)
        } catch (e: Exception) {
            logger.debug("GPG 서명 파싱 실패(commit={}): {}", commit.name, e.message)
            return GpgVerificationStatus.UNVERIFIED
        } ?: return GpgVerificationStatus.UNVERIFIED

        val issuerKeyId = GpgPublicKeyParser.formatKeyId(signature.keyID)
        val candidates = gpgKeyRepository.findByAssociatedKeyIdsContaining(issuerKeyId)
        if (candidates.isEmpty()) {
            return GpgVerificationStatus.UNVERIFIED
        }

        val authorEmail = commit.authorIdent?.emailAddress?.trim()?.lowercase()
        val signedData = signedDataOf(commit)

        for (candidate in candidates) {
            val parsed = try {
                GpgPublicKeyParser.parse(candidate.armoredPublicKey)
            } catch (e: Exception) {
                continue
            }
            val signingKey = parsed.publicKeyRing.getPublicKey(signature.keyID) ?: continue

            val cryptographicallyValid = try {
                signature.init(BcPGPContentVerifierBuilderProvider(), signingKey)
                signature.update(signedData)
                signature.verify()
            } catch (e: Exception) {
                logger.debug("GPG 서명 검증 실패(commit={}, keyId={}): {}", commit.name, issuerKeyId, e.message)
                false
            }

            if (cryptographicallyValid && authorEmail != null && candidate.verifiedEmails.contains(authorEmail)) {
                return GpgVerificationStatus.VERIFIED
            }
        }

        return GpgVerificationStatus.UNVERIFIED
    }

    // git이 실제로 서명을 계산하는 대상 바이트를 재구성한다: 커밋 객체의 원본 바이트에서
    // "gpgsig ...(연속줄 포함)..." 헤더 라인 전체(마지막 개행 포함)를 제거한 것과 동일하다.
    // RevCommit.getRawGpgSignature()의 구현(JGit 소스)이 쓰는 것과 동일한
    // RawParseUtils.headerStart/nextLfSkippingSplitLines를 그대로 재사용해 정확히 같은 경계를
    // 계산한다.
    private fun signedDataOf(commit: RevCommit): ByteArray {
        val raw = commit.rawBuffer
        val headerBytes = GPGSIG_HEADER
        val valueStart = RawParseUtils.headerStart(headerBytes, raw, 0)
        if (valueStart < 0) return raw

        val lineStart = valueStart - (headerBytes.size + 1)
        val valueEnd = RawParseUtils.nextLfSkippingSplitLines(raw, valueStart)
        // valueEnd는 그 헤더 값의 마지막 줄바꿈 문자 자신의 인덱스를 가리킨다(RawParseUtils 계약).
        val afterHeaderLine = valueEnd + 1

        return raw.copyOfRange(0, lineStart) + raw.copyOfRange(afterHeaderLine, raw.size)
    }

    // commit.rawGpgSignature는 gpgsig 헤더 값(대개 ASCII-armor PGP SIGNATURE 블록, 드물게 이미
    // 이진 형태)을 그대로 담고 있다. PGPUtil.getDecoderStream()이 armor 여부를 알아서 판별해
    // 디코딩하고, PGPObjectFactory로 패킷을 순회해 첫 PGPSignatureList의 첫 서명을 꺼낸다 —
    // JGit 자체의 커밋 서명 검증 코드(VerifySignatureCommand)가 쓰는 것과 동일한 절차다.
    private fun parseSignature(rawSignature: ByteArray): PGPSignature? {
        val decoderStream = PGPUtil.getDecoderStream(ByteArrayInputStream(rawSignature))
        val factory = PGPObjectFactory(decoderStream, BcKeyFingerprintCalculator())
        var obj = factory.nextObject()
        while (obj != null) {
            if (obj is PGPSignatureList && !obj.isEmpty) {
                return obj[0]
            }
            obj = factory.nextObject()
        }
        return null
    }

    companion object {
        private val GPGSIG_HEADER = "gpgsig".toByteArray(Charsets.US_ASCII)
    }
}
