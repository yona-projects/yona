/**
 * @author Ahn Hyeok Jun
 */

package models;

import javax.persistence.*;

import models.enumeration.ResourceType;
import models.resource.Resource;

import java.util.*;

import static com.avaje.ebean.Expr.eq;

/**
 * 게시물을 나타내는 클래스
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "number"}))
public class Posting extends AbstractPosting {
    private static final long serialVersionUID = 5287703642071155249L;

    public static Finder<Long, Posting> finder = new Finder<Long, Posting>(Long.class, Posting.class);

    public boolean notice;

    @OneToMany(cascade = CascadeType.ALL)
    public List<PostingComment> comments;

    /**
     * @return
     * @see models.AbstractPosting#getFinder()
     */
    public Finder<Long, ? extends AbstractPosting> getFinder() {
        return finder;
    }

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
    public static List<Posting> findRecentlyUpdated(Project project, int size) {
        return Posting.finder.where()
                .eq("project.id", project.id)
                .order().desc("createdDate")
                .findPagingList(size).getPage(0)
                .getList();
    }

    /**
     * @return
     * @see models.AbstractPosting#getComments()
     */
    @Transient
    public List<? extends Comment> getComments() {
        return comments;
    }

    public static Posting findByNumber(Project project, Long number) {
        return AbstractPosting.findByNumber(finder, project, number);
    }
}
