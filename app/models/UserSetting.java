/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models;

import io.ebean.Finder;
import io.ebean.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

@Entity
public class UserSetting extends Model {
    private static final long serialVersionUID = -2377215889638087516L;

    public static final Finder<Long, UserSetting> find = new Finder<>(UserSetting.class);

    @Id
    public Long id;

    @OneToOne
    public User user;

    public String loginDefaultPage;

    public UserSetting(User user) {
        this.user = user;
    }

    public static UserSetting findByUser(Long id){
        UserSetting userSetting = find.query().where().eq("user.id", id).findOne();
        if (userSetting == null) {
            userSetting = new UserSetting(User.find.byId(id));
        }
        return userSetting;
    }

}
