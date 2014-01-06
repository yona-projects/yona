/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park, kjkmadness
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
import org.eclipse.jgit.transport.ReceiveCommand.Type;
import org.eclipse.jgit.transport.ReceivePack;

import play.libs.Akka;
import play.mvc.Http.Request;
import actors.PullRequestMergingActor;
import actors.RelatedPullRequestMergingActor;
import akka.actor.Props;

/**
 * 성공한 ReceiveCommand 로 영향받은 branch 에 대해서
 * 관련 있는 오픈된 코드-보내기 요청을 찾아 코드가 안전한지 확인한다.
 * branch가 삭제된 경우 관련 있는 오픈된 코드-보내기 요청을 모두 삭제한다.
 */
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
        Set<String> branches = getUpdatedBranches(commands);
        for (String branch : branches) {
            PullRequestEventMessage message = new PullRequestEventMessage(user, request, project, branch);
            Akka.system().actorOf(new Props(RelatedPullRequestMergingActor.class)).tell(message, null);
        }

        Set<String> deletedBranches = getDeletedBranches(commands);
        for (String branch : deletedBranches) {
            List<PullRequest> pullRequests = PullRequest.findRelatedPullRequests(project, branch);
            for (PullRequest pullRequest : pullRequests) {
                pullRequest.delete();
            }
        }
    }

    /*
     * ReceiveCommand 중, branch update 에 해당하는 것들의 참조 branch set 을 구한다.
     */
    private Set<String> getUpdatedBranches(Collection<ReceiveCommand> commands) {
        return ReceiveCommandUtil.getRefNamesByCommandType(commands,
                Type.UPDATE,
                Type.UPDATE_NONFASTFORWARD);
    }

    /*
     * ReceiveCommand 중, branch delete 에 해당하는 것들의 참조 branch set 을 구한다.
     */
    private Set<String> getDeletedBranches(Collection<ReceiveCommand> commands) {
        return ReceiveCommandUtil.getRefNamesByCommandType(commands, Type.DELETE);
    }
}
