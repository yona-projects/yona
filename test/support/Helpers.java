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

/**
 * User: nori
 * Date: 13. 7. 16
 * Time: 오후 6:18
 */
public class Helpers {
    public static Map<String, String> makeTestConfig() {
        HashMap<String, String> config = new HashMap<>(play.test.Helpers.inMemoryDatabase());
        config.put("ebean.default", "models.*");
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

    private static void insertInitialData() {
        YamlUtil.insertDataFromYaml("initial-data.yml", new String[] {"users", "roles", "siteAdmins"});
    }

    private static void insertTestData() {
        YamlUtil.insertDataFromYaml("test-data.yml", new String[] {
                "users", "projects", "pullRequests", "milestones",
                "issues", "issueComments", "postings",
                "postingComments", "projectUsers" });
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
