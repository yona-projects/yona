package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.deploykey.DeployKey
import com.github.yonaprojects.yona.domain.gpgkey.GpgKey
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationConsent
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClient
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.sshkey.SshKey
import com.github.yonaprojects.yona.domain.twofactor.TotpSecretEncryptor
import com.github.yonaprojects.yona.domain.twofactor.TwoFactorTotpCredential
import com.github.yonaprojects.yona.domain.twofactor.WebauthnCredential
import com.github.yonaprojects.yona.domain.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.jdbc.core.JdbcTemplate
import java.security.MessageDigest
import java.sql.DriverManager
import java.sql.Types
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.TimeUnit

class SecurityColumnPortabilityTest {
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun securityValuesSurviveFullSchemaUpdateWithoutCrossDialectTypeChanges() {
        SchemaTestDatabase.dataSource().use { dataSource ->
            val administrator = if (SchemaTestDatabase.kind == "cubrid") {
                DriverManager.getConnection(dataSource.jdbcUrl, "dba", "")
            } else null
            var foreignTableCreated = false
            try {
                if (administrator != null) {
                    // A private owner also isolates this probe from other tests sharing the container.
                    val owner = "security_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    administrator.createStatement().use {
                        it.execute("CREATE USER $owner PASSWORD 'schema-fixture'")
                    }
                    dataSource.username = owner
                    dataSource.password = "schema-fixture"
                }
                val jdbc = JdbcTemplate(dataSource)
                if (administrator != null) {
                    // Refuse custom LOBs before any DDL instead of reinterpreting their values.
                    jdbc.execute("CREATE TABLE deploy_key (id BIGINT PRIMARY KEY, public_key CLOB)")
                    jdbc.update("INSERT INTO deploy_key (id,public_key) VALUES (1,CHAR_TO_CLOB(?))", "preserve-existing-lob")
                    assertThrows(CubridLobMigrationRequired::class.java) {
                        CubridSchemaGuard(dataSource).customize(mutableMapOf())
                    }
                    assertEquals("preserve-existing-lob", jdbc.queryForObject(
                        "SELECT CLOB_TO_CHAR(public_key) FROM deploy_key WHERE id=1", String::class.java,
                    ))
                    jdbc.execute("DROP TABLE deploy_key")
                    administrator.createStatement().use {
                        it.execute("CREATE TABLE dba.deploy_key (id BIGINT PRIMARY KEY, public_key CLOB)")
                        foreignTableCreated = true
                        it.execute("INSERT INTO dba.deploy_key VALUES (1,CHAR_TO_CLOB('other-schema-lob'))")
                        it.execute("GRANT SELECT ON dba.deploy_key TO PUBLIC")
                    }
                    CubridSchemaGuard(dataSource).customize(mutableMapOf())
                    assertThrows(CubridLobMigrationRequired::class.java) {
                        CubridSchemaGuard(dataSource).customize(mutableMapOf("hibernate.default_schema" to "\"DBA\""))
                    }
                }
                val suffix = UUID.randomUUID().toString()
                val textLength = if (SchemaTestDatabase.kind in setOf("mariadb", "mysql")) 60_000 else 1_000_013
                val storedText = "original-" + "A".repeat(textLength) + "-end"
                val binary = ByteArray(if (SchemaTestDatabase.kind in setOf("mariadb", "mysql")) 200 else 65_539) { it.toByte() }
                val encryptor = TotpSecretEncryptor("schema-fixture-only", "0123456789abcdef")
                val plainSecret = "ABCDEFGHIJKLMNOP234567ABCDEFGHIJKLMNOP23"
                val encrypted = encryptor.encrypt(plainSecret)
                val first = SchemaTestDatabase.factory(dataSource, "com.github.yonaprojects.yona.domain")
                first.afterPropertiesSet()
                val ids = mutableMapOf<String, Any>()
                try {
                    first.`object`!!.createEntityManager().use { entityManager ->
                        entityManager.transaction.begin()
                        val user = User(loginId = "schema-$suffix", name = "기존 사용자", email = "schema@example.invalid")
                        val project = Project(name = "schema-$suffix", owner = user.loginId, vcs = "GIT")
                        entityManager.persist(user)
                        entityManager.persist(project)
                        val deploy = DeployKey(project = project, title = "schema", publicKey = storedText, fingerprint = "deploy-$suffix")
                        val ssh = SshKey(user = user, title = "schema", publicKey = storedText, fingerprint = "ssh-$suffix")
                        val gpg = GpgKey(user = user, keyId = "ABCDEF0123456789", fingerprint = suffix, armoredPublicKey = storedText)
                        val totp = TwoFactorTotpCredential(user = user, label = "schema", encryptedSecret = encrypted)
                        val opaqueTotp = TwoFactorTotpCredential(user = user, label = "historical-opaque", encryptedSecret = storedText)
                        val webauthn = WebauthnCredential(user = user, credentialId = suffix,
                            publicKeyCose = binary, attestationObject = binary, attestationClientDataJson = binary,
                            signatureCount = 42, backupEligible = true, backupState = true, uvInitialized = true,
                            lastUsedAt = Instant.parse("2020-01-02T03:04:05.123Z"))
                        val client = OAuthRegisteredClient(id = suffix, clientId = suffix, clientName = "schema",
                            clientAuthenticationMethods = "none", authorizationGrantTypes = "authorization_code", scopes = "profile",
                            redirectUris = "https://example.invalid/callback", dynamicallyRegistered = true,
                            requireProofKey = true, requireAuthorizationConsent = true, reuseRefreshTokens = true,
                            accessTokenTtlSeconds = 1234, refreshTokenTtlSeconds = 5678, ownerId = user.id)
                        val consent = OAuthAuthorizationConsent("$suffix:user", suffix, user.loginId!!, "SCOPE_profile")
                        listOf(deploy, ssh, gpg, totp, opaqueTotp, webauthn, client, consent).forEach(entityManager::persist)
                        entityManager.flush()
                        ids.putAll(mapOf("deploy" to deploy.id!!, "ssh" to ssh.id!!, "gpg" to gpg.id!!,
                            "totp" to totp.id!!, "opaqueTotp" to opaqueTotp.id!!, "webauthn" to webauthn.id!!,
                            "client" to client.id, "consent" to consent.id))
                        entityManager.transaction.commit()
                    }
                } finally {
                    first.destroy()
                }
                val beforeTypes = columnTypes(dataSource)
                val second = SchemaTestDatabase.factory(dataSource, "com.github.yonaprojects.yona.domain")
                second.afterPropertiesSet()
                try {
                    second.`object`!!.createEntityManager().use { entityManager ->
                        entityManager.transaction.begin()
                        assertEquals(digest(storedText), digest(entityManager.find(DeployKey::class.java, ids["deploy"]).publicKey))
                        assertEquals(digest(storedText), digest(entityManager.find(SshKey::class.java, ids["ssh"]).publicKey))
                        assertEquals(digest(storedText), digest(entityManager.find(GpgKey::class.java, ids["gpg"]).armoredPublicKey))
                        assertEquals(digest(storedText), digest(entityManager.find(TwoFactorTotpCredential::class.java, ids["opaqueTotp"]).encryptedSecret))
                        assertEquals(plainSecret, encryptor.decrypt(entityManager.find(TwoFactorTotpCredential::class.java, ids["totp"]).encryptedSecret))
                        val credential = entityManager.find(WebauthnCredential::class.java, ids["webauthn"])
                        assertEquals(digest(binary), digest(credential.publicKeyCose))
                        assertEquals(digest(binary), digest(credential.attestationObject))
                        assertEquals(digest(binary), digest(credential.attestationClientDataJson))
                        assertEquals(42L, credential.signatureCount)
                        assertEquals(true, credential.backupEligible)
                        assertEquals(true, credential.backupState)
                        assertEquals(true, credential.uvInitialized)
                        assertEquals(Instant.parse("2020-01-02T03:04:05.123Z"), credential.lastUsedAt)
                        val client = entityManager.find(OAuthRegisteredClient::class.java, ids["client"])
                        assertEquals("none", client.clientAuthenticationMethods)
                        assertEquals("authorization_code", client.authorizationGrantTypes)
                        assertEquals("profile", client.scopes)
                        assertEquals("https://example.invalid/callback", client.redirectUris)
                        assertEquals(true, client.dynamicallyRegistered)
                        assertEquals(true, client.requireProofKey)
                        assertEquals(true, client.requireAuthorizationConsent)
                        assertEquals(true, client.reuseRefreshTokens)
                        assertEquals(1234L, client.accessTokenTtlSeconds)
                        assertEquals(5678L, client.refreshTokenTtlSeconds)
                        assertEquals(credential.user!!.id, client.ownerId)
                        assertEquals("SCOPE_profile", entityManager.find(OAuthAuthorizationConsent::class.java, ids["consent"]).authorities)
                        entityManager.transaction.commit()
                    }
                    assertEquals(beforeTypes, columnTypes(dataSource))
                    if (SchemaTestDatabase.kind == "cubrid") {
                        assertEquals(Types.VARCHAR, beforeTypes["deploy_key.public_key"]?.first)
                        assertEquals(Types.VARCHAR, beforeTypes["user_totp_credential.encrypted_secret"]?.first)
                        assertEquals("other-schema-lob", jdbc.queryForObject(
                            "SELECT CLOB_TO_CHAR(public_key) FROM dba.deploy_key WHERE id=1", String::class.java,
                        ))
                    }
                    val cascadeIds = second.`object`!!.createEntityManager().use { entityManager ->
                        entityManager.transaction.begin()
                        val user = User(loginId = "cascade-$suffix", name = "cascade", email = "cascade@example.invalid")
                        entityManager.persist(user)
                        val credential = WebauthnCredential(user = user, credentialId = "cascade-$suffix",
                            publicKeyCose = byteArrayOf(1), attestationObject = byteArrayOf(2), attestationClientDataJson = byteArrayOf(3))
                        entityManager.persist(credential)
                        entityManager.flush()
                        entityManager.transaction.commit()
                        user.id!! to credential.id!!
                    }
                    assertEquals(1, jdbc.update("DELETE FROM n4user WHERE id=?", cascadeIds.first))
                    assertEquals(0, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM user_webauthn_credential WHERE id=?", Int::class.java, cascadeIds.second,
                    ))
                } finally {
                    second.destroy()
                }
            } finally {
                administrator?.use { connection ->
                    if (foreignTableCreated) connection.createStatement().use { it.execute("DROP TABLE dba.deploy_key") }
                }
            }
        }
    }

    private fun columnTypes(dataSource: javax.sql.DataSource): Map<String, Pair<Int, String>> =
        dataSource.connection.use { connection ->
            val schema = if (SchemaTestDatabase.kind == "cubrid") connection.metaData.userName else null
            connection.metaData.getColumns(connection.catalog, schema, schema?.let { "$it.%" } ?: "%", "%").use { rows ->
                val result = mutableMapOf<String, Pair<Int, String>>()
                while (rows.next()) {
                    val key = rows.getString("TABLE_NAME").lowercase() + "." + rows.getString("COLUMN_NAME").lowercase()
                    if (key in setOf("deploy_key.public_key", "ssh_key.public_key", "gpg_key.armored_public_key",
                            "user_totp_credential.encrypted_secret", "user_webauthn_credential.public_key_cose")) {
                        result[key] = rows.getInt("DATA_TYPE") to rows.getString("TYPE_NAME")
                    }
                }
                result
            }
        }

    private fun digest(text: String): String = digest(text.toByteArray(Charsets.UTF_8))
    private fun digest(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
