/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik, kjkmadness
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
package models;

import org.junit.*;

import utils.WatchService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.fest.assertions.Assertions.assertThat;

public class PullRequestTest extends ModelTest<PullRequest> {
    @Test
    public void addIssueEvent() {
        // Given
        Pattern issuePattern = Pattern.compile("#\\d+");

        // When
        Matcher m = issuePattern.matcher("blah blah #12, sdl #13 sldkfjsd");

        // Then
        List<String> numberTexts = new ArrayList<>();
        while(m.find()) {
            numberTexts.add(m.group());
        }
        assertThat(numberTexts.size()).isEqualTo(2);
    }

    @Test
    public void getWatchers_default() {
        // Given
        PullRequest pullRequest = PullRequest.findById(1L);

        // When
        Set<User> watchers = pullRequest.getWatchers();

        // Then
        assertThat(watchers).containsOnly(pullRequest.contributor);
    }

    @Test
    public void getWatchers_with_comments() {
        // Given
        PullRequest pullRequest = PullRequest.findById(1L);
        User commentUser = getTestUser();
        PullRequestComment comment = new PullRequestComment();
        comment.pullRequest = pullRequest;
        comment.authorInfos(commentUser);
        comment.save();

        // When
        Set<User> watchers = pullRequest.getWatchers();

        // Then
        assertThat(watchers).containsOnly(pullRequest.contributor, commentUser);
    }

    @Test
    public void getWatchers_with_project_watcher() {
        // Given
        PullRequest pullRequest = PullRequest.findById(1L);
        User projectWatcher = getTestUser();
        projectWatcher.addWatching(pullRequest.toProject);

        // When
        Set<User> watchers = pullRequest.getWatchers();

        // Then
        assertThat(watchers).containsOnly(pullRequest.contributor, projectWatcher);
    }

    @Test
    public void getWatchers_explicitly_watch_unwatch() {
        // Given
        PullRequest pullRequest = PullRequest.findById(1L);
        User watcher = getTestUser(2L);
        WatchService.watch(watcher, pullRequest.asResource());

        User unwatcher = getTestUser(3L);
        unwatcher.addWatching(pullRequest.toProject);
        WatchService.unwatch(unwatcher, pullRequest.asResource());

        // When
        Set<User> watchers = pullRequest.getWatchers();

        // Then
        assertThat(watchers).containsOnly(pullRequest.contributor, watcher);
    }

    @Test
    public void getWatchers_private_project() {
        // Given
        PullRequest pullRequest = PullRequest.findById(2L);
        User watcher = getTestUser();
        WatchService.watch(watcher, pullRequest.asResource());

        // When
        Set<User> watchers = pullRequest.getWatchers();

        // Then
        assertThat(watchers).containsOnly(pullRequest.contributor);
    }
}
