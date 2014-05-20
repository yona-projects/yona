/*
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Changsung Kim
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

import models.Issue;
import models.User;
import play.mvc.Call;
import play.mvc.With;
import models.Project;
import play.mvc.Result;
import play.mvc.Controller;
import play.db.ebean.Transactional;
import actions.AnonymousCheckAction;
import models.enumeration.ResourceType;
import controllers.annotation.IsCreatable;

import java.util.ArrayList;
import java.util.List;

/**
 * 이슈에서 투표하기 위한 Controller
 */
@With(AnonymousCheckAction.class)
public class VoteApp extends Controller {

    /**
     * 투표하기
     *
     * 투표요청이 들어온 이슈에서 로그인 사용자의 투표가 등록된다.
     *
     * @param ownerName
     * @param projectName
     * @param issueNumber
     * @return
     */
    @Transactional
    @IsCreatable(ResourceType.ISSUE_COMMENT)
    public static Result vote(String ownerName, String projectName, Long issueNumber) {

        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, issueNumber);

        issue.addVoter(UserApp.currentUser());

        Call call = routes.IssueApp.issue(ownerName, projectName, issueNumber);

        return redirect(call);
    }

    /**
     * 투표 취소하기
     *
     * 투표요청이 들어온 이슈에서 로그인 사용자의 투표가 취소된다.
     *
     * @param ownerName
     * @param projectName
     * @param issueNumber
     * @return
     */
    @Transactional
    @IsCreatable(ResourceType.ISSUE_COMMENT)
    public static Result unvote(String ownerName, String projectName, Long issueNumber) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        Issue issue = Issue.findByNumber(project, issueNumber);

        issue.removeVoter(UserApp.currentUser());

        Call call = routes.IssueApp.issue(ownerName, projectName, issueNumber);

        return redirect(call);
    }

    public static List<User> getVotersForAvatar(List<User> voters, int size){
        return getSubList(voters, 0, size);
    }

    public static List<User> getVotersForName(List<User> voters, int fromIndex, int size){
        return getSubList(voters, fromIndex, fromIndex + size);
    }

    public static List<User> getVotersExceptCurrentUser(List<User> voters){
        List<User> result = new ArrayList<User>(voters);

        while(result.contains(UserApp.currentUser())){
            result.remove(UserApp.currentUser());
        }

        return result;
    }

    /**
     * Get subList of voters within its size.
     *
     * @param fromIndex
     * @param toIndex
     * @return
     */
    private static List<User> getSubList(List<User> voters, int fromIndex, int toIndex) {
        try {
            return voters.subList(
                    Math.max(0, fromIndex),
                    Math.min(voters.size(), toIndex)
            );
        } catch(IndexOutOfBoundsException e){
            play.Logger.warn("Failed to get subList of voters", e);
            return new ArrayList<User>();
        }
    }

}
