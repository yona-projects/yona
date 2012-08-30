package playRepository;

import java.util.HashMap;
import java.util.Map;

import controllers.GitApp;
import controllers.SvnApp;

public class RepositoryService {
    public static final String VCS_SUBVERSION = "Subversion";
    public static final String VCS_GIT = "GIT";

    public static Map<String, String> vcsTypes() {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put(VCS_GIT, "project.new.vcsType.git");
        map.put(VCS_SUBVERSION, "project.new.vcsType.subversion");
        return map;
    }

    public static void createRepository(String userName, String projectName, String type) throws Exception {
        if (type.equals(RepositoryService.VCS_GIT)) {
            GitApp.createRepository(userName, projectName);
        } else if (type.equals(RepositoryService.VCS_SUBVERSION)) {
            SvnApp.createRepository(userName, projectName);
        } else {
            throw new UnsupportedOperationException("only support git & svn!");
        }
    }
    public static void deleteRepository(String userName, String projectName, String type) throws Exception {
        if (type.equals(RepositoryService.VCS_GIT)) {
            GitApp.deleteRepository(userName, projectName);
        } else if (type.equals(RepositoryService.VCS_SUBVERSION)) {
            SvnApp.deleteRepository(userName, projectName);
        } else {
            throw new UnsupportedOperationException("only support git & svn!");
        }
    }
}