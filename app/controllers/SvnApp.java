/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
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

import models.Project;
import models.User;
import models.enumeration.Operation;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVHandlerFactory;
import play.mvc.*;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.*;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PipedInputStream;
import java.net.URISyntaxException;

public class SvnApp extends Controller {

    @With(BasicAuthAction.class)
    @BodyParser.Of(value = BodyParser.Raw.class, maxLength = Integer.MAX_VALUE)
    public static Result serviceWithPath(String path) throws ServletException, IOException, InterruptedException {
        return service();
    }

    @With(BasicAuthAction.class)
    @BodyParser.Of(value = BodyParser.Raw.class, maxLength = Integer.MAX_VALUE)
    public static Result service() throws ServletException, IOException, InterruptedException {
        // FIXME DAVServlet 들어내고 싶다.
        String path;
        try {
            path = new java.net.URI(request().uri()).getPath();
        } catch (URISyntaxException e) {
            return badRequest();
        }

        // If the url starts with slash, remove the slash.
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Split the url into three segments: "svn", userName, pathInfo
        String[] segments = path.split("/", 3);
        if (segments.length < 3) {
            return forbidden();
        }

        // Get userName and pathInfo from path segments.
        final String userName = segments[1];
        String pathInfo = segments[2];

        // Get projectName from the pathInfo.
        String projectName = pathInfo.split("/", 2)[0];

        // if user is anon, currentUser is null
        User currentUser = UserApp.currentUser();
        // Check the user has a permission to access this repository.
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        if (project == null) {
            return notFound();
        }

        PlayRepository repository = RepositoryService.getRepository(project);
        if (!AccessControl.isAllowed(currentUser, repository.asResource(),
                getRequestedOperation(request().method()))) {
            if (currentUser.isAnonymous()) {
                return BasicAuthAction.unauthorized(response());
            } else {
                return forbidden("You have no permission to access this repository.");
            }
        }

        // Start DAV Service
        PlayServletResponse response = startDavService(userName, pathInfo);

        // Wait until the status code is decided by the DAV service.
        // After that, get the status code.
        int status = response.waitAndGetStatus();

        // Send the response.
        UserApp.currentUser().visits(project);
        return sendResponse(status, response.getInputStream());
    }

    /**
     * WebDAV 서비스를 시작한다.
     *
     * @param ownerName 저장소 소유자의 이름. 저장소의 부모 경로를 얻기 위해 필요하다.
     * @param pathInfo
     */
    private static PlayServletResponse startDavService(final String ownerName, String pathInfo) throws IOException {
        // For DavServlet, transform HTTP request and HTTP response in this context to
        // ServletRequest and ServletResponse
        final PlayServletRequest request = new PlayServletRequest(request(),
                UserApp.currentUser().loginId, pathInfo);
        final PlayServletResponse response = new PlayServletResponse(response());

        new Thread() {
            @Override
            public void run() {
                try {
                    RepositoryService.createDavServlet(ownerName).service(request, response);
                    response.flushBuffer();
                    response.getOutputStream().close();
                } catch (Exception e) {
                    response.setStatus(500);
                    response.getStatusLock().notifyAll();
                    play.Logger.error("Failed to process a SVN request.", e);
                }
            }
        }.start();

        return response;
    }

    /**
     * 상태코드에 맞게 적절히 응답을 돌려준다.
     *
     * 1xx, 204, 304는 message body가 없어야 하며, 205는 길이가 0인 message body를 가져야만 한다.
     *
     * @param statusCode
     * @param input
     * @return
     */
    private static Result sendResponse(int statusCode, PipedInputStream input) {
        if (statusCode < 200 || statusCode == 204 || statusCode == 304) {
            // 1xx, 204 and 304 MUST NOT include message body.
            return status(statusCode);
        } else if (statusCode == 205) {
            // 205 MUST include message body of zero length.
            return status(statusCode, "");
        } else {
            return status(statusCode, input);
        }
    }

    private static Operation getRequestedOperation(String method) {
        if (DAVHandlerFactory.METHOD_OPTIONS.equals(method)
                || DAVHandlerFactory.METHOD_PROPFIND.equals(method)
                || DAVHandlerFactory.METHOD_GET.equals(method)
                || DAVHandlerFactory.METHOD_REPORT.equals(method)) {
            return Operation.READ;
        } else {
            return Operation.UPDATE;
        }
    }

}
