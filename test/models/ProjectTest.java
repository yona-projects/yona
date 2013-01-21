package models;

import static org.fest.assertions.Assertions.assertThat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.junit.Ignore;
import org.junit.Test;

import playRepository.Commit;
import playRepository.GitRepository;

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
        Project actualProject = Project.find.byId(project.id);
        
        assertThat(actualProject).isNotNull();
        assertThat(actualProject.name).isEqualTo("prj_test");
        assertThat(actualProject.siteurl).isEqualTo("http://localhost:9000/prj_test");
    }
    
    // FIXME after finding travis out of memory error
    @Ignore
    public void findMilestonesById() throws Exception {
        // Given
        // When
        Project sut = Project.find.byId(1l);
        // Then
        assertThat(sut.milestones.size()).isEqualTo(2);
    }
    
    @Test
    public void findIssueById() throws Exception {
        // Given
        // When
        Project sut = Project.find.byId(1l);
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
        assertThat(Project.find.byId(1l)).isNull();
        assertThat(ProjectUser.findByIds(1l, 1l)).isNull();
        assertThat(Issue.findById(1l)).isNull();
        assertThat(Milestone.findById(1l)).isNull();
    }
    
    @Test
    public void findById() throws Exception {
        // Given
        // When
        Project project = Project.find.byId(1l);
        // Then
        assertThat(project.name).isEqualTo("nForge4java");
        assertThat(project.overview).isEqualTo("nFORGE는 소프트웨어 개발에 필요한 기능들을 사용하기 편리하게 웹으로 묶은 협업 개발 플랫폼입니다.");
        assertThat(project.share_option).isEqualTo(true);
        assertThat(project.vcs).isEqualTo("GIT");
        assertThat(project.siteurl).isEqualTo("http://localhost:9000/nForge4java");
      
    }
    
    @Ignore 
    public void isOnlyManager() throws Exception {
        // Given
        // When
        List<Project> projectsHobi = Project.isOnlyManager(2l);
        List<Project> projectsEungjun = Project.isOnlyManager(5l);
        // Then
        assertThat(projectsHobi.size()).isEqualTo(1);
        assertThat(projectsEungjun.size()).isEqualTo(0);
    }

    @Test
    public void findProjectsByMember() throws Exception {
        // Given
        // When
        List<Project> projects = Project.findProjectsByMember(2l);
        // Then
        assertThat(projects.size()).isEqualTo(3);
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
    
    @Test
    public void isProject() throws Exception {
        // Given
        String userName = "hobi";
        String projectName = "nForge4java";
        String newProjectName = "NanumFont";
        // When
        boolean result1 = Project.isProject(userName, projectName);
        boolean result2 = Project.isProject(userName, newProjectName);
        // Then
        assertThat(result1).isEqualTo(true);
        assertThat(result2).isEqualTo(false);
    }
    
    @Test
    public void projectNameChangeable() throws Exception {
        // Given
        String userName = "hobi";
        Long projectId = 1l;
        String newProjectName1 = "HelloSocialApp";
        String newProjectName2 = "NanumFont";
        // When
        boolean result1 = Project.projectNameChangeable(projectId, userName, newProjectName1);
        boolean result2 = Project.projectNameChangeable(projectId, userName, newProjectName2);
        // Then
        assertThat(result1).isEqualTo(false);
        assertThat(result2).isEqualTo(true);
    }
}
