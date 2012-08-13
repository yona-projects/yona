package models;

import org.junit.Test;

import controllers.UserApp;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author "Hwi Ahn"
 *
 */
public class ProjectTest extends ModelTest<Project> {

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
        assertThat(actualProject.url).isEqualTo("http://localhost:9000/prj_test");
    }
    
    @Test
    public void findMilestonesById() throws Exception {
        // Given
        // When
        Project sut = Project.findById(1l);
        // Then
        assertThat(sut.milestones.size()).isEqualTo(2);
    }
    @Test
    public void findIssuessById() throws Exception {
        // Given
        // When
        Project sut = Project.findById(1l);
        // Then
        assertThat(sut.issues.size()).isEqualTo(7);
    }

    @Test
    public void delete() throws Exception {
        // Given
        // When
        Project.delete(1l);
        flush();
        // Then
        assertThat(Project.findById(1l)).isNull();
        assertThat(ProjectUser.findByIds(1l, 1l)).isNull();
        assertThat(Issue.findById(1l)).isNull();
        assertThat(Milestone.findById(1l)).isNull();
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
        assertThat(project.url).isEqualTo("http://localhost:9000/nForge4java");
      
    }
    
    @Test
    public void isOnlyManager() throws Exception {
        // Given
        // When
        List<Project> projects_hobi = Project.isOnlyManager(2l);
        List<Project> projects_eungjun = Project.isOnlyManager(5l);
        // Then
        assertThat(projects_hobi.size()).isEqualTo(1);
        assertThat(projects_eungjun.size()).isEqualTo(0);
    }

    @Test
    public void findProjectsByMember() throws Exception {
        // Given
        // When
        List<Project> projects = Project.findProjectsByMember(2l);
        // Then
        assertThat(projects.size()).isEqualTo(2);
    }
    
    @Test
    public void findByNameAndOwner() throws Exception {
        // Given
        String userName = "hobi";
        String projectName = "nForge4java";
        // When
        Project project = Project.findByNameAndOwner(userName, projectName);
        // Then
        assertThat(project.id).isEqualTo(1l);
    }
}
