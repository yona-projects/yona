/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Hwi Ahn
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

public enum Operation {
    READ("read"), UPDATE("edit"), DELETE("delete"), ACCEPT("accept"), REOPEN("reopen"), CLOSE("close"), WATCH("watch"), LEAVE("leave"),
    // this operation means an action which assign an issue to him or her self.
    ASSIGN_ISSUE("assign_issue");

    private String operation;

    Operation(String operation) {
        this.operation = operation;
    }

    public String operation() {
        return this.operation;
    }

    public static Operation getValue(String value) {
        for (Operation operation : Operation.values()) {
            if (operation.operation().equals(value)) {
                return operation;
            }
        }
        return Operation.READ;
    }
}
