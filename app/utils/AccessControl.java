package utils;

import models.Comment;
import models.Issue;
import models.IssueComment;
import models.Permission;
import models.Post;
import models.Project;
import models.ProjectUser;
import models.Role;
import models.enumeration.Operation;
import models.enumeration.Resource;
import models.enumeration.RoleType;
import play.db.ebean.Model;
import play.db.ebean.Model.Finder;

/**
 * @author "Hwi Ahn"
 */
public class AccessControl {


    /**
     * 
     * @param userId
     * @param projectId
     * @param resource
     * @param operation
     * @param resourceId
     * @return
     */
    public static boolean isAllowed(Object userSessionId, Long projectId, Resource resource,
            Operation operation, Long resourceId) {
        Long userId;
        if(userSessionId instanceof String) {
            userId = Long.parseLong((String) userSessionId);
        } else {
            userId = (Long) userSessionId;
        }
        
        boolean isAuthorEditible;

        switch (resource)
            {
            case ISSUE_POST:
                isAuthorEditible = isAuthor(userId, resourceId, new Finder<Long, Issue>(
                        Long.class, Issue.class))
                        && Project.findById(projectId).isAuthorEditable;
                break;
            case ISSUE_COMMENT:
                isAuthorEditible = isAuthor(userId, resourceId, new Finder<Long, IssueComment>(
                        Long.class, IssueComment.class));
                break;
            case BOARD_POST:
                isAuthorEditible = isAuthor(userId, resourceId, new Finder<Long, Post>(
                        Long.class, Post.class));
                break;
            case BOARD_COMMENT:
                isAuthorEditible = isAuthor(userId, resourceId, new Finder<Long, Comment>(
                        Long.class, Comment.class));
                break;
            default:
                isAuthorEditible = false;
                break;
            }
        if (ProjectUser.isMember(userId, projectId)) {
            return isAuthorEditible
                    || Permission.hasPermission(userId, projectId, resource, operation);
        } else { // Anonymous
            if (!Project.findById(projectId).share_option) {
                return false;
            }
            return isAuthorEditible
                    || Permission.hasPermission(RoleType.ANONYMOUS, resource, operation);
      }
    }

    /**
     * 
     * @param userId
     * @param resourceId
     * @param finder
     * @return
     */
    public static <T, K> boolean isAuthor(Long userId, Long resourceId, Model.Finder<K, T> finder) {
        int findRowCount = finder.where().eq("authorId", userId).eq("id", resourceId)
                .findRowCount();
        return (findRowCount != 0) ? true : false;
    }
}
