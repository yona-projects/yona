package dev.yona.domain.notification

import dev.yona.domain.project.ProjectTransfer

/**
 * 프로젝트 이전이 요청되었음을 알리는 애플리케이션 이벤트이다.
 * v1.6 `ProjectApp.sendTransferRequestMail`이 받는 사람(사용자, 또는 조직 관리자들)에게 수락 링크를 메일로 보내던 지점이다.
 *
 * 메일 발송은 메일 서브시스템을 옮길 때 이 이벤트를 받는 수신기로 추가한다. 수락 주소는 `/project/transfer/{id}/{confirmKey}`이다.
 *
 * @property transfer 저장된 이전 요청
 */
data class ProjectTransferRequestedEvent(val transfer: ProjectTransfer)
