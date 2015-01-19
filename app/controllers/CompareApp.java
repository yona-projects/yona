/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author kjkmadness
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
import models.Project;
import models.enumeration.Operation;
import play.mvc.Controller;
import play.mvc.Result;
import playRepository.Commit;
import playRepository.FileDiff;
import playRepository.PlayRepository;
import playRepository.RepositoryService;
import utils.ErrorViews;
import views.html.code.compare;
import views.html.code.compare_svn;

import java.util.List;

@AnonymousCheck
public class CompareApp extends Controller {
    @IsAllowed(Operation.READ)
    public static Result compare(String ownerName, String projectName, String revA, String revB)
            throws Exception {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        PlayRepository repository = RepositoryService.getRepository(project);
        Commit commitA = repository.getCommit(revA);
        Commit commitB = repository.getCommit(revB);

        if (commitA == null || commitB == null) {
            return notFound(ErrorViews.NotFound.render("error.notfound.commit", project));
        }

        if (RepositoryService.VCS_SUBVERSION.equals(project.vcs)) {
            String patch = repository.getPatch(revA, revB);
            if (patch == null) {
                return notFound(ErrorViews.NotFound.render("error.notfound", project));
            }
            return ok(compare_svn.render(project, commitA, commitB, patch));
        } else {
            List<FileDiff> diffs = repository.getDiff(revA, revB);
            if (diffs == null) {
                return notFound(ErrorViews.NotFound.render("error.notfound", project));
            }
            return ok(compare.render(project, commitA, commitB, diffs));
        }
    }
}
