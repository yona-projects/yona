package utils;

import java.util.List;

import models.Permission;
import models.Project;
import models.ProjectUser;
import models.Role;

/**
 * @author "Hwi Ahn"
 * 
 */
public class RoleCheck {
    public static String PERMISSION_BOARD = "board";
    public static String PERMISSION_ISSUE = "issue";
    public static String PERMISSION_MILESTONE = "milestone";
    public static String PERMISSION_PROJ_SETTING = "project.setting";
    public static String PERMISSION_SITE_SETTING = "site.setting";
    public static String PERMISSION_CODE = "code";    
    
    public static boolean roleCheck(String userId, String permissionChecked,
            String projectId) {
        boolean returnValue = false;
        
        if (Project.findById(Long.parseLong(projectId)).share_option
                && !permissionChecked.equals(PERMISSION_PROJ_SETTING))
            return !returnValue;
        
        ProjectUser projectUser = ProjectUser.findByIds(Long.parseLong(userId),
                Long.parseLong(projectId));
        
        if (projectUser == null)
            return returnValue;
        else {
            Role role = ProjectUser.findRoleByIds(Long.parseLong(userId),
                    Long.parseLong(projectId));
            List<Permission> permissions = role.getPermissions();
            for(Permission permission : permissions) {
                if(permission.name.equals(permissionChecked)) returnValue = true;
            }
        }
        
        return returnValue;
    }

    public static void roleGrant(Long userId, String roleGranted, Long projectId) {
        ProjectUser projectUser = ProjectUser.findByIds(userId, projectId);
        if (projectUser != null) {
            Role role = Role.findById(projectUser.role.id);
            if (!role.name.equals(roleGranted))
                ProjectUser.update(userId, projectId,
                        Role.findByName(roleGranted).id);
        } else
            ProjectUser.create(userId, projectId,
                    Role.findByName(roleGranted).id);
    }
}
