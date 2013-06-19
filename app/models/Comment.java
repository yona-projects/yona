package models;

import controllers.UserApp;
import controllers.routes;
import info.schleichardt.play2.mailplugin.Mailer;
import models.resource.Resource;

import org.apache.commons.mail.SimpleEmail;
import org.joda.time.*;
import play.data.validation.*;
import play.db.ebean.*;
import utils.*;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.*;

/**
 * 이슈 혹은 게시글의, 댓글
 */
@MappedSuperclass
abstract public class Comment extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @Constraints.Required @Column(length = 4000) @Size(max=4000)
    public String contents;

    @Constraints.Required
    public Date createdDate;

    public Long authorId;
    public String authorLoginId;
    public String authorName;

    /**
     * <p>댓글을 생성한다.</p>
     */
    public Comment() {
        createdDate = new Date();
    }

    /**
     * <p>몇일 전에 작성되었는지 반환한다.</p>
     *
     * @return 몇일 전에 작성되었는지
     */
    public Duration ago() {
        return JodaDateUtil.ago(this.createdDate);
    }

    /**
     * <p>이 댓글을 리소스 형식으로 반환한다.</p>
     *
     * <p>when: 이 리소스에 대해,
     * <ul>
     *     <li>접근권한이 있는지 검사할 때</li>
     *     <li>첨부파일을 첨부하거나 가져올 때</li>
     * </ul>
     * </p>
     *
     * @return {@link Resource}로서의 댓글
     */
    abstract public Resource asResource();

    /**
     * <p>이 댓글을 가지고 있는 이슈 혹은 게시글을 반환한다.</p>
     *
     * @return 이 댓글을 가지고 있는 이슈 혹은 게시글
     */
    abstract public AbstractPosting getParent();

    /**
     * <p>이 댓글을 작성한 저자를 설정한다.</p>
     *
     * <p>저자의 아이디, 로그인 아이디, 이름을 이 댓글에 저장해둔다. 아이디만 저장하지 않는 이유는, 저자가 탈퇴하여
     * 그 사용자에 대한 정보가 시스템에서 사라질 수도 있기 때문이다.</p>
     *
     * @param user 이 댓글을 작성한 저자
     */
    @Transient
    public void setAuthor(User user) {
        authorId = user.id;
        authorLoginId = user.loginId;
        authorName = user.name;
    }

    /**
     * <p>이 댓글을 저장한다.</p>
     *
     * <p>그 후 이 댓글을 갖고 게시글도 갱신한다.</p>
     */
    public void save() {
        super.save();
        getParent().update();
    }

    /**
     * <p>이 댓글을 삭제한다.</p>
     *
     * <p>동시에 모든 첨부파일을 삭제하고, 이 댓글을 갖고 게시글도 갱신한다.</p>
     */
    public void delete() {
        Attachment.deleteAll(asResource());
        super.delete();
        getParent().update();
    }
}
