package utils;

import java.util.List;

import models.Permission;
import models.Project;
import models.ProjectUser;
import models.Role;
import models.RolePermission;

/**
 * @author "Hwi Ahn"
 * 
 */
public class RoleCheck {
    public static boolean roleCheck(String userId, String operation,
            Long projectId) {
//        Role role = ProjectUser.findRoleByIds(Long.parseLong(userId), projectId);
//        List<Permission> permissions = RolePermission.findPermissionsByRole(role.id);
//        if(operation.equals("setting")){
//            
//        }
//        
//        if(Project.findById(projectId).share_option)
        
//        boolean returnValue = false;
//
//        if (Project.findById(projectId).share_option
//                && !permissionChecked.equals(PERMISSION_PROJ_SETTING))
//            return !returnValue;
//
//        ProjectUser projectUser = ProjectUser.findByIds(Long.parseLong(userId),
//                projectId);
//
//        if (projectUser == null)
//            return returnValue;
//        else {
//            Role role = ProjectUser.findRoleByIds(Long.parseLong(userId),
//                    projectId);
//            List<Permission> permissions = role.getPermissions();
//            for (Permission permission : permissions) {
//                if (permission.name.equals(permissionChecked))
//                    returnValue = true;
//            }
//        }
//
//        return returnValue;
        return true;
    }

    public static void roleGrant(Long userId, String roleGranted, Long projectId) {
//        ProjectUser projectUser = ProjectUser.findByIds(userId, projectId);
//        if (projectUser != null) {
//            Role role = Role.findById(projectUser.role.id);
//            if (!role.name.equals(roleGranted))
//                ProjectUser.update(userId, projectId,
//                        Role.findByName(roleGranted).id);
//        } else
//            ProjectUser.create(userId, projectId,
//                    Role.findByName(roleGranted).id);
    }
}
