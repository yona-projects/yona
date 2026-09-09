package com.github.yonaprojects.yona.domain.attachment

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import java.io.File
import java.io.InputStream

interface AttachmentService {
    // legacy Attachment.save()의 반환값(isCreated) 대응 — 두 번째 값은 동일
    // (name, hash, containerType, containerId)의 기존 첨부를 재사용했다면 false, 새로 만들었다면 true.
    fun store(
        inputStream: InputStream,
        name: String,
        containerType: ResourceType,
        containerId: String,
        ownerLoginId: String
    ): Pair<Attachment, Boolean>

    fun getFile(attachment: Attachment): File

    fun delete(attachment: Attachment)

    fun deleteAll(containerType: ResourceType, containerId: String)

    fun moveAll(
        fromType: ResourceType,
        fromId: String,
        toType: ResourceType,
        toId: String
    ): Int

    // legacy Attachment.moveOnlySelected(from, to, selectedFileIds) 대응. selectedIds 중
    // 실제로 fromType/fromId 컨테이너에 속해 있고(원래 있던 곳에서 옮기는 것이 맞는지) *또한*
    // moverLoginId가 그 첨부의 원 업로더(ownerLoginId)와 일치하는 것만 옮긴다. legacy는 임시 업로드를
    // ResourceType.USER+업로더id 컨테이너에 보관해 컨테이너 자체가 업로더별로 분리되지만, yona는
    // 모든 임시 업로드가 ResourceType.NOT_A_RESOURCE+"" 라는 공용 컨테이너를 공유하므로 ownerLoginId
    // 검사가 없으면 다른 사람이 업로드한(또는 이미 다른 리소스에 붙어버린) 첨부파일 ID를 추측해 자신의
    // 새 이슈/게시글/마일스톤에 강제로 재배선할 수 있다.
    fun moveOnlySelected(
        fromType: ResourceType,
        fromId: String,
        toType: ResourceType,
        toId: String,
        selectedIds: List<Long>,
        moverLoginId: String
    ): Int
}
