package utils;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;


/**
 * @author Keesun Baik
 */
public class GravatarUtilTest {

    @Test
    public void getAvatar() {
        // given
        String email = "whiteship2000@gmail.com";

        // when
        String result = GravatarUtil.getAvatar(email);

        // then
        assertThat(result).isEqualTo("http://www.gravatar.com/avatar/d3a3e1e76decd8760aaf9af6ab334264?s=80&d=http%3A%2F%2Fko.gravatar.com%2Fuserimage%2F53495145%2F0eaeeb47c620542ad089f17377298af6.png");
    }

    @Test
    public void getAvatarWithSize() {
        // given
        String email = "whiteship2000@gmail.com";
        int size = 40;

        // when
        String result = GravatarUtil.getAvatar(email, size);

        // then
        assertThat(result).contains("s=40");
    }

}
