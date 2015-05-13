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

import java.io.IOException;

import javax.servlet.ServletException;

import models.Project;
import models.enumeration.Operation;

import com.github.zafarkhaja.semver.Version;
import play.api.i18n.Lang;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.BasicAuthAction;
import utils.Config;

public class GitApp extends Controller {

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

    public static Result service(String ownerName, String projectName, String service,
            boolean isAdvertise) throws IOException, UnsupportedOperationException,
            ServletException {
        if (!isSupportedService(service)) {
            return forbidden(String.format("Unsupported service: '%s'", service));
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (project == null) {
            return notFound();
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
                if (isCharsetAllowed(request().getHeader("User-Agent"))) {
                    contentType += ";charset=" + Config.getCharset();
                    message = Messages.get(
                            "git.error.permission", user.loginId, ownerName, projectName);
                } else {
                    message = Messages.get(Lang.defaultLang(),
                            "git.error.permission", user.loginId, ownerName, projectName);
                }
                response().setHeader("Content-Type", contentType);
                return forbidden(message);
            }
        }

        if (isAdvertise) {
            return ok(RepositoryService
                    .gitAdvertise(project, service, response()));
        } else {
            if (request().body().isMaxSizeExceeded()) {
                return status(REQUEST_ENTITY_TOO_LARGE);
            } else {
                user.visits(project);
                return ok(RepositoryService
                        .gitRpc(project, service, request(), response()));
            }
        }
    }

    @With(BasicAuthAction.class)
    public static Result advertise(String ownerName, String projectName, String service)
            throws UnsupportedOperationException, IOException, ServletException {
        if (service == null) {
            // If service parameter is not specified then git server should do getanyfile service,
            // but we don't support that.
            return forbidden("Unsupported service: getanyfile");
        }
        return GitApp.service(ownerName, projectName, service, true);
    }

    @With(BasicAuthAction.class)
    public static Result serviceRpc(String ownerName, String projectName, String service)
            throws UnsupportedOperationException, IOException, ServletException {
        return GitApp.service(ownerName, projectName, service, false);
    }

}
