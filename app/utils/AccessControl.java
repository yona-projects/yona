package utils;

import models.Comment;
import models.Issue;
import models.IssueComment;
import models.Post;
import models.Project;
import models.ProjectUser;
import models.User;
import models.enumeration.Operation;
import models.enumeration.Resource;
import models.resource.GlobalResource;
import models.resource.ProjectResource;

import play.db.ebean.Model;
import play.db.ebean.Model.Finder;

/**
 * @author "Hwi Ahn"
 * @author "Yi EungJun"
 */
public class AccessControl {

    public static boolean isCreatable(User user, Resource resourceType) {
        // Only login users can create a resource.
        return !user.isAnonymous();
    }

    public static boolean isCreatable(User user, Project project, Resource resourceType) {
        if (user.isSiteManager()) {
            return true;
        }

        if (ProjectUser.isMember(user.id, project.id)) {
            // Project members can create anything.
            return true;
        } else {
            // If the project is private, nonmembers cannot create anything.
            if (!project.share_option) {
                return false;
            }

            // If the project is public, login users can create issues and posts.
            if (project.share_option && !user.isAnonymous()) {
                switch(resourceType){
                case ISSUE_POST:
                case BOARD_POST:
                    return true;
                default:
                    return false;
                }
            }

            return false;
        }
    }

    public static boolean isAllowed(User user, GlobalResource resource, Operation operation) {
        if (user.isSiteManager()) {
            return true;
        }

        if (operation.equals(Operation.READ)) {
            if (resource.getType().equals(Resource.PROJECT)) {
                Project project = Project.find.byId(resource.getId());
                return project.share_option || ProjectUser.isMember(user.id, project.id);
            }

            // anyone can read all resources which doesn't belong to a project.
            return true;
        }

        // UPDATE, DELETE
        switch(resource.getType()){
        case USER:
        case USER_AVATAR:
            return user.id == resource.getId();
        case PROJECT:
            return ProjectUser.isManager(user.id, resource.getId());
        default:
            // undefined
            return false;
        }
    }

    public static boolean isAllowed(User user, ProjectResource resource, Operation operation) {
        if (user.isSiteManager()) {
            return true;
        }

        Project project = resource.getProject();

        if (ProjectUser.isManager(user.id, project.id)) {
            return true;
        }

        switch(operation) {
        case READ:
            // If the project is public anyone can read its resources else only members can do that.
            return project.share_option || ProjectUser.isMember(user.id, project.id);
        case UPDATE:
            // Any members can update repository
            if (resource.getType() == Resource.CODE) {
                return ProjectUser.isMember(user.id, project.id);
            } else {
                return isEditableAsAuthor(user, project, resource);
            }
        case DELETE:
            return isEditableAsAuthor(user, project, resource);
        default:
            // undefined
            return false;
        }
    }

    private static boolean isEditableAsAuthor(User user, Project project, ProjectResource resource) {
        // author can update or delete her/his own article.
        if (!project.isAuthorEditable) {
            return false;
        }

        Finder<Long, ?> finder;

        switch (resource.getType()) {
        case ISSUE_POST:
            finder = new Finder<Long, Issue>(Long.class, Issue.class);
            break;
        case ISSUE_COMMENT:
            finder = new Finder<Long, IssueComment>(Long.class, IssueComment.class);
            break;
        case BOARD_POST:
            finder = new Finder<Long, Post>(Long.class, Post.class);
            break;
        case BOARD_COMMENT:
            finder = new Finder<Long, Comment>(Long.class, Comment.class);
            break;
        default:
            return false;
        }

        return isAuthor(user.id, resource.getId(), finder);
    }

	/**
	 *
	 * @param userId
	 * @param resourceId
	 * @param finder
	 * @return
	 */
	public static <T, K> boolean isAuthor(Long userId, Long resourceId,
			Model.Finder<K, T> finder) {
		int findRowCount = finder.where().eq("authorId", userId)
				.eq("id", resourceId).findRowCount();
		return (findRowCount != 0) ? true : false;
	}
}