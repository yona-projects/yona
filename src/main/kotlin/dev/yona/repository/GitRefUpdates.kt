package dev.yona.repository

import dev.yona.domain.vcs.RefUpdate
import dev.yona.domain.vcs.RefUpdateType
import org.eclipse.jgit.transport.ReceiveCommand

/** JGit의 푸시 명령([ReceiveCommand])을 종류와 무관한 [RefUpdate]로 바꾼다. */
object GitRefUpdates {

    /** [command]가 나타내는 ref 변경이다. 옛 번호와 새 번호는 JGit의 영 번호를 그대로 쓴다(감사 기록의 모양과 같다). */
    fun of(command: ReceiveCommand): RefUpdate = RefUpdate(command.refName, command.oldId.name, command.newId.name, typeOf(command.type))

    /** [commands] 전체를 같은 순서로 바꾼다. */
    fun of(commands: Collection<ReceiveCommand>): List<RefUpdate> = commands.map { of(it) }

    private fun typeOf(type: ReceiveCommand.Type): RefUpdateType = when (type) {
        ReceiveCommand.Type.CREATE -> RefUpdateType.CREATE
        ReceiveCommand.Type.UPDATE -> RefUpdateType.UPDATE
        ReceiveCommand.Type.UPDATE_NONFASTFORWARD -> RefUpdateType.FORCE_UPDATE
        ReceiveCommand.Type.DELETE -> RefUpdateType.DELETE
    }
}
