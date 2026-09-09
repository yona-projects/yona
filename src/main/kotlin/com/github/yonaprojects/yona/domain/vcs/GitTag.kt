package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import org.eclipse.jgit.lib.Constants

// GitBranch와 동일한 패턴의 git 태그 뷰 모델. lightweight(단순 ref)와
// annotated(태그 오브젝트: 태거/메시지 보유) 두 종류를 하나로 표현한다 — annotated=false면
// tagger/message는 항상 null(JGit이 애초에 그 정보를 만들지 않으므로), annotated=true면 최소
// tagger 또는 message 중 하나는 있을 수 있다(태거 없이 메시지만 있는 경우는 없다 — git이 태그
// 오브젝트 생성 시 태거를 항상 강제한다).
data class GitTag(
    val name: String,
    val targetCommit: Commit,
    val tagger: User? = null,
    val message: String? = null,
    val annotated: Boolean = false
) {
    val shortName: String = name.removePrefix(Constants.R_TAGS)
}
