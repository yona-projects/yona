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

import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.joda.time.Duration;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import utils.JodaDateUtil;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@MappedSuperclass
abstract public class AbstractPosting extends Model implements ResourceConvertible {
    public static final int FIRST_PAGE_NUMBER = 0;
    public static final int NUMBER_OF_ONE_MORE_COMMENTS = 1;

    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @Constraints.Required
    @Size(max=255)
    public String title;

    @Lob
    public String body;

    @Constraints.Required
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date createdDate;

    @Constraints.Required
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date updatedDate;

    public Long authorId;
    public String authorLoginId;
    public String authorName;

    @Transient
    public User author;

    @ManyToOne
    public Project project;

    protected Long number;

    // This field is only for ordering. This field should be persistent because
    // Ebean does NOT sort entities by transient field.
    public int numOfComments;

    abstract public int computeNumOfComments();

    public AbstractPosting() {
        this.createdDate = JodaDateUtil.now();
        this.updatedDate = JodaDateUtil.now();
    }

    public AbstractPosting(Project project, User author, String title, String body) {
        this();
        setAuthor(author);
        this.project = project;
        this.title = title;
        this.body = body;
    }

    /**
     * @see models.Issue#increaseNumber()
     * @see models.Posting#increaseNumber()
     */
    protected abstract Long increaseNumber();

    protected abstract void fixLastNumber();

    public Long getNumber() {
        return number;
    }

    public void setNumber(Long number) {
        this.number = number;
    }

    /**
     * @see #increaseNumber()
     * @see #computeNumOfComments()
     */
    @Transactional
    public void save() {
        if (number == null) {
            number = increaseNumber();
        }
        numOfComments = computeNumOfComments();

        try {
            super.save();
            updateMention();
        } catch (PersistenceException e) {
            Long oldNumber = number;
            fixLastNumber();
            number = increaseNumber();
            // What causes this PersistenceException?
            if (!oldNumber.equals(number)) {
                // caused by invalid number.
                play.Logger.warn(String.format("%s/%s: Invalid last number %d is fixed to %d",
                        asResource().getProject(), asResource().getType(), oldNumber, number));
                super.save();
            } else {
                // caused by the other reason.
                throw e;
            }
        }
    }

    @Transactional
    public void update() {
        numOfComments = computeNumOfComments();
        super.update();
        updateMention();
    }

    /**
     * use EBean save functionality directly
     * to prevent occurring select table lock
     */
    public void directSave(){
        updateMention();
        super.save();
    }

    public void updateNumber() {
        number = increaseNumber();
        super.update();
    }

    public static <T> T findByNumber(Finder<Long, T> finder, Project project, Long number) {
        return finder.where().eq("project.id", project.id).eq("number", number).findUnique();
    }

    public Duration ago() {
        return JodaDateUtil.ago(this.createdDate);
    }

    public Resource asResource(final ResourceType type) {
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
                return type;
            }

            @Override
            public Long getAuthorId() {
                return authorId;
            }
        };
    }

    @Transient
    public void setAuthor(User user) {
        authorId = user.id;
        authorLoginId = user.loginId;
        authorName = user.name;
    }

    @Transient
    public User getAuthor() {
        return User.findByLoginId(authorLoginId);
    }

    abstract public List<? extends Comment> getComments();

    public void delete() {
        for (Comment comment: getComments()) {
            comment.delete();
        }
        Attachment.deleteAll(asResource());
        NotificationEvent.deleteBy(this.asResource());
        super.delete();
    }

    public void updateProperties() {
        // default implementation for convenience
    }

    /**
     * @see {@link #getWatchers()}
     * @see <a href="https://github.com/nforge/yobi/blob/master/docs/technical/watch.md>watch.md</a>
     */
    @Transient
    public Set<User> getWatchers() {
        return getWatchers(new HashSet<User>());
    }

    /**
     * @see {@link #getWatchers()}
     * @see <a href="https://github.com/nforge/yobi/blob/master/docs/technical/watch.md>watch.md</a>
     */
    @Transient
    public Set<User> getWatchers(Set<User> baseWatchers) {
        Set<User> actualWatchers = new HashSet<>();

        actualWatchers.addAll(baseWatchers);

        actualWatchers.add(getAuthor());
        for (Comment c : getComments()) {
            User user = User.find.byId(c.authorId);
            if (user != null) {
                actualWatchers.add(user);
            }
        }

        return Watch.findActualWatchers(actualWatchers, asResource());
    }

    protected void updateMention() {
        if (this.body != null) {
            Mention.update(this.asResource(), NotificationEvent.getMentionedUsers(this.body));
        }
    }

    public abstract void checkLabels() throws IssueLabel.IssueLabelException;
}
