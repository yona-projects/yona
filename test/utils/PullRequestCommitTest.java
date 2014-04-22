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

public class PullRequestCommitTest {

    @Test
    public void isValid() {
        String url = "http://localhost:9000/admin/meme2/pullRequest/897/commit/c098727fc0c16ab7637435e56a82c8580b7a0f5f";
        assertThat(PullRequestCommit.isValid(url)).isTrue();

        url = "http://localhost:9000/admin/meme2/pullRequest/897";
        assertThat(PullRequestCommit.isValid(url)).isFalse();
    }

    @Test
    public void create(){
        String url = "http://localhost:9000/admin/meme2/pullRequest/897/commit/c098727fc0c16ab7637435e56a82c8580b7a0f5f";
        PullRequestCommit prc = new PullRequestCommit(url);
        assertThat(prc.getProjectName()).isEqualTo("meme2");
        assertThat(prc.getProjectOwner()).isEqualTo("admin");
        assertThat(prc.getPullRequestNumber()).isEqualTo(897);
        assertThat(prc.getCommitId()).isEqualTo("c098727fc0c16ab7637435e56a82c8580b7a0f5f");
    }
}
