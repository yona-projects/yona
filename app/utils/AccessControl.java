package utils;

import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.resource.GlobalResource;
import models.resource.Resource;

public class AccessControl {

    /**
     * Checks if an user has a permission to create a global resource.
     *
     * Currently it always returns true if the user is not anonymous.
     *
     * @param user
     * @return true if the user has the permission
     */
    public static boolean isGlobalResourceCreatable(User user) {
        return !user.isAnonymous();
    }

    /**
     * Checks if an user has a permission to create a resource of the given
     * type in the given project.
     *
     * 주의: 어떤 리소스의 저자이기 때문에 그 리소스에 속한 리소스를 생성할 수 있는지에 대한
     * 여부는 검사하지 않는다.
     *
     * @param user
     * @param project
     * @param resourceType
     * @return true if the user has the permission
     */
    public static boolean isProjectResourceCreatable(User user, Project project, ResourceType resourceType) {
        // Anonymous user cannot create anything.
        if (user == null || user.isAnonymous()) {
            return false;
        }

        // Site manager, Group admin, Project members can create anything.
        if (user.isSiteManager()
            || OrganizationUser.isAdmin(project.organization, user)
            || ProjectUser.isMember(user.id, project.id)) {
            return true;
        }

        // If the project is not public, nonmembers cannot create anything.
        if (!project.isPublic()) {
            return false;
        }

        // If the project is public, login users can create issues and posts.
        switch (resourceType) {
        case ISSUE_POST:
        case BOARD_POST:
        case ISSUE_COMMENT:
        case NONISSUE_COMMENT:
        case FORK:
        case COMMIT_COMMENT:
        case REVIEW_COMMENT:
            return true;
        default:
            return false;
        }
    }

    public static boolean isResourceCreatable(User user, Resource container, ResourceType resourceType) {
        if (isAllowedIfAuthor(user, container) || isAllowedIfAssignee(user, container)) {
            return true;
        }

        Project project = (container.getType() == ResourceType.PROJECT) ?
            Project.find.byId(Long.valueOf(container.getId())) : container.getProject();

        if (project == null) {
            return isGlobalResourceCreatable(user);
        } else {
            return isProjectResourceCreatable(user, project, resourceType);
        }
    }

    /**
     * Checks if an user has a permission to do the given operation to the given
     * resource.
     *
     * See docs/technical/access-control.md for more information.
     *
     *
     * @param user
     * @param resource
     * @param operation
     * @return true if the user has the permission
     * @see docs/technical/access-control.md
     */
    private static boolean isGlobalResourceAllowed(User user, GlobalResource resource,
                                                   Operation operation) {
        // Temporary attachments are allowed only for the user who uploads them.
        if (resource.getType() == ResourceType.ATTACHMENT
                && resource.getContainer().getType() == ResourceType.USER) {
            return user.id.toString().equals(resource.getContainer().getId());
        }

        if (operation == Operation.READ) {
            if (resource.getType() == ResourceType.PROJECT) {
                Project project = Project.find.byId(Long.valueOf(resource.getId()));
                if (project == null) {
                    return false;
                }
                if (project.isPublic()
                    || ProjectUser.isMember(user.id, project.id)
                    || OrganizationUser.isAdmin(project.organization, user)) {
                    return true;
                }
                if (project.isProtected()) {
                    return OrganizationUser.isMember(project.organization, user);
                }
                return false;
            }

            // anyone can read any resource which is not a project.
            return true;
        }

        if (operation == Operation.WATCH) {
            if (resource.getType() == ResourceType.PROJECT) {
                Project project = Project.find.byId(Long.valueOf(resource.getId()));
                if (project == null) {
                    return false;
                }
                if (project.isPublic()) {
                    return !user.isAnonymous();
                }
                if (ProjectUser.isMember(user.id, project.id)
                    || OrganizationUser.isAdmin(project.organization, user)) {
                    return true;
                }
                if (project.isProtected()) {
                    return OrganizationUser.isMember(project.organization, user);
                }
                return false;
            }
        }

        if (operation == Operation.LEAVE) {
            if (resource.getType() == ResourceType.PROJECT) {
                Project project = Project.find.byId(Long.valueOf(resource.getId()));
                return project != null && !project.isOwner(user) && ProjectUser.isMember(user.id, project.id);
            }
        }

        // UPDATE, DELETE
        switch(resource.getType()){
        case USER:
        case USER_AVATAR:
            return user.id.toString().equals(resource.getId());
        case PROJECT:
            if(ProjectUser.isManager(user.id, Long.valueOf(resource.getId()))) {
                return true;
            }
            // allow to admins of the group of the project.
            Project project = Project.find.byId(Long.valueOf(resource.getId()));
            if (project == null) {
                return false;
            }
            return OrganizationUser.isAdmin(project.organization, user);
        case ORGANIZATION:
            return OrganizationUser.isAdmin(Long.valueOf(resource.getId()), user.id);
        default:
            // undefined
            return false;
        }
    }

    /**
     * Checks if an user has a permission to do the given operation to the given
     * resource belongs to the given project.
     *
     * See docs/technical/access-control.md for more information.
     *
     * @param user
     * @param project
     * @param resource
     * @param operation
     * @return true if the user has the permission
     */
    private static boolean isProjectResourceAllowed(User user, Project project, Resource resource, Operation operation) {
        if (OrganizationUser.isAdmin(project.organization, user)) {
            return true;
        }

        if (user.isSiteManager()
                || ProjectUser.isManager(user.id, project.id)
                || isAllowedIfAuthor(user, resource)
                || isAllowedIfAssignee(user, resource)) {
            return true;
        }

        // If the resource is a project_transfer, only new owner can accept the request.
        if(resource.getType() == ResourceType.PROJECT_TRANSFER) {
            switch (operation) {
                case ACCEPT:
                    ProjectTransfer pt = ProjectTransfer.find.byId(Long.parseLong(resource.getId()));
                    User to = User.findByLoginId(pt.destination);
                    if(!to.isAnonymous()) {
                        return user.loginId.equals(pt.destination);
                    } else {
                        Organization receivingOrg = Organization.findByName(pt.destination);
                        return receivingOrg != null && OrganizationUser.isAdmin(receivingOrg.id, user.id);
                    }
                default:
                    return false;
            }
        }

        // Some resource's permission depends on their container.
        switch(resource.getType()) {
            case ISSUE_STATE:
            case ISSUE_ASSIGNEE:
            case ISSUE_MILESTONE:
            case ATTACHMENT:
                switch (operation) {
                    case READ:
                        return isAllowed(user, resource.getContainer(), Operation.READ);
                    case UPDATE:
                    case DELETE:
                        return isAllowed(user, resource.getContainer(), Operation.UPDATE);
                }
        }

        // Access Control for members, group members, nonmembers and anonymous.
        // - Anyone can read public project's resource.
        // - Group members can read protected projects' resource.
        // - Members can update anything and delete anything except code repository.
        // - Nonmember can update or delete a resource if only
        //     * the resource is not a code repository,
        //     * and the project to which the resource belongs is public.
        // See docs/technical/access-control.md for more information.
        switch(operation) {
        case READ:
            if (project.isPublic()) {
                return true;
            }
            if (ProjectUser.isMember(user.id, project.id)) {
                return true;
            }
            if (project.isProtected()) {
                return OrganizationUser.isMember(project.organization, user);
            }
            return false;
        case UPDATE:
            if (ProjectUser.isMember(user.id, project.id)) {
                return true;
            } else {
                return false;
            }
        case DELETE:
            if (resource.getType() == ResourceType.CODE) {
                return false;
            } else {
                return ProjectUser.isMember(user.id, project.id);
            }
        case ACCEPT:
        case CLOSE:
        case REOPEN:
            return ProjectUser.isMember(user.id, project.id);
        case WATCH:
            if (project.isPublic()) {
                return !user.isAnonymous();
            }
            if (ProjectUser.isMember(user.id, project.id)) {
                return true;
            }
            if (project.isProtected()) {
                return OrganizationUser.isMember(project.organization, user);
            }
            return false;
        default:
            // undefined
            return false;
        }

    }

    /**
     * Checks if an user has a permission to do the given operation to the given
     * resource.
     *
     * @param user
     * @param resource
     * @param operation
     * @return true if the user has the permission
     */
    public static boolean isAllowed(User user, Resource resource, Operation operation)
            throws IllegalStateException {
        if (user.isSiteManager()) {
            return true;
        }

        if (resource instanceof GlobalResource) {
            return isGlobalResourceAllowed(user, (GlobalResource) resource, operation);
        } else {
            Project project = resource.getProject();

            if (project == null) {
                throw new IllegalStateException("A project resource lost its project");
            }

            return isProjectResourceAllowed(user, project, resource, operation);
        }
    }

    /**
     * Checks if an user has a permission to do something to the given
     * resource as an author.
     *
     * Returns true if and only if these are all true:
     * - {@code resource} gives permission to read, modify and delete to its author.
     * - {@code user} is an author of the resource.
     *
     * @param user
     * @param resource
     * @return true if the user has the permission
     */
    private static boolean isAllowedIfAuthor(User user, Resource resource) {
        switch (resource.getType()) {
        case ISSUE_POST:
        case ISSUE_COMMENT:
        case NONISSUE_COMMENT:
        case BOARD_POST:
        case COMMIT_COMMENT:
        case COMMENT_THREAD:
        case REVIEW_COMMENT:
            return resource.isAuthoredBy(user);
        default:
            return false;
        }
    }

    /**
     * Checks if an user has a permission to do something to the given
     * resource as an assignee.
     *
     * Returns true if and only if these are all true:
     * - {@code resource} gives permission to read, modify and delete to its assignee.
     * - {@code user} is an assignee of the resource.
     *
     * @param user
     * @param resource
     * @return true if the user has the permission
     */
    private static boolean isAllowedIfAssignee(User user, Resource resource) {
        switch (resource.getType()) {
        case ISSUE_POST:
            Assignee assignee = Issue.finder.byId(Long.valueOf(resource.getId())).assignee;
            return assignee != null && assignee.user.id.equals(user.id);
        default:
            return false;
        }
    }
}
