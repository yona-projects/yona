package com.github.yonaprojects.yona.domain.project

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import java.io.File

class GitServiceSpec : DescribeSpec({

    describe("GitService") {
        it("임시 디렉터리 내에 베어(Bare) 저장소를 정상적으로 생성할 수 있어야 한다") {
            // Given
            val tempGitBase = tempdir()
            val gitService = GitServiceImpl(tempGitBase.absolutePath)

            // When
            val repoFile = gitService.createRepository("test-owner", "test-project")

            // Then
            repoFile.exists() shouldBe true
            repoFile.name shouldBe "test-project.git"
            
            File(repoFile, "HEAD").exists() shouldBe true
            File(repoFile, "refs").exists() shouldBe true
        }

        it("저장소 삭제를 요청하면 디렉터리가 물리적으로 지워져야 한다") {
            // Given
            val tempGitBase = tempdir()
            val gitService = GitServiceImpl(tempGitBase.absolutePath)
            gitService.createRepository("test-owner", "test-project")

            // When
            val deleteResult = gitService.deleteRepository("test-owner", "test-project")

            // Then
            deleteResult shouldBe true
            gitService.getRepositoryPath("test-owner", "test-project").exists() shouldBe false
        }

        it("존재하지 않는 저장소 삭제 요청 시 false를 반환해야 한다") {
            val tempGitBase = tempdir()
            val gitService = GitServiceImpl(tempGitBase.absolutePath)

            gitService.deleteRepository("test-owner", "non-existent") shouldBe false
        }

        it("이미 존재하는 저장소에 대해 생성 요청 시 기존 디렉터리를 반환해야 한다") {
            val tempGitBase = tempdir()
            val gitService = GitServiceImpl(tempGitBase.absolutePath)
            val repoFile1 = gitService.createRepository("test-owner", "test-project")
            
            val repoFile2 = gitService.createRepository("test-owner", "test-project")
            repoFile1.absolutePath shouldBe repoFile2.absolutePath
        }

        it("cloneRepository가 정상 동작해야 한다 (인증 정보 포함/미포함 및 기존 디렉터리 덮어쓰기)") {
            val tempGitBase = tempdir()
            val tempRemote = tempdir()
            val gitService = GitServiceImpl(tempGitBase.absolutePath)

            // 원격 저장소 더미 생성
            val remoteRepo = GitServiceImpl(tempRemote.absolutePath).createRepository("remote-owner", "remote-repo")
            
            // 인증 정보 없이 클론
            val cloned1 = gitService.cloneRepository(remoteRepo.absolutePath, "test-owner", "cloned-repo", null, null)
            cloned1.exists() shouldBe true

            // 기존에 존재하는 상태에서 인증 정보 포함하여 클론 시도
            val cloned2 = gitService.cloneRepository(remoteRepo.absolutePath, "test-owner", "cloned-repo", "user", "pass")
            cloned2.exists() shouldBe true
        }

        it("cloneRepository - authPw만 제공되면(authId는 null) 인증 정보를 설정하고 정상 클론해야 한다") {
            val tempGitBase = tempdir()
            val tempRemote = tempdir()
            val gitService = GitServiceImpl(tempGitBase.absolutePath)
            val remoteRepo = GitServiceImpl(tempRemote.absolutePath).createRepository("remote-owner", "remote-repo2")

            val cloned = gitService.cloneRepository(remoteRepo.absolutePath, "test-owner", "cloned-repo-pw-only", null, "pass")
            cloned.exists() shouldBe true
        }

        it("cloneRepository - authId만 제공되면(authPw는 null) 인증 정보를 설정하고 정상 클론해야 한다") {
            val tempGitBase = tempdir()
            val tempRemote = tempdir()
            val gitService = GitServiceImpl(tempGitBase.absolutePath)
            val remoteRepo = GitServiceImpl(tempRemote.absolutePath).createRepository("remote-owner", "remote-repo3")

            val cloned = gitService.cloneRepository(remoteRepo.absolutePath, "test-owner", "cloned-repo-id-only", "user", null)
            cloned.exists() shouldBe true
        }

        it("cloneRepository - authId/authPw가 둘 다 빈 문자열(null 아님)이면 인증 정보 없이 정상 클론해야 한다") {
            val tempGitBase = tempdir()
            val tempRemote = tempdir()
            val gitService = GitServiceImpl(tempGitBase.absolutePath)
            val remoteRepo = GitServiceImpl(tempRemote.absolutePath).createRepository("remote-owner", "remote-repo4")

            val cloned = gitService.cloneRepository(remoteRepo.absolutePath, "test-owner", "cloned-repo-empty-auth", "", "")
            cloned.exists() shouldBe true
        }
    }
})
