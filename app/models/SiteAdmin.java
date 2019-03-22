/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

package models;

import controllers.UserApp;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import java.util.Arrays;

@Entity
public class SiteAdmin extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @OneToOne
    public User admin;
    public static final String SITEADMIN_DEFAULT_LOGINID = "admin";

    public static final Model.Finder<Long, SiteAdmin> find = new Finder<>(Long.class, SiteAdmin.class);

    public static boolean exists(User user) {
        return user != null && find.where().eq("admin.id", user.id).findRowCount() > 0;
    }

    public static SiteAdmin findByUserLoginId(String userLoginId) {
        return find.where().eq("admin.loginId", userLoginId).findUnique();
    }

    public static User updateDefaultSiteAdmin(User user) {
        RandomNumberGenerator rng = new SecureRandomNumberGenerator();
        String passwordSalt = Arrays.toString(rng.nextBytes().getBytes());

        User defaultSiteAdmin = User.findByLoginId(SITEADMIN_DEFAULT_LOGINID);
        defaultSiteAdmin.name = user.name;
        defaultSiteAdmin.email = user.email;
        defaultSiteAdmin.passwordSalt = passwordSalt;
        defaultSiteAdmin.password = UserApp.hashedPassword(user.password, passwordSalt);
        defaultSiteAdmin.update();

        return defaultSiteAdmin;
    }
}
