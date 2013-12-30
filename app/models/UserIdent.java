package models;

import javax.persistence.Embeddable;

/**
 * 삭제된 유저에 대비하여
 *
 * @author Keesun Baik
 */
@Embeddable
public class UserIdent {

    public Long id;
    public String loginId;
    public String name;

    public UserIdent(User author) {
        id = author.id;
        loginId = author.loginId;
        name = author.name;
    }
}
