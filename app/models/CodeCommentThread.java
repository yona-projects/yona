package models;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * 커밋 뷰에서 생성되는 모든 쓰레드는 CodeCommentThread.
 *
 * @author Keesun Baik
 */
@Entity
@DiscriminatorValue("ranged")
public class CodeCommentThread extends CommentThread {
    private static final long serialVersionUID = 1L;

    public static final Finder<Long, CodeCommentThread> find = new Finder<>(Long.class, CodeCommentThread.class);

    @Embedded
    @Nullable
    public CodeRange codeRange = new CodeRange();

    public String prevCommitId = StringUtils.EMPTY;
    public String commitId;

    @ManyToMany
    public List<User> codeAuthors = new ArrayList<>();

    public boolean isCommitComment() {
        return ObjectUtils.equals(prevCommitId, StringUtils.EMPTY);
    }
}
