/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
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
package mailbox;

import com.sun.mail.imap.IMAPMessage;
import models.ModelTest;
import models.User;
import org.junit.Test;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IMAPMessageUtilTest extends ModelTest<User> {
    @Test
    public void extractSender() throws MessagingException {
        // given
        final User expected = User.find.byId(1l);
        Message message = mock(IMAPMessage.class);
        when(message.getFrom()).thenReturn(new InternetAddress[]{
                new InternetAddress(expected.email)
        });

        // when
        User actual = IMAPMessageUtil.extractSender(message);

        // then
        assertThat(expected.id).isEqualTo(actual.id);
    }

    @Test
    public void extractSender_Anonymous() throws MessagingException {
        // given
        Message message = mock(IMAPMessage.class);
        when(message.getFrom()).thenReturn(new InternetAddress[]{
                new InternetAddress("nouser@mail.com")
        });

        // when
        User actual = IMAPMessageUtil.extractSender(message);

        // then
        assertThat(actual.isAnonymous()).isTrue();
    }
}
