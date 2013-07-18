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
    public void add() {
        NotificationEvent event = new NotificationEvent();
        NotificationEvent.add(event);
        assertThat(NotificationMail.find.byId(event.notificationMail.id)).isNotNull();
    }

    @Test
    public void addTwoTimes() {
        NotificationEvent event = new NotificationEvent();
        NotificationEvent.add(event);
        int numOfMails = NotificationMail.find.all().size();
        NotificationEvent.add(event);
        assertThat(NotificationEvent.find.all().size()).isEqualTo(numOfMails);
    }

    @Test
    public void delete() {
        NotificationEvent event = new NotificationEvent();
        NotificationEvent.add(event);
        event.delete();
        assertThat(NotificationMail.find.byId(event.notificationMail.id)).isNull();
    }
}
