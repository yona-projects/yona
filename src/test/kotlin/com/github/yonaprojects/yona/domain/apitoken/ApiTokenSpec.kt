package com.github.yonaprojects.yona.domain.apitoken

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

// yona-wiki P3-02 Step1 — 2026-08-24 결정("무기한 토큰 발급 금지")을 실제 DB 제약으로 강제하는지
// 검증한다. OrganizationRepositorySpec 패턴(AbstractIntegrationTest + 리포지토리 직접 주입) 참고.
class ApiTokenSpec @Autowired constructor(
    private val apiTokenRepository: ApiTokenRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("ApiToken") {
            beforeEach {
                apiTokenRepository.deleteAll()
                userRepository.deleteAll()
            }

            // beforeEach는 각 테스트 "이전"에만 청소하므로 스펙의 마지막 테스트가 남긴 데이터는
            // 청소되지 않고 공유 테스트 DB에 그대로 남는다 — api_token.owner_id가 cascade delete
            // 없는 FK라, 이후 실행되는 다른 스펙의 userRepository.deleteAll()이 그 유저를 못 지워
            // 깨지는 문제가 전체 스위트 실행에서 실제로 발생했다(ApiTokenScopedAuthorizationIntegrationSpec과
            // 동일한 원인).
            afterSpec {
                apiTokenRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("expiresAt이 null이면 저장이 거부되어야 한다") {
                val owner = userRepository.save(
                    User(loginId = "token-owner", name = "토큰소유자", email = "token-owner@example.com")
                )
                val token = ApiToken(owner = owner, tokenHash = "hash-without-expiry", expiresAt = null)

                // DB 제약(NOT NULL) 위반 자체는 5개 DB 전부 동일하게 저장을 거부한다. CUBRID JDBC
                // 드라이버(11.3.2.0053)는 이 위반에 SQLState를 안 주고 벤더 고유 errorCode(-631)만
                // 주는 것으로 실측 확인됐는데, YonaCubridDialect.buildSQLExceptionConversionDelegate()가
                // 이 errorCode를 ConstraintViolationException(NOT_NULL)으로 명시 변환하므로 나머지
                // 4개 DB(MariaDB/Postgres/MySQL/SQL Server)와 동일하게 DataIntegrityViolationException으로
                // 번역된다.
                shouldThrow<org.springframework.dao.DataIntegrityViolationException> {
                    apiTokenRepository.saveAndFlush(token)
                }
            }

            it("expiresAt이 있으면 정상적으로 저장되고 tokenHash로 조회할 수 있어야 한다") {
                val owner = userRepository.save(
                    User(loginId = "token-owner2", name = "토큰소유자2", email = "token-owner2@example.com")
                )
                val expiry = Instant.now().plus(30, ChronoUnit.DAYS)
                val token = ApiToken(
                    owner = owner,
                    tokenHash = "hash-with-expiry",
                    expiresAt = expiry,
                    allRepositories = true
                )

                val saved = apiTokenRepository.saveAndFlush(token)

                saved.id shouldNotBe null

                val found = apiTokenRepository.findByTokenHash("hash-with-expiry").orElse(null)
                found shouldNotBe null
                // datetime 컬럼의 소수초 저장 정밀도는 DB마다 다르다(MariaDB/Postgres/MySQL=마이크로초,
                // CUBRID=밀리초, SQL Server=100나노초 — 5개 DB 전부 실측 확인). Instant.now()는 나노초
                // 정밀도라 어느 DB든 저장 후 재조회하면 정밀도 이하 자리가 잘려나가 정확히 일치하지
                // 않는다. expiresAt은 만료 여부 판정에만 쓰여 이 정도 정밀도 손실이 의미 없으므로,
                // 정확히 같은 값이 아니라 "차이가 1초 미만"인지로 근사 비교한다 — 어떤 DB가 어떤
                // 단위로 절삭하든(그리고 앞으로 새 DB가 추가되든) 깨지지 않는다.
                Duration.between(expiry, found.expiresAt!!).abs() shouldBeLessThan Duration.ofSeconds(1)
                found.allRepositories shouldBe true
                found.owner?.loginId shouldBe "token-owner2"
            }

            it("lastUsedAt은 발급 시점에 비워둘 수 있어야 한다(아직 사용된 적 없음)") {
                val owner = userRepository.save(
                    User(loginId = "token-owner3", name = "토큰소유자3", email = "token-owner3@example.com")
                )
                val token = ApiToken(
                    owner = owner,
                    tokenHash = "hash-unused",
                    expiresAt = Instant.now().plus(1, ChronoUnit.DAYS)
                )

                val saved = apiTokenRepository.saveAndFlush(token)

                saved.lastUsedAt shouldBe null
            }
        }
    }
}
