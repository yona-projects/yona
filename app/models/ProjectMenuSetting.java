/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Lee HeeGu
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

import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;

@Entity
public class ProjectMenuSetting extends Model {
    private static final long serialVersionUID = 1L;
    public static Finder<Long, ProjectMenuSetting> finder = new Finder<>(Long.class, ProjectMenuSetting.class);

    @Id
    public Long id;
    @OneToOne
    public Project project;
    public boolean code;
    public boolean issue;
    public boolean pullRequest;
    public boolean review;
    public boolean milestone;
    public boolean board;

    public ProjectMenuSetting(ProjectMenuSetting projectMenuSetting) {
        this.code = projectMenuSetting.code;
        this.issue = projectMenuSetting.issue;
        this.pullRequest = projectMenuSetting.pullRequest;
        this.review = projectMenuSetting.review;
        this.milestone = projectMenuSetting.milestone;
        this.board = projectMenuSetting.board;
    }
}
