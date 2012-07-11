package models;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;

public class UserTest extends ModelTest {
	
	@Test
	public void authenticate() throws Exception {
		// Given
		User user = new User();
		user.loginId = "hobi";
		user.password = "hobi00";
		// When
		User authenticate = User.authenticate(user);
		// Then
		assertThat(authenticate.name).isEqualTo("hobi");
	}
}
