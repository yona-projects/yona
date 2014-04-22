/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public class GravatarUtilTest {

    @Test
    public void getAvatar() {
        // given
        String email = "whiteship2000@gmail.com";

        // when
        String result = GravatarUtil.getAvatar(email);

        // then
        assertThat(result).isEqualTo("http://www.gravatar.com/avatar/d3a3e1e76decd8760aaf9af6ab334264?s=80&d=http%3A%2F%2Fko.gravatar.com%2Fuserimage%2F53495145%2F0eaeeb47c620542ad089f17377298af6.png");
    }

    @Test
    public void getAvatarWithSize() {
        // given
        String email = "whiteship2000@gmail.com";
        int size = 40;

        // when
        String result = GravatarUtil.getAvatar(email, size);

        // then
        assertThat(result).contains("s=40");
    }

}
