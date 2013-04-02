/**
 * @author Ahn Hyeok Jun
 */

package controllers;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;

import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;

import models.enumeration.Direction;
import models.enumeration.Matching;
import views.html.board.editPost;
import views.html.board.newPost;
import views.html.board.postList;

import utils.AccessControl;
import utils.Callback;
import utils.Constants;
import utils.JodaDateUtil;

import play.data.Form;
import play.mvc.Call;
import play.mvc.Result;

import java.io.IOException;

import static com.avaje.ebean.Expr.icontains;

public class BoardApp extends AbstractPostingApp {
    public static class SearchCondition extends AbstractPostingApp.SearchCondition {
        private ExpressionList<Posting> asExpressionList(Project project) {
            ExpressionList<Posting> el = Posting.finder.where().eq("project.id", project.id);

            if (filter != null) {
                el.or(icontains("title", filter), icontains("body", filter));
            }

            if (orderBy != null) {
                el.orderBy(orderBy + " " + orderDir);
            }

            return el;
        }
    }

    public static Result posts(String userName, String projectName, int pageNum) {
        Form<SearchCondition> postParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition searchCondition = postParamForm.bindFromRequest().get();
        searchCondition.pageNum = pageNum - 1;
        Project project = ProjectApp.getProject(userName, projectName);

        if (!AccessControl.isCreatable(User.findByLoginId(session().get("loginId")), project, ResourceType.BOARD_POST)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        ExpressionList<Posting> el = searchCondition.asExpressionList(project);
        Page<Posting> posts = el.findPagingList(ITEMS_PER_PAGE).getPage(searchCondition.pageNum);

        return ok(postList.render("menu.board", project, posts, searchCondition));
    }

    public static Result newPostForm(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        return newPostingForm(project,
                newPost.render("board.post.new", new Form<Posting>(Posting.class), project));
    }

    public static Result newPost(String userName, String projectName) {
        Form<Posting> postForm = new Form<Posting>(Posting.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);
        if (postForm.hasErrors()) {
            flash(Constants.WARNING, "board.post.empty");

            return redirect(routes.BoardApp.newPost(userName, projectName));
        } else {
            Posting post = postForm.get();
            post.createdDate = JodaDateUtil.now();
            post.setAuthor(UserApp.currentUser());
            post.project = project;

            post.save();

            // Attach all of the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, post.asResource());
        }

        return redirect(routes.BoardApp.posts(project.owner, project.name, 1));
    }

    public static Result post(String userName, String projectName, Long postId) {
        Project project = ProjectApp.getProject(userName, projectName);
        Posting post = Posting.finder.byId(postId);
        if (!AccessControl.isCreatable(User.findByLoginId(session().get("loginId")), project, ResourceType.BOARD_POST)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        if (post == null) {
            flash(Constants.WARNING, "board.post.notExist");
            return redirect(routes.BoardApp.posts(project.owner, project.name, 1));
        } else {
            Form<PostingComment> commentForm = new Form<PostingComment>(PostingComment.class);
            return ok(views.html.board.post.render(post, commentForm, project));
        }
    }

    public static Result newComment(String userName, String projectName, Long postId) throws IOException {
        final Posting post = Posting.finder.byId(postId);
        Project project = post.project;
        Call redirectTo = routes.BoardApp.post(project.owner, project.name, postId);
        Form<PostingComment> commentForm = new Form<PostingComment>(PostingComment.class)
                .bindFromRequest();

        final PostingComment comment = commentForm.get();

        return newComment(comment, commentForm, redirectTo, new Callback() {
            @Override
            public void run() {
                comment.posting = post;
            }
        });
    }

    public static Result deletePost(String userName, String projectName, Long postId) {
        Posting posting = Posting.finder.byId(postId);
        Project project = posting.project;

        return deletePosting(posting,
                routes.BoardApp.posts(project.owner, project.name, 1));
    }

    public static Result editPostForm(String userName, String projectName, Long postId) {
        Posting existPost = Posting.finder.byId(postId);
        Form<Posting> editForm = new Form<Posting>(Posting.class).fill(existPost);
        Project project = ProjectApp.getProject(userName, projectName);

        if (AccessControl.isAllowed(UserApp.currentUser(), existPost.asResource(), Operation.UPDATE)) {
            return ok(editPost.render("board.post.modify", editForm, postId, project));
        } else {
            flash(Constants.WARNING, "board.notAuthor");
            return redirect(routes.BoardApp.post(project.owner, project.name, postId));
        }
    }

    public static Result editPost(String userName, String projectName, Long postId) {
        Form<Posting> postForm = new Form<Posting>(Posting.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);
        Posting post = postForm.get();
        Posting original = Posting.finder.byId(postId);
        Call redirectTo = routes.BoardApp.posts(project.owner, project.name, 1);
        Callback doNothing = new Callback() {
            @Override
            public void run() { }
        };

        return editPosting(original, post, postForm, redirectTo, doNothing);
    }

    public static Result deleteComment(String userName, String projectName, Long postId,
            Long commentId) {
        Comment comment = PostingComment.find.byId(commentId);
        Project project = comment.asResource().getProject();

        return deleteComment(comment,
                routes.BoardApp.post(project.owner, project.name, comment.getParent().id));
    }
}
