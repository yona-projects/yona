package com.github.yonaprojects.yona.domain.deploykey

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.sshkey.SshPublicKeyFingerprint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired

private const val TEST_PUBLIC_KEY_1 =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHFAhHu7wL23JgqJtpP8u/JUqCaLm1vcYoohMQFAdpXS tester@example.com"
private const val TEST_PUBLIC_KEY_2 =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILRyWi6jud2ngJsCWbqDTigEMGZ6zxc+j8wSz5iQL6Bx tester2@example.com"

// yona-wiki P3-03 Step1 — DeployKey는 "repository_id 스코프 밖 저장소 접근 거부"가 핵심 요구사항
// (계획 문서 Step1 실패 테스트)이고, Step2에서 HTTPS Basic 인증에 쓰일 조회/발급 로직도 겸한다.
class DeployKeyServiceImplSpec @Autowired constructor(
    private val deployKeyService: DeployKeyService,
    private val deployKeyRepository: DeployKeyRepository,
    private val projectRepository: ProjectRepository
) : AbstractIntegrationTest() {

    init {
        describe("DeployKeyServiceImpl") {
            beforeEach {
                deployKeyRepository.deleteAll()
                projectRepository.deleteAll()
            }

            afterSpec {
                deployKeyRepository.deleteAll()
                projectRepository.deleteAll()
            }

            it("공개키를 등록하면 지문이 계산되고 HTTPS 토큰 원문과 해시가 달라야 한다") {
                val project = projectRepository.save(Project(owner = "gildong", name = "repo-a"))

                val issued = deployKeyService.create(project, "노트북 키", TEST_PUBLIC_KEY_1, readOnly = true)

                issued.deployKey.id shouldNotBe null
                issued.deployKey.fingerprint.startsWith("SHA256:") shouldBe true
                issued.rawHttpsToken shouldNotBe issued.deployKey.httpsTokenHash
                deployKeyRepository.findByHttpsTokenHash(hashDeployKeyToken(issued.rawHttpsToken)).isPresent shouldBe true
            }

            it("발급된 HTTPS 토큰 원문은 yona_dk_ 프리픽스로 시작해야 한다") {
                val project = projectRepository.save(Project(owner = "gildong", name = "repo-prefix"))

                val issued = deployKeyService.create(project, "프리픽스 확인", TEST_PUBLIC_KEY_1, readOnly = true)

                issued.rawHttpsToken.startsWith("yona_dk_") shouldBe true
            }

            // 계획 문서 Step1의 실패 테스트: repository_id 범위 밖 저장소 접근은 거부돼야 한다.
            it("isAuthorizedForProject는 자신이 스코프된 프로젝트 id와 다른 프로젝트에는 false를 반환해야 한다") {
                val ownProject = projectRepository.save(Project(owner = "gildong", name = "own-repo"))
                val otherProject = projectRepository.save(Project(owner = "gildong", name = "other-repo"))

                val issued = deployKeyService.create(ownProject, "스코프 확인용 키", TEST_PUBLIC_KEY_1, readOnly = true)

                deployKeyService.isAuthorizedForProject(issued.deployKey, ownProject.id!!) shouldBe true
                deployKeyService.isAuthorizedForProject(issued.deployKey, otherProject.id!!) shouldBe false
            }

            it("이미 등록된 공개키(다른 프로젝트라도)를 다시 등록하려 하면 거부해야 한다") {
                val projectA = projectRepository.save(Project(owner = "gildong", name = "dup-repo-a"))
                val projectB = projectRepository.save(Project(owner = "gildong", name = "dup-repo-b"))

                deployKeyService.create(projectA, "첫 등록", TEST_PUBLIC_KEY_1, readOnly = true)

                shouldThrow<IllegalArgumentException> {
                    deployKeyService.create(projectB, "중복 등록 시도", TEST_PUBLIC_KEY_1, readOnly = true)
                }
            }

            it("형식이 올바르지 않은 공개키는 InvalidPublicKeyException으로 거부해야 한다") {
                val project = projectRepository.save(Project(owner = "gildong", name = "invalid-key-repo"))

                shouldThrow<SshPublicKeyFingerprint.InvalidPublicKeyException> {
                    deployKeyService.create(project, "잘못된 키", "not-a-valid-key", readOnly = true)
                }
            }

            it("findByHttpsToken은 올바른 토큰으로만 조회되어야 하고 사용 시 lastUsedAt이 갱신돼야 한다") {
                val project = projectRepository.save(Project(owner = "gildong", name = "used-repo"))
                val issued = deployKeyService.create(project, "사용 확인용", TEST_PUBLIC_KEY_1, readOnly = true)

                deployKeyService.findByHttpsToken("wrong-token") shouldBe null
                val found = deployKeyService.findByHttpsToken(issued.rawHttpsToken)
                found shouldNotBe null
                found!!.lastUsedAt shouldBe null

                deployKeyService.markUsed(found)
                val reloaded = deployKeyRepository.findById(found.id!!).orElse(null)
                reloaded.lastUsedAt shouldNotBe null
            }

            it("delete는 그 프로젝트 소유의 deploy key만 삭제하고 다른 프로젝트 소유는 건드리지 않아야 한다") {
                val projectA = projectRepository.save(Project(owner = "gildong", name = "delete-repo-a"))
                val projectB = projectRepository.save(Project(owner = "gildong", name = "delete-repo-b"))
                val issuedA = deployKeyService.create(projectA, "A키", TEST_PUBLIC_KEY_1, readOnly = true)
                val issuedB = deployKeyService.create(projectB, "B키", TEST_PUBLIC_KEY_2, readOnly = true)

                // B 프로젝트를 대상으로 A의 deploy key id를 삭제 시도 -> 무시되어야 함.
                deployKeyService.delete(projectB, issuedA.deployKey.id!!)
                deployKeyRepository.findById(issuedA.deployKey.id!!).isPresent shouldBe true

                deployKeyService.delete(projectA, issuedA.deployKey.id!!)
                deployKeyRepository.findById(issuedA.deployKey.id!!).isPresent shouldBe false
                deployKeyRepository.findById(issuedB.deployKey.id!!).isPresent shouldBe true
            }

            it("listByProject는 해당 프로젝트의 deploy key만 반환해야 한다") {
                val projectA = projectRepository.save(Project(owner = "gildong", name = "list-repo-a"))
                val projectB = projectRepository.save(Project(owner = "gildong", name = "list-repo-b"))
                deployKeyService.create(projectA, "A키", TEST_PUBLIC_KEY_1, readOnly = true)
                deployKeyService.create(projectB, "B키", TEST_PUBLIC_KEY_2, readOnly = true)

                val listA = deployKeyService.listByProject(projectA.id!!)
                listA.size shouldBe 1
                listA.first().title shouldBe "A키"
            }
        }
    }
}
