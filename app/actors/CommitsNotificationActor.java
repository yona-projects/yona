/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

package actors;

import models.*;
import org.eclipse.jgit.revwalk.RevCommit;
import play.i18n.Messages;

import java.util.List;
import java.util.Set;

/**
 * Creates new commit notifications.
 */
public class CommitsNotificationActor extends PostReceiveActor {

    @Override
    void doReceive(PostReceiveMessage message) {
        CommitAndRefNames car = commitAndRefNames(message);
        List<RevCommit> commits = car.getCommits();
        List<String> refNames = car.getRefNames();

        Project project = message.getProject();
        User sender = message.getUser();

        String title;
        if(refNames.size() == 1) {
            title = Messages.get("notification.pushed.commits.to", project.name, commits.size(), refNames.get(0));
        } else {
            title = Messages.get("notification.pushed.commits", project.name, commits.size());
        }

        NotificationEvent.afterNewCommits(commits, refNames, project, sender, title);
    }

}
