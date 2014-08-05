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
package models;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Keeun Baik
 */
public class RecentlyVisitedProjectsTest extends ModelTest<RecentlyVisitedProjects> {

    User doortts;
    User nori;
    Project yobi;
    Project cubrid;

    @Before
    public void setup() {
        doortts = User.findByLoginId("doortts");
        nori = User.findByLoginId("nori");
        yobi = Project.findByOwnerAndProjectName("yobi", "projectYobi");
        cubrid = Project.findByOwnerAndProjectName("doortts", "CUBRID");
    }

    @After
    public void after() {
        for(RecentlyVisitedProjects visitedProjects: RecentlyVisitedProjects.find.all()) {
            visitedProjects.delete();
        }
    }

    @Test
    public void addVisit() {
        // When
        doortts.visits(yobi);

        // Then
        assertThat(RecentlyVisitedProjects.find.all().size()).isEqualTo(1);
        assertThat(doortts.recentlyVisitedProjects.visitedProjects.size()).isEqualTo(1);
    }

    @Test
    public void addVisitsWithTheSameProject() {
        // Given
        doortts.visits(yobi);
        assertThat(RecentlyVisitedProjects.find.all().size()).isEqualTo(1);
        assertThat(doortts.recentlyVisitedProjects.visitedProjects.size()).isEqualTo(1);

        // When
        doortts.visits(yobi);

        // Then
        assertThat(RecentlyVisitedProjects.find.all().size()).isEqualTo(1);
        assertThat(doortts.recentlyVisitedProjects.visitedProjects.size()).isEqualTo(1);
    }

    @Test
    public void addVisitsWithTheDiffrentProjects() {
        // Given
        doortts.visits(yobi);
        assertThat(RecentlyVisitedProjects.find.all().size()).isEqualTo(1);
        assertThat(doortts.recentlyVisitedProjects.visitedProjects.size()).isEqualTo(1);

        // When
        doortts.visits(cubrid);

        // Then
        assertThat(RecentlyVisitedProjects.find.all().size()).isEqualTo(1);
        assertThat(doortts.recentlyVisitedProjects.visitedProjects.size()).isEqualTo(2);
    }

    @Test
    public void addVisitsWithDifferentUsers() {
        // Given
        doortts.visits(yobi);
        assertThat(RecentlyVisitedProjects.find.all().size()).isEqualTo(1);
        assertThat(doortts.recentlyVisitedProjects.visitedProjects.size()).isEqualTo(1);

        // When
        nori.visits(yobi);

        // Then
        assertThat(RecentlyVisitedProjects.find.all().size()).isEqualTo(2);
        assertThat(doortts.recentlyVisitedProjects.visitedProjects.size()).isEqualTo(1);
        assertThat(nori.recentlyVisitedProjects.visitedProjects.size()).isEqualTo(1);
    }

    @Test
    public void recentlyVisitedProjects() throws InterruptedException {
        // Given
        doortts.visits(yobi);
        Thread.sleep(1000l);
        doortts.visits(cubrid);

        // When
        List<ProjectVisitation> projects = doortts.getVisitedProjects(2);

        // Then
        assertThat(projects.size()).isEqualTo(2);
        assertThat(projects.get(0).project).isEqualTo(cubrid);
        assertThat(projects.get(1).project).isEqualTo(yobi);
    }

    @Test
    public void recentlyVisitedProjectsWithRevisitation() throws InterruptedException {
        // Given
        doortts.visits(yobi);
        Thread.sleep(1000l);
        doortts.visits(cubrid);
        Thread.sleep(1000l);
        doortts.visits(yobi);

        // When
        List<ProjectVisitation> projects = doortts.getVisitedProjects(2);

        // Then
        assertThat(projects.size()).isEqualTo(2);
        assertThat(projects.get(0).project).isEqualTo(yobi);
        assertThat(projects.get(1).project).isEqualTo(cubrid);
    }

}
