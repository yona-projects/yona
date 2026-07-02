/**
 * @author Ahn Hyeok Jun
 */

package models;

import io.ebean.Finder;
import io.ebean.Ebean;
import io.ebean.SqlRow;

import static io.ebean.Expr.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

import models.enumeration.ResourceType;
import models.resource.Resource;
import utils.JodaDateUtil;
import utils.LobString;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "number"}))
public class Posting extends AbstractPosting {
    private static final long serialVersionUID = 5287703642071155249L;

    public static final Finder<Long, Posting> finder = new Finder<>(Posting.class);

    public boolean notice;
    public boolean readme;

    @Transient
    public String issueTemplate;

    //ToDo: Sperate it from posting for online commit
    @Transient
    public String path;

    //ToDo: Sperate it from posting for online commit
    @Transient
    public String branch;

    //ToDo: Sperate it from posting for online commit
    @Transient
    public String lineEnding;

    @OneToMany(cascade = CascadeType.ALL)
    public List<PostingComment> comments;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    public Set<IssueLabel> labels;

    public Set<Long> getLabelIds() {
        Set<Long> labelIds = new HashSet<>();

        for(IssueLabel label : this.labels){
            labelIds.add(label.id);
        }

        return labelIds;
    }

    public Posting(Project project, User author, String title, String body) {
        super(project, author, title, body);
    }

    /**
     * @see models.Project#increaseLastPostingNumber()
     */
    @Override
    protected Long increaseNumber() {
        return Project.increaseLastPostingNumber(project.id);
    }

    protected void fixLastNumber() {
        Project.fixLastPostingNumber(project.id);
    }

    /**
     * @see models.AbstractPosting#computeNumOfComments()
     */
    public int computeNumOfComments() {
        return comments.size();
    }

    @OneToOne
    public Posting parent;

    public Posting() {
        super();
    }

    @Override
    public Resource asResource() {
        return asResource(ResourceType.BOARD_POST);
    }

    public static List<Posting> findNotices(Project project) {
        return Posting.finder.query().where()
                .eq("project.id", project.id)
                .add(eq("notice", true))
                .orderBy().desc("createdDate")
                .findList();
    }

    public static List<Posting> findRecentlyCreated(Project project, int size) {
        return Posting.finder.query().where()
                .eq("project.id", project.id)
                .orderBy().desc("createdDate")
                .setFirstRow((0) * (size)).setMaxRows(size).findPagedList()
                .getList();
    }

    public static List<Posting> findRecentlyCreatedByDaysAgo(Project project, int days) {
        return Posting.finder.query().where()
                .eq("project.id", project.id)
                .ge("createdDate", JodaDateUtil.before(days)).orderBy().desc("createdDate").findList();
    }

    /**
     * @see models.AbstractPosting#getComments()
     */
    @Transient
    public List<? extends Comment> getComments() {
        Collections.sort(comments, Comment.comparator());
        return comments;
    }

    @Override
    public void checkLabels() {

    }

    public static Posting findByNumber(Project project, long number) {
        Posting posting = Posting.finder.query()
                .fetch("project")
                .where()
                .eq("project.id", project.id)
                .eq("number", number)
                .findOne();
        if (posting != null && (posting.body == null || LobString.needsUnwrap(posting.body))) {
            SqlRow row = Ebean.createSqlQuery("select body from posting where id = :id")
                    .setParameter("id", posting.id)
                    .findOne();
            if (row != null) {
                posting.body = LobString.unwrap(row.getString("body"));
            }
        }
        if (posting != null) {
            if (posting.comments == null) {
                posting.comments = PostingComment.find.query().where()
                        .eq("posting.id", posting.id)
                        .findList();
            }
            loadCommentContents(posting.comments);
        }
        return posting;
    }

    private static void loadCommentContents(List<PostingComment> comments) {
        for (PostingComment comment : comments) {
            if ((comment.contents == null || LobString.needsUnwrap(comment.contents)) && comment.id != null) {
                SqlRow row = Ebean.createSqlQuery("select contents from posting_comment where id = :id")
                        .setParameter("id", comment.id)
                        .findOne();
                if (row != null) {
                    comment.contents = LobString.unwrap(row.getString("contents"));
                }
            }
        }
    }

    public static int countAllCreatedBy(User user) {
        return finder.query().where().eq("author_id", user.id).findCount();
    }

    public static int countPostings(Project project) {
        return finder.query().where().eq("project", project).findCount();
    }

    /**
     * use EBean save functionality directly
     * to prevent occurring select table lock
     */
    public void directSave(){
        super.directSave();
    }

    public static Posting findREADMEPosting(Project project) {
        return Posting.finder.query().where()
                .eq("project.id", project.id)
                .add(eq("readme", true))
                .findOne();
    }

    public PostingComment findCommentByCommentId(Long id) {
        for (PostingComment comment: comments) {
            if (comment.id.equals(id)) {
                return comment;
            }
        }
        return null;
    }

    public static Posting from(Issue issue) {
        Posting posting = new Posting();

        posting.title = issue.title;
        posting.body = issue.body;
        posting.history = issue.history;
        posting.createdDate = issue.createdDate;
        posting.updatedDate = issue.updatedDate;
        posting.authorId = issue.authorId;
        posting.authorLoginId = issue.authorLoginId;
        posting.authorName = issue.authorName;
        posting.project = issue.project;

        return posting;
    }
}
