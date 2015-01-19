/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Wansoon Park, kjkmadness
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
package playRepository.hooks;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import models.Project;
import models.PullRequest;
import models.PullRequestEventMessage;
import models.User;

import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import play.libs.Akka;
import play.mvc.Http.Request;
import actors.RelatedPullRequestMergingActor;
import akka.actor.Props;

public class PullRequestCheck implements PostReceiveHook {
    private User user;
    private Request request;
    private Project project;

    public PullRequestCheck(User user, Request request, Project project) {
        this.user = user;
        this.request = request;
        this.project = project;
    }

    @Override
    public void onPostReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {
        Set<String> branches = ReceiveCommandUtil.getUpdatedBranches(commands);
        for (String branch : branches) {
            PullRequestEventMessage message = new PullRequestEventMessage(user, request, project, branch);
            Akka.system().actorOf(Props.create(RelatedPullRequestMergingActor.class)).tell(message, null);
        }

        Set<String> deletedBranches = ReceiveCommandUtil.getDeletedBranches(commands);
        for (String branch : deletedBranches) {
            List<PullRequest> pullRequests = PullRequest.findRelatedPullRequests(project, branch);
            for (PullRequest pullRequest : pullRequests) {
                pullRequest.delete();
            }
        }
    }
}
