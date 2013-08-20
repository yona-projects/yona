package controllers;

import models.Project;
import models.PullRequest;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.List;

/**
 * 없던 컬럼을 추가하거나 기존 데이터를 변경할 때 사용하는 데이터 교정용 컨트롤러 입니다.
 *
 * @author Keesun Baik
 */
public class DataSyncApp extends Controller {

    /**
     * {@code PullRequest}의 PullRequest#number 값을 초기화 합니다.
     * @return
     */
    @Transactional
    public static Result initPullRequestNumbers() {
        List<Project> projects = Project.find.all();
        for(Project project : projects) {
            List<PullRequest> pullRequests = PullRequest.findByToProject(project);
            for(PullRequest pullRequest : pullRequests) {
                if(pullRequest.number == null) {
                    pullRequest.number = project.nextPullRequestNumber();
                    pullRequest.update();
                }
            }
            project.update();
        }

        return ok("done!");
    }

}
