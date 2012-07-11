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

public class ProjectTest extends ModelTest{
	
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
	    Long id = new Long(1);
	    Project prj = new Project();
	    prj.name = "modifiedProjectName";
	    		
		Project.update(prj, id);
		
		Project actualProject = Project.findById(id);
		
		assertEquals("modifiedProjectName", actualProject.name);
		assertEquals("첫번째 프로젝트입니다.", actualProject.overview);
		assertEquals("false", Boolean.toString(actualProject.share_option));
		assertEquals("GIT", actualProject.vcs);
		assertEquals("http://localhost:9000/project/1", actualProject.url);
		assertEquals("1", Long.toString(actualProject.owner));
	}
	
	@Test
	public void testDelete(){
		Long id = new Long(1);
		Project prj = Project.findById(id);
		Project.delete(prj);
		
		assertEquals(null, Project.findById(id));
	}
}
