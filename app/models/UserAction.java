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
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.List;

@MappedSuperclass
abstract public class UserAction extends Model {
    private static final long serialVersionUID = 7150871138735757127L;
    @Id
    public Long id;

    @ManyToOne
    public User user;

    @Enumerated(EnumType.STRING)
    public models.enumeration.ResourceType resourceType;

    public String resourceId;

    public static <T extends UserAction> List<T> findBy(Finder<Long, T> finder,
                                             ResourceType resourceType, String resourceId) {
        return finder.where()
                .eq("resourceType", resourceType)
                .eq("resourceId", resourceId).findList();
    }

    public static <T extends UserAction> T findBy(Finder<Long, T> finder, User subject,
                                      ResourceType resourceType, String resourceId) {
        return finder.where()
                .eq("user.id", subject.id)
                .eq("resourceType", resourceType)
                .eq("resourceId", resourceId).findUnique();
    }

    public static <T extends UserAction> List<T> findBy(Finder<Long, T> finder, User subject,
                                                           ResourceType resourceType) {
        return finder.where()
                .eq("user.id", subject.id)
                .eq("resourceType", resourceType).findList();
    }

    public static <T extends UserAction> int countBy(Finder<Long, T> finder,
                                                        ResourceType resourceType, String resourceId) {
        return finder.where()
                .eq("resourceType", resourceType)
                .eq("resourceId", resourceId).findRowCount();
    }
}
