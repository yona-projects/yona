package models;

import com.avaje.ebean.Page;
import controllers.SearchApp;
import models.enumeration.ResourceType;
import models.resource.Resource;
import org.joda.time.Duration;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.*;
import utils.JodaDateUtil;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.Date;

import static com.avaje.ebean.Expr.contains;

/**
 * Created with IntelliJ IDEA.
 * User: nori
 * Date: 13. 3. 4
 * Time: 오후 6:17
 * To change this template use File | Settings | File Templates.
 */

@MappedSuperclass
abstract public class AbstractPosting extends Model {
    public static final int FIRST_PAGE_NUMBER = 0;
    public static final int NUMBER_OF_ONE_MORE_COMMENTS = 1;

    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @Constraints.Required
    @Size(max=255)
    public String title;

    @Constraints.Required
    @Lob
    public String body;

    @Constraints.Required
    @Formats.DateTime(pattern = "YYYY/MM/DD/hh/mm/ss")
    public Date createdDate;

    public Long authorId;
    public String authorLoginId;
    public String authorName;

    @ManyToOne
    public Project project;

    protected Long number;

    // This field is only for ordering. This field should be persistent because
    // Ebean does NOT sort entities by transient field.
    public int numOfComments;

    abstract public int computeNumOfComments();

    public AbstractPosting() {
        this.createdDate = JodaDateUtil.now();
    }

    protected abstract Finder<Long, ? extends AbstractPosting> getFinder();

    protected abstract Long increaseNumber();

    public Long getNumber() {
        return number;
    }

    @Transactional
    public void save() {
        if (number == null) {
            number = increaseNumber();
        }
        numOfComments = computeNumOfComments();
        super.save();
    }

    public void update() {
        numOfComments = computeNumOfComments();
        super.update();
    }

    public static <T> Page<T> find(Finder<Long, T> finder, Project project, SearchApp.ContentSearchCondition condition) {
        String filter = condition.filter;
        return finder.where().eq("project.id", project.id)
                .or(contains("title", filter), contains("body", filter))
                .findPagingList(condition.pageSize).getPage(condition.page - 1);
    }

    /**
     * 현재 글을 쓴지 얼마나 되었는지를 얻어내는 함수
     * @return
     */
    public Duration ago() {
        return JodaDateUtil.ago(this.createdDate);
    }

    abstract public Resource asResource();

    public Resource asResource(final ResourceType type) {
        return new Resource() {
	        @Override
	        public Long getId() {
	            return id;
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
}
