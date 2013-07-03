package models;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Created with IntelliJ IDEA.
 * User: nori
 * Date: 13. 7. 10
 * Time: 오전 11:36
 * To change this template use File | Settings | File Templates.
 */
public class NotificationEventTest extends ModelTest<NotificationEvent> {

    @Test
    public void save() {
        NotificationEvent event = new NotificationEvent();
        event.save();
        assertThat(NotificationMail.find.byId(event.notificationMail.id)).isNotNull();
    }

    @Test
    public void saveTwoTimes() {
        NotificationEvent event = new NotificationEvent();
        event.save();
        int numOfMails = NotificationMail.find.all().size();
        event.save();
        assertThat(NotificationEvent.find.all().size()).isEqualTo(numOfMails);
    }

    @Test
    public void delete() {
        NotificationEvent event = new NotificationEvent();
        event.save();
        event.delete();
        assertThat(NotificationMail.find.byId(event.notificationMail.id)).isNull();
    }
}
