/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
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

import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import play.data.validation.Constraints.Required;
import play.db.ebean.Model;

/**
 * 특정 프로젝트의 이슈 담당자를 나타낸 클래스
 */
@Entity
public class Assignee extends Model {

    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @ManyToOne
    @Required
    public User user;

    @ManyToOne
    @Required
    public Project project;

    @OneToMany(mappedBy = "assignee")
    public Set<Issue> issues;

    public static final Model.Finder<Long, Assignee> finder = new Finder<>(Long.class, Assignee.class);

    public Assignee(Long userId, Long projectId) {
        user = User.find.byId(userId);
        project = Project.find.byId(projectId);
    }

    /**
     * 이슈를 새로 만드거나 수정할 때 담당자가 지정되어 있다면 해당 담당자 정보를 저장한다.
     *
     * {@code projectId}에 해당하는 프로젝트에 {@code userId}에 해당하는 담당자가
     * 없을 때에만 새로 추가한다.
     *
     * @param userId
     * @param projectId
     * @return
     */
    public static Assignee add(Long userId, Long projectId) {
        Assignee assignee = finder.where()
                .eq("user.id", userId).eq("project.id", projectId).findUnique();
        if (assignee == null) {
            assignee = new Assignee(userId, projectId);
            assignee.save();
        }
        return assignee;
    }
}
