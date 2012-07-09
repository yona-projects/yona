package models;

import static org.junit.Assert.*;

import org.junit.Test;

public class ProjectTest {

	@Test
	public void testCreate() {
		Project prj = new Project();
		prj.name = "prj_test";
		prj.overview = "Overview for prj_test";
		prj.share_option = false;
		prj.vcs = "GIT";
		Project.create(prj);
		
		Project actualProject = Project.findById(prj.getId());
		
		assertEquals("prj_test", actualProject.name);
        assertEquals("Overview for prj_test", actualProject.overview);
        assertEquals(false, actualProject.share_option);
        assertEquals("GIT", actualProject.vcs);
	}

}
