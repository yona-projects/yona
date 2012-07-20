package models;

import org.junit.*;

import play.test.FakeApplication;
import play.test.Helpers;

public class ModelTest {
	protected FakeApplication app;

	@Before
	public void startApp() {
		app = Helpers.fakeApplication(Helpers.inMemoryDatabase());
		Helpers.start(app);
	}
	@After
	public void stopApp() {
		Helpers.stop(app);
	}

    /**
     * Returns the first user. (id : 1 / name : hobi)
     * @return User
     */
    protected User getTestUser() {
        return User.findById(1l);
    }

    /**
     * Returns user.
     * @param userId
     * @return
     */
    protected User getTestUser(Long userId) {
        return User.findById(userId);
    }
}
