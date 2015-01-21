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


public enum State {
    ALL("all"), OPEN("open"), CLOSED("closed"), REJECTED("rejected"), CONFLICT("conflict"), RESOLVED("resolved"), MERGED("merged");
    private String state;

    State(String state) {
        this.state = state;
    }

    public String state() {
        return this.state;
    }

    public static State getValue(String value) {
        for (State issueState : State.values()) {
            if (issueState.state().equals(value)) {
                return issueState;
            }
        }
        return State.OPEN;
    }
}
