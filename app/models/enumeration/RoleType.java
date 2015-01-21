/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
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
package models.enumeration;

public enum RoleType {
    MANAGER(1l), MEMBER(2l), SITEMANAGER(3l), ANONYMOUS(4l), GUEST(5l), ORG_ADMIN(6l), ORG_MEMBER(7l);

    private Long roleType;

    RoleType(Long roleType) {
        this.roleType = roleType;
    }

    public Long roleType() {
        return this.roleType;
    }

    public String getLowerCasedName(){
        return this.name().toLowerCase();
    }
}
