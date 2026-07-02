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

import com.github.zafarkhaja.semver.Version;
import models.Project;
import models.enumeration.Operation;
import play.api.i18n.Lang;
import play.i18n.Messages;
import play.mvc.BodyParser;
import utils.LegacyController;
import play.mvc.Result;
import play.mvc.With;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.BasicAuthAction;
import utils.Config;
import utils.MessagesUtil;
import utils.RequestUtil;

import javax.servlet.ServletException;
import java.io.IOException;

import static utils.HttpUtil.decodeUrlString;

public class GitApp extends LegacyController {

    public static boolean isSupportedService(String service) {
        return service != null
                && (service.equals("git-upload-pack") || service.equals("git-receive-pack"));
    }

    private static boolean isAllowed(Project project, String service) throws
            UnsupportedOperationException, IOException, ServletException {
        Operation operation = Operation.UPDATE;

        if (service.equals("git-upload-pack")) {
            operation = Operation.READ;
        }

        // Only members can access code?
        if(project.isCodeAccessibleMemberOnly && !project.hasMember(UserApp.currentUser())) {
            operation = Operation.UPDATE;
        }

        PlayRepository repository = RepositoryService.getRepository(project);
        return AccessControl
                .isAllowed(UserApp.currentUser(), repository.asResource(), operation);

    }

    /**
     * Checks whether the Git client allows Content-Type has a charset.
     *
     * Charset is allowed since Git 2.1.0.
     *
     * @param userAgent
     * @return
     */
    private static boolean isCharsetAllowed(String userAgent) {
        try {
            String version = Config.semverize(userAgent.substring(userAgent.indexOf('/') + 1));
            if (Version.valueOf(version).greaterThanOrEqualTo(Version.forIntegers(2, 1, 0))) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public Result service(String ownerName, String projectName, String service,
            boolean isAdvertise) throws IOException, UnsupportedOperationException,
            ServletException {
        if (!isSupportedService(service)) {
            return forbidden(String.format("Unsupported service: '%s'", service));
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (project == null) {
            Project previousProject = Project.findByPreviousPlaceOf(ownerName, projectName);
            if (previousProject != null) {
                project = previousProject;
            } else {
                return notFound();
            }
        }

        if (!project.vcs.equals(RepositoryService.VCS_GIT)) {
            return notFound();
        }

        models.User user = UserApp.currentUser();

        if (!isAllowed(project, service)) {
            if (user.isAnonymous()) {
                return BasicAuthAction.unauthorized(response());
            } else {
                String contentType = "text/plain", message;
                if (isCharsetAllowed(RequestUtil.getHeader(request(), "User-Agent"))) {
                    contentType += ";charset=" + Config.getCharset();
                    message = MessagesUtil.get(
                            "git.error.permission", user.loginId, ownerName, projectName);
                } else {
                    message = MessagesUtil.get(Lang.defaultLang(),
                            "git.error.notAllowedCharset", user.loginId, ownerName, projectName);
                }
                response().setHeader("Content-Type", contentType);
                return forbidden(message);
            }
        }

        if (isAdvertise) {
            return ok(RepositoryService
                    .gitAdvertise(project, service, response()))
                    .as(gitContentType(service, true));
        } else {
            if (request().body().asRaw() == null) {
                return status(REQUEST_ENTITY_TOO_LARGE);
            } else {
                user.visits(project);
                return ok(RepositoryService
                        .gitRpc(project, service, request(), response()))
                        .as(gitContentType(service, false));
            }
        }
    }

    private static String gitContentType(String service, boolean advertise) {
        return "application/x-" + service + (advertise ? "-advertisement" : "-result");
    }

    @With(BasicAuthAction.class)
    public Result advertise(String ownerName, String projectName, String service)
            throws UnsupportedOperationException, IOException, ServletException {
        if (service == null) {
            // If service parameter is not specified then git server should do getanyfile service,
            // but we don't support that.
            return forbidden("Unsupported service: getanyfile");
        }
        return service(ownerName, decodeUrlString(projectName), service, true);
    }

    @With(BasicAuthAction.class)
    @BodyParser.Of(BodyParser.Raw.class)
    public Result serviceRpc(String ownerName, String projectName, String service)
            throws UnsupportedOperationException, IOException, ServletException {
        return service(ownerName, projectName, service, false);
    }
}
