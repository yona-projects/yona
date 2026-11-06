package dev.yona.domain.vcs

import dev.yona.repository.GitRefUpdates
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.ReceiveCommand
import org.junit.jupiter.api.Test

/** 푸시로 바뀐 ref를 종류와 무관한 값으로 나타내는 [RefUpdate]와 그 도우미를 확인한다. */
class RefUpdateTest {

    private fun update(ref: String, type: RefUpdateType) = RefUpdate(ref, "a".repeat(40), "b".repeat(40), type)

    @Test
    fun `만들거나 갱신한 ref와 지운 ref와 갱신한 ref를 가려낸다`() {
        val updates = listOf(
            update("refs/heads/new", RefUpdateType.CREATE), update("refs/heads/ff", RefUpdateType.UPDATE),
            update("refs/heads/forced", RefUpdateType.FORCE_UPDATE), update("refs/heads/gone", RefUpdateType.DELETE)
        )
        // 빨리 감기가 아닌 갱신도 "갱신"에 든다
        assertThat(RefUpdates.pushed(updates)).containsExactly("refs/heads/new", "refs/heads/ff", "refs/heads/forced")
        assertThat(RefUpdates.deleted(updates)).containsExactly("refs/heads/gone")
        assertThat(RefUpdates.updated(updates)).containsExactly("refs/heads/ff", "refs/heads/forced")
    }

    @Test
    fun `같은 ref가 여러 번 나와도 한 번만 모으고 순서를 지킨다`() {
        val updates = listOf(update("refs/heads/b", RefUpdateType.UPDATE), update("refs/heads/a", RefUpdateType.UPDATE), update("refs/heads/b", RefUpdateType.UPDATE))
        assertThat(RefUpdates.pushed(updates)).containsExactly("refs/heads/b", "refs/heads/a")
    }

    @Test
    fun `옛 번호나 새 번호가 영이면 만들기와 지우기이다`() {
        assertThat(RefUpdate("r", RefUpdate.NULL_ID, "b".repeat(40), RefUpdateType.CREATE).isCreate).isTrue()
        assertThat(RefUpdate("r", "a".repeat(40), RefUpdate.NULL_ID, RefUpdateType.DELETE).isDelete).isTrue()
        assertThat(RefUpdate.NULL_ID).hasSize(40).matches("0+")
    }

    @Test
    fun `Git 푸시 명령을 종류와 무관한 변경으로 바꾼다`() {
        val old = ObjectId.fromString("a".repeat(40))
        val new = ObjectId.fromString("b".repeat(40))
        fun type(command: ReceiveCommand) = GitRefUpdates.of(command).type
        assertThat(type(ReceiveCommand(ObjectId.zeroId(), new, "refs/heads/x"))).isEqualTo(RefUpdateType.CREATE)
        assertThat(type(ReceiveCommand(old, new, "refs/heads/x", ReceiveCommand.Type.UPDATE))).isEqualTo(RefUpdateType.UPDATE)
        assertThat(type(ReceiveCommand(old, ObjectId.zeroId(), "refs/heads/x"))).isEqualTo(RefUpdateType.DELETE)
        val forced = ReceiveCommand(old, new, "refs/heads/x", ReceiveCommand.Type.UPDATE_NONFASTFORWARD)
        assertThat(type(forced)).isEqualTo(RefUpdateType.FORCE_UPDATE)
        val converted = GitRefUpdates.of(ReceiveCommand(old, new, "refs/heads/x"))
        assertThat(converted.ref).isEqualTo("refs/heads/x"); assertThat(converted.old).isEqualTo(old.name); assertThat(converted.new).isEqualTo(new.name)
        // 만들기의 옛 번호와 지우기의 새 번호는 영이다(감사 기록의 모양과 같다)
        assertThat(GitRefUpdates.of(ReceiveCommand(ObjectId.zeroId(), new, "refs/heads/x")).old).isEqualTo(RefUpdate.NULL_ID)
    }
}
