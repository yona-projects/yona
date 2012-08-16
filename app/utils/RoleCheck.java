package utils;

import play.db.ebean.Model;
import play.db.ebean.Model.Finder;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;

import models.Comment;
import models.Issue;
import models.IssueComment;
import models.Milestone;
import models.Permission;
import models.Post;
import models.Project;
import models.ProjectUser;
import models.Role;
import models.enumeration.Operation;
import models.enumeration.Resource;

/**
 * @author "Hwi Ahn"
 */
public class RoleCheck {

    public static boolean permissionCheck(Long userId, Long projectId,
            Resource resource, Operation operation) {
        return permissionCheck(userId, projectId, resource, operation, null);
    }

    public static boolean permissionCheck(Long userId, Long projectId,
            Resource resource, Operation operation, Long resourceId) {
       
        boolean isAuthorEditible;
        
        switch (resource) {
            case ISSUE_POST:
                isAuthorEditible = authorCheck(userId, resourceId,
                        new Finder<Long, Issue>(Long.class, Issue.class)) && Project.findById(projectId).isAuthorEditable;
                break;
            case ISSUE_COMMENT:
                isAuthorEditible = authorCheck(userId, resourceId,
                        new Finder<Long, IssueComment>(Long.class, IssueComment.class));
                break;
            case BOARD_POST:
                isAuthorEditible = authorCheck(userId, resourceId,
                        new Finder<Long, Post>(Long.class, Post.class));
                break;
            case BOARD_COMMENT:
                isAuthorEditible = authorCheck(userId, resourceId,
                        new Finder<Long, Comment>(Long.class, Comment.class));
                break;
            default:
                isAuthorEditible = false;
                break;
        }
        
        if (ProjectUser.isMember(userId, projectId)) {
            return isAuthorEditible || Permission.permissionCheck(userId, projectId, resource, operation);
        } else { // Anonymous
            if (!Project.findById(projectId).share_option)
                return false;
            return isAuthorEditible || Permission.permissionCheckByRole(Role.ANONYMOUS, resource,
                    operation);
        }
    }

    public static <T, K> boolean authorCheck(Long userId, Long resourceId,
            Model.Finder<K, T> finder) {
        int findRowCount = finder.where().eq("authorId", userId)
                .eq("id", resourceId).findRowCount();
        return (findRowCount != 0) ? true : false;
    }
}
