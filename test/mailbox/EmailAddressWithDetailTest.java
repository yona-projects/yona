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

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public class EmailAddressWithDetailTest {

    @Test
    public void email() {
        EmailAddressWithDetail addr1 = new EmailAddressWithDetail("test@mail.com");
        EmailAddressWithDetail addr2 = new EmailAddressWithDetail("test+1234@mail.com");

        assertThat(addr1.getUser()).describedAs("user part").isEqualTo("test");
        assertThat(addr1.getDomain()).describedAs("domain part").isEqualTo("mail.com");
        assertThat(addr1.getDetail()).describedAs("detail part").isEmpty();
        assertThat(addr2.getUser()).describedAs("user part").isEqualTo("test");
        assertThat(addr2.getDomain()).describedAs("domain part").isEqualTo("mail.com");
        assertThat(addr2.getDetail()).describedAs("detail part").isEqualTo("1234");
        assertThat(addr1.equalsExceptDetails(addr2)).describedAs("test@mail.com equals " +
                "test+1234@mail.com except its details").isTrue();
    }
}
