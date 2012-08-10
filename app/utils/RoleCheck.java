package utils;

import models.Permission;
import models.Project;
import models.enumeration.PermissionOperation;
import models.enumeration.PermissionResource;

/**
 * @author "Hwi Ahn"
 */
public class RoleCheck {
    /**
     * 해당 유저가 해당 프로젝트에서 해당 리소스에 대하여 해당 오퍼레이션을 행할 수 있는지 여부를 boolean 값으로 반환합니다.
     *
     * @param userId
     * @param projectId
     * @param resource
     * @param operation
     * @return
     */
    public static boolean roleCheck(String userId, Long projectId,
                                    PermissionResource resource, PermissionOperation operation) {
        if (Project.findById(projectId).share_option
            && operation.equals(PermissionOperation.READ))
            return true;

        return Permission.permissionCheck(Long.parseLong(userId), projectId,
            resource, operation);
    }

    public static boolean roleCheck(Long userId, Long projectId,
                                    PermissionResource resource, PermissionOperation operation) {
        if (Project.findById(projectId).share_option
            && operation.equals(PermissionOperation.READ))
            return true;

        return Permission.permissionCheck(userId, projectId,
            resource, operation);
    }
}
