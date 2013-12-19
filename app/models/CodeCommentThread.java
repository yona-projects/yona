package models;

import org.apache.commons.lang.StringUtils;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import java.util.List;

/**
 * 커밋 뷰에서 생성되는 모든 쓰레드는 CodeCommentThread.
 *
 * @author Keesun Baik
 */
@Entity
@DiscriminatorValue("ranged")
public class CodeCommentThread extends CommentThread {
    public static final Finder<Long, CodeCommentThread> find = new Finder<>(Long.class, CodeCommentThread.class);

    @Embedded
    public CodeRange codeRange;

    public String prevCommitId = StringUtils.EMPTY;
    public String commitId;

    @ManyToMany
    public List<User> codeAuthors;

}
