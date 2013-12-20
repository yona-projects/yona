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

import akka.actor.UntypedActor;
import models.PostReceiveMessage;
import models.Project;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import playRepository.GitRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Receive 이후에 후처리 작업을 비동기적으로 추가할 때 사용한다.
 * 이 클래스를 상속받아 할일을 구현하고 Akka를 사용하여 실행한다.
 *
 * @author Keesun Baik
 * @see IssueReferredFromCommitEventActor
 * @see CommitsNotificationActor
 */
public abstract class PostReceiveActor extends UntypedActor {

    @Override
    public void onReceive(Object message) throws Exception {
        if(!(message instanceof PostReceiveMessage)) {
            return;
        }

        PostReceiveMessage cap = (PostReceiveMessage)message;
        doReceive(cap);
    }

    abstract void doReceive(PostReceiveMessage cap);

    protected List<RevCommit> getCommits(PostReceiveMessage message) {
        List<RevCommit> commits = new ArrayList<>();
        for(ReceiveCommand command : message.getCommands()) {
            if(isNewOrUpdateCommand(command)) {
                commits.addAll(parseCommitsFrom(command, message.getProject()));
            }
        }
        return commits;
    }

    protected boolean isNewOrUpdateCommand(ReceiveCommand command) {
        List<ReceiveCommand.Type> allowdTypes = new ArrayList<>();
        allowdTypes.add(ReceiveCommand.Type.CREATE);
        allowdTypes.add(ReceiveCommand.Type.UPDATE);
        allowdTypes.add(ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
        return allowdTypes.contains(command.getType());
    }

    protected Collection<? extends RevCommit> parseCommitsFrom(ReceiveCommand command, Project project) {
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

            for (RevCommit rev : rw) {
                list.add(rev);
            }
            rw.dispose();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return list;
    }
}
