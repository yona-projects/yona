/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
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

import actors.ValidationEmailSender;
import org.apache.pekko.actor.Props;
import io.ebean.ExpressionList;
import controllers.routes;
import org.apache.commons.lang3.RandomStringUtils;
import play.data.validation.Constraints;
import io.ebean.Finder;
import io.ebean.Model;
import utils.Url;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import java.util.List;

@Entity
public class Email extends Model {

    private static final long serialVersionUID = 1L;

    public static final Finder<Long, Email> find = new Finder<>(Email.class);

    /**
     * pk
     */
    @Id
    public Long id;

    /**
     * owner
     */
    @ManyToOne
    public User user;

    /**
     * email value
     */
    @Constraints.Email
    @Constraints.Required
    public String email;

    /**
     * is validated email?
     */
    public boolean valid;

    public String token;

    @Transient
    public String confirmUrl;

    public static boolean exists(String newEmail, boolean valid) {
        ExpressionList<Email> el = findByEmailAndIsValid(newEmail, valid);

        if(valid) {
            Email uniqueValidatedEmail = el.findOne();
            return uniqueValidatedEmail != null;
        } else {
            List<Email> list = el.findList();
            return !list.isEmpty();
        }
    }

    public boolean validate(String token) {
        if(token.equals(this.token)) {
            this.valid = true;
            update();
            deleteOtherInvalidEmails(this.email);
            return true;
        } else {
            return false;
        }
    }

    public static void deleteOtherInvalidEmails(String emailAddress) {
        List<Email> invalidEmails = find.query().where().eq("email", emailAddress).eq("valid", false).findList();
        for(Email email : invalidEmails) {
            email.delete();
        }
    }

    public void sendValidationEmail() {
        this.token = RandomStringUtils.randomNumeric(50);
        this.confirmUrl = Url.create(routes.UserApp.confirmEmail(this.id, this.token).url());
        update();
        utils.AkkaUtil.system().actorOf(Props.create(ValidationEmailSender.class)).tell(this, null);
    }

    public static Email findByEmail(String email, boolean isValid) {
        return findByEmailAndIsValid(email, isValid).findOne();
    }

    private static ExpressionList<Email> findByEmailAndIsValid(String email, boolean isValid) {
        return find.query().where()
                    .eq("email", email)
                    .eq("valid", isValid);
    }
}
