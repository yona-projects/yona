/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park
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
package actions.support;

import org.junit.*;

import static org.fest.assertions.Assertions.*;


public class PathParserTest {

    private PathParser pathParser;

    @Test
    public void testPathParser() {
        String path = "/yobi/yobiProject/pullRequest/1";

        pathParser = new PathParser(path);

        assertThat(pathParser.getOwnerLoginId()).isEqualTo("yobi");
        assertThat(pathParser.getProjectName()).isEqualTo("yobiProject");
        assertThat(pathParser.getPathSegment(3)).isEqualTo("1");
    }

    @Test
    public void testPathParserIncludeContext() {
        String path = "/yobi/yobiProject/pullRequest/1";
        String context = "/test";

        pathParser = new PathParser(context, path);

        assertThat(pathParser.getOwnerLoginId()).isEqualTo("yobi");
        assertThat(pathParser.getProjectName()).isEqualTo("yobiProject");
        assertThat(pathParser.getPathSegment(3)).isEqualTo("1");

    }
}
