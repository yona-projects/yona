/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author BlueMir
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
package utils;

public enum MenuType {
    SITE_HOME(1), NEW_PROJECT(2), PROJECTS(3), HELP(4), SITE_SETTING(5), USER(6),
    PROJECT_HOME(100), BOARD(101), CODE(102), ISSUE(103), TASK(104), PROJECT_SETTING(105), MILESTONE(106), PULL_REQUEST(107), PROJECT_REVIEW(108), NONE(0);

    private int type;

    private MenuType(int type) {
        this.type = type;
    }
}
