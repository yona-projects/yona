package models;

import static org.junit.Assert.assertEquals;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;

import org.junit.Test;

public class IssueTest {

	@Test
	public void testCreate() {
		running(fakeApplication(), new Runnable() {
			public void run() {

//				Issue issue = new Issue();
//				issue.setTitle("Bug on test page");
//				issue.setBody("There is javascript erron on line 75.");
//
//				Issue.create(issue);
//				Issue actualIssue = Issue.findById(issue.getId());
//
//				assertEquals("Bug on test page", actualIssue.getTitle());
//				assertEquals("There is javascript erron on line 75.",
//						actualIssue.getBody());
//
//				Project prj = new Project();
//				prj.name = "prj_test";
//				prj.overview = "Overview for prj_test";
//				prj.share_option = false;
//				prj.vcs = "GIT";
//				Project.create(prj);
//
//				Project actualProject = Project.findById(prj.id);
//
//				assertEquals("prj_test", actualProject.name);
//				assertEquals("Overview for prj_test", actualProject.overview);
//				assertEquals(false, actualProject.share_option);
//				assertEquals("GIT", actualProject.vcs);
//				assertEquals(
//						"http://localhost:9000/project/"
//								+ Long.toString(actualProject.id),
//						actualProject.url);
			}
		});
	}

}
