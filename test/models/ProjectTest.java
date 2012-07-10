package models;

/*
 * @author: Hwi Ahn
 * 
 */


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
        assertEquals("http://localhost:9000/project/" + Long.toString(actualProject.id), actualProject.url);
	}
	
	@Test
	public void testUpdate(){
		Project prj1 = new Project();
		
		prj1.name = "prj_test";
		prj1.overview = "Overview for prj_test";
		prj1.share_option = false;
		prj1.vcs = "GIT";
		Long id = Project.create(prj1);
		
		Project prj2 = new Project();
		
		prj2.name = "prj";
		prj2.overview = "Overview for prj_test";
		prj2.share_option = false;
		prj2.vcs = "GIT";
		
		
		Project.update(prj2, id);
		
		Project actualProject = Project.findById(id);
		
		assertEquals("prj", actualProject.name);
		
	}
	
	@AfterClass
	public static void stopApp(){
		Helpers.stop(fakeApp);
	}


}
