/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jungkook Kim
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
package support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.Issue;
import models.Posting;
import models.Project;
import models.PullRequest;

import play.Application;
import play.GlobalSettings;
import play.test.FakeApplication;
import utils.YamlUtil;

public class Helpers {
    public static Map<String, String> makeTestConfig() {
        HashMap<String, String> config = new HashMap<>(play.test.Helpers.inMemoryDatabase());
        config.put("ebean.default", "models.*");
        config.put("application.secret", "foo");
        config.put("application.context", "/");
        config.put("smtp.user", "yobi");
        return config;
    }

    public static GlobalSettings makeTestGlobal() {
        return new GlobalSettings() {
            @Override
            public void onStart(Application application) {
                insertInitialData();
                insertTestData();
                PullRequest.regulateNumbers();
            }
        };
    }

    public static FakeApplication makeTestApplication(Map<String, String> config) {
        return play.test.Helpers.fakeApplication(config, makeTestGlobal());
    }

    public static FakeApplication makeTestApplication() {
        return makeTestApplication(makeTestConfig());
    }

    public static FakeApplication makeTestApplicationWithServiceGlobal(Map<String, String> config) {
        return play.test.Helpers.fakeApplication(config);
    }

    public static FakeApplication makeTestApplicationWithServiceGlobal() {
        return makeTestApplicationWithServiceGlobal(makeTestConfig());
    }

    public static void insertInitialData() {
        YamlUtil.insertDataFromYaml("initial-data.yml", new String[] {"users", "roles", "siteAdmins"});
    }

    public static void insertTestData() {
        YamlUtil.insertDataFromYaml("test-data.yml", new String[] {
                "users", "projects", "pullRequests", "milestones",
                "issues", "issueComments", "postings",
                "postingComments", "projectUsers", "organization", "organizationUsers", "projectMenuSettings"});
        // Do numbering for issues and postings.
        for (Project project : Project.find.findList()) {
            List<Issue> issues = Issue.finder.where()
                    .eq("project.id", project.id).orderBy("id desc")
                    .findList();

            for (Issue issue : issues) {
                issue.save();
            }

            List<Posting> postings = Posting.finder.where()
                    .eq("project.id", project.id).orderBy("id desc")
                    .findList();

            for (Posting posting : postings) {
                posting.save();
            }
        }
    }
}
