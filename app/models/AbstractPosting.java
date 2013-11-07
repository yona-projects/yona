package models;

import com.avaje.ebean.Page;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.resource.Resource;
import models.resource.ResourceConvertible;

import org.joda.time.Duration;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.*;
import utils.AccessControl;
import utils.JodaDateUtil;
import utils.WatchService;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.avaje.ebean.Expr.contains;

/**
 * {@link Posting}과 {@link Issue}의 공통 속성과 메서드를 모아둔 클래스
 */
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

    /**
     * {@link Comment} 개수를 반환한다.
     *
     * @return
     */
    abstract public int computeNumOfComments();

    public AbstractPosting() {
        this.createdDate = JodaDateUtil.now();
        this.updatedDate = JodaDateUtil.now();
    }

    /**
     * {@link Project}에 속한 {@link Issue} 또는 {@link Posting} 번호를 올린다.
     *
     * @return
     * @see models.Issue#increaseNumber()
     * @see models.Posting#increaseNumber()
     */
    protected abstract Long increaseNumber();

    protected abstract void fixLastNumber();

    public Long getNumber() {
        return number;
    }

    /**
     * 저장할 때 번호가 설정되어 있지 않다면 번호를 저장하고 댓글 개수를 설정한다.
     *
     * 종종 번호 계산의 근거가 되는 {@link Project#lastIssueNumber} 혹은
     * {@link Project#lastPostingNumber}의 값이 잘못되어서(0으로 리셋되는 경우가 있다)
     * {@link PersistenceException}이 발생하곤 한다. 이런 경우에는 값을 바로잡고 다시 저장한다.
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

        try {
            super.save();
        } catch (PersistenceException e) {
            Long oldNumber = number;
            fixLastNumber();
            number = increaseNumber();
            // What causes this PersistenceException?
            if (oldNumber != number) {
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

    /**
     * 갱신할 때 댓글 개수를 설정한다.
     */
    public void update() {
        numOfComments = computeNumOfComments();
        super.update();
    }


    public void updateNumber() {
        number = increaseNumber();
        super.update();
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

    @Transient
    public User getAuthor() {
        return User.findByLoginId(authorLoginId);
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
        NotificationEvent.deleteBy(this.asResource());
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
     * @see <a href="https://github.com/nforge/yobi/blob/master/docs/technical/watch.md>watch.md</a>
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

        actualWatchers.addAll(WatchService.findWatchers(project.asResource()));
        actualWatchers.addAll(WatchService.findWatchers(asResource()));
        actualWatchers.removeAll(WatchService.findUnwatchers(asResource()));

        Set<User> allowedWatchers = new HashSet<>();
        for (User watcher : actualWatchers) {
            if (AccessControl.isAllowed(watcher, asResource(), Operation.READ)) {
                allowedWatchers.add(watcher);
            }
        }

        return allowedWatchers;
    }
}
