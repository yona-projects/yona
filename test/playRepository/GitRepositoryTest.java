package playRepository;

import static org.fest.assertions.Assertions.assertThat;

import java.io.File;

import org.junit.*;

public class GitRepositoryTest {
    @Test
    public void gitRepository() throws Exception {
        //Given
        String userName = "hobi";
        String projectName  = "testProject"; 
        //When
        GitRepository repo = new GitRepository(userName, projectName);
        //Then
        assertThat(repo).isNotNull();
    }
    
   @Ignore @Test
    public void create() throws Exception {
        //Given
        String userName = "hobi";
        String projectName  = "testProject";
        GitRepository repo = new GitRepository(userName, projectName);
        //When
        repo.create();
        //Then
        File file = new File(GitRepository.REPO_PREFIX  + userName + "/" + projectName + ".git");
        assertThat(file.exists()).isTrue();
        file = new File(GitRepository.REPO_PREFIX + userName + "/" + projectName + ".git" + "/objects");
        assertThat(file.exists()).isTrue();
        file = new File(GitRepository.REPO_PREFIX + userName + "/" + projectName + ".git" + "/refs");
        assertThat(file.exists()).isTrue();
        
        //FIXME 지워지지가 않아...
        //cleanup
        new File(GitRepository.REPO_PREFIX  + userName + "/" + projectName + ".git").delete();
    }
    
    @Ignore @Test
    public void findFileInfo() throws Exception {
        //Given
        String userName = "hobi";
        String projectName  = "testProject";
        GitRepository repo = new GitRepository(userName, projectName);
        //When
        repo.findFileInfo("readme");
        //Then
    }
    @Ignore @Test
    public void getRawFile() throws Exception {
        //Given
        String userName = "hobi";
        String projectName  = "testProject";
        GitRepository repo = new GitRepository(userName, projectName);
        //When
        repo.getRawFile("readme");
        //Then
    }

}
