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

import java.io.IOException;

import javax.servlet.ServletException;

import models.Project;
import models.enumeration.Operation;

import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.BasicAuthAction;

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
                // If you want the Git client showing your custom message to
                // the user, the Content-Type should be exact "text/plain"
                // without any parameter. For more details, see the code at
                // https://github.com/git/git/commit/426e70d4a11ce3b4f70636d57c6a0ab16ae08a00#diff-eea0ad565ec5903a11b6023755d491cfR154
                response().setHeader("Content-Type", "text/plain");
                return forbidden(
                        String.format("'%s' has no permission to '%s/%s'.",
                            user.loginId, ownerName, projectName));
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
