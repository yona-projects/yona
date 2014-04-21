/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Ahn Hyeok Jun
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
import controllers.annotation.IsAllowed;
import models.Project;
import models.enumeration.Operation;
import org.apache.tika.Tika;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.tmatesoft.svn.core.SVNException;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.ErrorViews;
import views.html.code.nohead;
import views.html.code.nohead_svn;
import views.html.code.view;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CodeApp extends Controller {
    public static String hostName;

    /**
     * 기본 코드 브라우저 표시
     *
     * @param userName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     */
    @IsAllowed(Operation.READ)
    public static Result codeBrowser(String userName, String projectName)
            throws IOException, UnsupportedOperationException, ServletException {
        Project project = ProjectApp.getProject(userName, projectName);

        if (!RepositoryService.VCS_GIT.equals(project.vcs) && !RepositoryService.VCS_SUBVERSION.equals(project.vcs)) {
            return status(Http.Status.NOT_IMPLEMENTED, project.vcs + " is not supported!");
        }

        PlayRepository repository = RepositoryService.getRepository(project);

        // GIT 저장소이면서 브랜치가 하나도 없는 경우 NOHEAD 안내문 표시
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

    /**
     * 브랜치, 파일 경로를 인자로 받는 코드 브라우저 표시
     *
     * @param userName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param branch 브랜치 이름
     * @param path 파일 경로
     */
    @With(DefaultProjectCheckAction.class)
    public static Result codeBrowserWithBranch(String userName, String projectName, String branch, String path)
        throws UnsupportedOperationException, IOException, SVNException, GitAPIException, ServletException {
        Project project = ProjectApp.getProject(userName, projectName);

        if (!RepositoryService.VCS_GIT.equals(project.vcs) && !RepositoryService.VCS_SUBVERSION.equals(project.vcs)) {
            return status(Http.Status.NOT_IMPLEMENTED, project.vcs + " is not supported!");
        }

        PlayRepository repository = RepositoryService.getRepository(project);
        ObjectNode fileInfo = repository.getMetaDataFromPath(branch, path);
        if (fileInfo == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }
        fileInfo.put("path", path);

        List<ObjectNode> recursiveData = new ArrayList<>();
        List<String> branches = repository.getBranches();

        /** 해당 경로가 폴더이고 최상위가 아니면, 최상위 경로부터 순서대로 정보를 추가한다 **/
        if(fileInfo.get("type").getTextValue().equals("folder") && !path.equals("")){
            recursiveData.addAll(RepositoryService.getMetaDataFromAncestorDirectories(repository, branch, path));
        }
        recursiveData.add(fileInfo);

        return ok(view.render(project, branches, recursiveData, branch, path));
    }

    /**
     * AJAX 호출로 지정한 프로젝트 지정한 경로의 정보를 얻고자 할 때 사용된다
     *
     * @param userName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param path 파일 또는 폴더의 경로
     */
    @With(DefaultProjectCheckAction.class)
    public static Result ajaxRequest(String userName, String projectName, String path) throws Exception{
        PlayRepository repository = RepositoryService.getRepository(userName, projectName);
        ObjectNode fileInfo = repository.getMetaDataFromPath(path);

        if(fileInfo != null) {
            return ok(fileInfo);
        } else {
            return notFound();
        }
    }

    /**
     * AJAX 호출로 지정한 프로젝트의 특정 브랜치에서 지정한 경로의 정보를 얻고자 할 때 사용된다
     *
     * @param userName 프로젝트 소유자 이름
     * @param projectName 프로젝트 이름
     * @param branch 브랜치 이름
     * @param path 파일 또는 폴더의 경로
     */
    @With(DefaultProjectCheckAction.class)
    public static Result ajaxRequestWithBranch(String userName, String projectName, String branch, String path)
            throws UnsupportedOperationException, IOException, SVNException, GitAPIException, ServletException{
        CodeApp.hostName = request().host();
        PlayRepository repository = RepositoryService.getRepository(userName, projectName);
        ObjectNode fileInfo = repository.getMetaDataFromPath(branch, path);

        if(fileInfo != null) {
            return ok(fileInfo);
        } else {
            return notFound();
        }
    }

    /**
     * 지정한 프로젝트의 지정한 파일의 원본을 보여준다
     *
     * @param userName
     * @param projectName
     * @param revision
     * @param path
     */
    @With(DefaultProjectCheckAction.class)
    public static Result showRawFile(String userName, String projectName, String revision, String path) throws Exception{
        byte[] fileAsRaw = RepositoryService.getFileAsRaw(userName, projectName, revision, path);
        if(fileAsRaw == null){
            return redirect(routes.CodeApp.codeBrowserWithBranch(userName, projectName, revision, path));
        }
        return ok(fileAsRaw).as("text/plain");
    }

    /**
     * 지정판 프로젝트의 지정한 이미지 파일 원본을 보여준다
     *
     * @param userName
     * @param projectName
     * @param path
     */
    @With(DefaultProjectCheckAction.class)
    public static Result showImageFile(String userName, String projectName, String revision, String path) throws Exception{
        final byte[] fileAsRaw = RepositoryService.getFileAsRaw(userName, projectName, revision, path);
        String mimeType = tika.detect(fileAsRaw);
        return ok(fileAsRaw).as(mimeType);
    }

    private static Tika tika = new Tika();

    /**
     * 프로젝트의 저장소 URL을 반환하는 함수
     * 화면에 저장소 URL을 표시하기 위해 사용된다
     *
     * @param ownerName
     * @param projectName
     */
    public static String getURL(String ownerName, String projectName) {
        Project project = ProjectApp.getProject(ownerName, projectName);
        return getURL(project);
    }

    public static String getURL(Project project) {
        if (project == null) {
            return null;
        } else if (RepositoryService.VCS_GIT.equals(project.vcs)) {
            return utils.Url.create(Arrays.asList(project.owner, project.name));
        } else if (RepositoryService.VCS_SUBVERSION.equals(project.vcs)) {
            return utils.Url.create(Arrays.asList("svn", project.owner, project.name));
        } else {
            return null;
        }
    }

    /**
     * 현재 로그인 된 사용자 정보가 있으면
     * 프로젝트 저장소 URL에 사용자 ID를 포함해서 반환한다
     * 예: protocol://user@host.name/path
     *
     * @param project
     */
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

     /**
     * 지정한 프로젝트의 지정한 파일을 연다.
     *
     * @param userName
     * @param revision
     * @param path
     */
    @IsAllowed(Operation.READ)
    public static Result openFile(String userName, String projectName, String revision,
                           String path) throws Exception{
        byte[] raw = RepositoryService.getFileAsRaw(userName, projectName, revision, path);

        if(raw == null){
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        return ok(raw).as(tika.detect(raw));
    }
}
