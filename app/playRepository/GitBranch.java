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
package playRepository;

import models.PullRequest;
import models.User;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.Constants;

/**
 * @author Keesun Baik
 */
public class GitBranch {

    private String name;

    private String shortName;

    private GitCommit headCommit;

    private User user;

    private PullRequest pullRequest;

    public GitBranch(String name, GitCommit headCommit) {
        this.name = name;
        this.shortName = StringUtils.removeStart(name, Constants.R_HEADS);
        this.headCommit = headCommit;
        this.user = User.findByEmail(headCommit.getCommitterEmail());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GitCommit getHeadCommit() {
        return headCommit;
    }

    public void setHeadCommit(GitCommit headCommit) {
        this.headCommit = headCommit;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public PullRequest getPullRequest() {
        return pullRequest;
    }

    public void setPullRequest(PullRequest pullRequest) {
        this.pullRequest = pullRequest;
    }
}
