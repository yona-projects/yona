package models;

import static org.junit.Assert.*;

import org.junit.*;

import static play.test.Helpers.*;
import play.test.*;
import static org.fest.assertions.Assertions.*;

public class ProjectTest {
	public static FakeApplication fakeApp;
	
	@BeforeClass
	public static void startApp(){
		fakeApp = Helpers.fakeApplication(Helpers.inMemoryDatabase());
		Helpers.start(fakeApp);
	}

	@Test
	public void testCreate() {
		 running(fakeApplication(), new Runnable() {
			public void run() {
				Project prj = new Project();
				prj.name = "prj_test";
				prj.overview = "Overview for prj_test";
				prj.share_option = false;
				prj.vcs = "GIT";
				Project.create(prj);
				
				Project actualProject = Project.findById(prj.id);
				
				assertEquals("prj_test", actualProject.name);
		        assertEquals("Overview for prj_test", actualProject.overview);
		        assertEquals(false, actualProject.share_option);
		        assertEquals("GIT", actualProject.vcs);
			}
		});
	}
	
	@AfterClass
	public static void stopApp(){
		Helpers.stop(fakeApp);
	}


}
