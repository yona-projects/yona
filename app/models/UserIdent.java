package models;

import javax.persistence.Embeddable;

/**
 * 삭제된 유저에 대비하여
 *
 * @author Keesun Baik
 */
@Embeddable
public class UserIdent {

    public Long authorId;
    public String authorLoginId;
    public String authorName;

}
