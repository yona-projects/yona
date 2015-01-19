/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik, kjkmadness
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

import models.PostReceiveMessage;
import models.Project;
import models.User;

import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import actors.IssueReferredFromCommitEventActor;
import akka.actor.Props;

import play.libs.Akka;

public class IssueReferredFromCommitEvent implements PostReceiveHook {
    private Project project;
    private User user;

    public IssueReferredFromCommitEvent(Project project, User user) {
        this.project = project;
        this.user = user;
    }

    @Override
    public void onPostReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {
        PostReceiveMessage message = new PostReceiveMessage(commands, project, user);
        Akka.system().actorOf(Props.create(IssueReferredFromCommitEventActor.class)).tell(message, null);
    }
}
