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
}
