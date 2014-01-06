/**
 * @author Ahn Hyeok Jun
 */

package models;

import javax.persistence.*;

import models.enumeration.ResourceType;
import models.resource.Resource;
import utils.JodaDateUtil;

import java.util.*;

import static com.avaje.ebean.Expr.eq;

/**
 * 게시물을 나타내는 클래스
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "number"}))
public class Posting extends AbstractPosting {
    private static final long serialVersionUID = 5287703642071155249L;

    public static final Finder<Long, Posting> finder = new Finder<>(Long.class, Posting.class);

    public boolean notice;

    @OneToMany(cascade = CascadeType.ALL)
    public List<PostingComment> comments;

    /**
     * {@link Project}의 최근 게시물 번호를 반환한다.
     *
     * @return
     * @see models.Project#increaseLastPostingNumber()
     */
    @Override
    protected Long increaseNumber() {
        return project.increaseLastPostingNumber();
    }

    protected void fixLastNumber() {
        project.fixLastPostingNumber();
    }

    /**
     * @return
     * @see models.AbstractPosting#computeNumOfComments()
     */
    public int computeNumOfComments() {
        return comments.size();
    }

    public Posting() {
        super();
    }

    @Override
    public Resource asResource() {
        return asResource(ResourceType.BOARD_POST);
    }

    /**
     * 게시물 중에서 공지글 목록을 반환한다.
     *
     * when: 게시물 목록 화면을 보여줄 때 사용한다.
     *
     * @param project
     * @return
     */
    public static List<Posting> findNotices(Project project) {
        return Posting.finder.where()
                .eq("project.id", project.id)
                .add(eq("notice", true))
                .order().desc("createdDate")
                .findList();
    }

    /**
     * {@link Project}에 게시된 최근 게시물을 {@code size} 만큼 조회한다.
     *
     * when: 프로젝트 overview 화면에서 최근 활동 내역을 보여줄 때 사용한다.
     *
     * @param project
     * @param size
     * @return
     */
    public static List<Posting> findRecentlyCreated(Project project, int size) {
        return Posting.finder.where()
                .eq("project.id", project.id)
                .order().desc("createdDate")
                .findPagingList(size).getPage(0)
                .getList();
    }

    public static List<Posting> findRecentlyCreatedByDaysAgo(Project project, int days) {
        return Posting.finder.where()
                .eq("project.id", project.id)
                .ge("createdDate", JodaDateUtil.before(days)).order().desc("createdDate").findList();
    }

    /**
     * @return
     * @see models.AbstractPosting#getComments()
     */
    @Transient
    public List<? extends Comment> getComments() {
        Collections.sort(comments, Comment.comparator());
        return comments;
    }

    /**
     * {@code project}에 있는 {@code number}에 해당하는 글번호를 가진 게시물을 조회한다.
     *
     * @param project
     * @param number
     * @return
     */
    public static Posting findByNumber(Project project, Long number) {
        return AbstractPosting.findByNumber(finder, project, number);
    }

    /**
     * {@code project}에 있는 글 개수를 조회한다.
     *
     * @param project
     * @return
     */
    public static int countPostings(Project project) {
        return finder.where().eq("project", project).findRowCount();
    }
}
