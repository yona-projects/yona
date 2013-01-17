package support;


import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.config.ServerConfig;
import com.avaje.ebeaninternal.api.SpiEbeanServer;
import com.avaje.ebeaninternal.server.ddl.DdlGenerator;
import play.Application;
import play.Configuration;
import play.Plugin;
import play.api.libs.Files;
import play.db.DB;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TestEbeanPlugin extends Plugin {
    public void $init$() {
    }

    private final Application application;

    public TestEbeanPlugin(Application application) {
        this.application = application;
    }

    // --

    private final Map<String, EbeanServer> servers = new HashMap<String, EbeanServer>();

    /**
     * Reads the configuration file and initialises required Ebean servers.
     */
    public void onStart() {

        Configuration ebeanConfig = Configuration.root().getConfig("ebean");

        if (ebeanConfig != null) {
            for (String key : ebeanConfig.keys()) {

                ServerConfig config = new ServerConfig();
                config.setName(key);
                try {
                    config.setDataSource(new WrappingDatasource(DB.getDataSource(key)));
                } catch (Exception e) {
                    throw ebeanConfig.reportError(
                            key,
                            e.getMessage(),
                            e
                    );
                }
                if (key.equals("default")) {
                    config.setDefaultServer(true);
                }

                String[] toLoad = ebeanConfig.getString(key).split(",");
                Set<String> classes = new HashSet<String>();
                for (String load : toLoad) {
                    load = load.trim();
                    if (load.endsWith(".*")) {
                    	
//                        classes.addAll(application.getTypesAnnotatedWith(load.substring(0, load.length() - 2), javax.persistence.Entity.class));
//                        classes.addAll(application.getTypesAnnotatedWith(load.substring(0, load.length() - 2), javax.persistence.Embeddable.class));
                    } else {
                        classes.add(load);
                    }
                }
                for (String clazz : classes) {
                    try {
                        config.addClass(Class.forName(clazz, true, application.classloader()));
                    } catch (Throwable e) {
                        throw ebeanConfig.reportError(
                                key,
                                "Cannot register class [" + clazz + "] in Ebean server",
                                e
                        );
                    }
                }

//                config.add(new TestBeanPersistController());

                servers.put(key, EbeanServerFactory.create(config));

                // DDL
                if (!application.isProd()) {
                    boolean evolutionsEnabled = !"disabled".equals(application.configuration().getString("evolutionplugin"));
                    if (evolutionsEnabled) {
                        String evolutionScript = generateEvolutionScript(servers.get(key), config);
                        if (evolutionScript != null) {
                            File evolutions = application.getFile("conf/evolutions/" + key + "/1.sql");
                            if (!evolutions.exists() || Files.readFile(evolutions).startsWith("# --- Created by Ebean DDL")) {
                                Files.createDirectory(application.getFile("conf/evolutions/" + key));
                                Files.writeFileIfChanged(evolutions, evolutionScript);
                            }
                        }
                    }
                }

            }
        }

    }

    /**
     * Helper method that generates the required evolution to properly run Ebean.
     */
    public static String generateEvolutionScript(EbeanServer server, ServerConfig config) {
        DdlGenerator ddl = new DdlGenerator((SpiEbeanServer) server, config.getDatabasePlatform(), config);
        String ups = ddl.generateCreateDdl();
        String downs = ddl.generateDropDdl();

        if (ups == null || ups.trim().isEmpty()) {
            return null;
        }

        return (
                "# --- Created by Ebean DDL\n" +
                        "# To stop Ebean DDL generation, remove this comment and start using Evolutions\n" +
                        "\n" +
                        "# --- !Ups\n" +
                        "\n" +
                        ups +
                        "\n" +
                        "# --- !Downs\n" +
                        "\n" +
                        downs
        );
    }

    /**
     * <code>DataSource</code> wrapper to ensure that every retrieved connection has auto-commit disabled.
     */
    static class WrappingDatasource implements javax.sql.DataSource {

        public java.sql.Connection wrap(java.sql.Connection connection) throws java.sql.SQLException {
            connection.setAutoCommit(false);
            return connection;
        }

        // --

        final javax.sql.DataSource wrapped;

        public WrappingDatasource(javax.sql.DataSource wrapped) {
            this.wrapped = wrapped;
        }

        public java.sql.Connection getConnection() throws java.sql.SQLException {
            return wrap(wrapped.getConnection());
        }

        public java.sql.Connection getConnection(String username, String password) throws java.sql.SQLException {
            return wrap(wrapped.getConnection(username, password));
        }

        public int getLoginTimeout() throws java.sql.SQLException {
            return wrapped.getLoginTimeout();
        }

        public java.io.PrintWriter getLogWriter() throws java.sql.SQLException {
            return wrapped.getLogWriter();
        }

        public void setLoginTimeout(int seconds) throws java.sql.SQLException {
            wrapped.setLoginTimeout(seconds);
        }

        public void setLogWriter(java.io.PrintWriter out) throws java.sql.SQLException {
            wrapped.setLogWriter(out);
        }

        public boolean isWrapperFor(Class<?> iface) throws java.sql.SQLException {
            return wrapped.isWrapperFor(iface);
        }

        public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
            return wrapped.unwrap(iface);
        }

        public java.util.logging.Logger getParentLogger() {
            return null;
        }

    }
}
