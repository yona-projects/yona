package models;

import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;

/**
 * Created with IntelliJ IDEA.
 * User: nori
 * Date: 13. 7. 10
 * Time: 오전 11:19
 * To change this template use File | Settings | File Templates.
 */
@Entity
public class NotificationMail extends Model {
    @Id
    public Long id;

    @OneToOne
    public NotificationEvent notificationEvent;

    public static Finder<Long, NotificationMail> find = new Finder<>(Long.class,
            NotificationMail.class);
}
