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
 * This abstract implementation can be used to add after-receive jobs.
 * To add a post hook, extends this classs and execute it with Akka
 *
 * @author Keesun Baik
 * @see IssueReferredFromCommitEventActor
 * @see CommitsNotificationActor
 */
public abstract class PostReceiveActor extends UntypedActor {

    @Override
    public void onReceive(Object message) {
        if(!(message instanceof PostReceiveMessage)) {
            return;
        }

        PostReceiveMessage cap = (PostReceiveMessage)message;
        doReceive(cap);
    }

    abstract void doReceive(PostReceiveMessage cap);

    class CommitAndRefNames {

        List<RevCommit> commits = new ArrayList<>();
        List<String> refNames = new ArrayList<>();

        public List<RevCommit> getCommits() {
            return commits;
        }

        public void setCommits(List<RevCommit> commits) {
            this.commits = commits;
        }

        public List<String> getRefNames() {
            return refNames;
        }

        public void setRefNames(List<String> refNames) {
            this.refNames = refNames;
        }

        public void addAll(Collection<? extends RevCommit> revCommits) {
            this.commits.addAll(revCommits);
        }

        public void add(String refName) {
            this.refNames.add(refName);
        }
    }

    protected CommitAndRefNames commitAndRefNames(PostReceiveMessage message) {
        CommitAndRefNames car = new CommitAndRefNames();
        for(ReceiveCommand command : message.getCommands()) {
            if(isNewOrUpdateCommand(command)) {
                car.addAll(parseCommitsFrom(command, message.getProject()));
                car.add(command.getRefName());
            }
        }
        return car;
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
