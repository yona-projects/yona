package controllers;

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.node.ObjectNode;

import models.Project;
import play.mvc.*;
import playRepository.RepositoryService;
import utils.Config;
import views.html.code.codeView;

public class CodeApp extends Controller {
	public static Result codeBrowser(String userName, String projectName) throws IOException {
        Project project = ProjectApp.getProject(userName, projectName);
        if (RepositoryService.VCS_GIT.equals(project.vcs)) {
            String msg = "Clone this Repository : git clone " + CodeApp.getURL(userName, projectName);
            return ok(codeView.render(msg, project));
        } else if (RepositoryService.VCS_SUBVERSION.equals(project.vcs)) {
            String msg = "Check out Repository : svn checkout " + CodeApp.getSvnURL(userName, projectName);
            return ok(codeView.render(msg, project));
        } else {
            return status(501, project.vcs + " is not supported!");
        }
    }

    public static Result ajaxRequest(String userName, String projectName, String path) throws Exception{
        ObjectNode findFileInfo =  RepositoryService.getMetaDataFrom(userName, projectName, path);
        if(findFileInfo != null) {
            return ok(findFileInfo);
        } else {
            return status(403);
        }
    }

    public static Result showRawFile(String userName, String projectName, String path) throws Exception{
        return ok(RepositoryService.getFileAsRaw(userName, projectName, path));
    }

    public static String getURL(String ownerName, String projectName) {
        return utils.Url.create(Arrays.asList(ownerName, projectName));
    }

    public static String getSvnURL(String ownerName, String projectName) {
        String[] pathSegments = { "svn", ownerName, projectName };
        return Config.getScheme("http") + "://" + Config.getHostport(request().host()) + "/"
                + StringUtils.join(pathSegments, "/");
    }
}
