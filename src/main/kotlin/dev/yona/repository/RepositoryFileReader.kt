package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.io.CharsetDetection
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.stereotype.Component

/**
 * 저장소의 `HEAD`에 있는 파일을 문자열로 읽는다. 이슈 템플릿(`ISSUE_TEMPLATE.md`)처럼 저장소 파일을 한 번 읽어 쓰는 곳에 쓴다.
 * v1.6 `PlayRepository.getRawFile("HEAD", path)`와 `FileUtil.detectCharset`에 해당한다.
 */
@Component
class RepositoryFileReader(private val repositories: RepositoryService, private val svn: dev.yona.repository.svn.SvnRepositories) {

    /**
     * [path] 파일의 내용을 읽는다. 저장소를 열 수 없거나 파일이 없거나 디렉터리이면 `null`이다.
     * v1.6처럼 읽는 중 생긴 모든 오류는 파일이 없는 것으로 본다.
     */
    fun readText(project: Project, path: String): String? = try {
        if (project.vcs == RepositoryService.VCS_SUBVERSION) {
            svn.rawFile(project.owner.orEmpty(), project.name.orEmpty(), "HEAD", path)?.let { String(it, java.nio.charset.Charset.forName(CharsetDetection.detect(it))) }
        } else repositories.openRepository(project).use { repository ->
            val head = repository.resolve(Constants.HEAD) ?: return@use null
            RevWalk(repository).use { walk ->
                val treeWalk = TreeWalk.forPath(repository, path, walk.parseTree(head)) ?: return@use null
                treeWalk.use {
                    if (it.isSubtree) return@use null
                    val bytes = repository.open(it.getObjectId(0)).bytes
                    String(bytes, java.nio.charset.Charset.forName(CharsetDetection.detect(bytes)))
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}
