package playRepository.hooks;

import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import java.util.Collection;

public class RejectPushToReservedRefs implements PreReceiveHook {
    public RejectPushToReservedRefs() {
    }

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        for (ReceiveCommand command : commands) {
            String refName = command.getRefName();
            if (refName.equals("refs/yobi") || refName.startsWith("refs/yobi/")) {
                command.setResult(
                        ReceiveCommand.Result.REJECTED_OTHER_REASON,
                        "refs/yobi/* is reserved for internal use");
            }
        }
    }
}
