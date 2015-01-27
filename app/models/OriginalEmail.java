/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
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
import play.data.validation.Constraints;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"resource_type", "resource_id"}))
public class OriginalEmail extends Model {
    public static final Finder<Long, OriginalEmail> finder = new Finder<>(Long.class,
            OriginalEmail.class);

    private static final long serialVersionUID = 9079975193167733297L;

    @Id
    public Long id;

    @Constraints.Required
    @Column(unique=true)
    public String messageId;

    @Constraints.Required
    @Enumerated(EnumType.STRING)
    public ResourceType resourceType;

    @Constraints.Required
    public String resourceId;

    @Constraints.Required
    private Date handledDate;

    public static OriginalEmail findBy(Resource resource) {
        return finder.where()
                .eq("resourceType", resource.getType())
                .eq("resourceId", resource.getId())
                .findUnique();
    }

    public static boolean exists(Resource resource) {
        return findBy(resource) != null;
    }

    public OriginalEmail(String messageId, Resource resource) {
        this.messageId = messageId;
        this.resourceType = resource.getType();
        this.resourceId = resource.getId();
    }

    @Override
    public void save() {
        handledDate = new Date();
        super.save();
    }

    public Date getHandledDate() {
        return handledDate;
    }
}
