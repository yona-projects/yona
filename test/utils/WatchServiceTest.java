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
package utils;

import static org.fest.assertions.Assertions.*;
import static play.test.Helpers.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.Issue;
import models.Unwatch;
import models.User;
import models.Watch;
import models.enumeration.ResourceType;
import models.resource.Resource;

import org.junit.*;

import play.test.*;

public class WatchServiceTest {
    private FakeApplication application;

    @Before
    public void setUp() {
        application = fakeApplication(inMemoryDatabase());
        start(application);
    }

    @After
    public void tearDown() {
        stop(application);
    }

    @Test
    public void watch() {
        // Given
        Resource resource = Issue.finder.byId(1L).asResource();
        User user = User.find.byId(1L);

        // When
        WatchService.watch(user, resource);

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
        WatchService.unwatch(user, resource);

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
        WatchService.watch(watcher1, resource);
        WatchService.watch(watcher2, resource);

        // When
        Set<User> watchers = WatchService.findWatchers(resource);

        // Then
        assertThat(watchers).containsOnly(watcher1, watcher2);
    }

    @Test
    public void findUnwatchers() {
        // Given
        Resource resource = Issue.finder.byId(1L).asResource();
        User unwatcher1 = User.find.byId(2L);
        User unwatcher2 = User.find.byId(3L);
        WatchService.unwatch(unwatcher1, resource);
        WatchService.unwatch(unwatcher2, resource);

        // When
        Set<User> unwachers = WatchService.findUnwatchers(resource);

        // Then
        assertThat(unwachers).containsOnly(unwatcher1, unwatcher2);
    }

    @Test
    public void findWatchedResourceIds() {
        // Given
        User user = User.find.byId(2L);
        Resource resource1 = Issue.finder.byId(1L).asResource();
        Resource resource2 = Issue.finder.byId(2L).asResource();
        WatchService.watch(user, resource1);
        WatchService.watch(user, resource2);

        // When
        List<String> watchedResourceIds = WatchService.findWatchedResourceIds(user, ResourceType.ISSUE_POST);

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
        WatchService.watch(user1, resource1);
        WatchService.watch(user2, resource2);

        // When
        // Then
        assertThat(WatchService.isWatching(user1, resource1)).isTrue();
        assertThat(WatchService.isWatching(user2, resource1)).isFalse();
        assertThat(WatchService.isWatching(user1, resource2)).isFalse();
        assertThat(WatchService.isWatching(user2, resource2)).isTrue();
    }
}
