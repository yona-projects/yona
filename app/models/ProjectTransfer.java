/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keeun Baik
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

/**
 * 프로젝트 이관 정보
 *
 * 이관 요청을 할 때 새로운 프로젝트 이관 정보를 생성하고, 이관이 완려되면 {@code accepted}가 true로 바뀐다.
 * 새로운 이관 요청을 만들때 받을 사용자의 프로젝트 이름을 확인하고 중복되는 프로젝트가 있을 경우에는 프로젝트 fork와 동일한 정책으로 프로젝트 이름을 변경한다.
 * 동일한 프로젝트를 동일한 사용자에서 동일한 사용자로 여러번 이관 요청을 할 경우에는 이전 요청의 날짜와 확인키를 변경한다.
 * 요청이 수락되면 이전 이관 요청 중에서 동일한 프로젝트를 반대로 주고받은 요청을 삭제한다.
 */
@Entity
public class ProjectTransfer extends Model implements ResourceConvertible {

    private static final long serialVersionUID = 1L;

    public static Finder<Long, ProjectTransfer> find = new Finder<>(Long.class, ProjectTransfer.class);

    @Id
    public Long id;

    @ManyToOne
    public User from;

    @ManyToOne
    public User to;

    @ManyToOne
    public Project project;

    @Temporal(TemporalType.TIMESTAMP)
    public Date requested;

    public String confirmKey;

    public boolean accepted;

    public String newProjectName;

    public static ProjectTransfer requestNewTransfer(Project project, User from, User to) {
        ProjectTransfer pt = find.where()
                .eq("project", project)
                .eq("from", from)
                .eq("to", to)
                .findUnique();

        if(pt != null) {
            pt.requested = new Date();
            pt.confirmKey = RandomStringUtils.randomAlphanumeric(50);
            pt.update();
        } else {
            pt = new ProjectTransfer();
            pt.project = project;
            pt.from = from;
            pt.to = to;
            pt.requested = new Date();
            pt.confirmKey = RandomStringUtils.randomAlphanumeric(50);
            pt.newProjectName = Project.newProjectName(to.loginId, project.name);
            pt.save();
        }

        return pt;
    }

    public String getAcceptUrl() {
        return Url.create(routes.ProjectApp.acceptTransfer(id, confirmKey).url());
    }

    /**
     * 현재 시간 기준으로 하루 안에 생성되었고 수락하지 않은 {@code ProjectTransfer} 요청을 찾습니다.
     * @param id
     * @return
     */
    public static ProjectTransfer findValidOne(Long id) {
        Date now = new Date();
        DateTime oneDayBefore = new DateTime(now).minusDays(1);

        return find.where()
                .eq("id", id)
                .eq("accepted", false)
                .between("requested", oneDayBefore, now)
                .findUnique();
    }

    public static void deleteExisting(Project project, User from, User to) {
        ProjectTransfer pt = find.where()
                .eq("project", project)
                .eq("from", from)
                .eq("to", to)
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
