/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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

import javax.persistence.Entity;
import java.util.List;

@Entity
public class Unwatch extends UserAction {
    private static final long serialVersionUID = 1L;

    public static final Finder<Long, Unwatch> find = new Finder<>(Long.class, Unwatch.class);

    public static List<Unwatch> findBy(ResourceType resourceType, String resourceId) {
        return findBy(find, resourceType, resourceId);
    }

    public static Unwatch findBy(User watcher, ResourceType resourceType, String resourceId) {
        return findBy(find, watcher, resourceType, resourceId);
    }

    public static List<Unwatch> findBy(User user, ResourceType resourceType) {
        return findBy(find, user, resourceType);
    }
}
