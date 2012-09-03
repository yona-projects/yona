package models;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.avaje.ebean.Page;

public class UserTest extends ModelTest<User> {

	@Test
	public void authenticate() throws Exception {
		// Given
		User user1 = new User();
		user1.loginId = "hobi";
		user1.password = "hobi00";
		
		User user2 = new User();
        user2.loginId = "hobi";
        user2.password = "hobi";
		// When
		// Then
		assertThat(User.authenticate(user1)).isEqualTo(true);
		assertThat(User.authenticate(user2)).isEqualTo(false);
	}

	@Test
	public void findById() throws Exception {
		// Given
		// When
		User user = User.findById(2l);
		// Then
		assertThat(user.name).isEqualTo("Hobi");
	}

	@Test
	public void findByName() throws Exception {
		// Given
		// When
		User user = User.findByName("Hobi");
		// Then
		assertThat(user.id).isEqualTo(2l);
	}
	
	@Test
    public void findNameById() throws Exception {
        //Given
        //When
	    String name = User.findNameById(2l);
        //Then
	    assertThat(name).isEqualTo("Hobi");
    }
	
	@Test
    public void options() throws Exception {
        // Given
        // When
        Map<String, String> userOptions = User.options();
        // Then
        assertThat(userOptions).hasSize(6);
    }
	
	@Test
	public void findByLoginId() throws Exception {
	    // Given
	    // When
	    User user = User.findByLoginId("k16wire");
	    // Then
	    assertThat(user.id).isEqualTo(3l);
	}
	
	@Test
	public void findUsers() throws Exception {
	    // Given
	    // When
	    Page<User> users = User.findUsers(0, null);
	    Page<User> searchUsers = User.findUsers(0, "ho");
	    // Then
	    assertThat(users.getTotalRowCount()).isEqualTo(4);
	    assertThat(searchUsers.getTotalRowCount()).isEqualTo(1);
	}
	
	@Test
	public void findProjectsById() throws Exception {
	    // Given
	    // When
	    User user = User.findProjectsById(1l);
	    // Then
	    assertThat(user.projectUser.size()).isEqualTo(3);
	    assertThat(user.projectUser.iterator().next().project.name).isEqualTo("nForge4java");
	}
	
	@Test
    public void findUsersByProject() throws Exception {
        // Given
        // When
        List<User> users = User.findUsersByProject(2l);
        // Then
        assertThat(users.size()).isEqualTo(3);
    }
	
	@Test
	public void isLoginId() throws Exception {
	    // Given
	    String existingId = "hobi";
	    String nonExistingId = "hobiii";
	    // When
	    boolean result1 = User.isLoginId(existingId);
	    boolean result2 = User.isLoginId(nonExistingId);
	    boolean result3 = User.isLoginId(null);
	    // Then
	    assertThat(result1).isEqualTo(true);
	    assertThat(result2).isEqualTo(false);
	    assertThat(result3).isEqualTo(false);
	}
}
