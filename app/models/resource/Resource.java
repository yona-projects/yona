package models.resource;

import actions.support.PathParser;
import models.*;
import models.enumeration.ResourceType;
import play.db.ebean.Model;
import playRepository.Commit;
import playRepository.RepositoryService;

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
            case PULL_REQUEST_COMMENT:
                finder = PullRequestComment.find;
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
            case PULL_REQUEST_COMMENT:
                return PullRequestComment.find.byId(longId).asResource();
            case USER_AVATAR:
                return User.find.byId(longId).avatarAsResource();
            case ORGANIZATION:
                resource = Organization.find.byId(longId).asResource();
                break;
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
    public void delete() { throw new UnsupportedOperationException(); }

    /**
     * {@code parser}와 {@code project} 정보에서 {@code resourceType}에 해당하는 리소스를 찾는다.
     * - 해당하는 리소스가 있을 때는 {@link models.resource.ResourceConvertible} 타입 객체를 리턴한다.
     * - 해당하는 리소스가 없을 때는 리턴한 레퍼런스가 null일 수도 있다.
     *
     * @param parser
     * @param resourceType
     * @param project
     * @return
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
            default:
                throw new IllegalAccessError(getInvalidResourceTypeMessage(resourceType));
        }
    }

}
