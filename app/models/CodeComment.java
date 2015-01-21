/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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

import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.joda.time.Duration;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import javax.persistence.*;
import java.beans.Transient;
import java.util.Date;


@MappedSuperclass
abstract public class CodeComment extends Model implements ResourceConvertible, TimelineItem {
    private static final long serialVersionUID = 1L;
    public static final Finder<Long, CodeComment> find = new Finder<>(Long.class, CodeComment.class);

    @Id
    public Long id;
    @ManyToOne
    public Project project;
    public String path;
    public Integer line;
    @Enumerated(EnumType.STRING)
    public CodeRange.Side side;
    @Lob @Constraints.Required
    public String contents;
    @Constraints.Required
    public Date createdDate;
    public Long authorId;
    public String authorLoginId;
    public String authorName;

    public CodeComment() {
        createdDate = new Date();
    }


    @Transient
    public void setAuthor(User user) {
        authorId = user.id;
        authorLoginId = user.loginId;
        authorName = user.name;
    }

    @Override
    public Date getDate() {
        return createdDate;
    }

    public Duration ago() {
        return JodaDateUtil.ago(this.createdDate);
    }

    abstract public Resource asResource();

    abstract public String getCommitId();
}
