package com.github.yonaprojects.yona.domain.mail

/**
 * yona의 mailbox/EmailAddressWithDetail.java 대응.
 * "yona+owner/project@example.com" 형태의 plus-addressing에서
 * user/detail/domain을 분리한다.
 */
data class EmailAddressDetail(
    val user: String,
    val detail: String,
    val domain: String
) {
    /**
     * detail을 제외한 user/domain이 기준 주소와 같으면(=이 시스템으로 온 메일이면) true.
     */
    fun isToYona(baseAddress: String): Boolean {
        val base = of(baseAddress)
        return user == base.user && domain == base.domain
    }

    override fun toString(): String {
        return if (detail.isEmpty()) "$user@$domain" else "$user+$detail@$domain"
    }

    companion object {
        fun of(address: String): EmailAddressDetail {
            val at = address.indexOf('@')
            require(at >= 0) { "'$address' is not a valid email address" }

            val plus = address.indexOf('+')
            return if (plus in 0 until at) {
                EmailAddressDetail(
                    user = address.substring(0, plus),
                    detail = address.substring(plus + 1, at),
                    domain = address.substring(at + 1)
                )
            } else {
                EmailAddressDetail(
                    user = address.substring(0, at),
                    detail = "",
                    domain = address.substring(at + 1)
                )
            }
        }
    }
}
