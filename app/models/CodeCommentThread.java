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

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Repository;

import javax.persistence.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static models.CodeRange.Side;
import static models.CodeRange.Side.A;
import static models.CodeRange.Side.B;

/**
 * @author Keesun Baik
 */
@Entity
@DiscriminatorValue("ranged")
public class CodeCommentThread extends CommentThread {
    private static final long serialVersionUID = 1L;

    public static final Finder<Long, CodeCommentThread> find = new Finder<>(Long.class, CodeCommentThread.class);

    @Embedded
    public CodeRange codeRange = new CodeRange();

    public String prevCommitId = StringUtils.EMPTY;
    public String commitId;

    @Transient
    private Boolean _isOutdated;

    @ManyToMany(cascade = CascadeType.ALL)
    public List<User> codeAuthors = new ArrayList<>();

    public boolean isCommitComment() {
        return ObjectUtils.equals(prevCommitId, StringUtils.EMPTY);
    }

    private String unexpectedSideMessage(Side side) {
        return String.format("Expected '%s' or '%s', but '%s'", A, B, side);
    }

    public boolean isOnChangesOfPullRequest() {
        return isOnPullRequest() && StringUtils.isNotEmpty(commitId);
    }

    public boolean isOnAllChangesOfPullRequest() {
        return isOnChangesOfPullRequest() && StringUtils.isNotEmpty(prevCommitId);
    }

    public boolean isOutdated() throws IOException, GitAPIException {
        if (codeRange.startLine == null || prevCommitId == null || commitId == null) {
            return false;
        }

        // cache
        if (_isOutdated != null) {
            return _isOutdated;
        }

        if (!isOnPullRequest()) {
            return false;
        }

        if (pullRequest.mergedCommitIdFrom == null || pullRequest.mergedCommitIdTo == null) {
            return false;
        }

        if (isCommitComment()) {
            return PullRequestCommit.getByCommitId(pullRequest, commitId) == null;
        }

        String path = codeRange.path;
        if (path.length() > 0 && path.charAt(0) == '/') {
            path = path.substring(1);
        }

        Repository repository = pullRequest.getRepository();

        try {
            if (StringUtils.isNotEmpty(prevCommitId)) {
                _isOutdated = !PullRequest.noChangesBetween(repository,
                    pullRequest.mergedCommitIdFrom, repository, prevCommitId, path);
            }

            if (_isOutdated) {
                return _isOutdated;
            }

            _isOutdated = !PullRequest.noChangesBetween(repository,
                pullRequest.mergedCommitIdTo, repository, commitId, path);
        } catch (MissingObjectException e) {
            play.Logger.warn("Possible false positive of outdated detection because of missing git object: " + e.getMessage());
            return true;
        }

        return _isOutdated;
    }
}
