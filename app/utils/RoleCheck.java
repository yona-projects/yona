package utils;

import models.ProjectUser;
import models.Role;

/**
 * @author "Hwi Ahn"
 * 
 */
public class RoleCheck {
    public static boolean roleCheck(String userId, String roleToBeChecked,
            String projectId) {
        // ProjectUser projectUser =
        // ProjectUser.findById(Long.parseLong(userId),
        // Long.parseLong(projectId));
        // Project project = Project.findById(Long.parseLong(projectId));
        // if(project.share_option) return true;

        return false;
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
