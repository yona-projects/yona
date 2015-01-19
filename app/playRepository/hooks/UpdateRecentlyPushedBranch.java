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
import models.PushedBranch;

import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

import utils.JodaDateUtil;

public class UpdateRecentlyPushedBranch implements PostReceiveHook {
    private Project project;

    public UpdateRecentlyPushedBranch(Project project) {
        this.project = project;
    }

    @Override
    public void onPostReceive(ReceivePack receivePack, Collection<ReceiveCommand> commands) {
        removeOldPushedBranches();
        saveRecentlyPushedBranch(ReceiveCommandUtil.getPushedBranches(commands));
        deletePushedBranch(ReceiveCommandUtil.getDeletedBranches(commands));
    }

    private void removeOldPushedBranches() {
        List<PushedBranch> list = project.getOldPushedBranches();
        for (PushedBranch pushedBranch : list) {
            pushedBranch.delete();
        }
    }

    private void saveRecentlyPushedBranch(Set<String> pushedBranches) {
        for (String branch : pushedBranches) {
            PushedBranch pushedBranch = PushedBranch.find.where()
                            .eq("project", project).eq("name", branch).findUnique();

            if (pushedBranch != null) {
                pushedBranch.pushedDate = JodaDateUtil.now();
                pushedBranch.update();
            }

            if (isNotExistsPushedBranch(branch, pushedBranch) && isNotTag(branch)) {
                pushedBranch = new PushedBranch(JodaDateUtil.now(), branch, project);
                pushedBranch.save();
            }
        }
    }

    private boolean isNotTag(String branch) {
        return !branch.contains(org.eclipse.jgit.lib.Constants.R_TAGS);
    }

    private boolean isNotExistsPushedBranch(String branch, PushedBranch pushedBranch) {
        return pushedBranch == null && PullRequest.findByFromProjectAndBranch(project, branch).isEmpty();
    }

    private void deletePushedBranch(Set<String> deletedBranches) {
        for (String branch : deletedBranches) {
            PushedBranch pushedBranch = PushedBranch.find.where().eq("project", project)
                    .eq("name", branch).findUnique();
            if (pushedBranch != null) {
                pushedBranch.delete();
            }
        }
    }
}
