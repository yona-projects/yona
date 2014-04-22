/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Ahn Hyeok Jun
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

import com.avaje.ebean.annotation.Transactional;
import models.resource.Resource;
import models.resource.ResourceConvertible;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.joda.time.Duration;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.JodaDateUtil;

import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.util.Comparator;
import java.util.Date;

/**
 * 이슈 혹은 게시글의, 댓글
 */
@MappedSuperclass
abstract public class Comment extends Model implements TimelineItem, ResourceConvertible {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @Lob @Constraints.Required
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
    @Override
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
    @Transactional
    public void save() {
        super.save();
        updateMention();
        getParent().update();
    }

    protected void updateMention() {
        Mention.add(this.asResource(), NotificationEvent.getMentionedUsers(this.contents));
    }

    /**
     * <p>이 댓글을 삭제한다.</p>
     *
     * <p>동시에 모든 첨부파일을 삭제하고, 이 댓글을 갖고 게시글도 갱신한다.</p>
     */
    public void delete() {
        Attachment.deleteAll(asResource());
        NotificationEvent.deleteBy(this.asResource());
        super.delete();
        getParent().update();
    }

    public static Comparator<Comment> comparator(){
        return new Comparator<Comment>() {
            @Override
            public int compare(Comment o1, Comment o2) {
                return o1.createdDate.compareTo(o2.createdDate);
            }
        };
    }

    public Date getDate() {
        return createdDate;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        Comment rhs = (Comment) obj;
        return new EqualsBuilder()
                .appendSuper(super.equals(obj))
                .append(this.id, rhs.id)
                .append(this.contents, rhs.contents)
                .append(this.createdDate, rhs.createdDate)
                .append(this.authorId, rhs.authorId)
                .append(this.authorLoginId, rhs.authorLoginId)
                .append(this.authorName, rhs.authorName)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .appendSuper(super.hashCode())
                .append(id)
                .append(contents)
                .append(createdDate)
                .append(authorId)
                .append(authorLoginId)
                .append(authorName)
                .toHashCode();
    }
}
