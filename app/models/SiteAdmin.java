/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun, Suwon Chae, Keeun Baik
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
