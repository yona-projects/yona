package actors;

import akka.actor.UntypedActor;
import controllers.routes;
import models.*;
import models.enumeration.EventType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import play.i18n.Messages;
import playRepository.GitCommit;
import playRepository.GitRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;

/**
 * @author Keesun Baik
 */
public class CommitCheckActor extends UntypedActor {

    @Override
    public void onReceive(Object object) throws Exception {
        if(!(object instanceof CommitCheckMessage)) {
            return;
        }

        CommitCheckMessage message = (CommitCheckMessage)object;
        List<RevCommit> commits = getCommits(message);
        for(RevCommit commit : commits) {
            addIssueEvent(commit, message.getProject());
        }

    }

    private void addIssueEvent(RevCommit commit, Project project) {
        GitCommit gitCommit = new GitCommit(commit);
        String fullMessage = gitCommit.getMessage();
        List<Issue> referredIssues = IssueEvent.findReferredIssue(fullMessage, project);
        String newValue = getNewEventValue(gitCommit, project);
        for(Issue issue : referredIssues) {
            IssueEvent issueEvent = new IssueEvent();
            issueEvent.issue = issue;
            issueEvent.senderLoginId = gitCommit.getCommitterName();
            issueEvent.newValue = newValue;
            issueEvent.created = new Date();
            issueEvent.eventType = EventType.ISSUE_REFERRED;
            issueEvent.save();
        }
    }

    private String getNewEventValue(GitCommit gitCommit, Project project) {
        return Messages.get("issue.event.referred.from.commit",
                gitCommit.getCommitterName(), gitCommit.getShortId(),
                routes.CodeHistoryApp.show(project.owner, project.name, gitCommit.getId()));
    }

    private List<RevCommit> getCommits(CommitCheckMessage message) {
        List<RevCommit> commits = new ArrayList<>();
        for(ReceiveCommand command : message.getCommands()) {
            if(isNewOrUpdateCommand(command)) {
                commits.addAll(parseCommitsFrom(command, message.getProject()));
            }
        }
        return commits;
    }

    private Collection<? extends RevCommit> parseCommitsFrom(ReceiveCommand command, Project project) {
        Repository repository = GitRepository.buildGitRepository(project);
        List<RevCommit> list = new ArrayList<>();

        try {
            ObjectId endRange = command.getNewId();
            ObjectId startRange = command.getOldId();

            RevWalk rw = new RevWalk(repository);
            rw.markStart(rw.parseCommit(endRange));
            if (startRange.equals(ObjectId.zeroId())) {
                // maybe this is a tag or an orphan branch
                list.add(rw.parseCommit(endRange));
                rw.dispose();
                return list;
            } else {
                rw.markUninteresting(rw.parseCommit(startRange));
            }

            Iterable<RevCommit> revlog = rw;
            for (RevCommit rev : revlog) {
                list.add(rev);
            }
            rw.dispose();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return list;
    }

    private boolean isNewOrUpdateCommand(ReceiveCommand command) {
        List<ReceiveCommand.Type> allowdTypes = new ArrayList<>();
        allowdTypes.add(ReceiveCommand.Type.CREATE);
        allowdTypes.add(ReceiveCommand.Type.UPDATE);
        allowdTypes.add(ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
        return allowdTypes.contains(command.getType());
    }


}
