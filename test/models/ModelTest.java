package models;

import com.avaje.ebean.Ebean;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import play.test.FakeApplication;
import play.test.Helpers;
import support.EbeanUtil;

import java.lang.reflect.ParameterizedType;

public class ModelTest<T> {
    protected static FakeApplication app;
    protected static EbeanUtil ebeanUiUtil;
    protected Class<T> type;

    @SuppressWarnings("unchecked")
    public ModelTest() {
        this.type = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass())
                .getActualTypeArguments()[0];
        ebeanUiUtil = new EbeanUtil<T>(type);
    }

    @BeforeClass
    public static void startApp() {
        app = Helpers.fakeApplication(Helpers.inMemoryDatabase()); 
        Helpers.start(app);
        
    }

    @AfterClass
    public static void stopApp() {
        Helpers.stop(app);
    }

    @Before
    public void beginTransaction() {
        Ebean.beginTransaction();
    }

    @After
    public void rollbackTransaction() {
        if (Ebean.currentTransaction() != null) {
            Ebean.rollbackTransaction();
        }
    }

    /**
     * Returns the first user. (id : 2 / name : hobi)
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
        ebeanUiUtil.flush(model);
    }

    protected void flush(Long id) {
        ebeanUiUtil.flush(id);
    }

    protected void flush() {
        flush(1l);
    }

}
