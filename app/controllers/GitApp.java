package controllers;

import git.GitRepository;

import java.io.IOException;

import play.Logger;
import play.mvc.*;
import views.html.code.gitView;

/**
 * 
 * @author Ahn Hyeok Jun
 * @e-mail qa22ahj@google.com
 * 
 * @class git repository에 접근할때의 컨트롤러. push, pull만을 처리하고 코드 브라우저의 접근은 CodeApp에서
 *        처리한다. TODO showRawCode와 showCodeBrowser가 여기 있어야 되는지고려할것
 * 
 */
public class GitApp extends Controller {
    public static final String REPO_PREFIX = "repo/git/";
    public static String HOST_URL = "http://localhost:9000/";

    /**
     * 클라이언트로 부터 요청을 받아 레파지토리에 push pull 하는 함수.
     * 
     * @param projectName
     *            서비스를 받아야할 프로젝트 이름
     * @param service
     *            받는 서비스.
     * @return 글라이언트에게 줄 응답 몸통
     * @throws IOException
     */
    public static Result advertise(String ownerName, String projectName, String service)
            throws IOException {
        Logger.debug("GitApp.advertise : " + request().toString());

        response().setContentType("application/x-" + service + "-advertisement");

        byte[] buf = GitRepository.getGitRepository(ownerName, projectName).advertise(service);

        if (buf == null)
            return forbidden("Unsupported service: '" + service + "'");

        return ok(buf);
    }

    /**
     * 서비스의 사용가능 상태를 확인
     * 
     * @param projectName
     *            해당 레파지토리를 가지고 있는 프로젝트명
     * @param service
     *            사용하려는 서비스 이름. 두가지 밖에 없음.
     * @return 클라이언트에게 줄 응답
     * @throws IOException
     */
    public static Result serviceRpc(String ownerName, String projectName, String service)
            throws IOException {
        Logger.debug("GitApp.advertise : " + request().toString());

        response().setContentType("application/x-" + service + "-result");

        byte[] requestBody = request().body().asRaw().asBytes();
        byte[] buf = GitRepository.getGitRepository(ownerName, projectName).serviceRpc(service,
                requestBody);

        return ok(buf);
    }

    /**
     * Raw포맷으로 소스를 보여주는 코드
     * TODO 후에 codeApp으로 옯길예정
     * 
     * @param userName
     * @param projectName
     * @param path
     * @return
     * @throws IOException
     */
    public static Result showRawCode(String userName, String projectName, String path)
            throws IOException {
        return ok(GitRepository.getGitRepository(userName, projectName).getFileByByteArray(path));
    }

    /**
     * 코드 브라우져를 표시하는 코드 
     * TODO 후에 codeApp으로 옯길예정
     * 
     * @param userName
     * @param projectName
     * @return
     */
    public static Result showCodeBrowser(String userName, String projectName) {
        return ok(gitView.render(HOST_URL + projectName,
                ProjectApp.getProject(userName, projectName)));
    }
}
