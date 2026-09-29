package com.github.yonaprojects.yona.domain.user

import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress

object EmailAddressValidator {
    fun isValid(email: String): Boolean {
        if (email.isBlank()) return false
        return try {
            val address = InternetAddress(email, true)
            address.validate()
            // Account emails contain a single address, not a display name, list or group.
            !address.isGroup && address.address == email
        } catch (e: AddressException) {
            false
        }
    }
}
