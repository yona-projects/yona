/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Sangcheol Hwang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import static org.fest.assertions.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import models.enumeration.UserState;
import org.junit.Test;

import com.avaje.ebean.Page;
import org.omg.PortableInterceptor.ACTIVE;
import play.data.validation.Validation;

public class UserTest extends ModelTest<User> {

    @Test
    public void save() throws Exception {
        User user = new User();

        user.loginId="foo";
        assertThat(Validation.getValidator().validate(user).size()).describedAs("'foo' should be accepted.").isEqualTo(0);

        user.loginId=".foo";
        assertThat(Validation.getValidator().validate(user).size()).describedAs("'.foo' should NOT be accepted.").isGreaterThan(0);

        user.loginId="foo.bar";
        assertThat(Validation.getValidator().validate(user).size()).describedAs("'foo.bar' should be accepted.").isEqualTo(0);

        user.loginId="foo.";
        assertThat(Validation.getValidator().validate(user).size()).describedAs("'foo.' should NOT be accepted.").isGreaterThan(0);

        user.loginId="_foo";
        assertThat(Validation.getValidator().validate(user).size()).describedAs("'_foo' should NOT be accepted.").isGreaterThan(0);

        user.loginId="foo_bar";
        assertThat(Validation.getValidator().validate(user).size()).describedAs("'foo_bar' should be accepted.").isEqualTo(0);

        user.loginId="foo_";
        assertThat(Validation.getValidator().validate(user).size()).describedAs("'foo_' should NOT be accepted.").isGreaterThan(0);

        user.loginId="-foo";
        assertThat(Validation.getValidator().validate(user).size()).describedAs("'-foo' should be accepted.").isEqualTo(0);

        user.loginId="foo-";
        assertThat(Validation.getValidator().validate(user).size()).describedAs("'foo-' should be accepted.").isEqualTo(0);
    }

	@Test
	public void findById() throws Exception {
		// Given
		// When
		User user = User.find.byId(2l);
		// Then
		assertThat(user.name).isEqualTo("Yobi");
	}

	@Test
    public void findNameById() throws Exception {
        //Given
        //When
	    String name = User.find.byId(2l).name;
        //Then
	    assertThat(name).isEqualTo("Yobi");
    }

	@Test
    public void options() throws Exception {
        // Given
        // When
        Map<String, String> userOptions = User.options();
        // Then
        assertThat(userOptions).hasSize(7);
    }

	@Test
	public void findByLoginId() throws Exception {
	    // Given
	    // When
	    User user = User.findByLoginId("laziel");
	    // Then
	    assertThat(user.id).isEqualTo(3l);
	}

	@Test
	public void findUsers() throws Exception {
	    // Given
	    // When
	    Page<User> searchUsers = User.findUsers(0, "yobi", UserState.ACTIVE);
	    // Then
	    assertThat(searchUsers.getTotalRowCount()).isEqualTo(1);
	}

	@Test
    public void findUsersByProject() throws Exception {
        // Given
        // When
        List<User> users = User.findUsersByProject(2l);
        // Then
        assertThat(users.size()).isEqualTo(2);
    }

	@Test
	public void isLoginId() throws Exception {
	    // Given
	    String existingId = "yobi";
	    String nonExistingId = "yobiii";
	    // When
	    boolean result1 = User.isLoginIdExist(existingId);
	    boolean result2 = User.isLoginIdExist(nonExistingId);
	    boolean result3 = User.isLoginIdExist(null);
	    // Then
	    assertThat(result1).isEqualTo(true);
	    assertThat(result2).isEqualTo(false);
	    assertThat(result3).isEqualTo(false);
	}

    @Test
    public void isEmailExist() throws Exception {
        // Given
        String expectedUser = "doortts@gmail.com";

    	// When // Then
        assertThat(User.isEmailExist(expectedUser)).isTrue();
    }

    @Test
    public void isEmailExist_nonExist() throws Exception {
        // Given
        String expectedEmail = "nekure@gmail.com";

        // When // Then
        assertThat(User.isEmailExist(expectedEmail)).isFalse();
    }

    @Test
    public void watchingProject() {
        // Given
        Project project = Project.find.byId(1l);
        User user = User.findByLoginId("doortts");
        assertThat(project.getWatchingCount()).isEqualTo(0);
        assertThat(user.getWatchingProjects().size()).isEqualTo(0);

        // When
        user.addWatching(project);

        // Then
        assertThat(user.getWatchingProjects().size()).isEqualTo(1);
        assertThat(user.getWatchingProjects().contains(project)).isTrue();
        assertThat(project.getWatchingCount()).isEqualTo(1);

        // when
        user.removeWatching(project);

        // Then
        assertThat(user.getWatchingProjects().size()).isEqualTo(0);
        assertThat(user.getWatchingProjects().contains(project)).isFalse();
        assertThat(project.getWatchingCount()).isEqualTo(0);
    }

    @Test
    public void changeState() {
        // Given
        User user = new User();
        user.loginId = "foo";
        user.save();
        Project project = new Project();
        project.save();
        Issue issue = new Issue();
        issue.project = project;
        issue.assignee = new Assignee(user.id, project.id);
        issue.save();

        // When
        user.changeState(UserState.DELETED);

        // Then
        issue.refresh();
        assertThat(issue.assignee).isNull();

        // To keep data clean after this test.
        user.delete();
    }
}
