package dev.yona.repository

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.slf4j.LoggerFactory
import java.io.File
import java.util.SortedMap
import java.util.TreeMap

/**
 * 폴더 목록의 각 항목을 마지막으로 바꾼 커밋을 v1.6과 같은 규칙으로 찾는다 (v1.6 `playRepository.ObjectFinder`).
 *
 * `git log -1 -- 경로`와 결과가 다를 수 있어 그대로 옮긴다(P-10 결정 3, S1 명세 3번). 규칙은 이렇다.
 * 1. 폴더의 항목 이름을 모두 모으고, 로그를 커밋 시각 순으로 훑는다(폴더 경로가 있으면 그 경로로 거른 로그).
 * 2. 커밋마다 폴더 안 항목의 **객체 번호**를 부모의 것과 비교한다(모드와 종류는 비교하지 않는다). **부모 중 하나라도** 같은 번호면 "바뀌지 않았다"고 본다.
 * 3. 모든 부모와 다른 번호를 가진 항목은 그 커밋이 마지막으로 바꾼 것이다.
 * 4. 끝까지 못 찾은 항목은 `git log -1 -- 폴더/이름`으로 찾는다. v1.6은 이때 경로를 `File(basePath, name).path`로 만들어, 루트 목록에서는 `"/이름"`이 되어
 *    **절대 찾지 못한다**. 그래서 빠른 경로가 못 찾은 루트 항목은 목록에서 빠진다. 같은 동작을 따른다.
 *
 * 병합 커밋이 있는 이력에서 `git log -1`과 다르다. 루트 목록은 경로로 거르지 않아 병합을 단순화하지 않는다.
 *
 * @param describe 찾은 커밋의 정보를 항목에 채운다
 * @param keyOf 항목 이름을 결과의 키로 바꾼다 (v1.6 `extendPath`)
 */
class ObjectFinder(
    private val repository: Repository,
    private val basePath: String,
    walk: TreeWalk,
    private val head: ObjectId,
    private val describe: (MutableMap<String, Any?>, RevCommit) -> Unit,
    private val keyOf: (String) -> String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val targets = LinkedHashMap<String, MutableMap<String, Any?>>()
    private val found: SortedMap<String, Map<String, Any?>> = TreeMap()

    init {
        while (walk.next()) targets[walk.nameString] = linkedMapOf("type" to if (walk.isSubtree) "folder" else "file")
    }

    fun find(): SortedMap<String, Map<String, Any?>> {
        Git(repository).use { git ->
            fun logFrom(from: ObjectId) = git.log().add(from).apply { if (basePath.isNotEmpty()) addPath(basePath) }.call().iterator()
            var iterator = logFrom(head)
            var previous: RevCommit? = null
            var current: RevCommit? = null
            var count = 0
            while (count++ < COMMIT_HISTORY_LIMIT && targets.isNotEmpty()) {
                if (iterator.hasNext()) {
                    current = parsed(iterator.next())
                } else {
                    // v1.6의 "Illegal state" 처리: 마지막 커밋에서 로그를 다시 시작한다. 예외가 나면 반복을 끝낸다.
                    try {
                        iterator = logFrom(requireNotNull(current).id)
                        current = parsed(iterator.next())
                        if (current == previous) current = parsed(iterator.next())
                    } catch (e: Exception) {
                        log.warn("Failed to continue the history walk of {}", basePath, e)
                        break
                    }
                }
                val commit = requireNotNull(current)
                val objects = HashMap<String, ObjectId>()
                entries(commit).forEach { (name, id) -> if (name in targets) objects[name] = id }
                for (parent in commit.parents) {
                    entries(parsed(parent)).forEach { (name, id) -> if (id == objects[name]) objects.remove(name) }
                }
                for (name in objects.keys) {
                    record(name, commit)
                    targets.remove(name)
                }
                previous = commit
            }
            // 폴백: 루트에서는 경로가 "/이름"이 되어 찾지 못한다(v1.6과 같은 결함)
            for (name in targets.keys.toList()) {
                val path = File(basePath, name).path
                val iterator2 = git.log().add(head).addPath(path).setMaxCount(1).call().iterator()
                if (iterator2.hasNext()) record(name, iterator2.next())
            }
        }
        return found
    }

    private fun record(name: String, commit: RevCommit) {
        val entry = requireNotNull(targets[name])
        describe(entry, commit)
        found[keyOf(name)] = entry
    }

    private fun parsed(commit: RevCommit): RevCommit = RevWalk(repository).use { it.parseCommit(commit.id) }

    /** 커밋의 폴더 안 항목을 `이름 -> 객체 번호`로 읽는다. 폴더가 없으면 빈 맵이다. */
    private fun entries(commit: RevCommit): Map<String, ObjectId> {
        val result = HashMap<String, ObjectId>()
        TreeWalk(repository).use { walk ->
            if (basePath.isEmpty()) {
                walk.addTree(commit.tree)
            } else {
                val sub = TreeWalk.forPath(repository, basePath, commit.tree) ?: return emptyMap()
                sub.use { if (!it.isSubtree) return emptyMap() else walk.addTree(it.getObjectId(0)) }
            }
            walk.isRecursive = false
            while (walk.next()) result[walk.nameString] = walk.getObjectId(0)
        }
        return result
    }

    companion object {
        /** 훑을 커밋 수의 상한이다 (v1.6 `COMMIT_HISTORY_LIMIT`). */
        const val COMMIT_HISTORY_LIMIT = 1000 * 1000
    }
}
