package com.github.yonaprojects.yona.domain.user

interface UserService {
    fun findByLoginId(loginId: String): User?
    fun findByEmail(email: String): User?
    fun createUser(user: User): User
    fun isLoginIdExist(loginId: String): Boolean
    fun isEmailExist(email: String): Boolean
    fun addEmail(userId: Long, newEmail: String): Email
    fun deleteEmail(userId: Long, emailId: Long)
    fun sendValidationEmail(userId: Long, emailId: Long, serverUrl: String)
    fun confirmEmail(emailId: Long, token: String): Boolean
    fun setAsMainEmail(userId: Long, emailId: Long)
    fun verifyUser(loginId: String, verificationCode: String): Boolean
    fun createVerification(user: User): UserVerification
    fun sendVerificationEmail(user: User, serverUrl: String)
}
