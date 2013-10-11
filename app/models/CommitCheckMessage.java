package models;

import org.eclipse.jgit.transport.ReceiveCommand;

import java.util.Collection;

/**
 * @author Keesun Baik
 */
public class CommitCheckMessage {

    private Collection<ReceiveCommand> commands;

    private Project project;

    public CommitCheckMessage(Collection<ReceiveCommand> commands, Project project) {
        this.commands = commands;
        this.project = project;
    }

    public Collection<ReceiveCommand> getCommands() {
        return commands;
    }

    public Project getProject() {
        return project;
    }
}
