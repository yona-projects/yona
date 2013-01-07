/**
 * @author Ahn Hyeok Jun
 */

package controllers;

import models.*;
import models.enumeration.*;
import play.Logger;
import play.data.Form;
import play.mvc.*;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.Request;
import utils.*;
import views.html.board.*;

import java.io.*;

public class BoardApp extends Controller {
    
    //TODO 이 클래스는 원래 따로 존재해야 함.
    public static class SearchCondition{
        public final static String ORDERING_KEY_ID = "id";
        public final static String ORDERING_KEY_TITLE = "title";
        public final static String ORDERING_KEY_AGE = "date";
        public final static String ORDERING_KEY_AUTHOR = "authorName";
        
        public SearchCondition() {
            this.order = Direction.DESC.direction();
            this.key = ORDERING_KEY_ID;
            this.filter = "";
            this.pageNum = 1;
        }

        public String order;
        public String key;
        public String filter;
        public int pageNum;
    }
    

    public static Result posts(String userName, String projectName) {

        Form<SearchCondition> postParamForm = new Form<SearchCondition>(SearchCondition.class);
        SearchCondition postSearchCondition = postParamForm.bindFromRequest().get();
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isAllowed(session().get("userId"), project)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        Logger.debug(postSearchCondition.filter);
        return ok(postList.render(
                "menu.board",
                project,
                Post.findOnePage(project.owner, project.name, postSearchCondition.pageNum,
                        Direction.getValue(postSearchCondition.order), postSearchCondition.key, postSearchCondition.filter), postSearchCondition));
    }

    public static Result newPostForm(String userName, String projectName) {
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isAllowed(session().get("userId"), project)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        return ok(newPost.render("board.post.new", new Form<Post>(Post.class), project));
    }

    public static Result newPost(String userName, String projectName) {
        Form<Post> postForm = new Form<Post>(Post.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);
        if (postForm.hasErrors()) {
            flash(Constants.WARNING, "board.post.empty");

            return redirect(routes.BoardApp.newPost(userName, projectName));
        } else {
            Post post = postForm.get();
            post.authorId = UserApp.currentUser().id;
            post.authorName = UserApp.currentUser().name;
            post.commentCount = 0;
            post.filePath = saveFile(request());
            post.project = project;
            Post.write(post);

            // Attach all of the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, project.id, Resource.BOARD_POST, post.id);
        }

        return redirect(routes.BoardApp.posts(project.owner, project.name));
    }

    public static Result post(String userName, String projectName, Long postId) {
        Post post = Post.findById(postId);
        Project project = ProjectApp.getProject(userName, projectName);
        if (!AccessControl.isAllowed(session().get("userId"), project)) {
            return unauthorized(views.html.project.unauthorized.render(project));
        }

        if (post == null) {
            flash(Constants.WARNING, "board.post.notExist");
            return redirect(routes.BoardApp.posts(project.owner, project.name));
        } else {
            Form<Comment> commentForm = new Form<Comment>(Comment.class);
            return ok(views.html.board.post.render(post, commentForm, project));
        }
    }

    public static Result newComment(String userName, String projectName, Long postId) {
        Form<Comment> commentForm = new Form<Comment>(Comment.class).bindFromRequest();

        Project project = ProjectApp.getProject(userName, projectName);
        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "board.comment.empty");
            return redirect(routes.BoardApp.post(project.owner, project.name, postId));
        } else {
            Comment comment = commentForm.get();
            comment.post = Post.findById(postId);
            comment.authorId = UserApp.currentUser().id;
            comment.authorName = UserApp.currentUser().name;
            comment.filePath = saveFile(request());

            Comment.write(comment);
            Post.countUpCommentCounter(postId);

            // Attach all of the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, project.id, Resource.BOARD_COMMENT, comment.id);

            return redirect(routes.BoardApp.post(project.owner, project.name, postId));
        }
    }

    public static Result deletePost(String userName, String projectName, Long postId) {
        Project project = ProjectApp.getProject(userName, projectName);
        Post.delete(postId);
        Attachment.deleteAll(Resource.BOARD_POST, postId);
        return redirect(routes.BoardApp.posts(project.owner, project.name));
    }

    public static Result editPostForm(String userName, String projectName, Long postId) {
        Post existPost = Post.findById(postId);
        Form<Post> editForm = new Form<Post>(Post.class).fill(existPost);
        Project project = ProjectApp.getProject(userName, projectName);


        if (AccessControl.isAllowed(UserApp.currentUser().id, project.id, Resource.BOARD_POST, Operation.EDIT, postId)) {
            return ok(editPost.render("board.post.modify", editForm, postId, project));
        } else {
            flash(Constants.WARNING, "board.notAuthor");
            return redirect(routes.BoardApp.post(project.owner, project.name, postId));
        }
    }

    public static Result editPost(String userName, String projectName, Long postId) {
        Form<Post> postForm = new Form<Post>(Post.class).bindFromRequest();
        Project project = ProjectApp.getProject(userName, projectName);

        if (postForm.hasErrors()) {
            flash(Constants.WARNING, "board.post.empty");
            return redirect(routes.BoardApp.editPost(userName, projectName, postId));
        } else {

            Post post = postForm.get();
            post.authorId = UserApp.currentUser().id;
            post.authorName = UserApp.currentUser().name;
            post.id = postId;
            post.filePath = saveFile(request());
            post.project = project;

            Post.edit(post);

            // Attach the files in the current user's temporary storage.
            Attachment.attachFiles(UserApp.currentUser().id, project.id, Resource.BOARD_POST, post.id);
        }

        return redirect(routes.BoardApp.posts(project.owner, project.name));

    }

    public static Result deleteComment(String userName, String projectName, Long postId, Long commentId) {
        Comment.delete(commentId);
        Post.countDownCommentCounter(postId);
        Attachment.deleteAll(Resource.BOARD_COMMENT, commentId);
        return redirect(routes.BoardApp.post(userName, projectName, postId));
    }

    private static String saveFile(Request request) {
        MultipartFormData body = request.body().asMultipartFormData();

        FilePart filePart = body.getFile("filePath");

        if (filePart != null) {
            File saveFile = new File("public/uploadFiles/" + filePart.getFilename());
            filePart.getFile().renameTo(saveFile);
            return filePart.getFilename();
        }
        return null;
    }

}
