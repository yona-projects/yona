/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package actors;

import models.*;
import org.eclipse.jgit.revwalk.RevCommit;
import play.i18n.Messages;

import java.util.List;
import java.util.Set;

/**
 * Creates new commit notifications.
 *
 * @author Keesun Baik
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

        Set<User> watchers = Watch.findWatchers(project.asResource());
        watchers.remove(sender);

        NotificationEvent.afterNewCommits(commits, refNames, project, sender, title, watchers);
    }

}
