package utils;

import play.Play;
import play.db.DBApi;

import javax.sql.DataSource;

public final class DBUtil {
    private DBUtil() {
    }

    public static DataSource getDataSource(String name) {
        return Play.application().injector().instanceOf(DBApi.class).getDatabase(name).getDataSource();
    }
}
