package models;

import org.junit.After;
import org.junit.Before;
import play.test.FakeApplication;
import play.test.Helpers;
//import support.EbeanUtil;

import static play.test.Helpers.callAction;
import static play.test.Helpers.fakeRequest;

public class ModelTest<T> {
    protected static FakeApplication app;
    protected Class<T> type;

    @SuppressWarnings("unchecked")
    public ModelTest() {
    }

    @Before
    public void startApp() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    @After
    public void stopApp() {
        Helpers.stop(app);
    }

    /**
     * Returns the first user. (id : 2 / name : yobi)
     * 
     * @return User
     */
    protected User getTestUser() {
        return User.find.byId(2l);
    }

    /**
     * Returns user.
     * 
     * @param userId
     * @return
     */
    protected User getTestUser(Long userId) {
        return User.find.byId(userId);
    }

    /**
     * Returns the first project. (id : 1 / name : nForge4java)
     * 
     * @return Project
     */
    protected Project getTestProject() {
        return Project.find.byId(1l);
    }

    /**
     * Returns project.
     * 
     * @return Project
     */
    protected Project getTestProject(Long projectId) {
        return Project.find.byId(projectId);
    }

    @SuppressWarnings("unchecked")
    protected void flush(T model) {
//        ebeanUiUtil.flush(model);
    }

    protected void flush(Long id) {
//        ebeanUiUtil.flush(id);
    }

    protected void flush() {
        flush(1l);
    }

}
