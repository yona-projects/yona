/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
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
package models.resource;

import actions.support.PathParser;
import models.*;
import models.enumeration.ResourceType;
import play.db.ebean.Model;
import playRepository.Commit;
import playRepository.RepositoryService;
import utils.Config;

import java.util.EnumSet;

public abstract class Resource {
    public static boolean exists(ResourceType type, String id) {
        Model.Finder<Long, ? extends Model> finder;

        switch(type) {
            case ISSUE_POST:
                finder = Issue.finder;
                break;
            case ISSUE_ASSIGNEE:
                finder = Assignee.finder;
                break;
            case ISSUE_COMMENT:
                finder = IssueComment.find;
                break;
            case NONISSUE_COMMENT:
                finder = PostingComment.find;
                break;
            case LABEL:
                finder = Label.find;
                break;
            case BOARD_POST:
                finder = Posting.finder;
                break;
            case USER:
                finder = User.find;
                break;
            case PROJECT:
                finder = Project.find;
                break;
            case ATTACHMENT:
                finder = Attachment.find;
                break;
            case MILESTONE:
                finder = Milestone.find;
                break;
            case COMMIT_COMMENT:
                finder = CommitComment.find;
                break;
            case PULL_REQUEST:
                finder = PullRequest.finder;
                break;
            case REVIEW_COMMENT:
                finder = ReviewComment.find;
                break;
            case ORGANIZATION:
                finder = Organization.find;
                break;
            case COMMIT:
                try {
                    String[] pair = id.split(":");
                    Project project = Project.find.byId(Long.valueOf(pair[0]));
                    return RepositoryService.getRepository(project).getCommit(pair[1]) != null;
                } catch (Exception e) {
                    play.Logger.error("Failed to determine whether the commit exists", e);
                    return false;
                }
            case COMMENT_THREAD:
                finder = CommentThread.find;
                break;
            default:
                throw new IllegalArgumentException(getInvalidResourceTypeMessage(type));
        }

        return finder.byId(Long.valueOf(id)) != null;
    }

    public static String getInvalidResourceTypeMessage(ResourceType resourceType) {
        if (EnumSet.allOf(ResourceType.class).contains(resourceType)) {
            return "Unsupported resource type " + resourceType;
        } else {
            return "Unknown resource type " + resourceType;
        }
    }

    public static Resource get(ResourceType resourceType, String resourceId) {
        Resource resource;

        if (resourceType.equals(ResourceType.COMMIT)) {
            return Commit.getAsResource(resourceId);
        }

        Long longId = Long.valueOf(resourceId);

        switch(resourceType) {
            case ISSUE_POST:
                resource = Issue.finder.byId(longId).asResource();
                break;
            case ISSUE_COMMENT:
                resource = IssueComment.find.byId(longId).asResource();
                break;
            case NONISSUE_COMMENT:
                resource = PostingComment.find.byId(longId).asResource();
                break;
            case LABEL:
                resource = Label.find.byId(longId).asResource();
                break;
            case BOARD_POST:
                resource = Posting.finder.byId(longId).asResource();
                break;
            case USER:
                resource = User.find.byId(longId).asResource();
                break;
            case PROJECT:
                resource = Project.find.byId(longId).asResource();
                break;
            case ATTACHMENT:
                resource = Attachment.find.byId(longId).asResource();
                break;
            case MILESTONE:
                resource = Milestone.find.byId(longId).asResource();
                break;
            case COMMIT_COMMENT:
                resource = CommitComment.find.byId(longId).asResource();
                break;
            case PULL_REQUEST:
                return PullRequest.finder.byId(longId).asResource();
            case USER_AVATAR:
                return User.find.byId(longId).avatarAsResource();
            case REVIEW_COMMENT:
                return ReviewComment.find.byId(longId).asResource();
            case ORGANIZATION:
                resource = Organization.find.byId(longId).asResource();
                break;
            case COMMENT_THREAD:
                return CommentThread.find.byId(longId).asResource();
            default:
                throw new IllegalArgumentException(getInvalidResourceTypeMessage(resourceType));
        }

        return resource;
    }

    public ResourceParam asParameter() {
        return ResourceParam.get(this);
    }

    abstract public String getId();
    abstract public Project getProject();
    abstract public ResourceType getType();
    public Resource getContainer() { return null; }
    public Long getAuthorId() { return null; }
    public boolean isAuthoredBy(User user) { return getAuthorId() != null && getAuthorId().equals(user.id); }
    public void delete() { throw new UnsupportedOperationException(); }

    public String getDetail() {
        Project project = getProject();
        String path = getPath();

        if (project != null && path != null) {
            return project + "/" + path;
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;

        Resource that = (Resource) object;

        if (!getId().equals(that.getId())) return false;
        if (!getType().equals(that.getType())) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + (getId() != null ? getId().hashCode() : 0);
        result = 31 * result + (getType() != null ? getType().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return getPath();
    }

    private String getPath() {
        return getType().resource() + "/" + getId();
    }

    /**
     * @see {@link actions.IsAllowedAction}
     */
    public static ResourceConvertible getResourceObject(PathParser parser, Project project, ResourceType resourceType) {
        switch (resourceType) {
            case PROJECT:
                return project;
            case MILESTONE:
                return Milestone.findById(Long.parseLong(parser.getPathSegment(3)));
            case BOARD_POST:
                return Posting.findByNumber(project, Long.parseLong(parser.getPathSegment(3)));
            case ISSUE_POST:
                return Issue.findByNumber(project, Long.parseLong(parser.getPathSegment(3)));
            case ISSUE_LABEL:
                return IssueLabel.finder.byId(Long.parseLong(parser.getPathSegment(4)));
            case PULL_REQUEST:
                return PullRequest.findOne(project, Long.parseLong(parser.getPathSegment(3)));
            case COMMIT_COMMENT:
                return CommitComment.find.byId(Long.parseLong(parser.getPathSegment(5)));
            case ISSUE_LABEL_CATEGORY:
                return IssueLabelCategory.find.byId(Long.parseLong(parser.getPathSegment(5)));
            default:
                throw new IllegalAccessError(getInvalidResourceTypeMessage(resourceType));
        }
    }

    /**
     * Finds a resource by the given resource path.
     *
     * The format of resource path is as follows:
     *
     *     resource-type "/" resource-id
     *
     * @param path
     * @return
     */
    public static Resource findByPath(String path) {
        String[] segments = path.split("/");

        if (segments.length < 2) {
            return null;
        }

        ResourceType resourceType;

        try {
            resourceType = ResourceType.getValue(segments[0]);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return Resource.get(resourceType, segments[1]);
    }

    public String getMessageId() {
        return String.format("<%s@%s>",
                this, Config.getHostname());
    }
}
