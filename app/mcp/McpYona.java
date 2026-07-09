/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package mcp;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Comment;
import models.Issue;
import models.IssueComment;
import models.Milestone;
import models.Posting;
import models.PostingComment;
import models.Project;
import models.User;
import models.enumeration.Operation;
import play.libs.Json;
import utils.AccessControl;
import utils.JodaDateUtil;

import java.util.Date;

public final class McpYona {
    private McpYona() {
    }

    public static Project readableProject(User user, String owner, String projectName) {
        Project project = Project.findByOwnerAndProjectName(owner, projectName);
        if (project == null || !canRead(user, project)) {
            throw new IllegalArgumentException("Project not found or not readable: " + owner + "/" + projectName);
        }
        return project;
    }

    public static boolean canRead(User user, Project project) {
        return project != null && AccessControl.isAllowed(user, project.asResource(), Operation.READ);
    }

    public static boolean canRead(User user, Issue issue) {
        return issue != null
                && issue.project != null
                && canRead(user, issue.project)
                && AccessControl.isAllowed(user, issue.asResource(), Operation.READ);
    }

    public static boolean canRead(User user, Posting posting) {
        return posting != null
                && posting.project != null
                && canRead(user, posting.project)
                && AccessControl.isAllowed(user, posting.asResource(), Operation.READ);
    }

    public static boolean canRead(User user, Milestone milestone) {
        return milestone != null
                && milestone.project != null
                && canRead(user, milestone.project)
                && AccessControl.isAllowed(user, milestone.asResource(), Operation.READ);
    }

    public static ObjectNode projectSummary(Project project) {
        ObjectNode node = Json.newObject();
        node.put("id", project.id);
        node.put("owner", project.owner);
        node.put("name", project.name);
        node.put("fullName", project.owner + "/" + project.name);
        node.put("scope", project.projectScope == null ? null : project.projectScope.name());
        node.put("vcs", project.vcs);
        node.put("overview", project.overview);
        node.put("createdDate", date(project.createdDate));
        node.put("url", controllers.routes.ProjectApp.project(project.owner, project.name).url());
        return node;
    }

    public static ObjectNode projectDetail(Project project) {
        ObjectNode node = projectSummary(project);
        node.put("lastIssueNumber", lastIssueNumber(project));
        node.put("lastPostingNumber", lastPostingNumber(project));
        node.put("memberCount", project.members().size());
        node.put("issueCount", Issue.finder.query().where().eq("project.id", project.id).findCount());
        node.put("postCount", Posting.finder.query().where().eq("project.id", project.id).findCount());
        node.put("milestoneCount", Milestone.find.query().where().eq("project.id", project.id).findCount());
        return node;
    }

    public static ObjectNode issueSummary(Issue issue) {
        ObjectNode node = Json.newObject();
        node.put("id", issue.id);
        node.put("number", issue.getNumber());
        node.put("title", issue.title);
        node.put("state", issue.state == null ? null : issue.state.state());
        node.put("authorLoginId", issue.authorLoginId);
        node.put("authorName", issue.authorName);
        node.put("createdDate", date(issue.createdDate));
        node.put("updatedDate", date(issue.updatedDate));
        node.put("numOfComments", issue.comments == null ? issue.numOfComments : issue.computeNumOfComments());
        node.put("url", controllers.routes.IssueApp.issue(issue.project.owner, issue.project.name, issue.getNumber()).url());
        if (issue.assignee != null && issue.assignee.user != null) {
            node.put("assigneeLoginId", issue.assignee.user.loginId);
            node.put("assigneeName", issue.assignee.user.name);
        }
        if (issue.milestone != null) {
            node.put("milestone", issue.milestone.title);
        }
        return node;
    }

    public static ObjectNode issueDetail(Issue issue) {
        ObjectNode node = issueSummary(issue);
        node.put("body", issue.body);
        ArrayNode comments = Json.newArray();
        for (Comment comment : issue.getComments()) {
            if (comment instanceof IssueComment) {
                comments.add(commentNode(comment));
            }
        }
        node.set("comments", comments);
        return node;
    }

    public static ObjectNode postingSummary(Posting posting) {
        ObjectNode node = Json.newObject();
        node.put("id", posting.id);
        node.put("number", posting.getNumber());
        node.put("title", posting.title);
        node.put("authorLoginId", posting.authorLoginId);
        node.put("authorName", posting.authorName);
        node.put("createdDate", date(posting.createdDate));
        node.put("updatedDate", date(posting.updatedDate));
        node.put("numOfComments", posting.comments == null ? posting.numOfComments : posting.computeNumOfComments());
        node.put("notice", posting.notice);
        node.put("url", controllers.routes.BoardApp.post(posting.project.owner, posting.project.name, posting.getNumber()).url());
        return node;
    }

    public static ObjectNode postingDetail(Posting posting) {
        ObjectNode node = postingSummary(posting);
        node.put("body", posting.body);
        ArrayNode comments = Json.newArray();
        for (Comment comment : posting.getComments()) {
            if (comment instanceof PostingComment) {
                comments.add(commentNode(comment));
            }
        }
        node.set("comments", comments);
        return node;
    }

    public static ObjectNode milestoneSummary(Milestone milestone) {
        ObjectNode node = Json.newObject();
        Project project = fullyLoadedProject(milestone.project);
        node.put("id", milestone.id);
        node.put("title", milestone.title);
        node.put("state", milestone.state == null ? null : milestone.state.state());
        node.put("dueDate", date(milestone.dueDate));
        node.put("numOpenIssues", milestone.getNumOpenIssues());
        node.put("numClosedIssues", milestone.getNumClosedIssues());
        node.put("url", controllers.routes.MilestoneApp.milestone(
                project.owner, project.name, milestone.id).url());
        return node;
    }

    public static ObjectNode milestoneDetail(Milestone milestone) {
        ObjectNode node = milestoneSummary(milestone);
        node.put("contents", milestone.contents);
        return node;
    }

    private static ObjectNode commentNode(Comment comment) {
        ObjectNode node = Json.newObject();
        node.put("id", comment.id);
        node.put("authorLoginId", comment.authorLoginId);
        node.put("authorName", comment.authorName);
        node.put("contents", comment.contents);
        node.put("createdDate", date(comment.createdDate));
        return node;
    }

    private static String date(Date date) {
        return date == null ? null : JodaDateUtil.getDateString(date);
    }

    private static long lastIssueNumber(Project project) {
        Issue issue = Issue.finder.query().where()
                .eq("project.id", project.id)
                .orderBy().desc("number")
                .setMaxRows(1)
                .findOne();
        return issue == null || issue.getNumber() == null ? 0L : issue.getNumber();
    }

    private static long lastPostingNumber(Project project) {
        Posting posting = Posting.finder.query().where()
                .eq("project.id", project.id)
                .orderBy().desc("number")
                .setMaxRows(1)
                .findOne();
        return posting == null || posting.getNumber() == null ? 0L : posting.getNumber();
    }

    private static Project fullyLoadedProject(Project project) {
        if (project == null || project.id == null) {
            return project;
        }
        if (project.owner != null && project.name != null) {
            return project;
        }
        return Project.find.byId(project.id);
    }
}
