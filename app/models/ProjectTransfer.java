/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keeun Baik
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

import controllers.routes;
import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.apache.commons.lang3.RandomStringUtils;
import org.joda.time.DateTime;
import play.db.ebean.Model;
import utils.Url;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

@Entity
public class ProjectTransfer extends Model implements ResourceConvertible {

    private static final long serialVersionUID = 1L;

    public static final Finder<Long, ProjectTransfer> find = new Finder<>(Long.class, ProjectTransfer.class);

    @Id
    public Long id;

    // who requested this transfer.
    @ManyToOne
    public User sender;

    /**
     * Destination can be either an user or an organization.
     * If you want to transfer to an user, then destination have to be set with the user's loginId.
     * If you want to transfer to an organization, then destination have to be set with the organization's name.
     */
    public String destination;

    @ManyToOne
    public Project project;

    @Temporal(TemporalType.TIMESTAMP)
    public Date requested;

    public String confirmKey;

    public boolean accepted;

    public String newProjectName;

    public static ProjectTransfer requestNewTransfer(Project project, User sender, String destination) {
        ProjectTransfer pt = find.where()
                .eq("project", project)
                .eq("sender", sender)
                .eq("destination", destination)
                .findUnique();

        if(pt != null) {
            pt.requested = new Date();
            pt.confirmKey = RandomStringUtils.randomAlphanumeric(50);
            pt.update();
        } else {
            pt = new ProjectTransfer();
            pt.project = project;
            pt.sender = sender;
            pt.destination = destination;
            pt.requested = new Date();
            pt.confirmKey = RandomStringUtils.randomAlphanumeric(50);
            pt.newProjectName = Project.newProjectName(destination, project.name);
            pt.save();
        }

        return pt;
    }

    public String getAcceptUrl() {
        return Url.create(routes.ProjectApp.acceptTransfer(id, confirmKey).url());
    }

    public static ProjectTransfer findValidOne(Long id) {
        Date now = new Date();
        DateTime oneDayBefore = new DateTime(now).minusDays(1);

        return find.where()
                .eq("id", id)
                .eq("accepted", false)
                .between("requested", oneDayBefore, now)
                .findUnique();
    }

    public static void deleteExisting(Project project, User sender, String destination) {
        ProjectTransfer pt = find.where()
                .eq("project", project)
                .eq("sender", sender)
                .eq("destination", destination)
                .findUnique();

        if(pt != null) {
            pt.delete();
        }
    }

    public Resource asResource() {
        return new Resource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.PROJECT_TRANSFER;
            }
        };
    }

    public static List<ProjectTransfer> findByProject(Project project) {
        return find.where()
                .eq("project", project)
                .findList();
    }
}
