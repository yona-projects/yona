package dev.yona.repository.hg

import dev.yona.domain.gpgkey.SignedPayload
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.gpg.HgSigsFile

/**
 * Mercurial 커밋의 서명을 읽는다(X-7 9c, `decisions.md` D-88). Mercurial의 서명(`hg sign`, `hgext/gpg`)은 **체인지셋의 노드(40자 16진수와 줄바꿈)만** 서명하고,
 * 그 서명은 저장소 안 `.hgsigs` 파일에 한 줄(`<노드> 0 <base64 서명>`)로 적어 **나중 커밋**에 담는다. 그래서 서명한 커밋 자신은 서명을 갖지 못하고, 어떤 커밋이
 * 서명됐는지는 **보고 있는 리비전에서 보이는 `.hgsigs`**로 정한다(`hg sigs`와 같은 기준, D-88 1번). 서명 검증은 Git과 같은 `GpgVerificationService`가 한다.
 */
object HgSignatures {

    /**
     * 리비전 [atNode]에서 보이는 `.hgsigs`가 담은 서명이다: 서명한 노드(40자) → 서명 원문과 서명. 한 노드에 서명이 여럿이면 앞의 것이다. `.hgsigs`가 없거나 읽을
     * 수 없는 줄은 서명 없음으로 보고 건너뛴다(한 줄이 깨졌다고 나머지 서명까지 버리지 않는다). [atNode]가 `null`이면 비어 있다.
     */
    fun visibleAt(hg: Hg, atNode: String?): Map<String, SignedPayload> {
        if (atNode == null) return emptyMap()
        val text = try { String(hg.cat().setFile(SIGS_FILE).setRevision(atNode).call(), Charsets.UTF_8) } catch (e: Exception) { return emptyMap() }
        val result = LinkedHashMap<String, SignedPayload>()
        for (line in text.lines()) {
            if (line.isBlank()) continue
            val entry = try { HgSigsFile.parse(line, SIGS_FILE).singleOrNull()?.entry } catch (e: Exception) { null } ?: continue
            try {
                result.putIfAbsent(entry.nodeHex, SignedPayload(entry.signedText(), entry.signature))
            } catch (e: Exception) {
                // 알 수 없는 서명 버전은 건너뛴다
            }
        }
        return result
    }

    /** 체인지셋이 서명 파일(`.hgsigs`)만 바꾸는 커밋이면 `true`이다. 이런 커밋은 자기 자신을 서명할 수 없다(D-88 3번). */
    fun onlySigns(files: List<String>): Boolean = files.isNotEmpty() && files.all { it == SIGS_FILE }

    /** 서명 파일의 이름이다. */
    const val SIGS_FILE = ".hgsigs"
}
