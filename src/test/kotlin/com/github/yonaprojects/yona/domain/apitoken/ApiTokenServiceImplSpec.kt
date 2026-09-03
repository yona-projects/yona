package com.github.yonaprojects.yona.domain.apitoken

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.springframework.beans.factory.annotation.Autowired

// yona-wiki P3-02 Step6.6 — ApiTokenService 발급/조회/폐기 로직 검증. OrganizationRepositorySpec/
// ApiTokenSpec 패턴(AbstractIntegrationTest + 리포지토리 직접 주입)을 그대로 따른다.
class ApiTokenServiceImplSpec @Autowired constructor(
    private val apiTokenService: ApiTokenService,
    private val apiTokenRepository: ApiTokenRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository
) : AbstractIntegrationTest() {

    init {
        describe("ApiTokenServiceImpl") {
            beforeEach {
                apiTokenRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            afterSpec {
                apiTokenRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("이름/스코프를 지정해 토큰을 발급하면 원문 토큰과 해시가 다르고, 해시로 조회할 수 있어야 한다") {
                val owner = userRepository.save(
                    User(loginId = "issue-owner", name = "발급자", email = "issue-owner@example.com")
                )

                val issued = apiTokenService.issue(
                    owner = owner,
                    name = "CI 토큰",
                    allRepositories = true,
                    scopedProjectIds = emptyList(),
                    scopePermissions = mapOf(ApiTokenScopeGroup.ISSUES to ApiTokenPermission.WRITE),
                    expiresInDays = 30
                )

                issued.rawToken shouldNotBe issued.apiToken.tokenHash
                issued.apiToken.name shouldBe "CI 토큰"
                issued.apiToken.id shouldNotBe null

                val found = apiTokenRepository.findByTokenHash(hashApiToken(issued.rawToken)).orElse(null)
                found shouldNotBe null
                found.scopes.find { it.scopeGroup == ApiTokenScopeGroup.ISSUES }?.permission shouldBe ApiTokenPermission.WRITE
            }

            // 갭 분석 5번("토큰 형식에 식별 프리픽스 없음") 해소 — GitHub의 github_pat_/ghp_처럼,
            // 시크릿 스캐닝 툴이 인식하고 로그/코드에 노출됐을 때 육안으로도 식별 가능하게 한다.
            it("발급된 토큰 원문은 yona_pat_ 프리픽스로 시작해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "prefix-owner", name = "발급자P", email = "prefix-owner@example.com")
                )

                val issued = apiTokenService.issue(
                    owner = owner,
                    name = "프리픽스 확인용 토큰",
                    allRepositories = true,
                    scopedProjectIds = emptyList(),
                    scopePermissions = emptyMap(),
                    expiresInDays = 30
                )

                issued.rawToken shouldStartWith "yona_pat_"
            }

            it("permission이 NONE인 그룹은 ApiTokenScope로 저장하지 않아야 한다") {
                val owner = userRepository.save(
                    User(loginId = "none-owner", name = "발급자2", email = "none-owner@example.com")
                )

                val issued = apiTokenService.issue(
                    owner = owner,
                    name = "일부 스코프만",
                    allRepositories = true,
                    scopedProjectIds = emptyList(),
                    scopePermissions = mapOf(
                        ApiTokenScopeGroup.ISSUES to ApiTokenPermission.READ,
                        ApiTokenScopeGroup.CODE to ApiTokenPermission.NONE
                    ),
                    expiresInDays = 30
                )

                issued.apiToken.scopes.size shouldBe 1
                issued.apiToken.scopes.first().scopeGroup shouldBe ApiTokenScopeGroup.ISSUES
            }

            it("선택 저장소(allRepositories=false)로 발급하면 지정한 프로젝트만 scopedProjects에 담겨야 한다") {
                val owner = userRepository.save(
                    User(loginId = "selective-owner", name = "발급자3", email = "selective-owner@example.com")
                )
                val allowedProject = projectRepository.save(Project(owner = owner.loginId, name = "allowed-repo"))
                projectRepository.save(Project(owner = owner.loginId, name = "excluded-repo"))

                val issued = apiTokenService.issue(
                    owner = owner,
                    name = "선택 저장소 토큰",
                    allRepositories = false,
                    scopedProjectIds = listOf(allowedProject.id!!),
                    scopePermissions = mapOf(ApiTokenScopeGroup.CODE to ApiTokenPermission.READ),
                    expiresInDays = 30
                )

                issued.apiToken.allRepositories shouldBe false
                issued.apiToken.scopedProjects.map { it.name } shouldBe listOf("allowed-repo")
            }

            it("이름이 비어있으면 발급을 거부해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "blank-name-owner", name = "발급자4", email = "blank-name-owner@example.com")
                )

                shouldThrow<IllegalArgumentException> {
                    apiTokenService.issue(
                        owner = owner,
                        name = "  ",
                        allRepositories = true,
                        scopedProjectIds = emptyList(),
                        scopePermissions = emptyMap(),
                        expiresInDays = 30
                    )
                }
            }

            // 갭 분석 4번("만료일 상한 없음") 해소 — GitHub Fine-grained PAT의 "최대 1년" 제약 대응.
            it("만료일이 366일을 초과하면 발급을 거부해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "long-expiry-owner", name = "발급자5", email = "long-expiry-owner@example.com")
                )

                shouldThrow<IllegalArgumentException> {
                    apiTokenService.issue(
                        owner = owner,
                        name = "너무 긴 만료일",
                        allRepositories = true,
                        scopedProjectIds = emptyList(),
                        scopePermissions = emptyMap(),
                        expiresInDays = 367
                    )
                }
            }

            it("listByOwner는 해당 owner의 토큰만 최신순으로 반환해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "list-owner", name = "발급자6", email = "list-owner@example.com")
                )
                val otherOwner = userRepository.save(
                    User(loginId = "other-owner", name = "다른유저", email = "other-owner@example.com")
                )
                apiTokenService.issue(owner, "토큰A", true, emptyList(), emptyMap(), 30)
                apiTokenService.issue(owner, "토큰B", true, emptyList(), emptyMap(), 30)
                apiTokenService.issue(otherOwner, "다른유저 토큰", true, emptyList(), emptyMap(), 30)

                val tokens = apiTokenService.listByOwner(owner)

                tokens.map { it.name } shouldBe listOf("토큰B", "토큰A")
            }

            it("revoke는 owner 본인 토큰만 삭제하고 다른 사용자의 토큰은 건드리지 않아야 한다") {
                val owner = userRepository.save(
                    User(loginId = "revoke-owner", name = "발급자7", email = "revoke-owner@example.com")
                )
                val stranger = userRepository.save(
                    User(loginId = "revoke-stranger", name = "타인", email = "revoke-stranger@example.com")
                )
                val issued = apiTokenService.issue(owner, "폐기될 토큰", true, emptyList(), emptyMap(), 30)
                val strangersToken = apiTokenService.issue(stranger, "타인의 토큰", true, emptyList(), emptyMap(), 30)

                apiTokenService.revoke(stranger, issued.apiToken.id!!) // 타인이 폐기 시도 -> 무시되어야 함
                apiTokenRepository.findById(issued.apiToken.id!!).isPresent shouldBe true

                apiTokenService.revoke(owner, issued.apiToken.id!!)
                apiTokenRepository.findById(issued.apiToken.id!!).isPresent shouldBe false
                apiTokenRepository.findById(strangersToken.apiToken.id!!).isPresent shouldBe true
            }
        }
    }
}
