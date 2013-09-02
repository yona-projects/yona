package utils;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Keesun Baik
 */
public class PullRequestCommitTest {

    @Test
    public void isValid() {
        String url = "http://localhost:9000/admin/meme2/pullRequest/897/commit/c098727fc0c16ab7637435e56a82c8580b7a0f5f";
        assertThat(PullRequestCommit.isValid(url)).isTrue();

        url = "http://localhost:9000/admin/meme2/pullRequest/897";
        assertThat(PullRequestCommit.isValid(url)).isFalse();
    }

    @Test
    public void create(){
        String url = "http://localhost:9000/admin/meme2/pullRequest/897/commit/c098727fc0c16ab7637435e56a82c8580b7a0f5f";
        PullRequestCommit prc = new PullRequestCommit(url);
        assertThat(prc.getProjectName()).isEqualTo("meme2");
        assertThat(prc.getProjectOwner()).isEqualTo("admin");
        assertThat(prc.getPullRequestNumber()).isEqualTo(897);
        assertThat(prc.getCommitId()).isEqualTo("c098727fc0c16ab7637435e56a82c8580b7a0f5f");
    }
}
