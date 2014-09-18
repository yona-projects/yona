/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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

import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import controllers.annotation.IsOnlyGitAvailable;
import models.Project;
import models.enumeration.Operation;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.GitBranch;
import playRepository.GitRepository;
import views.html.code.branches;

import java.io.IOException;
import java.util.List;

/**
 * @author Keesun Baik
 */
@IsOnlyGitAvailable
@AnonymousCheck
public class BranchApp extends Controller {

    @IsAllowed(Operation.READ)
    public static Result branches(String loginId, String projectName) throws IOException, GitAPIException {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        GitRepository gitRepository = new GitRepository(project);
        List<GitBranch> allBranches = gitRepository.getBranches();
        final GitBranch headBranch = gitRepository.getHeadBranch();

        // filter the head branch from all branch list.
        CollectionUtils.filter(allBranches, new Predicate() {
            @Override
            public boolean evaluate(Object o) {
                GitBranch gitBranch = (GitBranch)o;
                return !gitBranch.getName().equals(headBranch.getName());
            }
        });

        return ok(branches.render(project, allBranches, headBranch));
    }

    @IsAllowed(Operation.DELETE)
    public static Result deleteBranch(String loginId, String projectName, String branchName) throws GitAPIException {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        Repository repository = GitRepository.buildGitRepository(project);
        GitRepository.deleteBranch(repository, branchName);
        return redirect(routes.BranchApp.branches(loginId, projectName));
    }

    @IsAllowed(Operation.UPDATE)
    public static Result setAsDefault(String loginId, String projectName, String branchName) throws IOException, GitAPIException {
        Project project = Project.findByOwnerAndProjectName(loginId, projectName);
        GitRepository gitRepository = new GitRepository(project);
        gitRepository.setDefaultBranch(branchName);

        return utils.HttpUtil.isRequestedWithXHR(request()) ? ok() : redirect(routes.BranchApp.branches(loginId, projectName));
    }
}
