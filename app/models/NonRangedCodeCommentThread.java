/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
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

import org.apache.commons.lang3.StringUtils;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * @author Keesun Baik
 */
@Entity
@DiscriminatorValue("non_ranged")
public class NonRangedCodeCommentThread extends CommentThread {

    private static final long serialVersionUID = -1L;

    public String prevCommitId = StringUtils.EMPTY;
    public String commitId;

    public boolean isOnChangesOfPullRequest() {
        return isOnPullRequest() && StringUtils.isNotEmpty(commitId);
    }

}
