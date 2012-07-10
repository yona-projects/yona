package models;

import org.junit.*;

import play.test.FakeApplication;
import play.test.Helpers;

public class ModelTest {
	protected static FakeApplication app;

	@BeforeClass
	public static void startApp() {
		app = Helpers.fakeApplication(Helpers.inMemoryDatabase());
		Helpers.start(app);
	}
	@AfterClass
	public static void stopApp() {
		Helpers.stop(app);
	}
}
