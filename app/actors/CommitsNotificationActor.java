/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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

import controllers.routes;
import models.NotificationEvent;
import models.PostReceiveMessage;
import models.Project;
import models.User;
import models.enumeration.EventType;
import org.eclipse.jgit.revwalk.RevCommit;
import playRepository.GitCommit;
import utils.WatchService;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 새로운 커밋 알림을 생성한다.
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
            title = String.format("[%s] pushed %d commits to %s.", project.name, commits.size(), refNames.get(0));
        } else {
            title = String.format("[%s] pushed %d commits.", project.name, commits.size());
        }

        Set<User> watchers = WatchService.findWatchers(project.asResource());
        watchers.remove(sender);

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = title;
        notiEvent.senderId = sender.id;
        notiEvent.receivers = watchers;
        notiEvent.urlToView = routes.CodeHistoryApp.historyUntilHead(project.owner, project.name).url();
        notiEvent.resourceId = project.id.toString();
        notiEvent.resourceType = project.asResource().getType();
        notiEvent.eventType = EventType.NEW_COMMIT;
        notiEvent.oldValue = null;
        notiEvent.newValue = message(commits, refNames, project);
        NotificationEvent.add(notiEvent);
    }

    private String message(List<RevCommit> commits, List<String> refNames, Project project) {
        StringBuilder result = new StringBuilder();

        if(commits.size() > 0) {
            result.append("New Commits: \n");
            for(RevCommit commit : commits) {
                GitCommit gitCommit = new GitCommit(commit);
                // <a href="/owner/project/commit/1231234">1231234</a>: commit's short message \n
                result.append("<a href=\"");
                result.append(routes.CodeHistoryApp.show(project.owner, project.name, gitCommit.getId()).url());
                result.append("\">");
                result.append(gitCommit.getShortId());
                result.append("</a>");
                result.append(": ");
                result.append(gitCommit.getShortMessage());
                result.append("\n");
            }
        }

        if(refNames.size() > 0) {
            result.append("Branches: \n");
            for(String refName: refNames) {
                // <a href="/owner/project/branch_name">branch_name</a> \n
                result.append("<a href=\"");
                String branchName;
                try {
                    branchName = URLEncoder.encode(refName, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                result.append(routes.CodeApp.codeBrowserWithBranch(project.owner, project.name, branchName, "").url());
                result.append("\">");
                result.append(refName);
                result.append("</a>");
                result.append("\n");
            }
        }

        return result.toString();
    }
}
