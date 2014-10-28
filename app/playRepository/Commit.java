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
package playRepository;

import models.*;
import models.enumeration.ResourceType;
import models.resource.Resource;

import java.util.*;

public abstract class Commit {

    public abstract String getShortId();
    public abstract String getId();
    public abstract String getShortMessage();
    public abstract String getMessage();
    public abstract User getAuthor();
    public abstract String getAuthorName();
    public abstract String getAuthorEmail();
    public abstract Date getAuthorDate();
    public abstract TimeZone getAuthorTimezone();
    public abstract String getCommitterName();
    public abstract String getCommitterEmail();
    public abstract Date getCommitterDate();
    public abstract TimeZone getCommitterTimezone();
    public abstract int getParentCount();

    public Set<User> getWatchers(Project project) {
        Set<User> actualWatchers = new HashSet<>();

        // Add the author
        if (!getAuthor().isAnonymous()) {
            actualWatchers.add(getAuthor());
        }

        // Add every user who comments on this commit
        if (project.vcs.equals(RepositoryService.VCS_GIT)) {
            List<CommentThread> threads = CommentThread.find.where()
                    .eq("project.id", project.id)
                    .eq("commitId", getId())
                    .eq("pullRequest.id", null).findList();
            for (CommentThread thread : threads) {
                for (ReviewComment comment : thread.reviewComments) {
                    User user = User.find.byId(comment.author.id);
                    if (user != null) {
                        actualWatchers.add(user);
                    }
                }
            }
        } else {
            List<CommitComment> comments = CommitComment.find.where()
                    .eq("project.id", project.id).eq("commitId", getId()).findList();
            for (CommitComment c : comments) {
                User user = User.find.byId(c.authorId);
                if (user != null) {
                    actualWatchers.add(user);
                }
            }
        }

        return Watch.findActualWatchers(actualWatchers, asResource(project));
    }

    public static Project getProjectFromResourceId(String resourceId) {
        String[] pair = resourceId.split(":");
        return Project.find.byId(Long.valueOf(pair[0]));
    }

    public static Resource getAsResource(Project project, String commitId) {
        return getAsResource(project.id + ":" + commitId);
    }

    public static Resource getAsResource(final String resourceId) {
        return new Resource() {

            @Override
            public String getId() {
                return resourceId;
            }

            @Override
            public Project getProject() {
                return getProjectFromResourceId(resourceId);
            }

            @Override
            public ResourceType getType() {
                return ResourceType.COMMIT;
            }
        };
    }

    public Resource asResource(final Project project) {
        return new Resource() {

            @Override
            public String getId() {
                return project.id + ":" + Commit.this.getId();
            }

            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.COMMIT;
            }

            @Override
            public Long getAuthorId() {
                User author = getAuthor();

                if (author != null) {
                    return getAuthor().id;
                } else {
                    return null;
                }
            }
        };
    }
}
