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
import models.*;
import org.apache.commons.lang.ArrayUtils;

import javax.mail.*;
import javax.mail.internet.*;

public class IMAPMessageUtil {
    public static User extractSender(Message msg) throws MessagingException {
        for (Address addr : msg.getFrom()) {
            User user = User.findByEmail(((InternetAddress)addr).getAddress());
            if (!user.equals(User.anonymous)) {
                return user;
            }
        }

        return User.anonymous;
    }

    public static String asString(IMAPMessage msg) throws MessagingException {
        return String.format("{Subject: %s, From: %s, To: %s}",
                msg.getSubject(),
                ArrayUtils.toString(msg.getFrom()),
                ArrayUtils.toString(msg.getAllRecipients()));
    }

    public static String getIdLeftFromMessageId(String messageId) {
        int start = messageId.indexOf('<');
        int at = messageId.indexOf('@');
        return messageId.substring(start + 1, at).trim().replaceFirst("^/", "");
    }
}
