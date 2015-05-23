/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2015 NAVER Corp.
 * http://yobi.io
 *
 * @author Jihwan Chun
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
import models.Webhook;

import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import java.util.List;

public class RequestByWebhooks implements PostReceiveHook {
    private Project project;
    private User user;

    public RequestByWebhooks(Project project, User user) {
        this.project = project;
        this.user = user;
    }

    @Override
    public void onPostReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {
        List<Webhook> webhookList = Webhook.findByProject(project.id);
        for (Webhook webhook : webhookList) {
            // TODO : When we support more event types, we should get event type list from webhook object
            String[] eventTypes = {"push"};
            webhook.sendRequestToPayloadUrl(eventTypes);
        }
    }
}
