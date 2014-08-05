/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author kjkmadness
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
package playRepository;

import static org.fest.assertions.Assertions.assertThat;

import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

import models.*;
import org.junit.*;

public class CommitTest extends ModelTest<Commit> {

    @Before
    public void before() {
        for(Watch watch : Watch.find.all()) {
            watch.delete();
        }
        for(Unwatch unwatch : Unwatch.find.all()) {
            unwatch.delete();
        }
    }

    @Test
    public void getWatchers_no_watchers() {
        // Given
        Commit commit = createTestCommit("test", User.anonymous);

        // When
        Set<User> watchers = commit.getWatchers(getTestProject());

        // Then
        assertThat(watchers).isEmpty();
    }

    @Test
    public void getWatchers_with_author() {
        // Given
        User author = getTestUser();
        Commit commit = createTestCommit("test", author);

        // When
        Set<User> watchers = commit.getWatchers(getTestProject());

        // Then
        assertThat(watchers).containsOnly(author);
    }

    @Test
    public void getWatchers_with_commet_git() {
        // Given
        User author = getTestUser(2L);
        String commitId = "test";
        Commit commit = createTestCommit(commitId, author);

        User commentUser = getTestUser(3L);
        Project project = getTestProject();
        ReviewComment comment = new ReviewComment();
        CodeCommentThread thread = new CodeCommentThread();
        thread.project = project;
        thread.commitId = commitId;
        comment.thread = thread;
        comment.author = new UserIdent(commentUser);
        comment.save();

        // When
        Set<User> watchers = commit.getWatchers(project);

        // Then
        assertThat(watchers).containsOnly(author, commentUser);
        comment.delete();
    }

    @Test
    public void getWatchers_with_comment_svn() {
        // Given
        User author = getTestUser(4L);
        String commitId = "1";
        Commit commit = createTestCommit(commitId, author);

        User commentUser = getTestUser(3L);
        Project project = Project.find.byId(3l);
        CommitComment comment = new CommitComment();
        comment.project = project;
        comment.commitId = commitId;
        comment.setAuthor(commentUser);
        comment.save();

        // When
        Set<User> watchers = commit.getWatchers(project);

        // Then
        assertThat(watchers).containsOnly(author, commentUser);
    }

    @Test
    public void getWatchers_with_project_watcher() {
        // Given
        User author = getTestUser(2L);
        Commit commit = createTestCommit("test", author);

        User projectWatcher = getTestUser(3L);
        Project project = getTestProject();
        projectWatcher.addWatching(project);

        // When
        Set<User> watchers = commit.getWatchers(project);

        // Then
        assertThat(watchers).containsOnly(author, projectWatcher);
    }

    @Test
    public void getWatchers_explicitly_watch_unwatch() {
        // Given
        User author = getTestUser(2L);
        Commit commit = createTestCommit("test", author);
        Project project = getTestProject();

        User watcher = getTestUser(3L);
        Watch.watch(watcher, commit.asResource(project));

        User unwatcher = getTestUser(4L);
        unwatcher.addWatching(project);
        Watch.unwatch(unwatcher, commit.asResource(project));

        // When
        Set<User> watchers = commit.getWatchers(project);

        // Then
        assertThat(watchers).containsOnly(author, watcher);
    }

    @Test
    public void getWatchers_private_project() {
        // Given
        User author = getTestUser(1L);
        Commit commit = createTestCommit("test", author);
        Project project = getTestProject(5L);

        User watcher = getTestUser(2L);
        Watch.watch(watcher, commit.asResource(project));

        // When
        Set<User> watchers = commit.getWatchers(project);

        // Then
        assertThat(watchers).containsOnly(author);
    }

    private Commit createTestCommit(final String id, final User author) {
        return new Commit() {
            @Override
            public String getShortMessage() {
                return null;
            }

            @Override
            public String getShortId() {
                return null;
            }

            @Override
            public int getParentCount() {
                return 0;
            }

            @Override
            public String getMessage() {
                return null;
            }

            @Override
            public String getId() {
                return id;
            }

            @Override
            public TimeZone getCommitterTimezone() {
                return null;
            }

            @Override
            public String getCommitterName() {
                return null;
            }

            @Override
            public String getCommitterEmail() {
                return null;
            }

            @Override
            public Date getCommitterDate() {
                return null;
            }

            @Override
            public TimeZone getAuthorTimezone() {
                return null;
            }

            @Override
            public String getAuthorName() {
                return null;
            }

            @Override
            public String getAuthorEmail() {
                return null;
            }

            @Override
            public Date getAuthorDate() {
                return null;
            }

            @Override
            public User getAuthor() {
                return author;
            }
        };
    }
}
