package utils;

import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;

import models.resource.Resource;

/**
 * @author "Hwi Ahn"
 * @author "Yi EungJun"
 */
public class AccessControl {

    public static boolean isCreatable(User user, ResourceType resourceType) {
        // Only login users can create a resource.
        return !user.isAnonymous();
    }

    public static boolean isProjectResourceCreatable(User user, Project project, ResourceType resourceType) {
        if (user == null) return false;
        if (user.isSiteManager()) {
            return true;
        }

        if (ProjectUser.isMember(user.id, project.id)) {
            // Project members can create anything.
            return true;
        } else {
            // If the project is private, nonmembers cannot create anything.
            if (!project.isPublic) {
                return false;
            }

            // If the project is public, login users can create issues and posts.
            if (!user.isAnonymous()) {
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

    private static boolean isGlobalResourceAllowed(User user, Resource resource, Operation operation) {
        // Temporary attachments are allowed only for the user who uploads them.
        if (resource.getType() == ResourceType.ATTACHMENT
                && resource.getContainer().getType() == ResourceType.USER) {
            return user.id == resource.getContainer().getId();
        }

        if (operation == Operation.READ) {
            if (resource.getType() == ResourceType.PROJECT) {
                Project project = Project.find.byId(resource.getId());
                return project != null && (project.isPublic || ProjectUser.isMember(user.id, project.id));
            }

            // anyone can read any resource which is not a project.
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

    private static boolean isProjectResourceAllowed(User user, Project project, Resource resource, Operation operation) {
        if (ProjectUser.isManager(user.id, project.id)) {
            return true;
        }

        // If the resource is an attachment, the permission depends on its container.
        if (resource.getType() == ResourceType.ATTACHMENT) {
            switch(operation) {
                case READ:
                    return isAllowed(user, resource.getContainer(), Operation.READ);
                case UPDATE:
                case DELETE:
                    return isAllowed(user, resource.getContainer(), Operation.UPDATE);
            }
        }

        // Access Control for members, nonmembers and anonymous.
        // - Anyone can read public project's resource.
        // - Members can update anything and delete anything except code repository.
        // - Nonmember can update or delete a resource if only
        //     * the user is the author of the resource,
        //     * the resource is not a code repository,
        //     * and the project to which the resource belongs is public.
        // See docs/technical/access-control.md for more information.
        switch(operation) {
        case READ:
            return project.isPublic || ProjectUser.isMember(user.id, project.id);
        case UPDATE:
            if (ProjectUser.isMember(user.id, project.id)) {
                return true;
            }

            if (resource.getType() == ResourceType.CODE) {
                // Nonmember cannot update the repository.
                return false;
            } else {
                return project.isPublic && isEditableAsAuthor(user, project, resource);
            }
        case DELETE:
            if (resource.getType() == ResourceType.CODE) {
                return false;
            } else {
                return ProjectUser.isMember(user.id, project.id) ||
                        (project.isPublic && isEditableAsAuthor(user, project, resource));
            }
        default:
            // undefined
            return false;
        }

    }

    public static boolean isAllowed(User user, Resource resource, Operation operation) {
        if (user.isSiteManager()) {
            return true;
        }

        Project project = resource.getProject();

        if (project == null) {
            return isGlobalResourceAllowed(user, resource, operation);
        } else {
            return isProjectResourceAllowed(user, project, resource, operation);
        }
    }

    private static boolean isEditableAsAuthor(User user, Project project, Resource resource) {
        switch (resource.getType()) {
        case ISSUE_POST:
        case ISSUE_COMMENT:
        case NONISSUE_COMMENT:
        case BOARD_POST:
            return resource.getAuthorId() == user.id;
        default:
            return false;
        }
    }
}
