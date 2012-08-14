/**
 * @author Ahn Hyeok Jun
 */

package controllers;

import java.io.File;
import models.Comment;
import models.Post;
import models.Post.Param;
import models.Project;
import models.enumeration.Direction;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.Request;
import play.mvc.Result;
import views.html.board.boardError;
import views.html.board.editPost;
import views.html.board.newPost;
import views.html.board.notExsitPage;
import views.html.board.postList;

public class BoardApp extends Controller {

    public static Result posts(String ownerName, String projectName) {

        Form<Post.Param> postParamForm = new Form<Post.Param>(Post.Param.class);
        Param postParam = postParamForm.bindFromRequest().get();
        Project project = Project.findByNameAndOwner(ownerName, projectName);

        return ok(postList.render(
                "게시판",
                project,
                Post.findOnePage(project.name, postParam.pageNum,
                        Direction.getValue(postParam.order), postParam.key), postParam));
    }

    public static Result newPost(String ownerName, String projectName) {
        Project project = Project.findByNameAndOwner(ownerName, projectName);
        return ok(newPost.render("새 게시물", new Form<Post>(Post.class), project));
    }

    public static Result savePost(String ownerName, String projectName) {
        Form<Post> postForm = new Form<Post>(Post.class).bindFromRequest();
        Project project = Project.findByNameAndOwner(ownerName, projectName);
        if (postForm.hasErrors()) {
            return ok(boardError.render("본문과 제목은 반드시 써야합니다.",
                    routes.BoardApp.newPost(project.owner, project.name), project));
        } else {
            Post post = postForm.get();
            post.authorId = UserApp.currentUser().id;
            post.commentCount = 0;
            post.filePath = saveFile(request());
            post.project = project;
            Post.write(post);
        }

        return redirect(routes.BoardApp.posts(project.owner, project.name));
    }

    public static Result post(String ownerName, String projectName, Long postId) {
        Post post = Post.findById(postId);
        Project project = Project.findByNameAndOwner(ownerName, projectName);
        if (post == null) {
            return ok(notExsitPage.render("존재하지 않는 게시물", project));
        } else {
            Form<Comment> commentForm = new Form<Comment>(Comment.class);
            return ok(views.html.board.post.render(post, commentForm, project));
        }
    }

    public static Result saveComment(String ownerName, String projectName, Long postId) {
        Form<Comment> commentForm = new Form<Comment>(Comment.class).bindFromRequest();

        Project project = Project.findByNameAndOwner(ownerName, projectName);
        if (commentForm.hasErrors()) {
            return ok(boardError.render("본문은 반드시 쓰셔야 합니다.",
                    routes.BoardApp.post(project.owner, project.name, postId), project));

        } else {
            Comment comment = commentForm.get();
            comment.post = Post.findById(postId);
            comment.authorId = UserApp.currentUser().id;
            comment.filePath = saveFile(request());

            Comment.write(comment);
            Post.countUpCommentCounter(postId);

            return redirect(routes.BoardApp.post(project.owner, project.name, postId));
        }
    }

    public static Result deletePost(String ownerName, String projectName, Long postId) {
        Project project = Project.findByNameAndOwner(ownerName, projectName);
        Post.delete(postId);
        return redirect(routes.BoardApp.posts(project.owner, project.name));
    }

    public static Result editPost(String ownerName, String projectName, Long postId) {
        Post existPost = Post.findById(postId);
        Form<Post> editForm = new Form<Post>(Post.class).fill(existPost);
        Project project = Project.findByNameAndOwner(ownerName, projectName);

        if (UserApp.currentUser().id == existPost.authorId) {
            return ok(editPost.render("게시물 수정", editForm, postId, project));
        } else {
            return ok(boardError.render("글쓴이가 아닙니다.",
                    routes.BoardApp.post(project.owner, project.name, postId), project));
        }
    }

    public static Result updatePost(String ownerName, String projectName, Long postId) {
        Form<Post> postForm = new Form<Post>(Post.class).bindFromRequest();
        Project project = Project.findByNameAndOwner(ownerName, projectName);

        if (postForm.hasErrors()) {
            return ok("입력값이 잘못되었습니다.");
        } else {

            Post post = postForm.get();
            post.authorId = UserApp.currentUser().id;
            post.id = postId;
            post.filePath = saveFile(request());
            post.project = project;

            Post.edit(post);
        }

        return redirect(routes.BoardApp.posts(project.owner, project.name));
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
