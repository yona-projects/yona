package utils;

import models.Project;
import models.ProjectUser;
import models.User;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import models.resource.GlobalResource;
import models.resource.Resource;

public class AccessControl {

    /**
     * user가 주어진 resourceType을 생성할 수 있는지 여부를 반환한다.
     *
     * 현재는 Global Resource 중에서 Project를 생성할 때만 사용한다.
     *
     * 유저가 로그인하지 않았으면 생성권한이 없다고 판단한다.
     *
     * @param user
     * @return user가 해당 resourceType을 생성할 수 있는지 여부
     */
    public static boolean isGlobalResourceCreatable(User user) {
        return !user.isAnonymous();
    }

    /**
     * user가 해당 project에서 주어진 resourceType의 resource를 생성할 수 있는 여부를 반환한다.
     *
     * 자신이 프로젝트 멤버일 경우에는 프로젝트에 속하는 모든 리소스에 대한 생성권한을 갖고
     * 로그인 유저일 경우에는 이슈와 게시물에 한해서만 생성할 수 있다.
     *
     * 주의: 어떤 리소스의 저자이기 때문에 그 리소스에 속한 리소스를 생성할 수 있는지에 대한
     * 여부는 검사하지 않는다.
     *
     * @param user
     * @param project
     * @param resourceType
     * @return user가 해당 project에서 주어진 resourceType의 resource를 생성할 수 있는지 여부
     */

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

            return false;
        }
    }

    public static boolean isResourceCreatable(User user, Resource container, ResourceType resourceType) {
        if (isAllowedIfAuthor(user, container)) {
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
     * Global 리소스에 대해 주어진 리소스의 operation을 허용하는지 여부
     *
     * 임시 업로드 파일은 해당 파일을 업로드한 사용자만 접근할 수 있다.
     * 비공개 프로젝트는 해당 프로젝트의 멤버만 접근할 수 있다.
     * 공개 프로젝트는 모든 사용자가 접근할 수 있다.
     * 사용자 및 사용자의 아바타는 그 사용자 본인만 갱신 혹은 삭제할 수 있다.
     * 프로젝트는 그 프로젝트의 관리자만이 갱신 혹은 삭제할 수 있다.
     * 익명의 사용자는 지켜보기를 사용 할 수 없다. 비공개 프로젝트에서는 프로젝트 멤버만이 지켜보기를 사용 할 수 있다.
     *
     * @param user
     * @param resource
     * @param operation
     * @return
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
                return project != null && (project.isPublic || ProjectUser.isMember(user.id, project.id));
            }

            // anyone can read any resource which is not a project.
            return true;
        }

        if (operation == Operation.WATCH) {
            if (resource.getType() == ResourceType.PROJECT) {
                Project project = Project.find.byId(Long.valueOf(resource.getId()));
                return project != null && project.isPublic ? !user.isAnonymous() : ProjectUser.isMember(user.id, project.id);
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
            return ProjectUser.isManager(user.id, Long.valueOf(resource.getId()));
        default:
            // undefined
            return false;
        }
    }

    /**
     * {@code user}가 프로젝트 리소스인 {@code resource}에 {@code operation}을
     * 하는 것이 허용되는지의 여부를 반환한다.
     *
     * See docs/technical/access-control.md for more information.
     *
     * @param user
     * @param project
     * @param resource
     * @param operation
     * @return
     */
    private static boolean isProjectResourceAllowed(User user, Project project, Resource resource, Operation operation) {
        if (user.isSiteManager()
                || ProjectUser.isManager(user.id, project.id)
                || isAllowedIfAuthor(user, resource)) {
            return true;
        }

        // Some resource's permission depends on their container.
        switch(resource.getType()) {
            case ISSUE_STATE:
            case ISSUE_ASSIGNEE:
            case ISSUE_MILESTONE:
            case ATTACHMENT:
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
        //     * the resource is not a code repository,
        //     * and the project to which the resource belongs is public.
        // See docs/technical/access-control.md for more information.
        switch(operation) {
        case READ:
            return project.isPublic || ProjectUser.isMember(user.id, project.id);
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
            if (project.isPublic) {
                return !user.isAnonymous();
            } else {
                return ProjectUser.isMember(user.id, project.id);
            }
        default:
            // undefined
            return false;
        }

    }

    /**
     * {@code user}가 {@code resource}에 {@code operation}을 하는 것이
     * 허용되는지의 여부를 반환한다.
     *
     * @param user
     * @param resource
     * @param operation
     * @return {@code user}가 {@code resource}에 {@code operation}을
     *         하는 것이 허용되는지의 여부
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
     * {@code user}가 {@code resource}에 대해 저자로서의 읽기, 수정,
     * 삭제 권한을 갖는지의 여부를 반환한다.
     *
     * 다음의 두 조건이 모두 참인 경우에만 참을 반환한다.
     * - {@code resource}가 저자에게 읽기, 수정, 삭제 권한을
     *   부여하는 리소스인가
     * - {@code user}가 저자인가
     *
     * @param user
     * @param resource
     * @return {@code user}가 {@code resource}에 대해 저자로서의
    *          읽기, 수정, 삭제 권한을 갖는지의 여부
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
}
