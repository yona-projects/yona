package com.github.yonaprojects.yona.domain.mail

/**
 * IMAP으로 수신한 이메일을 순수 데이터로 옮겨온 것.
 * jakarta.mail.Message에 직접 의존하지 않게 분리해, 실제 IMAP 연결 없이도
 * 라우팅/생성 로직(IncomingMailService)을 단위테스트할 수 있게 한다.
 */
data class InboundEmailMessage(
    val messageId: String,
    val subject: String,
    val fromAddress: String,
    val fromName: String,
    val recipientAddresses: List<String>,
    val inReplyTo: String?,
    val references: String?,
    // text/plain 파트가 있으면 그 내용, text/plain이 아예 없고 text/html뿐이면 그 원본 HTML(P1-47).
    val textBody: String,
    // 위 textBody가 원본 HTML인지 여부. true면 cid 치환 후 그대로(마크다운 처리기가 렌더링 시점에
    // 이미 OWASP sanitizer를 거치므로 안전, P0-08) 저장한다 (P1-47).
    val isHtml: Boolean = false,
    val attachments: List<InboundAttachment> = emptyList()
)

// yona CreationViaEmail.saveAttachment()/saveAttachments() 대응 (P1-29, contentId는 P1-47에서 추가).
data class InboundAttachment(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
    val contentId: String? = null
)
