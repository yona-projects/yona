/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Sangcheol Hwang
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

import actions.NullProjectCheckAction;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;

import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;

import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.data.Form;
import play.db.ebean.Transactional;
import play.libs.Json;
import play.mvc.Call;
import play.mvc.Result;
import play.mvc.With;
import playRepository.BareCommit;
import playRepository.BareRepository;
import playRepository.RepositoryService;
import utils.*;
import views.html.board.create;
import views.html.board.edit;
import views.html.board.list;
import views.html.board.view;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.avaje.ebean.Expr.icontains;

public class BoardApp extends AbstractPostingApp {
    public static class SearchCondition extends AbstractPostingApp.SearchCondition {
        private ExpressionList<Posting> asExpressionList(Project project) {
            ExpressionList<Posting> el = Posting.finder.where().eq("project.id", project.id);

            if (filter != null) {
                el.or(icontains("title", filter), icontains("body", filter));
            }

            if (StringUtils.isNotBlank(orderBy)) {
                el.orderBy(orderBy + " " + orderDir);
            }

            return el;
        }
    }

    @IsAllowed(value = Operation.READ, resourceType = ResourceType.PROJECT)
    public static Result posts(String userName, String projectName, int pageNum) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        Form<SearchCondition> postParamForm = new Form<>(SearchCondition.class);
        SearchCondition searchCondition = postParamForm.bindFromRequest().get();
        searchCondition.pageNum = pageNum - 1;
        if (searchCondition.orderBy.equals("id")) {
            searchCondition.orderBy = "createdDate";
        }

        ExpressionList<Posting> el = searchCondition.asExpressionList(project);
        el.eq("notice", false);
        Page<Posting> posts = el.findPagingList(ITEMS_PER_PAGE).getPage(searchCondition.pageNum);
        List<Posting> notices = Posting.findNotices(project);

        return ok(list.render("menu.board", project, posts, searchCondition, notices));
    }

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.BOARD_POST)
    public static Result newPostForm(String userName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        boolean isAllowedToNotice =
                AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.BOARD_NOTICE);

        String preparedBodyText = "";
        if(readmeEditRequested() && projectHasReadme(project)){
            preparedBodyText = BareRepository.readREADME(project);
        }

        return ok(create.render("post.new", new Form<>(Posting.class), project, isAllowedToNotice, preparedBodyText));
    }

    private static boolean projectHasReadme(Project project) {
        return project.readme() != null;
    }

    private static boolean readmeEditRequested() {
        return request().getQueryString("readme") != null;
    }

    @Transactional
    @IsCreatable(ResourceType.BOARD_POST)
    public static Result newPost(String userName, String projectName) {
        Form<Posting> postForm = new Form<>(Posting.class).bindFromRequest();
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        if (postForm.hasErrors()) {
            boolean isAllowedToNotice =
                    AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.BOARD_NOTICE);
            return badRequest(create.render("error.validation", postForm, project, isAllowedToNotice, ""));
        }

        final Posting post = postForm.get();

        if (post.body == null) {
            return status(REQUEST_ENTITY_TOO_LARGE, ErrorViews.RequestTextEntityTooLarge.render());
        }

        post.createdDate = JodaDateUtil.now();
        post.updatedDate = JodaDateUtil.now();
        post.setAuthor(UserApp.currentUser());
        post.project = project;

        if (post.readme) {
            Posting readmePosting = Posting.findREADMEPosting(project);
            if (readmePosting != null) {
                return editPost(userName, projectName, readmePosting.getNumber());
            } else {
                commitReadmeFile(project, post);
            }
        }
        post.save();
        attachUploadFilesToPost(post.asResource());
        NotificationEvent.afterNewPost(post);

        if (post.readme) {
            return redirect(routes.ProjectApp.project(userName, projectName));
        }
        return redirect(routes.BoardApp.post(project.owner, project.name, post.getNumber()));
    }

    private static void commitReadmeFile(Project project, Posting post){
        BareCommit bare = new BareCommit(project, UserApp.currentUser());
        try{
            bare.commitTextFile("README.md", post.body, post.title);
        } catch (IOException e) {
            e.printStackTrace();
            play.Logger.error(e.getMessage());
        }
    }

    @IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)
    public static Result post(String userName, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(userName, projectName);
        Posting post = Posting.findByNumber(project, number);

        if(post.readme && RepositoryService.VCS_GIT.equals(project.vcs)){
            post.body = BareRepository.readREADME(project);
        }

        if (request().getHeader("Accept").contains("application/json")) {
            ObjectNode json = Json.newObject();
            json.put("title", post.title);
            json.put("body", post.body);
            json.put("author", post.authorLoginId);
            return ok(json);
        }

        UserApp.currentUser().visits(project);
        Form<PostingComment> commentForm = new Form<>(PostingComment.class);
        return ok(view.render(post, commentForm, project));
    }

    @With(NullProjectCheckAction.class)
    public static Result editPostForm(String owner, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Posting posting = Posting.findByNumber(project, number);

        if (!AccessControl.isAllowed(UserApp.currentUser(), posting.asResource(), Operation.UPDATE)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        Form<Posting> editForm = new Form<>(Posting.class).fill(posting);
        boolean isAllowedToNotice = ProjectUser.isAllowedToNotice(UserApp.currentUser(), project);

        if(posting.readme && RepositoryService.VCS_GIT.equals(project.vcs)){
            posting.body = BareRepository.readREADME(project);
        }
        return ok(edit.render("post.modify", editForm, posting, number, project, isAllowedToNotice));
    }

    /**
     * @see AbstractPostingApp#editPosting(models.AbstractPosting, models.AbstractPosting, play.data.Form, play.mvc.Call, java.lang.Runnable)
     */
    @Transactional
    @With(NullProjectCheckAction.class)
    public static Result editPost(String userName, String projectName, Long number) {
        Form<Posting> postForm = new Form<>(Posting.class).bindFromRequest();
        Project project = Project.findByOwnerAndProjectName(userName, projectName);

        if (postForm.hasErrors()) {
            boolean isAllowedToNotice =
                    AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.BOARD_NOTICE);
            return badRequest(edit.render("error.validation", postForm, Posting.findByNumber(project, number), number, project, isAllowedToNotice));
        }

        final Posting post = postForm.get();
        final Posting original = Posting.findByNumber(project, number);
        if (post.readme) {
            post.setAuthor(UserApp.currentUser());
            commitReadmeFile(project, post);
            unmarkAnotherReadmePostingIfExists(project, number);
        }
        Call redirectTo = routes.BoardApp.post(project.owner, project.name, number);
        Runnable updatePostingBeforeUpdate = new Runnable() {
            @Override
            public void run() {
                post.comments = original.comments;
            }
        };

        return editPosting(original, post, postForm, redirectTo, updatePostingBeforeUpdate);
    }

    private static void unmarkAnotherReadmePostingIfExists(Project project, Long postingNumber) {
        Posting previousReadmePosting = Posting.findREADMEPosting(project);
        if(previousReadmePosting != null && !Objects.equals(previousReadmePosting.getNumber(), postingNumber)){
            previousReadmePosting.readme = false;
            previousReadmePosting.directSave();
        }
    }

    /**
     * @see controllers.AbstractPostingApp#delete(play.db.ebean.Model, models.resource.Resource, play.mvc.Call)
     */
    @Transactional
    @IsAllowed(value = Operation.DELETE, resourceType = ResourceType.BOARD_POST)
    public static Result deletePost(String owner, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Posting posting = Posting.findByNumber(project, number);
        Call redirectTo = routes.BoardApp.posts(project.owner, project.name, 1);

        return delete(posting, posting.asResource(), redirectTo);
    }

    /**
     * @see controllers.AbstractPostingApp#saveComment(models.Comment, play.data.Form, play.mvc.Call, Runnable)
     */
    @Transactional
    @IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)
    @With(NullProjectCheckAction.class)
    public static Result newComment(String owner, String projectName, Long number) throws IOException {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        final Posting posting = Posting.findByNumber(project, number);
        Form<PostingComment> commentForm = new Form<>(PostingComment.class)
                .bindFromRequest();

        if (commentForm.hasErrors()) {
            return badRequest(ErrorViews.BadRequest.render("error.validation", project, MenuType.BOARD));
        }

        if (!AccessControl.isResourceCreatable(
                    UserApp.currentUser(), posting.asResource(), ResourceType.NONISSUE_COMMENT)) {
            return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
        }

        final PostingComment comment = commentForm.get();
        PostingComment existingComment = PostingComment.find.where().eq("id", comment.id).findUnique();

        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "common.comment.empty");
            return redirect(routes.BoardApp.post(project.owner, project.name, number));
        }

        Comment savedComment;
        if (existingComment != null) {
            existingComment.contents = comment.contents;
            savedComment = saveComment(existingComment, getContainerUpdater(posting, comment));
            NotificationEvent.afterCommentUpdated(savedComment);
        } else {
            savedComment = saveComment(comment, getContainerUpdater(posting, comment));
            NotificationEvent.afterNewComment(savedComment);
        }

        return redirect(RouteUtil.getUrl(savedComment));
    }

    private static Runnable getContainerUpdater(final Posting posting, final PostingComment comment) {
        return new Runnable() {
            @Override
            public void run() {
                comment.posting = posting;
            }
        };
    }
    /**
     * @see controllers.AbstractPostingApp#delete(play.db.ebean.Model, models.resource.Resource, play.mvc.Call)
     */
    @Transactional
    @With(NullProjectCheckAction.class)
    public static Result deleteComment(String userName, String projectName, Long number, Long commentId) {
        Comment comment = PostingComment.find.byId(commentId);
        Project project = comment.asResource().getProject();
        Call redirectTo = routes.BoardApp.post(project.owner, project.name, number);

        return delete(comment, comment.asResource(), redirectTo);
    }
}
