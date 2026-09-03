package com.github.yonaprojects.yona.domain.project

import java.io.File

interface GitService {
    fun createRepository(owner: String, name: String): File
    fun getRepositoryPath(owner: String, name: String): File
    fun deleteRepository(owner: String, name: String): Boolean
    fun cloneRepository(gitUrl: String, owner: String, name: String, authId: String?, authPw: String?): File
}
