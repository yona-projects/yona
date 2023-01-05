/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.apache.commons.lang.StringUtils;
import org.joda.time.Duration;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.db.ebean.Transactional;
import utils.JodaDateUtil;

import javax.annotation.Nonnull;
import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@MappedSuperclass
abstract public class AbstractPosting extends Model implements ResourceConvertible {
    public static final Finder<Long, AbstractPosting> finder = new Finder<>(Long.class, AbstractPosting.class);
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

    @Lob
    public String history;

    @Constraints.Required
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date createdDate;

    @Constraints.Required
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date updatedDate;

    public Long authorId;
    public String authorLoginId;
    public String authorName;

    public Long updatedByAuthorId;

    @Transient
    public User author;

    @ManyToOne
    public Project project;

    protected Long number;

    // This field is only for ordering. This field should be persistent because
    // Ebean does NOT sort entities by transient field.
    public int numOfComments;

    @Transient
    public Boolean isPublish = false;

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
            TitleHead.saveTitleHeadKeyword(project, title);
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
    public void saveWithNumber(long number) {
        this.number = number;
        numOfComments = computeNumOfComments();
        super.save();
        updateMention();
    }

    @Transactional
    public void update() {
        try {
            numOfComments = computeNumOfComments();
            super.update();
        } catch (PersistenceException ole) {
            play.Logger.warn("PersistenceException: " + ole.getMessage());
        } catch (Exception e) {
            play.Logger.warn(e.getMessage());
        }
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

    public static <T> List<T> findByProject(Finder<Long, T> finder, Project project) {
        return finder.where().eq("project.id", project.id).findList();
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
        TitleHead.deleteTitleHeadKeyword(project, title);
        Attachment.deleteAll(asResource());
        NotificationEvent.deleteBy(this.asResource());
        super.delete();
    }

    public void deleteOnly() {
        super.delete();
    }

    public void updateProperties() {
        // default implementation for convenience
    }

    @Transient
    public Set<User> getWatchers() {
        return getWatchers(true);
    }

    /**
     * @see {@link #getWatchers()}
     * @see <a href="https://github.com/nforge/yobi/blob/master/docs/technical/watch.md>watch.md</a>
     */
    @Transient
    public Set<User> getWatchers(boolean allowedWatchersOnly) {
        return getWatchers(new HashSet<User>(), allowedWatchersOnly);
    }

    /**
     * @see {@link #getWatchers()}
     * @see <a href="https://github.com/nforge/yobi/blob/master/docs/technical/watch.md>watch.md</a>
     */
    @Transient
    public Set<User> getWatchers(Set<User> baseWatchers, boolean allowedWatchersOnly) {
        Set<User> actualWatchers = new HashSet<>();

        actualWatchers.addAll(baseWatchers);

        actualWatchers.add(getAuthor());

        return Watch.findActualWatchers(actualWatchers, asResource(), allowedWatchersOnly);
    }

    protected void updateMention() {
        if (this.body != null) {
            Mention.update(this.asResource(), NotificationEvent.getMentionedUsers(this.body));
        }
    }

    public abstract void checkLabels() throws IssueLabel.IssueLabelException;

    public boolean isAuthoredBy(@Nonnull User user){
        return StringUtils.equalsIgnoreCase(this.authorLoginId, user.loginId);
    }
}
