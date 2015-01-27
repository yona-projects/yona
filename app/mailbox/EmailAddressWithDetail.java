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

import org.apache.commons.lang3.StringUtils;
import play.Configuration;

import javax.annotation.Nonnull;

/**
 * An email address whose detail part is parsed.
 */
public class EmailAddressWithDetail {
    @Nonnull
    private final String user;

    @Nonnull
    private String detail;

    @Nonnull
    private final String domain;

    public EmailAddressWithDetail(@Nonnull String address) {
        int plus = address.indexOf('+');
        int at = address.indexOf('@');

        if (plus < 0) {
            user = address.substring(0, at);
            detail = "";
        } else {
            user = address.substring(0, plus);
            detail = address.substring(plus + 1, at);
        }

        domain = address.substring(at + 1);
    }

    /**
     * @return  whether this email address is to Yobi
     */
    public boolean isToYobi() {
        Configuration imapConfig = Configuration.root().getConfig("imap");
        String yobiAddr = imapConfig.getString("address", imapConfig.getString("user"));
        return this.equalsExceptDetails(new EmailAddressWithDetail(yobiAddr));
    }

    @Nonnull
    public String getUser() {
        return user;
    }

    @Nonnull
    public String getDetail() {
        return detail;
    }

    @Nonnull
    public String getDomain() {
        return domain;
    }

    /**
     * Set the detail part.
     *
     * @param detail
     */
    public void setDetail(@Nonnull String detail) {
        if (detail == null) {
            throw new IllegalArgumentException("detail must not be null");
        }
        this.detail = detail;
    }

    /**
     * @param that
     * @return  whether the given address has the same detail with this
     */
    public boolean equalsExceptDetails(@Nonnull EmailAddressWithDetail that) {
        return this.getUser().equals(that.getUser())
            && this.getDomain().equals(that.getDomain());
    }

    public String toString() {
        return StringUtils.join(new String[]{user, detail}, "+") + "@" + domain;
    }
}
