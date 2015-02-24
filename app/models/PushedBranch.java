/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Wansoon Park
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
package models;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Wansoon Park
 */
@Table(name = "project_pushed_branch")
@Entity
public class PushedBranch extends Model {
    private static final long serialVersionUID = 1L;
    public static final Finder<Long, PushedBranch> find = new Finder<>(Long.class, PushedBranch.class);
    public PushedBranch() {
    }

    public PushedBranch(Date pushedDate, String branch, Project project) {
        this.pushedDate = pushedDate;
        this.name = branch;
        this.project = project;
    }

    @Id
    public Long id;
    public Date pushedDate;
    public String name;

    @ManyToOne
    public Project project;

    public String getShortName() {
        return StringUtils.removeStart(this.name, Constants.R_HEADS);
    }

    public static void removeByPullRequestFrom(PullRequest pullRequest) {
        PushedBranch pushedBranch = find.where().eq("project",  pullRequest.fromProject).eq("name", pullRequest.fromBranch).findUnique();
        if(pushedBranch != null){
            pushedBranch.delete();
        }
    }

    public static List<PushedBranch> findByOwnerAndOriginalProject(User owner, Project originalProject) {
        List<PushedBranch> branches = new ArrayList<>();
        List<Project> forkedProjects = Project.findByOwnerAndOriginalProject(owner.loginId, originalProject);
        for (Project forkedProject : forkedProjects) {
            branches.addAll(forkedProject.getRecentlyPushedBranches());
        }
        return branches;
    }
}
