/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Suwon Chae
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
import models.resource.GlobalResource;
import models.resource.Resource;
import play.i18n.Messages;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NullUser extends User {
    private static final long serialVersionUID = -1L;

    public NullUser(){
        this.id = -1l;
        this.name = Messages.get("user.notExists.name");
        this.loginId = "";
        this.email = "";
        this.createdDate = new Date();
    }

    public List<Project> myProjects(){
        return new ArrayList<>();
    }

    public boolean isAnonymous() {
        return true;
    }

    @Override
    public Resource asResource() {
        return new GlobalResource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public ResourceType getType() {
                return ResourceType.USER;
            }
        };
    }

    public boolean isSiteManager() {
        return false;
    }

    @Override
    public void visits(Project project) {
        // do nothing
    }
}
