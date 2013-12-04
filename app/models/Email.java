/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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
import akka.actor.Props;
import com.avaje.ebean.ExpressionList;
import controllers.routes;
import org.apache.commons.lang3.RandomStringUtils;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.libs.Akka;
import utils.Url;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;
import java.util.List;

@Entity
public class Email extends Model {

    private static final long serialVersionUID = 1L;

    public static final Finder<Long, Email> find = new Finder<>(Long.class, Email.class);

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

    /**
     * 이메일 검증에 사용할 토큰
     */
    public String token;

    @Transient
    public String confirmUrl;

    /**
     * 검증된 메일로 확인된 메일은 한개만 있을 수 있고
     * 검증된 메일로 확인되지 않은 메일은 여러개가 있을 수 있다.
     *
     * @param newEmail
     * @param valid 검증된 이메일중에 있는지 확인하고 싶을 떄는 true 검증되지 않은 이메일 중에서 확인하고 싶을 때는 false
     * @return
     */
    public static boolean exists(String newEmail, boolean valid) {
        ExpressionList<Email> el = findByEmailAndIsValid(newEmail, valid);

        if(valid) {
            Email uniqueValidatedEmail = el.findUnique();
            return uniqueValidatedEmail != null;
        } else {
            List<Email> list = el.findList();
            return !list.isEmpty();
        }
    }

    /**
     * {@code token}이 유효한지 확인하고 유효한 값이면 동일한 이메일로 검증되지 않은 이메일을 삭제한다.
     *
     * @param token
     * @return 유효한 토큰으로 검증됐으면 true 아니면 false
     */
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

    /**
     * {@code emailAddress}로 등록되어있는 보조 이메일 중에서 검증되지 않은 이메일을 삭제한다.
     *
     * when: 새로운 유저가 {@code emailAddress}로 가입했을때 또는 해당 이메일로 검증된 보조 이메일이 생성됐을 때 사용한다.
     *
     * @param emailAddress
     */
    public static void deleteOtherInvalidEmails(String emailAddress) {
        List<Email> invalidEmails = find.where().eq("email", emailAddress).eq("valid", false).findList();
        for(Email email : invalidEmails) {
            email.delete();
        }
    }

    /**
     * 확인 메일 보내는 작업 때문에 응답이 늦어질 수 있기 때문에 {@link ValidationEmailSender}로 위임한다.
     */
    public void sendValidationEmail() {
        this.token = RandomStringUtils.randomNumeric(50);
        this.confirmUrl = Url.create(routes.UserApp.confirmEmail(this.id, this.token).url());
        update();
        Akka.system().actorOf(new Props(ValidationEmailSender.class)).tell(this, null);
    }

    public static Email findByEmail(String email, boolean isValid) {
        return findByEmailAndIsValid(email, isValid).findUnique();
    }

    private static ExpressionList<Email> findByEmailAndIsValid(String email, boolean isValid) {
        return find.where()
                    .eq("email", email)
                    .eq("valid", isValid);
    }
}
