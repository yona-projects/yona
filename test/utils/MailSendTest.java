/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Suwon Chae
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
package utils;

import info.schleichardt.play2.mailplugin.Mailer;
import org.apache.commons.mail.SimpleEmail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.test.FakeApplication;
import play.test.Helpers;


import static org.fest.assertions.Assertions.assertThat;

public class MailSendTest {
    protected static FakeApplication app;

    @Before
    public void startApp() {
        app = support.Helpers.makeTestApplication();
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
    public static final String SENDER_LOCALHOST = "yobiproject.mail@gmail.com";
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
