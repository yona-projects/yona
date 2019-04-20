/**
 * Yobi, Project Hosting SW
 * <p>
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models.enumeration;

public enum UserState {
    ACTIVE("ACTIVE"), LOCKED("LOCKED"), DELETED("DELETED"), GUEST("GUEST"), SITE_ADMIN("SITE_ADMIN");

    private String state;

    UserState(String state) {
        this.state = state;
    }

    public String state() {
        return this.state;
    }

    public static UserState of(String value) {
        for (UserState userState : UserState.values()) {
            if (userState.state().equalsIgnoreCase(value)) {
                return userState;
            }
        }
        return null;
    }
}
