/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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
import actions.NullProjectCheckAction;
import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.tmatesoft.svn.core.SVNException;
import play.data.Form;
import play.mvc.*;
import playRepository.Commit;
import playRepository.FileDiff;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.*;
import views.html.code.diff;
import views.html.code.history;
import views.html.code.nohead;
import views.html.code.svnDiff;
import views.html.error.notfound;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@AnonymousCheck
public class CodeHistoryApp extends Controller {

    private static final int HISTORY_ITEM_LIMIT = 25;


    @With(DefaultProjectCheckAction.class)
    public static Result historyUntilHead(String ownerName, String projectName) throws IOException,
            UnsupportedOperationException, ServletException, GitAPIException,
            SVNException {
        return history(ownerName, projectName, null, null);
    }

    @IsAllowed(Operation.READ)
    public static Result history(String ownerName, String projectName, String branch, String path) throws IOException,
            UnsupportedOperationException, ServletException, GitAPIException,
            SVNException {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        PlayRepository repository = RepositoryService.getRepository(project);
        branch = HttpUtil.decodePathSegment(branch);
        path = HttpUtil.decodePathSegment(path);

        String pageStr = HttpUtil.getFirstValueFromQuery(request().queryString(), "page");
        int page = 0;
        if (StringUtils.isNotEmpty(pageStr)) {
            page = Integer.parseInt(pageStr);
        }

        try {
            List<Commit> commits = repository.getHistory(page, HISTORY_ITEM_LIMIT, branch, path);

            if (commits == null) {
                return notFound(ErrorViews.NotFound.render("error.notfound", project));
            }

            return ok(history.render(project, commits, page, branch, path));
        } catch (NoHeadException e) {
            return notFound(nohead.render(project));
        }
    }

    @IsAllowed(Operation.READ)
    public static Result show(String ownerName, String projectName, String commitId)
            throws IOException, UnsupportedOperationException, ServletException, GitAPIException,
            SVNException, NoSuchMethodException {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        PlayRepository repository = RepositoryService.getRepository(project);

        Commit commit;

        try {
            commit = repository.getCommit(commitId);
        } catch (org.eclipse.jgit.errors.MissingObjectException e) {
            return notFound(ErrorViews.NotFound.render("error.notfound.commit", project));
        }

        if(commit == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound.commit", project));
        }

        Commit parentCommit = repository.getParentCommitOf(commitId);
        List<CommentThread> threads
                = CommentThread.findByCommitId(CommentThread.find, project, commit.getId());

        String selectedBranch = StringUtils.defaultIfBlank(request().getQueryString("branch"), "HEAD");
        String path = StringUtils.defaultIfBlank(request().getQueryString("path"), "");

        if(project.vcs.equals(RepositoryService.VCS_SUBVERSION)) {
            String patch = repository.getPatch(commitId);

            if (patch == null) {
                return notFound(ErrorViews.NotFound.render("error.notfound", project));
            }

            List<CommitComment> comments = CommitComment.find.where()
                .eq("commitId", commitId)
                .eq("project.id", project.id)
                .order("createdDate")
                .findList();

            return ok(svnDiff.render(project, commit, parentCommit, patch, comments, selectedBranch, path));
        } else {
            List<FileDiff> fileDiffs = repository.getDiff(commitId);

            if (fileDiffs == null) {
                return notFound(ErrorViews.NotFound.render("error.notfound", project));
            }

            return ok(diff.render(project, commit, parentCommit, threads, selectedBranch,
                    fileDiffs, path));
        }
    }

    @With(NullProjectCheckAction.class)
    public static Result newSVNComment(String ownerName, String projectName, String commitId)
            throws IOException, ServletException, SVNException {
        Form<CommitComment> codeCommentForm = new Form<>(CommitComment.class)
                .bindFromRequest();

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (codeCommentForm.hasErrors()) {
            return badRequest(ErrorViews.BadRequest.render("error.validation", project));
        }

        Commit commit = RepositoryService.getRepository(project).getCommit(commitId);

        if (commit == null) {
            return notFound(notfound.render("error.notfound", project, request().path()));
        }

        if (!AccessControl.isResourceCreatable(
                    UserApp.currentUser(), commit.asResource(project), ResourceType.COMMIT_COMMENT)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        CommitComment codeComment = codeCommentForm.get();
        codeComment.project = project;
        codeComment.commitId = commitId;
        codeComment.createdDate = new Date();
        codeComment.setAuthor(UserApp.currentUser());
        codeComment.save();

        Attachment.moveAll(UserApp.currentUser().asResource(), codeComment.asResource());

        NotificationEvent.afterNewSVNCommitComment(project, codeComment);

        return redirect(RouteUtil.getUrl(codeComment));
    }

    @IsCreatable(ResourceType.COMMIT_COMMENT)
    public static Result newComment(String ownerName, String projectName, String commitId)
            throws IOException, ServletException, SVNException {
        Form<CodeRange> codeRangeForm = new Form<>(CodeRange.class).bindFromRequest();

        Form<ReviewComment> reviewCommentForm = new Form<>(ReviewComment.class)
                .bindFromRequest();

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if(project.vcs.equals(RepositoryService.VCS_SUBVERSION)) {
            return newSVNComment(ownerName, projectName, commitId);
        }

        if (reviewCommentForm.hasErrors()) {
            return badRequest(ErrorViews.BadRequest.render("error.validation", project));
        }

        if (RepositoryService.getRepository(project).getCommit(commitId) == null) {
            return notFound(notfound.render("error.notfound", project, request().path()));
        }

        ReviewComment comment = reviewCommentForm.get();
        comment.author = new UserIdent(UserApp.currentUser());
        if (comment.thread == null) {
            if (codeRangeForm.errors().isEmpty()) {
                CodeCommentThread thread = new CodeCommentThread();
                thread.commitId = commitId;
                thread.prevCommitId = null;
                User codeAuthor = RepositoryService
                        .getRepository(project)
                        .getCommit(commitId)
                        .getAuthor();
                if (!codeAuthor.isAnonymous()) {
                    thread.codeAuthors.add(codeAuthor);
                }
                thread.codeRange = codeRangeForm.get();
                comment.thread = thread;
            } else {
                NonRangedCodeCommentThread thread = new NonRangedCodeCommentThread();
                thread.commitId = commitId;
                thread.prevCommitId = null;
                comment.thread = thread;
            }
            comment.thread.project = project;
            comment.thread.state = CommentThread.ThreadState.OPEN;
            comment.thread.createdDate = comment.createdDate;
            comment.thread.author = comment.author;
        } else {
            comment.thread = CommentThread.find.byId(comment.thread.id);
        }
        comment.save();

        Attachment.moveAll(UserApp.currentUser().asResource(), comment.asResource());

        NotificationEvent.afterNewCommitComment(project, comment, commitId);

        return redirect(RouteUtil.getUrl(comment));
    }

    @With(DefaultProjectCheckAction.class)
    @IsAllowed(value = Operation.DELETE, resourceType = ResourceType.COMMIT_COMMENT)
    public static Result deleteComment(String ownerName, String projectName, String commitId,
                                       Long id) {
        CommitComment codeComment = CommitComment.find.byId(id);
        codeComment.delete();

        Call toView = routes.CodeHistoryApp.show(ownerName, projectName, commitId);

        return redirect(toView);
    }
}
