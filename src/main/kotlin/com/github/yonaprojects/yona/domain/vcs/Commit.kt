package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.gpgkey.GpgVerificationStatus
import com.github.yonaprojects.yona.domain.user.User
import java.util.Date
import java.util.TimeZone

abstract class Commit {
    abstract fun getShortId(): String
    abstract fun getId(): String
    abstract fun getShortMessage(): String
    abstract fun getMessage(): String?
    abstract fun getAuthor(): User?
    abstract fun getAuthorName(): String?
    abstract fun getAuthorEmail(): String?
    abstract fun getAuthorDate(): Date?
    abstract fun getAuthorTimezone(): TimeZone?
    abstract fun getCommitterName(): String?
    abstract fun getCommitterEmail(): String?
    abstract fun getCommitterDate(): Date?
    abstract fun getCommitterTimezone(): TimeZone?
    abstract fun getParentCount(): Int

    // 커밋은 이 앱에서 JPA 엔티티로 미러링되지 않고 항상 git/svn 저장소
    // 원본에서 그때그때 읽어오므로, GPG 검증
    // 결과도 DB 캐시 컬럼이 아니라 조회 시점에 즉시 계산해 반환한다(GitCommit 참고). SVN은 GPG
    // 서명 개념이 없으므로 SvnCommit은 항상 UNSIGNED를 반환한다.
    abstract fun getGpgVerificationStatus(): GpgVerificationStatus
}
