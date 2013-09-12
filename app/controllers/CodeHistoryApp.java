package controllers;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;

import models.*;
import models.enumeration.EventType;
import models.enumeration.Operation;

import models.enumeration.ResourceType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.tmatesoft.svn.core.SVNException;

import play.data.Form;
import play.mvc.Call;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.Commit;
import playRepository.FileDiff;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.HttpUtil;
import utils.ErrorViews;
import utils.PullRequestCommit;
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
        return history(ownerName, projectName, null, null);
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
    public static Result history(String ownerName, String projectName, String branch, String path) throws IOException,
            UnsupportedOperationException, ServletException, GitAPIException,
            SVNException {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (project == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        PlayRepository repository = RepositoryService.getRepository(project);

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        String pageStr = HttpUtil.getFirstValueFromQuery(request().queryString(), "page");
        int page = 0;
        if (pageStr != null) {
            page = Integer.parseInt(pageStr);
        }

        try {
            List<Commit> commits = repository.getHistory(page, HISTORY_ITEM_LIMIT, branch, path);

            if (commits == null) {
                return notFound(ErrorViews.NotFound.render("error.notfound", project, null));
            }

            return ok(history.render(project, commits, page, branch, path));
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
            return notFound(ErrorViews.NotFound.render("error.notfound"));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), project.asResource(), Operation.READ)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        PlayRepository repository = RepositoryService.getRepository(project);
        List<FileDiff> fileDiffs = repository.getDiff(commitId);
        String patch = repository.getPatch(commitId);
        Commit commit = repository.getCommit(commitId);
        Commit parentCommit = repository.getParentCommitOf(commitId);

        if (fileDiffs == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound", project, null));
        }

        if (patch == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound", project, null));
        }

        List<CodeComment> comments = CodeComment.find.where().eq("commitId",
                commitId).eq("project.id", project.id).findList();

        String selectedBranch = request().getQueryString("branch");
        
        return ok(diff.render(project, commit, parentCommit, patch, comments, selectedBranch, fileDiffs));
    }

    public static Result newComment(String ownerName, String projectName, String commitId)
            throws IOException, ServletException, SVNException {
        Form<CodeComment> codeCommentForm = new Form<>(CodeComment.class)
                .bindFromRequest();

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (project == null) {
            return notFound(notfound_default.render(request().path()));
        }

        if (codeCommentForm.hasErrors()) {
            return badRequest(ErrorViews.BadRequest.render("error.validation", project));
        }

        if (RepositoryService.getRepository(project).getCommit(commitId) == null) {
            return notFound(notfound.render("error.notfound", project, request().path()));
        }

        if (!AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project,
                ResourceType.CODE_COMMENT)) {
            return forbidden(forbidden.render("error.forbidden", project));
        }

        CodeComment codeComment = codeCommentForm.get();
        codeComment.project = project;
        codeComment.commitId = commitId;
        codeComment.createdDate = new Date();
        codeComment.setAuthor(UserApp.currentUser());
        codeComment.save();

        Attachment.moveAll(UserApp.currentUser().asResource(), codeComment.asResource());

        Call toView = routes.CodeHistoryApp.show(project.owner, project.name, commitId);
        toView = backToThePullRequestCommitView(toView);

        addNotificationEventForNewComment(project, codeComment, toView);

        return redirect(toView + "#comment-" + codeComment.id);
    }

    private static Call backToThePullRequestCommitView(Call toView) {
        String referer = request().getHeader("Referer");
        if(PullRequestCommit.isValid(referer)) {
            PullRequestCommit prc = new PullRequestCommit(referer);
            toView = routes.PullRequestApp.commitView(prc.getProjectOwner(), prc.getProjectName(), prc.getPullRequestNumber(), prc.getCommitId());
        }
        return toView;
    }

    private static void addNotificationEventForNewComment(Project project, CodeComment codeComment, Call toView) throws IOException, SVNException, ServletException {
        Commit commit = RepositoryService.getRepository(project).getCommit(codeComment.commitId);
        Set<User> watchers = commit.getWatchers(project);
        watchers.addAll(NotificationEvent.getMentionedUsers(codeComment.contents));
        watchers.remove(UserApp.currentUser());

        NotificationEvent notiEvent = new NotificationEvent();
        notiEvent.created = new Date();
        notiEvent.title = NotificationEvent.formatReplyTitle(project, commit);
        notiEvent.senderId = UserApp.currentUser().id;
        notiEvent.receivers = watchers;
        notiEvent.urlToView = toView.absoluteURL(request());
        notiEvent.resourceId = codeComment.id.toString();
        notiEvent.resourceType = codeComment.asResource().getType();
        notiEvent.eventType = EventType.NEW_COMMENT;
        notiEvent.oldValue = null;
        notiEvent.newValue = codeComment.contents;

        NotificationEvent.add(notiEvent);
    }

    public static Result deleteComment(String ownerName, String projectName, String commitId,
                                       Long id) {
        CodeComment codeComment = CodeComment.find.byId(id);

        if (codeComment == null) {
            return notFound(notfound_default.render(request().path()));
        }

        if (!AccessControl.isAllowed(UserApp.currentUser(), codeComment.asResource(),
                Operation.DELETE)) {
            return forbidden(forbidden.render("error.forbidden", codeComment.project));
        }

        codeComment.delete();

        Call toView = routes.CodeHistoryApp.show(ownerName, projectName, commitId);
        toView = backToThePullRequestCommitView(toView);

        return redirect(toView);
    }
}
