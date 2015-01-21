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
package models;

import play.Logger;
import play.db.ebean.Model;
import play.db.ebean.Transactional;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

/**
 * @author Keeun Baik
 */
@Entity
public class RecentlyVisitedProjects extends Model {

    private static final long serialVersionUID = 1L;

    public static final Finder <Long, RecentlyVisitedProjects> find = new Finder<>(Long.class, RecentlyVisitedProjects.class);

    @Id
    public Long id;

    @OneToOne
    @JoinColumn(name = "user_id")
    public User user;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "recently_visited_projects_id")
    public List<ProjectVisitation> visitedProjects;

    /**
     * Add new ProjectVisitation to existing RecentlyVisitedProjects or new one.
     *
     * Updating the existing ProjectVisitation can be failed by OptimisticLockException.
     *
     * @param user
     * @param project
     * @return
     */
    @Transactional
    public static RecentlyVisitedProjects addNewVisitation(User user, Project project) {
        RecentlyVisitedProjects existingOne = find.where().eq("user", user).findUnique();
        if(existingOne != null) {
            existingOne.refresh();
            existingOne.add(project);
            existingOne.update();
            return existingOne;
        }

        RecentlyVisitedProjects newOne = new RecentlyVisitedProjects();
        newOne.user = user;
        newOne.add(project);
        newOne.save();
        return newOne;
    }

    private void add(Project project) {
        ProjectVisitation existingPV = ProjectVisitation.findBy(this, project);
        if(existingPV != null) {
            updateExistingPv(existingPV);
        } else {
            ProjectVisitation newPV = new ProjectVisitation();
            newPV.recentlyVisitedProjects = this;
            newPV.project = project;
            newPV.visited = new Date();
            this.visitedProjects.add(newPV);
        }
    }

    private void updateExistingPv(ProjectVisitation existingPV) {
        try {
            existingPV.visited = new Date();
            existingPV.update();
        } catch (OptimisticLockException e) {
            Logger.warn("OptimisticLockException occurred when updating ProjectVisitation: " + existingPV.id);
        }
    }

    public List<ProjectVisitation> findRecentlyVisitedProjects(int size) {
        return ProjectVisitation.findRecentlyVisitedProjects(this, size);
    }
}
