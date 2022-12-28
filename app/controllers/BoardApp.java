/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package controllers;

import actions.NullProjectCheckAction;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsCreatable;
import models.*;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
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
import views.html.organization.group_board_list;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

import static com.avaje.ebean.Expr.icontains;
import static controllers.MigrationApp.composePlainCommentsJson;
import static play.libs.Json.toJson;
import static utils.JodaDateUtil.getOptionalShortDate;

public class BoardApp extends AbstractPostingApp {
    public static class SearchCondition extends AbstractPostingApp.SearchCondition {
        public List<String> projectNames;
        public String [] labelIds;
        public Set<Long> labelIdSet = new HashSet<>();
        private ExpressionList<Posting> asExpressionList(Project project) {
            ExpressionList<Posting> el = Posting.finder.where().eq("project.id", project.id);

            if (filter != null) {
                el.or(icontains("title", filter), icontains("body", filter));
            }

            if (CollectionUtils.isNotEmpty(labelIdSet)) {
                Set<IssueLabel> labels = IssueLabel.finder.where().idIn(new ArrayList<>(labelIdSet)).findSet();
                el.in("id", Posting.finder.where().in("labels", labels).findIds());
            }

            if (StringUtils.isNotBlank(orderBy)) {
                el.orderBy(orderBy + " " + orderDir);
            }

            return el;
        }

        private ExpressionList<Posting> asExpressionList(@Nonnull Organization organization) {
            ExpressionList<Posting> el = Posting.finder.where();

            if(isFilteredByProject()){
                el.in("project.id", getFilteredProjectIds(organization));
            } else {
                el.in("project.id", getVisibleProjectIds(organization));
            }

            if (filter != null) {
                el.or(icontains("title", filter), icontains("body", filter));
            }

            if (StringUtils.isNotBlank(orderBy)) {
                el.orderBy(orderBy + " " + orderDir);
            }

            return el;
        }

        private boolean isFilteredByProject() {
            return CollectionUtils.isNotEmpty(projectNames);
        }

        private List<Long> getFilteredProjectIds(@Nonnull Organization organization) {
            List<Long> projectIdsFilter = new ArrayList<>();
            for(String projectName: projectNames){
                for(Project project: organization.projects){
                    if(project.name.equalsIgnoreCase(projectName)
                            && getVisibleProjectIds(organization).contains(project.id.toString())) {
                        projectIdsFilter.add(project.id);
                        break;
                    }
                }
            }
            return projectIdsFilter;
        }

        private List<String> getVisibleProjectIds(Organization organization) {
            List<Project> projects = organization.getVisibleProjects(UserApp.currentUser());
            List<String> projectsIds = new ArrayList<String>();
            for (Project project : projects) {
                projectsIds.add(project.id.toString());
            }
            return projectsIds;
        }
    }

    @AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)
    public static Result organizationBoards(@Nonnull String organizationName, int pageNum) {

        Form<SearchCondition> postParamForm = new Form<>(SearchCondition.class);
        SearchCondition searchCondition = postParamForm.bindFromRequest().get();
        searchCondition.pageNum = pageNum - 1;
        if (searchCondition.orderBy.equals("id")) {
            searchCondition.orderBy = "createdDate";
        }

        Organization organization = Organization.findByName(organizationName);
        if (organization == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound.organization"));
        }
        ExpressionList<Posting> el = searchCondition.asExpressionList(organization);
        Page<Posting> posts = el.findPagingList(ITEMS_PER_PAGE).getPage(searchCondition.pageNum);

        return ok(group_board_list.render("menu.board", organization, posts, searchCondition, null));
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
        searchCondition.labelIdSet.addAll(LabelApp.getLabelIds(request()));
        searchCondition.labelIdSet.remove(null);

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

        if(issueTemplateEditRequested()){
            preparedBodyText = StringUtils.defaultIfBlank(project.getIssueTemplate(), "");
        }

        if (textFileEditRequested()) {
            boolean isAllowedToFileEdit =
                    AccessControl.isProjectResourceCreatable(UserApp.currentUser(), project, ResourceType.COMMIT);
            if(!isAllowedToFileEdit){
                return forbidden(ErrorViews.Forbidden.render("error.forbidden", project));
            }
            preparedBodyText = GitUtil.getReadTextFile(project,
                    getBranchNameFromQueryString(), request().getQueryString("path"));
        }

        return ok(create.render("post.new", new Form<>(Posting.class), project, isAllowedToNotice, preparedBodyText));
    }

    private static boolean projectHasReadme(Project project) {
        return project.readme() != null;
    }

    private static boolean readmeEditRequested() {
        return request().getQueryString("readme") != null;
    }

    private static boolean issueTemplateEditRequested() {
        return request().getQueryString("issueTemplate") != null;
    }

    private static boolean textFileEditRequested() {
        return request().getQueryString("path") != null;
    }

    private static String getBranchNameFromQueryString() {
        return request().getQueryString("branch");
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

        if (post.issueTemplate.equals("true")) {
            commitIssueTemplateFile(project, post);
            return redirect(routes.ProjectApp.project(project.owner, projectName));
        }

        if(StringUtils.isNotEmpty(post.path) && UserApp.currentUser().isMemberOf(project)){
            GitUtil.commitTextFile(project, post.branch, post.path,
                    LineEnding.changeLineEnding(post.body, post.lineEnding), post.title);
            return redirect(routes.CodeApp.codeBrowserWithBranch(project.owner, project.name, post.branch, HttpUtil.getEncodeEachPathName(post.path)));
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
            ObjectId objectId = bare.commitTextFile("README.md", post.body, post.title);
            play.Logger.debug("Online Commit: README " + project.name + ":" + objectId);
        } catch (IOException e) {
            e.printStackTrace();
            play.Logger.error(e.getMessage());
        }
    }

    private static void commitIssueTemplateFile(Project project, Posting post){
        BareCommit bare = new BareCommit(project, UserApp.currentUser());
        try{
            ObjectId objectId = bare.commitTextFile("ISSUE_TEMPLATE.md", post.body, post.title);
            play.Logger.debug("Online Commit: ISSUE_TEMPLATE " + project.name + ":" + objectId);
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
            post.body = StringUtils.defaultString(BareRepository.readREADME(project));
        }

        if (request().getHeader("Accept").contains("application/json")) {
            ObjectNode json = Json.newObject();
            json.put("title", post.title);
            json.put("type", post.asResource().getType().toString());
            json.put("author", post.authorLoginId);
            json.put("authorName", post.authorName);
            json.put("created_at", post.createdDate.getTime());
            json.put("body", post.body);
            json.put("attachments", toJson(Attachment.findByContainer(post.asResource())));
            json.put("comments", toJson(composePlainCommentsJson(post, ResourceType.NONISSUE_COMMENT)));
            return ok(json);
        }

        UserApp.currentUser().visits(project);
        UserApp.currentUser().visits(post);

        Form<PostingComment> commentForm = new Form<>(PostingComment.class);
        return ok(view.render(post, commentForm, project));
    }

    @With(NullProjectCheckAction.class)
    public static Result editPostForm(String owner, String projectName, Long number) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        Posting posting = Posting.findByNumber(project, number);

        if (!AccessControl.isAllowed(UserApp.currentUser(), posting.asResource(), Operation.READ)) {
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
                if(isSelectedToSendNotificationMail() || !original.isAuthoredBy(UserApp.currentUser())){
                    NotificationEvent.afterUpdatePosting(original.body, post);
                }
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

        NotificationEvent.afterResourceDeleted(posting, UserApp.currentUser());
        return delete(posting, posting.asResource(), redirectTo);
    }

    /**
     * @see controllers.AbstractPostingApp#saveComment(Comment comment, Runnable containerUpdater)
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

        if (commentForm.hasErrors()) {
            flash(Constants.WARNING, "common.comment.empty");
            return redirect(routes.BoardApp.post(project.owner, project.name, number));
        }

        if(StringUtils.isNotEmpty(comment.parentCommentId)){
            comment.setParentComment(PostingComment.find.byId(Long.valueOf(comment.parentCommentId)));
        }

        AddPreviousContent(posting, comment);

        Comment savedComment = saveComment(project, posting, comment);

        return redirect(RouteUtil.getUrl(savedComment));
    }

    private static void AddPreviousContent(Posting posting, PostingComment comment) {
        if(posting.numOfComments == 0) {
            comment.previousContents = getPrevious("Original posting", posting.body, posting.updatedDate, posting.authorLoginId);
        } else {
            Comment previousComment;
            if (comment.parentCommentId != null) {
                List<PostingComment> siblingComments = comment.getSiblingComments();
                if (siblingComments.size() > 0) {
                    previousComment = siblingComments.get(siblingComments.size() - 1);
                } else {
                    previousComment = comment.getParentComment();
                }
            } else {
                previousComment = posting.comments.get(posting.comments.size() - 1);
            }
            comment.previousContents = getPrevious("Previous comment", previousComment.contents, previousComment.createdDate, previousComment.authorLoginId);
        }
    }

    private static String getPrevious(String templateTitle, String contents, Date updatedDate, String authorLoginId) {
        return "\n\n<br />\n\n--- " + templateTitle + " from @" + authorLoginId + "  " + getOptionalShortDate(updatedDate) + " ---\n\n<br />\n\n" + contents;
    }

    // Just made for compatibility. No meanings.
    public static Result updateComment(String ownerName, String projectName, Long number, Long commentId) throws IOException {
        return newComment(ownerName, projectName, number);
    }

    private static Comment saveComment(Project project, Posting posting, PostingComment comment) {
        Comment savedComment;
        PostingComment existingComment = (PostingComment)PostingComment.find.where().eq("id", comment.id).findUnique();
        if (existingComment != null) {
            existingComment.contents = comment.contents;
            savedComment = saveComment(existingComment, getContainerUpdater(posting, comment));
            if(isSelectedToSendNotificationMail() || !existingComment.isAuthoredBy(UserApp.currentUser())){
                NotificationEvent.afterCommentUpdated(savedComment);
            }
        } else {
            comment.projectId = project.id;
            savedComment = saveComment(comment, getContainerUpdater(posting, comment));
            NotificationEvent.afterNewComment(savedComment);
        }
        return savedComment;
    }

    private static Runnable getContainerUpdater(final Posting posting, final PostingComment comment) {
        return new Runnable() {
            @Override
            public void run() {
                posting.updatedDate = JodaDateUtil.now();
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
