package models.resource;

import models.*;
import models.enumeration.ResourceType;
import play.db.ebean.Model;
import play.libs.F;
import play.mvc.QueryStringBindable;
import playRepository.Commit;
import playRepository.RepositoryService;

import java.util.EnumSet;
import java.util.Map;

public abstract class Resource {
    public static boolean exists(ResourceType type, String id) {
        Model.Finder<Long, ? extends Model> finder = null;

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
            case CODE_COMMENT:
                finder = CodeComment.find;
                break;
            case PULL_REQUEST:
                finder = PullRequest.finder;
                break;
            case SIMPLE_COMMENT:
                finder = SimpleComment.find;
                break;
            case COMMIT:
                try {
                    String[] pair = id.split(":");
                    Project project = Project.find.byId(Long.valueOf(pair[0]));
                    if (RepositoryService.getRepository(project).getCommit(pair[1]) != null) {
                        return true;
                    } else {
                        return false;
                    }
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
        Resource resource = null;

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
                resource = null;
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
            case CODE_COMMENT:
                resource = CodeComment.find.byId(longId).asResource();
                break;
            case PULL_REQUEST:
                return PullRequest.finder.byId(longId).asResource();
            case SIMPLE_COMMENT:
                return SimpleComment.find.byId(longId).asResource();
            case COMMIT:
                return Commit.getAsResource(resourceId);
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
}
