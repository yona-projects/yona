package models;

import com.avaje.ebean.Page;
import controllers.SearchApp;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.resource.Resource;
import org.joda.time.Duration;
import play.Logger;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.*;
import utils.AccessControl;
import utils.JodaDateUtil;

import javax.persistence.*;
import javax.validation.constraints.Size;
import javax.xml.stream.util.XMLEventConsumer;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.avaje.ebean.Expr.contains;

/**
 * {@link Posting}과 {@link Issue}의 공통 속성과 메서드를 모아둔 클래스
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

    @Transient
    public User author;

    @ManyToOne
    public Project project;

    protected Long number;

    // This field is only for ordering. This field should be persistent because
    // Ebean does NOT sort entities by transient field.
    public int numOfComments;

    abstract protected Set<User> getExplicitWatchers();

    abstract protected Set<User> getExplicitUnwatchers();

    /**
     * {@link Comment} 개수를 반환한다.
     *
     * @return
     */
    abstract public int computeNumOfComments();

    public AbstractPosting() {
        this.createdDate = JodaDateUtil.now();
    }

    protected abstract Finder<Long, ? extends AbstractPosting> getFinder();

    /**
     * {@link Project}에 속한 {@link Issue} 또는 {@link Posting} 번호를 올린다.
     *
     * @return
     * @see models.Issue#increaseNumber()
     * @see models.Posting#increaseNumber()
     */
    protected abstract Long increaseNumber();

    public Long getNumber() {
        return number;
    }

    /**
     * 저장할 때 번호가 설정되어 있지 않다면 번호를 저장하고 댓글 개수를 설정한다.
     *
     * @see #increaseNumber()
     * @see #computeNumOfComments()
     */
    @Transactional
    public void save() {
        if (number == null) {
            number = increaseNumber();
        }
        numOfComments = computeNumOfComments();
        Logger.debug(String.format(">> new posting: before== #:%s by %s, %s, %s", number, authorLoginId, title, new Date() ));
        super.save();
        Logger.debug(String.format(">> new posting: after == #:%s, id:%s", number, id ));
    }

    /**
     * 갱신할 때 댓글 개수를 설정한다.
     */
    public void update() {
        numOfComments = computeNumOfComments();
        super.update();
    }

    /**
     * 특정 {@link Project}에 속한 {@link AbstractPosting} 중에서
     * title과 body에 특정한 값을 포함하고 있는 것들을 반환한다.
     *
     * when: {@link SearchApp#contentsSearch(String, String, int)}에서
     * 특정 프로젝트에 속한 이슈와 글을 검색할 때 사용한다.
     *
     * @param finder
     * @param project
     * @param condition
     * @param <T>
     * @return
     */
    public static <T> Page<T> find(Finder<Long, T> finder, Project project, SearchApp.ContentSearchCondition condition) {
        String filter = condition.filter;
        return finder.where().eq("project.id", project.id)
                .or(contains("title", filter), contains("body", filter))
                .findPagingList(condition.pageSize).getPage(condition.page - 1);
    }

    public static <T> T findByNumber(Finder<Long, T> finder, Project project, Long number) {
        return finder.where().eq("project.id", project.id).eq("number", number).findUnique();
    }

    /**
     * 현재 글을 쓴지 얼마나 되었는지를 얻어내는 함수
     *
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

    /**
     * 이슈나 글쓴이에 관한 정보를 저장한다.
     *
     * when: 새 글이나 새 이슈를 등록할 때 사용한다.
     *
     * @param user
     */
    @Transient
    public void setAuthor(User user) {
        authorId = user.id;
        authorLoginId = user.loginId;
        authorName = user.name;
    }

    /**
     * 이슈나 글에 달려있는 댓글을 반환한다.
     *
     * @return
     */
    abstract public List<? extends Comment> getComments();

    /**
     * 삭제할 때 이슈나 글에 달려있는 댓글과 첨부파일을 삭제한다.
     */
    public void delete() {
        for (Comment comment: getComments()) {
            comment.delete();
        }
        Attachment.deleteAll(asResource());
        super.delete();
    }

    /**
     * 특정 프로퍼티를 업데이트 할 때 사용한다.
     */
    public void updateProperties() {
        // default implementation for convenience
    }

    /**
     * 지켜보고 있는 모든 사용자들을 얻는다.
     *
     * when: 알림 메일을 발송할 때, 지켜보기 버튼을 그릴 때
     *
     * @return 이 이슈를 지켜보고 있는 모든 사용자들의 집합
     * @see {@link #getWatchers()}
     * @see <a href="https://github.com/nforge/hive/blob/master/docs/technical/watch.md>watch.md</a>
     */
    @Transient
    public Set<User> getWatchers() {
        return getWatchers(new HashSet<User>());
    }

    /**
     * 지켜보고 있는 모든 사용자들을 얻는다.
     *
     * @param baseWatchers 지켜보고 있는 사용자들이 더 있다면 이 파라메터를 통해 넘겨받는다. e.g. 이슈 담당자
     * @return
     * @see {@link #getWatchers()}
     * @see <a href="https://github.com/nforge/hive/blob/master/docs/technical/watch.md>watch.md</a>
     */
    @Transient
    public Set<User> getWatchers(Set<User> baseWatchers) {
        Set<User> actualWatchers = new HashSet<>();

        actualWatchers.addAll(baseWatchers);

        actualWatchers.add(User.find.byId(authorId));
        for (Comment c : getComments()) {
            User user = User.find.byId(c.authorId);
            if (user != null) {
                actualWatchers.add(user);
            }
        }

        actualWatchers.addAll(getExplicitWatchers());
        actualWatchers.removeAll(getExplicitUnwatchers());

        Set<User> allowedWatchers = new HashSet<>();
        for (User watcher : actualWatchers) {
            if (AccessControl.isAllowed(watcher, asResource(), Operation.READ)) {
                allowedWatchers.add(watcher);
            }
        }

        return allowedWatchers;
    }

    /**
     * {@code user}가 명시적으로 이 게시물을 지켜보는 것으로 설정한다.
     *
     * @param user
     */
    @Transactional
    public void watch(User user) {
        getExplicitWatchers().add(user);
        getExplicitUnwatchers().remove(user);
        update();
    }

    /**
     * {@code user}가 명시적으로 이 게시물을 무시하는 것(지켜보지 않는 것)으로 설정한다.
     *
     * @param user
     */
    @Transactional
    public void unwatch(User user) {
        getExplicitUnwatchers().add(user);
        getExplicitWatchers().remove(user);
        update();
    }
}
