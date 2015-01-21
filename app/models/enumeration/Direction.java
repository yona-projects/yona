/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yoon
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

public enum Direction {

    ASC("asc"), DESC("desc");

    private String direction;

    Direction(String direction) {
        this.direction = direction;
    }

    public String direction() {
        return this.direction;
    }

    public static Direction getValue(String value) {
        for (Direction direction : Direction.values()) {
            if (direction.direction().equals(value)) {
                return direction;
            }
        }
        return Direction.ASC;
    }

}
