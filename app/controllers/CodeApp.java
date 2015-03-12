/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Ahn Hyeok Jun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package controllers;

import actions.DefaultProjectCheckAction;
import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import models.Project;
import models.enumeration.Operation;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.tmatesoft.svn.core.SVNException;
import play.mvc.*;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.ErrorViews;
import utils.FileUtil;
import utils.HttpUtil;
import views.html.code.nohead;
import views.html.code.nohead_svn;
import views.html.code.view;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

@AnonymousCheck
public class CodeApp extends Controller {
    public static String hostName;

    @IsAllowed(Operation.READ)
    public static Result codeBrowser(String userName, String projectName)
            throws IOException, UnsupportedOperationException, ServletException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        if (!RepositoryService.VCS_GIT.equals(project.vcs) && !RepositoryService.VCS_SUBVERSION.equals(project.vcs)) {
            return status(Http.Status.NOT_IMPLEMENTED, project.vcs + " is not supported!");
        }

        PlayRepository repository = RepositoryService.getRepository(project);

        if(repository.isEmpty()) {
            switch (project.vcs) {
                case RepositoryService.VCS_GIT:
                    return ok(nohead.render(project));
                case RepositoryService.VCS_SUBVERSION:
                    return ok(nohead_svn.render(project));
            }
        }

        String defaultBranch = project.defaultBranch();
        if (defaultBranch == null) {
            defaultBranch = "HEAD";
        } else if (defaultBranch.split("/").length >= 3) {
            defaultBranch = defaultBranch.split("/", 3)[2];
        }
        defaultBranch = URLEncoder.encode(defaultBranch, "UTF-8");
        return redirect(routes.CodeApp.codeBrowserWithBranch(userName, projectName, defaultBranch, ""));
    }

    @With(DefaultProjectCheckAction.class)
    public static Result codeBrowserWithBranch(String userName, String projectName, String branch, String path)
        throws UnsupportedOperationException, IOException, SVNException, GitAPIException, ServletException {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        if (!RepositoryService.VCS_GIT.equals(project.vcs) && !RepositoryService.VCS_SUBVERSION.equals(project.vcs)) {
            return status(Http.Status.NOT_IMPLEMENTED, project.vcs + " is not supported!");
        }

        branch = HttpUtil.decodePathSegment(branch);
        path = HttpUtil.decodePathSegment(path);

        PlayRepository repository = RepositoryService.getRepository(project);
        List<String> branches = repository.getRefNames();
        List<ObjectNode> recursiveData = RepositoryService.getMetaDataFromAncestorDirectories(
                repository, branch, path);

        if (recursiveData == null) {
            return notFound(ErrorViews.NotFound.render());
        }

        return ok(view.render(project, branches, recursiveData, branch, path));
    }

    @With(DefaultProjectCheckAction.class)
    public static Result ajaxRequest(String userName, String projectName, String path) throws Exception{
        PlayRepository repository = RepositoryService.getRepository(userName, projectName);
        path = HttpUtil.decodePathSegment(path);
        ObjectNode fileInfo = repository.getMetaDataFromPath(path);

        if(fileInfo != null) {
            return ok(fileInfo);
        } else {
            return notFound();
        }
    }

    @With(DefaultProjectCheckAction.class)
    public static Result ajaxRequestWithBranch(String userName, String projectName, String branch, String path)
            throws UnsupportedOperationException, IOException, SVNException, GitAPIException, ServletException{
        CodeApp.hostName = request().host();
        PlayRepository repository = RepositoryService.getRepository(userName, projectName);
        branch = HttpUtil.decodePathSegment(branch);
        path = HttpUtil.decodePathSegment(path);
        ObjectNode fileInfo = repository.getMetaDataFromPath(branch, path);

        if(fileInfo != null) {
            return ok(fileInfo);
        } else {
            return notFound();
        }
    }

    @With(DefaultProjectCheckAction.class)
    public static Result showRawFile(String userName, String projectName, String revision, String path) throws Exception{
        path = HttpUtil.decodePathSegment(path);
        revision = HttpUtil.decodePathSegment(revision);
        byte[] fileAsRaw = RepositoryService.getFileAsRaw(userName, projectName, revision, path);
        if(fileAsRaw == null){
            return redirect(routes.CodeApp.codeBrowserWithBranch(userName, projectName, revision, path));
        }

        MediaType mediaType = FileUtil.detectMediaType(fileAsRaw, FilenameUtils.getName(path));

        String mediaTypeString = "text/plain";
        String charset = FileUtil.getCharset(mediaType);
        if (charset != null) {
            mediaTypeString += "; charset=" + charset;
        }

        return ok(fileAsRaw).as(mediaTypeString);
    }

    @With(DefaultProjectCheckAction.class)
    public static Result showImageFile(String userName, String projectName, String revision, String path) throws Exception{
        revision = HttpUtil.decodePathSegment(revision);
        path = HttpUtil.decodePathSegment(path);
        final byte[] fileAsRaw = RepositoryService.getFileAsRaw(userName, projectName, revision, path);
        String mimeType = tika.detect(fileAsRaw);
        return ok(fileAsRaw).as(mimeType);
    }

    private static Tika tika = new Tika();

    public static String getURL(String ownerName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        return getURL(project);
    }

    public static String getURL(Project project) {
        if (project == null) {
            return null;
        } else if (RepositoryService.VCS_GIT.equals(project.vcs)) {
            return utils.Url.createWithContext(Arrays.asList(project.owner, project.name));
        } else if (RepositoryService.VCS_SUBVERSION.equals(project.vcs)) {
            return utils.Url.createWithContext(Arrays.asList("svn", project.owner, project.name));
        } else {
            return null;
        }
    }

    public static String getURLWithLoginId(Project project) {
        String url = getURL(project);
        if(url != null) {
            String loginId = session().get(UserApp.SESSION_LOGINID);
            if(loginId != null && !loginId.isEmpty()) {
                url = url.replace("://", "://" + loginId + "@");
            }
        }
        return url;
    }

    @IsAllowed(Operation.READ)
    public static Result openFile(String userName, String projectName, String revision,
                           String path) throws Exception{
        revision = HttpUtil.decodePathSegment(revision);
        path = HttpUtil.decodePathSegment(path);
        byte[] raw = RepositoryService.getFileAsRaw(userName, projectName, revision, path);

        if(raw == null){
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        return ok(raw).as(FileUtil.detectMediaType(raw, FilenameUtils.getName(path)).toString());
    }
}
