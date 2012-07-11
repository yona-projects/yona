package controllers;

import models.Issue;
import models.User;
import play.data.Form;
import play.mvc.*;
import play.mvc.Http.MultipartFormData;

import views.html.issue.*;

public class IssueApp extends Controller {

    public static Result issueList(int pageNum){
        return ok(list.render("이슈", Issue.findOnePage(pageNum)));
    }

    
//     public static Result newIssue() {
//     return ok(newIssue.render("새 이슈"), new Form<Issue>(Issue.class));
//     }

//     public static Result saveIssue() {
//     // TODO form에 있는 정보 받아와서 DB에저장 파일 세이브도 구현할것
//     Form<Issue> form = new Form<Issue>(Issue.class).bindFromRequest();
//    
//     if (form.hasErrors()) {
//     return ok("입력값이 잘못되었습니다.");
//     } else {
//     Issue issue = form.get();
//     issue.writerId = User.findByName("hobi").id;// 유저의 정보가 받아와서 넣을것
//    
//     MultipartFormData body = request().body().asMultipartFormData();
//    
//     // FilePart filePart = body.getFile("filePath");
//     //
//     // File saveFile = new File("public/uploadFiles/" +
//     // filePart.getFilename());
//     // filePart.getFile().renameTo(saveFile);
//     // article.filePath = saveFile.getAbsolutePath();
//     Issue.create(issue);
//     }
//     return redirect(routes.IssueApp.issueList(1));
//     }

//    public static Result delete(int issueNum) {
//        Issue.delete(issueNum);
//        return redirect(routes.IssueApp.issueList(1));
//    }

}
