package models;

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

    @Embedded
    public CodeRange codeRange;

    public String commitId;

    @ManyToMany
    public List<User> codeAuthors;

}
