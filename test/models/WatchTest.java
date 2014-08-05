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
package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.test.FakeApplication;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.start;
import static play.test.Helpers.stop;

public class WatchTest extends ModelTest<Watch> {

    @Before
    public void setup() {
        for(Watch watch : Watch.find.all()) {
            watch.delete();
        }

        for(Unwatch unwatch : Unwatch.find.all()) {
            unwatch.delete();
        }
    }

    @Test
    public void watch() {
        // Given
        Resource resource = Issue.finder.byId(1L).asResource();
        User user = User.find.byId(1L);

        // When
        Watch.watch(user, resource);

        // Then
        assertThat(Watch.findBy(user, resource.getType(), resource.getId())).isNotNull();
        assertThat(Unwatch.findBy(user, resource.getType(), resource.getId())).isNull();
    }

    @Test
    public void unwatch() {
        // Given
        Resource resource = Issue.finder.byId(1L).asResource();
        User user = User.find.byId(1L);

        // When
        Watch.unwatch(user, resource);

        // Then
        assertThat(Watch.findBy(user, resource.getType(), resource.getId())).isNull();
        assertThat(Unwatch.findBy(user, resource.getType(), resource.getId())).isNotNull();
    }

    @Test
    public void findWatchers() {
        // Given
        Resource resource = Issue.finder.byId(1L).asResource();
        User watcher1 = User.find.byId(2L);
        User watcher2 = User.find.byId(3L);
        Watch.watch(watcher1, resource);
        Watch.watch(watcher2, resource);

        // When
        Set<User> watchers = Watch.findWatchers(resource);

        // Then
        assertThat(watchers).containsOnly(watcher1, watcher2);
    }

    @Test
    public void findUnwatchers() {
        // Given
        Resource resource = Issue.finder.byId(1L).asResource();
        User unwatcher1 = User.find.byId(2L);
        User unwatcher2 = User.find.byId(3L);
        Watch.unwatch(unwatcher1, resource);
        Watch.unwatch(unwatcher2, resource);

        // When
        Set<User> unwachers = Watch.findUnwatchers(resource);

        // Then
        assertThat(unwachers).containsOnly(unwatcher1, unwatcher2);
    }

    @Test
    public void findWatchedResourceIds() {
        // Given
        User user = User.find.byId(2L);
        Resource resource1 = Issue.finder.byId(1L).asResource();
        Resource resource2 = Issue.finder.byId(2L).asResource();
        Watch.watch(user, resource1);
        Watch.watch(user, resource2);

        // When
        List<String> watchedResourceIds = Watch.findWatchedResourceIds(user, ResourceType.ISSUE_POST);

        // Then
        assertThat(watchedResourceIds).containsOnly(resource1.getId(), resource2.getId());
    }

    @Test
    public void isWatching() {
        // Given
        User user1 = User.find.byId(2L);
        User user2 = User.find.byId(3L);
        Resource resource1 = Issue.finder.byId(1L).asResource();
        Resource resource2 = Issue.finder.byId(2L).asResource();
        Watch.watch(user1, resource1);
        Watch.watch(user2, resource2);

        // When
        // Then
        assertThat(Watch.isWatching(user1, resource1)).isTrue();
        assertThat(Watch.isWatching(user2, resource1)).isFalse();
        assertThat(Watch.isWatching(user1, resource2)).isFalse();
        assertThat(Watch.isWatching(user2, resource2)).isTrue();
    }

    @Test
    public void findActualWatchers() {
        // Given
        Resource resource_of_private_project = Issue.finder.byId(5L).asResource();

        User watch_issue_not_member = User.find.byId(2L);
        Watch.watch(watch_issue_not_member, resource_of_private_project);

        User watch_isssue = User.find.byId(3L);
        Watch.watch(watch_isssue, resource_of_private_project);

        User watch_project = User.find.byId(4L);
        Watch.watch(watch_project, resource_of_private_project.getProject().asResource());

        User watch_project_unwatch_issue = User.find.byId(5L);
        Watch.watch(watch_project_unwatch_issue, resource_of_private_project.getProject().asResource());
        Watch.unwatch(watch_project_unwatch_issue, resource_of_private_project);

        User base_watcher_not_member = User.find.byId(6L);
        Set<User> baseWatchers = new HashSet<>();
        baseWatchers.add(base_watcher_not_member);

        // When
        Set<User> actualWatchers = Watch.findActualWatchers(baseWatchers, resource_of_private_project);

        // Then
        assertThat(actualWatchers).containsOnly(watch_isssue, watch_project);
    }

}
