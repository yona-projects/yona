package com.github.yonaprojects.yona.domain.project

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File

@Service
class GitServiceImpl(
    @Value("\${yona.git.base-dir:/tmp/yona/git}")
    private val baseDir: String
) : GitService {

    override fun createRepository(owner: String, name: String): File {
        val repoDir = getRepositoryPath(owner, name)
        if (repoDir.exists()) {
            return repoDir
        }
        val repository = RepositoryBuilder()
            .setGitDir(repoDir)
            .setBare()
            .build()
        repository.create(true)
        return repoDir
    }

    override fun getRepositoryPath(owner: String, name: String): File {
        return File(baseDir, "$owner/$name.git")
    }

    override fun deleteRepository(owner: String, name: String): Boolean {
        val repoDir = getRepositoryPath(owner, name)
        return if (repoDir.exists()) {
            repoDir.deleteRecursively()
        } else {
            false
        }
    }

    override fun cloneRepository(gitUrl: String, owner: String, name: String, authId: String?, authPw: String?): File {
        val repoDir = getRepositoryPath(owner, name)
        if (repoDir.exists()) {
            repoDir.deleteRecursively()
        }
        val cloneCommand = Git.cloneRepository()
            .setURI(gitUrl)
            .setDirectory(repoDir)
            .setCloneAllBranches(true)
            .setBare(true)

        if (!authId.isNullOrEmpty() || !authPw.isNullOrEmpty()) {
            cloneCommand.setCredentialsProvider(
                UsernamePasswordCredentialsProvider(authId ?: "", authPw ?: "")
            )
        }

        cloneCommand.call().use { git ->
            // resource close
        }
        return repoDir
    }
}
