package controllers;

import com.avaje.ebean.Page;

import models.Issue;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.issue.issueList;
import views.html.issue.newIssue;

public class IssueApp extends Controller {

    public static Result issueList(int pageNum, int status) {
        Page<Issue> page = null;
        if (status == Issue.STATUS_OPEN) {
            page = Issue.findOnlyOpenIssues(pageNum);
        } else if (status == Issue.STATUS_CLOSED) {
            page = Issue.findOnlyClosedIssues(pageNum);
        } else if (status == Issue.STATUS_NONE) {
            page = Issue.findOnePage(pageNum);
        } else {
            page = Issue.findOnePage(pageNum);
        }

        return ok(issueList.render("이슈", page));
    }

    public static Result newIssue() {
        return ok(newIssue.render("새 이슈", new Form<Issue>(Issue.class)));
    }
    
//    public static Result findByTitle(){
//        
//    }

}
