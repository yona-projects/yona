package controllers;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;

import com.avaje.ebean.ExpressionList;
import models.Attachment;
import models.CodeComment;
import models.Project;
import models.enumeration.Operation;

import models.enumeration.ResourceType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.tmatesoft.svn.core.SVNException;

import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.Commit;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.HttpUtil;
import views.html.code.history;
import views.html.code.nohead;
import views.html.code.diff;
import views.html.error.forbidden;
import views.html.error.notfound;
import views.html.error.notfound_default;

public class CodeHistoryApp extends Controller {

    private static final int HISTORY_ITEM_LIMIT = 25;


    /**
     * 코드 저장소의 커밋 로그 목록 페이지에 대한 요청에 응답한다.
     *
     * when: 코드 메뉴의 커밋 탭을 클릭했을 때
     *
     * {@code ownerName}과 {@code projectName}에 대응하는 프로젝트의 코드 저장소에서, HEAD 까지의 커밋
     * 목록에 대한 페이지로 응답한다. 브랜치는 선택하지 않은 것으로 간주한다.
     *
     * @param ownerName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @return 커밋 로그 목록 HTML 페이지를 담은 응답
     * @throws IOException
     * @throws UnsupportedOperationException
     * @throws ServletException
     * @throws GitAPIException
     * @throws SVNException
     */
    public static Result historyUntilHead(String ownerName, String projectName) throws IOException,
            UnsupportedOperationException, ServletException, GitAPIException,
            SVNException {
        return history(ownerName, projectName, null);
    }

    /**
     * 코드 저장소의 특정 {@code branch}에 대한 커밋 로그 목록 페이지에 대한 요청에 응답한다.
     *
     * when: 코드 메뉴의 커밋 탭에서 특정 브랜치를 선택했을 때
     *
     * {@code ownerName}과 {@code projectName}에 대응하는 프로젝트의 코드 저장소에서, 지정한
     * {@code branch}의 커밋 목록 중, 한 페이지의 크기를 {@link #HISTORY_ITEM_LIMIT}로 했을 때
     * {@code page}(요청의 쿼리에서 얻음)번째 페이지를 응답 메시지에 담아 반환한다.
     *
     * 만약 HEAD가 존재하지 않는 경우에는, 저장소를 만들어야 한다는 안내 페이지로 응답한다.
     *
     * @param ownerName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param branch 선택한 브랜치
     * @return 커밋 로그 목록 HTML 페이지를 담은 응답
     * @throws IOException
     * @throws UnsupportedOperationException
     * @throws ServletException
     * @throws GitAPIException
     * @throws SVNException
     */
    public static Result history(String ownerName, String projectName, String branch) throws IOException,
            UnsupportedOperationException, ServletException, GitAPIException,
            SVNException {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (project == null) {
            return notFound();
        }

        PlayRepository repository = RepositoryService.getRepository(project);

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return forbidden(views.html.error.forbidden.render(project));
        }

        String pageStr = HttpUtil.getFirstValueFromQuery(request().queryString(), "page");
        int page = 0;
        if (pageStr != null) {
            page = Integer.parseInt(pageStr);
        }

        try {
            List<Commit> commits = repository.getHistory(page, HISTORY_ITEM_LIMIT, branch);

            if (commits == null) {
                return notFound();
            }

            return ok(history.render(project, commits, page, branch));
        } catch (NoHeadException e) {
            return notFound(nohead.render(project));
        }
    }

    /**
     * 코드 저장소의 특정 커밋을 보여달라는 요청에 응답한다.
     *
     * when: 코드 메뉴의 커밋 탭에서, 커밋의 목록 중 특정 커밋을 클릭했을 때
     *
     * {@code ownerName}과 {@code projectName}에 대응하는 프로젝트의 코드 저장소에서, 지정한
     * {@code commitId}에 해당하는 커밋과 그 부모 커밋과의 차이를 HTML 페이지로 렌더링하여 응답한다.
     *
     * @param ownerName 프로젝트 소유자
     * @param projectName 프로젝트 이름
     * @param commitId 커밋 아이디
     * @return 특정 커밋을 보여주는 HTML 페이지를 담은 응답
     * @throws IOException
     * @throws UnsupportedOperationException
     * @throws ServletException
     * @throws GitAPIException
     * @throws SVNException
     */
    public static Result show(String ownerName, String projectName, String commitId)
            throws IOException, UnsupportedOperationException, ServletException, GitAPIException,
            SVNException {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (project == null) {
            return notFound();
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return forbidden(views.html.error.forbidden.render(project));
        }

        String patch = RepositoryService.getRepository(project).getPatch(commitId);
        Commit commit = RepositoryService.getRepository(project).getCommit(commitId);

        if (patch == null) {
            return notFound();
        }

        List<CodeComment> comments = CodeComment.find.where().eq("commitId",
                commitId).eq("project.id", project.id).findList();

        return ok(diff.render(project, commit, patch, comments));
    }

    public static Result newComment(String ownerName, String projectName, String commitId)
            throws IOException, ServletException, SVNException {
        Form<CodeComment> codeCommentForm = new Form<CodeComment>(CodeComment.class)
                .bindFromRequest();

        if (codeCommentForm.hasErrors()) {
            return badRequest(codeCommentForm.errors().toString());
        }

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (project == null) {
            return notFound(notfound_default.render("error.notfound", request().path()));
        }

        if (RepositoryService.getRepository(project).getCommit(commitId) == null) {
            return notFound(notfound.render("error.notfound", project, request().path()));
        }

        if (!AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project,
                ResourceType.CODE_COMMENT)) {
            return forbidden(forbidden.render(project));
        }

        CodeComment codeComment = codeCommentForm.get();
        codeComment.project = project;
        codeComment.commitId = commitId;
        codeComment.createdDate = new Date();
        codeComment.setAuthor(UserApp.currentUser());
        codeComment.save();

        Attachment.moveAll(UserApp.currentUser().asResource(), codeComment.asResource());

        return redirect(routes.CodeHistoryApp.show(project.owner, project.name, commitId));
    }

    public static Result deleteComment(String ownerName, String projectName, String commitId,
                                       Long id) {
        CodeComment codeComment = CodeComment.find.byId(id);

        if (codeComment == null) {
            return notFound(notfound_default.render("error.notfound", request().path()));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), codeComment.asResource(),
                Operation.DELETE)) {
            return forbidden(forbidden.render(codeComment.project));
        }

        codeComment.delete();

        return redirect(routes.CodeHistoryApp.show(ownerName, projectName, commitId));
    }
}
