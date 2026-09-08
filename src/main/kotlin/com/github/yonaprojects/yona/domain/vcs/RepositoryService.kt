package com.github.yonaprojects.yona.domain.vcs

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import com.github.yonaprojects.yona.domain.gpgkey.GpgSignatureVerifier
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File

@Service
class RepositoryService(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    // yona-wiki P3-03 Step9 — 커밋 목록/상세 화면의 GPG Verified 배지 계산에 쓴다.
    private val gpgSignatureVerifier: GpgSignatureVerifier,
    @Value("\${yona.git.base-dir:/tmp/yona/git}")
    private val gitBaseDir: String,
    @Value("\${yona.svn.base-dir:/tmp/yona/svn}")
    private val svnBaseDir: String,
    // yona-wiki P3-12(Mercurial 지원) 1라운드.
    @Value("\${yona.hg.base-dir:/tmp/yona/hg}")
    private val hgBaseDir: String,
    // 사용자 요청 — 새 프로젝트 기본 브랜치를 "master" 대신 "main"으로. 호스트 git의
    // init.defaultBranch 설정에 기대지 않고 애플리케이션 설정으로 결정론적으로 고정한다.
    @Value("\${yona.git.default-branch:main}")
    private val gitDefaultBranch: String
) {
    private val objectMapper = ObjectMapper()

    fun getRepository(project: Project): PlayRepository {
        val vcsType = project.vcs?.uppercase() ?: "GIT"
        return if (vcsType == "SUBVERSION" || vcsType == "SVN") {
            SvnRepository(
                ownerName = project.owner ?: "",
                projectName = project.name,
                baseDir = svnBaseDir
            ) { loginId ->
                userRepository.findByLoginId(loginId).orElse(null)
            }
        } else if (vcsType == "MERCURIAL" || vcsType == "HG") {
            HgRepository(
                ownerName = project.owner ?: "",
                projectName = project.name,
                baseDir = hgBaseDir,
                userResolver = { _, email ->
                    if (email != null) {
                        userRepository.findByEmail(email).orElse(null)
                    } else {
                        null
                    }
                },
                gpgVerifier = { nativeHgCommit -> gpgSignatureVerifier.verify(nativeHgCommit) }
            )
        } else {
            GitRepository(
                ownerName = project.owner ?: "",
                projectName = project.name,
                baseDir = gitBaseDir,
                userResolver = { _, email ->
                    if (email != null) {
                        userRepository.findByEmail(email).orElse(null)
                    } else {
                        null
                    }
                },
                defaultBranch = gitDefaultBranch,
                gpgVerifier = { revCommit -> gpgSignatureVerifier.verify(revCommit) }
            )
        }
    }

    fun getFileAsRaw(userName: String, projectName: String, revision: String, path: String): ByteArray? {
        val project = projectRepository.findByOwnerAndName(userName, projectName).orElse(null) ?: return null
        return getRepository(project).getRawFile(revision, path)
    }

    fun getMetaDataFromAncestorDirectories(
        repository: PlayRepository,
        branch: String,
        path: String
    ): List<ObjectNode>? {
        val recursiveData = ArrayList<ObjectNode>()
        var partialPath = ""
        val pathArray = path.split("/").filter { it.isNotEmpty() }
        val pathLength = pathArray.size

        val metaData = repository.getMetaDataFromPath(branch, "") ?: return null
        metaData.put("path", "")
        recursiveData.add(metaData)

        for (i in 0 until pathLength) {
            partialPath = if (partialPath.isEmpty()) pathArray[i] else "$partialPath/${pathArray[i]}"
            if (!repository.isIntermediateFolder(partialPath)) {
                val subMeta = repository.getMetaDataFromPath(branch, partialPath) ?: return null
                subMeta.put("path", partialPath)
                recursiveData.add(subMeta)
            }
        }
        return recursiveData
    }
}
