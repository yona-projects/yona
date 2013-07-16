package utils;

import controllers.routes;
import info.schleichardt.play2.mailplugin.Mailer;
import junit.framework.Assert;
import models.ModelTest;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import play.test.FakeApplication;
import play.test.Helpers;

import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.callAction;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;

/**
 * User: doortts
 * Date: 4/2/13
 * Time: 4:49 PM
 */
public class MailSendTest {
    protected static FakeApplication app;

    @Before
    public void startApp() {
        app = Helpers.fakeApplication(support.Config.makeTestConfig());
        Helpers.start(app);
    }

    @After
    public void stopApp() {
        Helpers.stop(app);
    }

    public static final String DEFAULT_TEXT_MESSAGE = "Dear recipient,\n\n\n" +
            "this is the mailtext.\n\nsincerely sender\n\n" +
            "테스트 메일입니다. 답장을 보내지 마세요. :)";
    public static final String DEFAULT_HTML_MESSAGE = "<html><body style='background: red'><p>Hello</p></body></html>";
    public static final String SENDER_LOCALHOST = "hiveproject.mail@gmail.com";
    public static final String RECIPIENT_LOCALHOST = "nforge@nhn.com";
    public static final String SUBJECT = "메일 발송 테스트입니다 - the subject of the email";
    public static final String ATTACHEMENT_TXT_FILE_NAME = "the-text-file.txt";


    @Test
    public void testSendSimpleMail() throws Exception {
        //Given
        SimpleEmail email = new SimpleEmail();
        email.setFrom(SENDER_LOCALHOST);
        email.setSubject(SUBJECT);
        email.addTo(RECIPIENT_LOCALHOST);
        email.setMsg(DEFAULT_TEXT_MESSAGE);
        email.setCharset("utf-8");

        //When
        Mailer.send(email);

        //Then
        assertThat(Mailer.history().get(0)).isEqualTo(email);
    }
}
