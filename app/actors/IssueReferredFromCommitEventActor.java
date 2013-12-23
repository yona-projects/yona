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
import models.*;
import models.enumeration.EventType;
import org.eclipse.jgit.revwalk.RevCommit;
import play.i18n.Messages;
import playRepository.GitCommit;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 커밋에서 언급한 이슈에 이슈 참조 이벤트를 생성한다.
 *
 * @author Keesun Baik
 */
public class IssueReferredFromCommitEventActor extends PostReceiveActor {

    @Override
    void doReceive(PostReceiveMessage cap) {
        List<RevCommit> commits = commitAndRefNames(cap).getCommits();
        for(RevCommit commit : commits) {
            addIssueEvent(commit, cap.getProject());
        }
    }

    private void addIssueEvent(RevCommit commit, Project project) {
        GitCommit gitCommit = new GitCommit(commit);
        String fullMessage = gitCommit.getMessage();
        Set<Issue> referredIssues = IssueEvent.findReferredIssue(fullMessage, project);
        String newValue = getNewEventValue(gitCommit, project);

        for(Issue issue : referredIssues) {
            IssueEvent issueEvent = new IssueEvent();
            issueEvent.issue = issue;
            issueEvent.senderLoginId = gitCommit.getCommitterName();
            issueEvent.senderEmail = gitCommit.getCommitterEmail();
            issueEvent.newValue = newValue;
            issueEvent.created = new Date();
            issueEvent.eventType = EventType.ISSUE_REFERRED;
            issueEvent.save();
        }
    }

    private String getNewEventValue(GitCommit gitCommit, Project project) {
        User user = User.findByCommitterEmail(gitCommit.getCommitterEmail());
        String userAvatar = utils.TemplateHelper.getUserAvatar(user, "small");
        String commiterLoginId = user.isAnonymous() ? "" : user.loginId;

        return Messages.get("issue.event.referred.from.commit",
            commiterLoginId,
            userAvatar,
            gitCommit.getShortId(),
            routes.CodeHistoryApp.show(project.owner, project.name, gitCommit.getId()),
            gitCommit.getShortMessage(),
            gitCommit.getMessage());
    }

}
