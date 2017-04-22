/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models;

import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;

@Entity
public class UserSetting extends Model {
    private static final long serialVersionUID = -2377215889638087516L;

    public static final Model.Finder<Long, UserSetting> find = new Finder<>(Long.class, UserSetting.class);

    @Id
    public Long id;

    @OneToOne
    public User user;

    public String loginDefaultPage;

    public UserSetting(User user) {
        this.user = user;
    }

    public static UserSetting findByUser(Long id){
        UserSetting userSetting = find.where().eq("user.id", id).findUnique();
        if (userSetting == null) {
            userSetting = new UserSetting(User.find.byId(id));
        }
        return userSetting;
    }

}
