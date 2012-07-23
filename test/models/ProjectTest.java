package models;

/*
 * @author: Hwi Ahn
 * 
 */

import org.junit.Test;

import controllers.UserApp;

import java.util.Date;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class ProjectTest extends ModelTest {

    @Test
    public void create() throws Exception {
        // Given
        Project project = new Project();
        project.name = "prj_test";
        project.overview = "Overview for prj_test";
        project.share_option = false;
        project.vcs = "GIT";
        // When
        Project.create(project);
        // Then
        Project actualProject = Project.findById(project.id);
        
        assertThat(actualProject).isNotNull();
        assertThat(actualProject.name).isEqualTo("prj_test");
    }

    @Test
    public void update() throws Exception {
        // Given
        Project prj = new Project();
        prj.name = "modifiedProjectName";
        // When
        Project.update(prj, 1l);
        // Then
        Project actualProject = Project.findById(1l);

        assertThat(actualProject.name).isEqualTo("modifiedProjectName");
        assertThat(actualProject.overview).isEqualTo("nFORGE는 소프트웨어 개발에 필요한 기능들을 사용하기 편리하게 웹으로 묶은 협업 개발 플랫폼입니다.");
    }

    @Test
    public void delete() throws Exception {
        // Given
        // When
        Project.delete(1l);
        // Then
        assertThat(Project.findById(1l)).isNull();
    }
    
    @Test
    public void findById() throws Exception {
        // Given
        // When
        Project project = Project.findById(1l);
        // Then
        assertThat(project.name).isEqualTo("nForge4java");
        assertThat(project.overview).isEqualTo("nFORGE는 소프트웨어 개발에 필요한 기능들을 사용하기 편리하게 웹으로 묶은 협업 개발 플랫폼입니다.");
        assertThat(project.share_option).isEqualTo(true);
        assertThat(project.vcs).isEqualTo("GIT");
        assertThat(project.url).isEqualTo("http://localhost:9000/project/1");
      
    }
    
    @Test
    public void findByOwner() throws Exception {
        // Given
        // When
        List<Project> projectList = Project.findByOwner(1l);
        // Then
        assertThat(projectList.size()).isEqualTo(2);      
    }
}
