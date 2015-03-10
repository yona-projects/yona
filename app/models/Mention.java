/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
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
import models.resource.Resource;
import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.util.Set;

@Entity
public class Mention extends Model {
    private static final long serialVersionUID = 5803239458057753468L;

    public static final Finder<Long, Mention> find = new Finder<>(Long.class, Mention.class);

    @Id
    public Long id;

    public ResourceType resourceType;

    public String resourceId;

    @ManyToOne
    public User user;

    /**
     * Store the list of mentioned users.
     *
     * Yobi keeps the list of mentioned users to find them quickly. Every resource mentioning
     * users MUST add them by using this method. If not some features like "Issues Mentioning
     * You" ignore the mention.
     *
     * @param resource the resource mentioning the users
     * @param mentionedUsers the users mentioned by the resource
     */
    public static void update(Resource resource, Set<User> mentionedUsers) {
        for (Mention mention : find.where().eq("resourceType", resource.getType()).eq("resourceId",
                resource.getId()).findList()) {
            if (mentionedUsers.contains(mention.user)) {
                mentionedUsers.remove(mention.user);
            } else {
                mention.delete();
            }
        }

        for (User user : mentionedUsers) {
            Mention mention = new Mention();
            mention.resourceId = resource.getId();
            mention.resourceType = resource.getType();
            mention.user = user;
            mention.save();
        }
    }
}
