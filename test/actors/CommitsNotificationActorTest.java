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
package actors;

import controllers.routes;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Keesun Baik
 */
public class CommitsNotificationActorTest {

    @Test
    public void branchLinkUrlTest() throws UnsupportedEncodingException {
        // Given
        String ownerLoginid = "whiteship";
        String proejctName = "yobi";
        String branchName = "refs/heads/feature/review";

        // When
        String url = routes.CodeApp.codeBrowserWithBranch(ownerLoginid, proejctName, branchName, "").url();

        // Then
        assertThat(url).isEqualTo("/whiteship/yobi/code/refs%2Fheads%2Ffeature%2Freview");
    }
}
