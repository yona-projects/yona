package dev.yona.audit

import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.io.File
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 메시지 번들이 v1.6 `conf/messages*`와 같은 키를 가지는지, 템플릿이 참조하는 키가 모두 번들에 있는지 대조한다(동등성 점검 9b).
 *
 * `audit/v16-messages-*.txt`는 v1.6 번들 파일에서 뽑은 키 스냅샷이다. 값은 설정 이름 하나(`error.tooLargeText.admin`)를 빼고 같다(점검 시점에 확인).
 */
class MessageParityTest {

    private fun bundleKeys(file: String): Set<String> = Properties().also { props ->
        File("src/main/resources/$file").reader(Charsets.UTF_8).use { props.load(it) }
    }.stringPropertyNames()

    private fun snapshot(tag: String): Set<String> =
        javaClass.getResourceAsStream("/audit/v16-messages-$tag.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }.toSet()

    /**
     * v1.6에 없고 추가 개발 항목(D-28)에서 일부러 더한 키이다. 키를 더할 때는 `decisions.md`에 근거를 적고 여기에도 올린다.
     * 모든 언어 번들에 이 키들이 있어야 한다(없으면 영어로 대체되는 키가 생긴다).
     * - X-18 Git 태그 화면(D-30): 태그 탭과 태그 화면의 글
     * - 브랜치 삭제 확인창(D-31): `code.branches.deleteConfirm`
     * - X-14 풀 리퀘스트 담당자와 라벨(D-34): 알림 문구와 타임라인 문구
     * - X-37i 기기 인증(D-52): `deviceAuth.*` 화면 글
     * - X-17 프로젝트 위키(D-60): `menu.wiki`, `wiki.*` 화면 글
     */
    private val addedKeys = setOf(
        "menu.wiki", "wiki.pages", "wiki.pages.filter", "wiki.pages.more", "wiki.pages.empty", "wiki.empty.title", "wiki.empty.create", "wiki.page.new", "wiki.page.edit", "wiki.page.delete", "wiki.page.delete.confirm", "wiki.page.history", "wiki.page.lastEdited", "wiki.page.notFound", "wiki.revision.notFound", "wiki.page.title", "wiki.page.title.help", "wiki.page.message", "wiki.page.save", "wiki.error.title", "wiki.error.exists", "wiki.error.conflict", "wiki.error.unavailable", "wiki.history.title", "wiki.history.compare", "wiki.history.empty", "wiki.history.older", "wiki.history.newer", "wiki.compare.title", "wiki.compare.nochange", "wiki.compare.required", "wiki.clone.title", "wiki.clone.copy", "wiki.attach", "wiki.attach.help", "wiki.attach.failed", "wiki.attach.invalid", "wiki.attach.tooLarge",
        "title.tags", "code.tags.annotated", "code.tags.lightweight", "code.tags.notCommit", "code.tags.empty", "code.tags.deleteConfirm",
        "code.branches.deleteConfirm",
        "notification.pullrequest.assigned", "notification.pullrequest.unassigned", "notification.type.pullrequest.assignee.changed",
        "notification.type.pullrequest.label.changed", "pullRequest.event.assignee", "pullRequest.event.message.assigned", "pullRequest.event.message.unassigned", "pullRequest.assignedToMe",
        "notification.type.pullrequest.review.submitted", "notification.type.pullrequest.review.dismissed", "notification.type.pullrequest.review.rule.notice",
        "pullRequest.not.acceptable.because.changes.requested",
        "pullRequest.review.area.title", "pullRequest.review.area.empty", "pullRequest.review.count", "pullRequest.review.author.not.counted", "pullRequest.review.state.approved", "pullRequest.review.state.changes_requested", "pullRequest.review.state.commented", "pullRequest.review.state.dismissed", "pullRequest.review.dismissed.label", "pullRequest.review.dismissed.stale", "pullRequest.review.form.title", "pullRequest.review.form.placeholder", "pullRequest.review.form.commented", "pullRequest.review.form.approved", "pullRequest.review.form.changes_requested", "pullRequest.review.form.submit", "pullRequest.review.dismiss", "pullRequest.review.dismiss.reason", "pullRequest.review.bypass.note", "pullRequest.event.message.review.approved", "pullRequest.event.message.review.changes_requested", "pullRequest.event.message.review.commented", "pullRequest.event.message.review.dismissed", "pullRequest.event.message.review.dismissed.stale",
        "project.reviewer.dismiss.stale", "pullRequest.review.rule.notice", "pullRequest.review.state.notice",
        "deviceAuth.title", "deviceAuth.code.label", "deviceAuth.code.help", "deviceAuth.code.submit", "deviceAuth.error.notFound", "deviceAuth.consent.title", "deviceAuth.consent.description", "deviceAuth.consent.warning",
        "deviceAuth.approve", "deviceAuth.deny", "deviceAuth.done.approved", "deviceAuth.done.denied",
        "apiToken.title", "pullRequest.not.acceptable.because.open.conversations", "pullRequest.not.acceptable.because.no.merge.method", "project.ruleset", "project.ruleset.description", "project.ruleset.new", "project.ruleset.name", "project.ruleset.enforcement", "project.ruleset.enforcement.active", "project.ruleset.enforcement.disabled", "project.ruleset.empty", "project.ruleset.col.target", "project.ruleset.col.rules", "project.ruleset.bypass", "project.ruleset.bypass.description", "project.ruleset.bypass.admins", "project.ruleset.target", "project.ruleset.target.description", "project.ruleset.rules", "project.ruleset.rule.deletions", "project.ruleset.rule.deletions.desc", "project.ruleset.rule.deletions.short", "project.ruleset.rule.linear", "project.ruleset.rule.linear.desc", "project.ruleset.rule.linear.short", "project.ruleset.rule.pr", "project.ruleset.rule.pr.desc", "project.ruleset.rule.pr.short", "project.ruleset.rule.approvals", "project.ruleset.rule.approvals.desc", "project.ruleset.rule.changes", "project.ruleset.rule.changes.short", "project.ruleset.rule.conversation", "project.ruleset.rule.conversation.short", "project.ruleset.rule.force", "project.ruleset.rule.force.desc", "project.ruleset.rule.force.short", "project.ruleset.rule.updates", "project.ruleset.rule.updates.desc", "project.ruleset.rule.updates.short", "pullRequest.not.acceptable.because.updates.restricted", "project.ruleset.summary.approvals", "project.ruleset.save", "project.ruleset.cancel", "project.ruleset.delete", "project.ruleset.legacy", "project.ruleset.error", "project.ruleset.error.name", "project.ruleset.error.pattern", "pullRequest.merge.desc.merge", "pullRequest.merge.desc.squash", "pullRequest.merge.desc.rebase", "pullRequest.merge.options", "pullRequest.merge.message.edit", "pullRequest.merge.message.title", "pullRequest.merge.message.body", "pullRequest.merge.rebase.conflict", "pullRequest.merge.rebase.merge-commits", "pullRequest.event.message.merged.squash", "pullRequest.event.message.merged.rebase", "project.merge.title", "project.merge.allowMergeCommit", "project.merge.allowSquashMerge", "project.merge.allowRebaseMerge", "project.merge.default", "project.merge.merge", "project.merge.squash", "project.merge.rebase", "project.merge.description", "reviewComment.edited", "pullRequest.review.pending.start", "pullRequest.review.pending.add", "pullRequest.review.pending.single", "pullRequest.review.pending.badge", "pullRequest.review.pending.bar", "pullRequest.review.pending.submit", "pullRequest.review.pending.view", "pullRequest.review.pending.discard", "pullRequest.review.pending.discard.confirm", "pullRequest.review.pending.delete", "pullRequest.review.state.pending", "pullRequest.review.request.placeholder", "pullRequest.review.request.cancel", "pullRequest.review.request.again", "pullRequest.review.request.done", "pullRequest.review.request.cancelled", "pullRequest.reviewRequestedFilter", "pullRequest.reviewRequestedOfMe", "notification.pullrequest.review.requested", "notification.type.pullrequest.review.requested", "notification.type.pullrequest.review.unrequested", "pullRequest.event.reviewRequest", "pullRequest.event.message.reviewRequested", "pullRequest.event.message.reviewUnrequested", "apiToken.edit", "apiToken.save", "apiToken.regenerate", "apiToken.regenerate.description", "apiToken.regenerate.confirm", "apiToken.delete.description", "apiToken.name.help", "apiToken.scopes.help", "apiToken.description", "apiToken.list.empty", "apiToken.name", "apiToken.prefix", "apiToken.scopes", "apiToken.createdDate", "apiToken.lastUsed", "apiToken.expires", "apiToken.neverUsed", "apiToken.noExpiration", "apiToken.expired", "apiToken.delete", "apiToken.delete.confirm", "apiToken.legacy", "apiToken.legacy.warning", "apiToken.create", "apiToken.create.name", "apiToken.create.expiration", "apiToken.expiration.days", "apiToken.expiration.none", "apiToken.expiration.custom", "apiToken.create.expiresDate", "apiToken.create.scopes", "apiToken.created.once", "apiToken.error.name", "apiToken.error.scope", "apiToken.error.expires", "apiToken.error.limit", "apiToken.scope.repo", "apiToken.scope.public_repo", "apiToken.scope.admin:repo_hook", "apiToken.scope.write:repo_hook", "apiToken.scope.read:repo_hook", "apiToken.scope.admin:org", "apiToken.scope.write:org", "apiToken.scope.read:org", "apiToken.scope.user", "apiToken.scope.read:user", "apiToken.scope.user:email", "apiToken.scope.site_admin"
    )

    @Test
    fun `언어별 번들의 키는 v1_6과 같고 추가한 키만 더 있다`() {
        mapOf(
            "default" to "messages.properties", "ja_JP" to "messages_ja_JP.properties", "ko_KR" to "messages_ko_KR.properties",
            "ru_RU" to "messages_ru_RU.properties", "uz_UZ" to "messages_uz_UZ.properties"
        ).forEach { (tag, file) ->
            val v16 = snapshot(tag); val v2 = bundleKeys(file)
            assertEquals(emptySet(), v16 - v2, "$file: v1.6에 있고 v2에 없는 키")
            assertEquals(addedKeys, v2 - v16, "$file: v2에만 있는 키는 의도적으로 더한 키와 같아야 한다")
        }
    }

    @Test
    fun `템플릿이 참조하는 메시지 키는 모두 번들에 있다`() {
        val keys = bundleKeys("messages.properties")
        val comment = Regex("<!--/\\*.*?\\*/-->", RegexOption.DOT_MATCHES_ALL)
        val refs = Regex("#\\{([A-Za-z0-9_.\\-]+)[\\s,)}]|#messages\\.msg(?:OrNull)?\\('([A-Za-z0-9_.\\-]+)'")
        val missing = PathMatchingResourcePatternResolver().getResources("file:src/main/resources/templates/**/*.html").flatMap { resource ->
            val text = comment.replace(resource.file.readText(Charsets.UTF_8), "")
            refs.findAll(text).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.filter { it !in keys }.map { "${resource.file.name}: $it" }.toList()
        }
        assertTrue(missing.isEmpty(), "번들에 없는 키: $missing")
    }
}
