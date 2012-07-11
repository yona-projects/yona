package models;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

public class ReplyTest extends ModelTest {

    private User testUser;

    @Before
    public void setUp() {
        testUser = User.findByName("hobi");
    }

    @Test
    public void testWrite() throws Exception {
        // Given
        Reply reply = new Reply();
        reply.articleNum = 1l;
        reply.contents = "testThing";
        reply.writerId = testUser.id;
        // When
        long id = Reply.write(reply);
        // Then
        assertThat(Reply.find.byId(id)).isNotNull();
    }
}
