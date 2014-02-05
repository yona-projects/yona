package controllers;

import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import org.tmatesoft.svn.core.SVNException;
import play.data.Form;
import play.db.ebean.Transactional;
import play.mvc.Call;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.RepositoryService;
import utils.AccessControl;
import utils.Constants;
import utils.ErrorViews;

import java.io.IOException;
import java.net.URL;

import controllers.annotation.IsCreatable;

import javax.servlet.ServletException;

import views.html.error.notfound;

/**
 * {@link models.PullRequestComment} CRUD 컨트롤러
 *
 */
public class PullRequestCommentApp extends Controller {

    @IsCreatable(ResourceType.PULL_REQUEST_COMMENT)
    public static Result newComment(String ownerName, String projectName, Long pullRequestId,
                                    String commitId) throws IOException, ServletException,
            SVNException {
        Form<CodeRange> codeRangeForm = new Form<>(CodeRange.class).bindFromRequest();

        Form<ReviewComment> reviewCommentForm = new Form<>(ReviewComment.class)
                .bindFromRequest();

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (reviewCommentForm.hasErrors()) {
            return badRequest(ErrorViews.BadRequest.render("error.validation", project));
        }

        PullRequest pullRequest = PullRequest.findById(pullRequestId);

        if (pullRequest == null) {
            return notFound(notfound.render("error.notfound", project, request().path()));
        }

        ReviewComment comment = reviewCommentForm.get();
        comment.author = new UserIdent(UserApp.currentUser());
        if (comment.thread == null) {
            if (codeRangeForm.errors().isEmpty()) {
                CodeCommentThread thread = new CodeCommentThread();

                if (commitId != null) {
                    thread.commitId = commitId;
                } else {
                    thread.commitId = pullRequest.mergedCommitIdTo;
                    thread.prevCommitId = pullRequest.mergedCommitIdFrom;
                }

                User codeAuthor = RepositoryService
                        .getRepository(project)
                        .getCommit(thread.commitId)
                        .getAuthor();
                if (!codeAuthor.isAnonymous()) {
                    thread.codeAuthors.add(codeAuthor);
                }

                thread.codeRange = codeRangeForm.get();

                comment.thread = thread;
            } else {
                // non-range
                NonRangedCodeCommentThread thread = new NonRangedCodeCommentThread();
                thread.commitId = commitId;
                comment.thread = thread;
            }
            comment.thread.project = project;
            comment.thread.state = CommentThread.ThreadState.OPEN;
            comment.thread.createdDate = comment.createdDate;
            comment.thread.author = comment.author;
            pullRequest.commentThreads.add(comment.thread);
        } else {
            comment.thread = CommentThread.find.byId(comment.thread.id);
        }
        comment.save();
        pullRequest.update();

        Attachment.moveAll(UserApp.currentUser().asResource(), comment.asResource());

        Call toView =
                routes.PullRequestApp.pullRequestChanges(ownerName, projectName, pullRequest.number);
        String urlToView = toView + "#comment-" + comment.id;

        NotificationEvent.afterNewComment(UserApp.currentUser(), pullRequest, comment, urlToView);

        return redirect(urlToView);
    }

    @Transactional
    public static Result deleteComment(Long id) {
        PullRequestComment pullRequestComment = PullRequestComment.findById(id);
        if(pullRequestComment == null) {
            notFound();
        }
        if (!AccessControl.isAllowed(UserApp.currentUser(), pullRequestComment.asResource(), Operation.DELETE)) {
            return forbidden(ErrorViews.Forbidden.render("error.auth.unauthorized.waringMessage"));
        }
        pullRequestComment.delete();
        String backPageUrl = request().getHeader("Referer");
        return redirect(backPageUrl);
    }

}
