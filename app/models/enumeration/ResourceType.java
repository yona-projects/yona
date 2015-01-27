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

import play.api.i18n.Lang;
import play.i18n.Messages;

public enum ResourceType {
    ISSUE_POST("issue_post"),
    ISSUE_ASSIGNEE("issue_assignee"),
    ISSUE_STATE("issue_state"),
    ISSUE_CATEGORY("issue_category"),
    ISSUE_MILESTONE("issue_milestone"),

    ISSUE_LABEL("issue_label"),
    BOARD_POST("board_post"),
    BOARD_CATEGORY("board_category"),
    BOARD_NOTICE("board_notice"),
    CODE("code"),
    MILESTONE("milestone"),
    WIKI_PAGE("wiki_page"),
    PROJECT_SETTING("project_setting"),
    SITE_SETTING("site_setting"),
    USER("user"),
    USER_AVATAR("user_avatar"),
    PROJECT("project"),
    ATTACHMENT("attachment"),
    ISSUE_COMMENT("issue_comment"),
    NONISSUE_COMMENT("nonissue_comment"),
    LABEL("label"),
    PROJECT_LABELS("project_labels"),
    FORK("fork"),
    COMMIT_COMMENT("code_comment"),
    PULL_REQUEST("pull_request"),
    COMMIT("commit"),
    COMMENT_THREAD("comment_thread"),
    REVIEW_COMMENT("review_comment"),
    ORGANIZATION("organization"),
    PROJECT_TRANSFER("project_transfer"),
    ISSUE_LABEL_CATEGORY("issue_label_category"),
    NOT_A_RESOURCE("");

    private String resource;

    ResourceType(String resource) {
        this.resource = resource;
    }

    public String resource() {
        return this.resource;
    }

    public static ResourceType getValue(String value) {
        for (ResourceType resourceType : ResourceType.values()) {
            if (resourceType.resource().equals(value)) {
                return resourceType;
            }
        }
        throw new IllegalArgumentException("No matching resource type found for [" + value + "]");
    }

    public String asPathSegment() {
        switch(this) {
            case ISSUE_POST:
                return "issue";
            case BOARD_POST:
                return "post";
            case COMMENT_THREAD:
                return "review";
            case COMMIT:
                return "commit";
            default:
                return this.resource;
        }
    }

    public String getName(Lang lang) {
        return Messages.get(lang, "resource." + resource);
    }
}
