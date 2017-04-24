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
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity
public class UserVerification extends Model {
    private static final long serialVersionUID = 7819377239127603471L;

    public static final Model.Finder<Long, UserVerification> find = new Finder<>(Long.class, UserVerification.class);

    @Id
    public Long id;

    @OneToOne
    public User user;

    public String loginId;

    public String verificationCode;

    public Long timestamp;

    public static synchronized UserVerification newVerification(User user) {
        UserVerification v = new UserVerification();
        v.user = user;
        v.loginId = user.loginId;
        v.verificationCode = UUID.randomUUID().toString();
        v.timestamp = new Date().getTime();
        v.save();
        return v;
    }

    public static UserVerification findbyUser(User user) {
        List<UserVerification> list = find.where().eq("user.id", user.id).findList();
        if (list != null && list.size() > 0) {
            return list.get(0);
        } else {
            return null;
        }
    }

    public static UserVerification findbyLoginIdAndVerificationCode(String loginId, String verificationCode) {
        List<UserVerification> list = find.where().eq("login_id", loginId)
                .eq("verificationCode", verificationCode).findList();
        if (list != null && list.size() > 0) {
            return list.get(0);
        } else {
            return null;
        }
    }

    public boolean isValidDate(){
        if( this.timestamp + 60*60*24*1000  > new Date().getTime()) {
            return true;
        } else {
            this.delete();
            play.Logger.error("Email validation is expired: " + this.loginId + ":" + this.verificationCode);
            return false;
        }
    }

    public void invalidate(){
        this.delete();
    }

    @Override
    public String toString() {
        return "UserVerification{" +
                "id=" + id +
                ", user=" + user +
                ", loginId='" + loginId + '\'' +
                ", verificationCode='" + verificationCode + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
