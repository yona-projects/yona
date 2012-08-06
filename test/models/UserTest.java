package models;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

public class UserTest extends ModelTest<User> {

	@Test
	public void authenticate() throws Exception {
		// Given
		User user = new User();
		user.loginId = "hobi";
		user.password = "hobi00";
		// When
		User authenticate = User.authenticate(user);
		// Then
		assertThat(authenticate.name).isEqualTo("Hobi");
	}

	@Test
	public void findById() throws Exception {
		// Given
		// When
		User user = User.findById(1l);
		// Then
		assertThat(user.name).isEqualTo("Hobi");
	}

	@Test
	public void findByName() throws Exception {
		// Given
		// When
		User user = User.findByName("Hobi");
		// Then
		assertThat(user.id).isEqualTo(1l);
	}
	@Test
    public void findNameById() throws Exception {
        //Given
        //When
	    String name = User.findNameById(1l);
        //Then
	    assertThat(name).isEqualTo("Hobi");
    }
	
	@Test
    public void options() throws Exception {
        // Given
        // When
        Map<String, String> userOptions = User.options();
        // Then
        assertThat(userOptions).hasSize(4);
    }
	
	@Test
	public void findByLoginId() throws Exception {
	    // Given
	    // When
	    User user = User.findByLoginId("k16wire");
	    // Then
	    assertThat(user.id).isEqualTo(2l);
	}
}
