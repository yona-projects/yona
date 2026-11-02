package dev.yona.domain.issue

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.pullrequest.PullRequest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * Magpie의 이슈 계약(`contract:tracker`, 로드맵 X-23)을 채우는 읽기 전용 조회이다. 이슈를 닫은 시각, 작성자의 첫 기여 시각, 사람과 기간별 댓글 활동을 한 번에 묶어 구한다.
 * 활동의 기준은 **댓글**이다(Magpie의 GitHub 어댑터가 `commenter:`로 찾는 것과 같다). 날짜는 그 사람이 창 안에서 처음 댓글을 단 시각이고 창은 `since` 이상 `until` 미만이다.
 */
@Service
@Transactional(readOnly = true)
class TrackerActivityService(private val issues: IssueRepository) {

    @PersistenceContext
    private lateinit var em: EntityManager

    /** 활동이 있는 스레드(이슈 또는 풀 리퀘스트) 하나이다. [commentUrlSuffix]는 밀어내기 후보 댓글을 가리키는 앵커(`#comment-번호`)이고 없으면 `null`이다. */
    data class ActivityThread(val number: Long, val isPullRequest: Boolean, val firstActivity: Date, val labels: List<String>, val pushbackCommentId: Long? = null)

    /** 이슈 번호(행 번호) → 닫은 시각이다. 닫힘 이벤트가 없는 이슈(옮겨 온 데이터)는 들어 있지 않다. */
    fun closedAt(issueIds: Collection<Long>): Map<Long, Date> {
        if (issueIds.isEmpty()) return emptyMap()
        return em.createQuery(
            "select e.issue.id, max(e.created) from IssueEvent e where e.issue.id in :ids and e.eventType = :type and lower(cast(e.newValue as string)) = 'closed' group by e.issue.id", Array<Any>::class.java
        ).setParameter("ids", issueIds).setParameter("type", EventType.ISSUE_STATE_CHANGED).resultList.mapNotNull { row -> (row[0] as? Long)?.let { id -> (row[1] as? Date)?.let { id to it } } }.toMap()
    }

    /** 작성자의 첫 기여이다. 시각이 같으면 이슈가 풀 리퀘스트보다, 번호(행 번호)가 작은 쪽이 먼저이다. */
    data class FirstContribution(val date: Date, val isPullRequest: Boolean, val id: Long)

    /** 작성자(사용자 번호) → 이 프로젝트에서 처음 이슈나 풀 리퀘스트를 낸 것이다. 시각은 초 단위라 같은 시각에 낸 것이 있으면 [FirstContribution]의 순서로 하나만 가린다. */
    fun firstContributions(projectId: Long, authorIds: Collection<Long>): Map<Long, FirstContribution> {
        if (authorIds.isEmpty()) return emptyMap()
        val result = HashMap<Long, FirstContribution>()
        fun offer(author: Long, candidate: FirstContribution) {
            val current = result[author]
            val better = current == null || candidate.date.before(current.date) ||
                (candidate.date == current.date && (current.isPullRequest && !candidate.isPullRequest || current.isPullRequest == candidate.isPullRequest && candidate.id < current.id))
            if (better) result[author] = candidate
        }
        // 옮겨 온 옛 데이터에는 시각이 비어 있는 행이 있을 수 있어 건너뛴다
        em.createQuery("select i.authorId, i.createdDate, i.id from Issue i where i.project.id = :p and i.authorId in :ids and i.createdDate is not null and i.createdDate = " +
            "(select min(j.createdDate) from Issue j where j.project.id = :p and j.authorId = i.authorId)", Array<Any>::class.java)
            .setParameter("p", projectId).setParameter("ids", authorIds).resultList.forEach { offer(it[0] as Long, FirstContribution(it[1] as Date, false, it[2] as Long)) }
        em.createQuery("select p.contributor.id, p.created, p.id from PullRequest p where p.toProject.id = :p and p.contributor.id in :ids and p.created is not null and p.created = " +
            "(select min(q.created) from PullRequest q where q.toProject.id = :p and q.contributor.id = p.contributor.id)", Array<Any>::class.java)
            .setParameter("p", projectId).setParameter("ids", authorIds).resultList.forEach { offer(it[0] as Long, FirstContribution(it[1] as Date, true, it[2] as Long)) }
        return result
    }

    /**
     * [login]이 [since] 이상 [until] 미만에 댓글을 단 스레드이다. [triagedOnly]이면 **다른 사람이 연 이슈**만(풀 리퀘스트와 자기 이슈는 뺀다). 처음 댓글을 단 시각 순이다.
     * [maintainers]와 [phrases]가 있으면 스레드마다 구성원이 처음 남긴 문구 댓글을 찾아 [ActivityThread.pushbackCommentId]에 담는다(본문은 내보내지 않는다).
     */
    fun commented(projectId: Long, login: String, since: Date, until: Date, triagedOnly: Boolean, maintainers: Set<String> = emptySet(), phrases: List<String> = emptyList()): List<ActivityThread> {
        val issueRows = em.createQuery(
            "select c.issue.id, min(c.createdDate) from IssueComment c where c.projectId = :p and c.authorLoginId = :login and c.createdDate >= :since and c.createdDate < :until" +
                (if (triagedOnly) " and (c.issue.authorLoginId is null or c.issue.authorLoginId <> :login)" else "") + " group by c.issue.id", Array<Any>::class.java
        ).setParameter("p", projectId).setParameter("login", login).setParameter("since", since).setParameter("until", until).resultList
        val pullRows = if (triagedOnly) emptyList() else em.createQuery(
            "select t.pullRequest.id, min(rc.createdDate) from ReviewComment rc join rc.thread t where t.pullRequest is not null and t.pullRequest.toProject.id = :p and rc.authorLoginId = :login " +
                "and rc.createdDate >= :since and rc.createdDate < :until group by t.pullRequest.id", Array<Any>::class.java
        ).setParameter("p", projectId).setParameter("login", login).setParameter("since", since).setParameter("until", until).resultList

        // 분류(`triaged`)는 댓글에 더해 라벨, 상태, 담당자, 마일스톤을 바꾼 것도 센다(Magpie 계약 `list_triaged`). 날짜는 둘 중 이른 쪽이다
        val firstByIssue = LinkedHashMap<Long, Date>()
        issueRows.forEach { row -> firstByIssue[row[0] as Long] = row[1] as Date }
        if (triagedOnly) {
            em.createQuery(
                "select e.issue.id, min(e.created) from IssueEvent e where e.issue.project.id = :p and e.senderLoginId = :login and e.eventType in :types and e.created >= :since and e.created < :until " +
                    "and (e.issue.authorLoginId is null or e.issue.authorLoginId <> :login) group by e.issue.id", Array<Any>::class.java
            ).setParameter("p", projectId).setParameter("login", login).setParameter("since", since).setParameter("until", until).setParameter("types", TRIAGE_EVENTS).resultList.forEach { row ->
                val id = row[0] as? Long ?: return@forEach; val date = row[1] as? Date ?: return@forEach
                firstByIssue.merge(id, date) { a, b -> if (a.before(b)) a else b }
            }
        }
        val threads = ArrayList<ActivityThread>()
        val issueById = issues.findAllById(firstByIssue.keys).associateBy { it.id }
        firstByIssue.forEach { (id, date) ->
            val issue = issueById[id] ?: return@forEach
            threads += ActivityThread(requireNotNull(issue.number), false, date, issue.labels.map { it.name.orEmpty() }.sorted(), pushback(issue, maintainers, phrases))
        }
        pullRows.forEach { row ->
            val pull = em.find(PullRequest::class.java, row[0] as Long) ?: return@forEach
            threads += ActivityThread(requireNotNull(pull.number), true, row[1] as Date, pull.labels.map { it.name.orEmpty() }.sorted())
        }
        return threads.sortedWith(compareBy({ it.firstActivity }, { it.isPullRequest }, { it.number }))
    }

    /** 구성원이 처음 남긴, 문구를 담은 댓글의 번호이다. 문구나 명단이 없거나 해당 댓글이 없으면 `null`이다. */
    private fun pushback(issue: Issue, maintainers: Set<String>, phrases: List<String>): Long? {
        if (maintainers.isEmpty() || phrases.isEmpty()) return null
        val wanted = phrases.map { it.lowercase() }
        return em.createQuery("select c from IssueComment c where c.issue.id = :id order by c.createdDate asc, c.id asc", IssueComment::class.java).setParameter("id", issue.id).resultList
            .firstOrNull { c -> c.authorLoginId in maintainers && c.contents.orEmpty().lowercase().let { text -> wanted.any { it in text } } }?.id
    }

    private companion object {
        /** 분류로 세는 이슈 이벤트이다: 상태, 담당자, 라벨, 마일스톤 변경. */
        val TRIAGE_EVENTS = listOf(EventType.ISSUE_STATE_CHANGED, EventType.ISSUE_ASSIGNEE_CHANGED, EventType.ISSUE_LABEL_CHANGED, EventType.ISSUE_MILESTONE_CHANGED)
    }
}
