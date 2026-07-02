package modules;

import com.typesafe.config.ConfigFactory;
import io.ebean.DB;
import io.ebean.Ebean;
import mailbox.MailboxService;
import models.*;
import play.Application;
import play.Configuration;
import play.Environment;
import play.api.db.evolutions.ApplicationEvolutions;
import play.inject.ApplicationLifecycle;
import utils.*;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;

@Singleton
public class YonaRuntime {
    private static final String[] INITIAL_ENTITY_NAME = {"users", "roles", "siteAdmins"};
    private static final String DEFAULT_SECRET = "VA2v:_I=h9>?FYOH:@ZhW]01P<mWZAKlQ>kk>Bo`mdCiA>pDw64FcBuZdDh<47Ew";

    private static final ConfigFile CONFIG_FILE = new ConfigFile("config", "application.conf");
    private static final ConfigFile LOGGER_CONFIG_FILE = new ConfigFile("logger", "application-logger.xml");
    private static final ConfigFile OAUTH_PROVIDER_CONF_FILE = new ConfigFile("conf", "social-login.conf");

    private final MailboxService mailboxService = new MailboxService();
    private boolean secretInvalid = false;
    private boolean restartRequired = false;
    private boolean failedToUpdateSecretKey = false;

    @Inject
    public YonaRuntime(Application application, ApplicationLifecycle lifecycle, ApplicationEvolutions applicationEvolutions) {
        Ebean.use(DB.getDefault());
        start(application);
        lifecycle.addStopHook(() -> {
            stop();
            return CompletableFuture.completedFuture(null);
        });
    }

    static play.api.Configuration loadConfiguration(Environment environment, play.api.Configuration initialConfiguration) {
        initLoggerConfig();
        initAuthProviderConfig();

        Configuration generatedConfiguration = initConfig(environment.classLoader());
        if (generatedConfiguration == null) {
            return initialConfiguration;
        }
        return new play.api.Configuration(
                generatedConfiguration.underlying().withFallback(initialConfiguration.underlying()));
    }

    static com.typesafe.config.Config loadConfiguration(Environment environment, com.typesafe.config.Config initialConfig) {
        return loadConfiguration(environment, new play.api.Configuration(initialConfig)).underlying();
    }

    private static Configuration initConfig(ClassLoader classloader) {
        if (CONFIG_FILE.isLocationSpecified()) {
            return null;
        }

        try {
            if (CONFIG_FILE.getPath().toFile().exists()) {
                return null;
            }
        } catch (URISyntaxException e) {
            play.Logger.error("Failed to check whether the config file exists", e);
            return null;
        }

        try {
            CONFIG_FILE.createByDefault();
            return new Configuration(ConfigFactory.load(classloader,
                    ConfigFactory.parseFileAnySyntax(CONFIG_FILE.getPath().toFile())));
        } catch (Exception e) {
            play.Logger.error("Failed to initialize configuration", e);
            return null;
        }
    }

    private static void initLoggerConfig() {
        try {
            if (!LOGGER_CONFIG_FILE.isLocationSpecified() && !LOGGER_CONFIG_FILE.getPath().toFile().exists()) {
                try {
                    LOGGER_CONFIG_FILE.createByDefault();
                } catch (Exception e) {
                    play.Logger.error("Failed to initialize logger configuration", e);
                }
            }
        } catch (URISyntaxException e) {
            play.Logger.error("Failed to check whether the logger config file exists", e);
        }
    }

    private static void initAuthProviderConfig() {
        try {
            if (!OAUTH_PROVIDER_CONF_FILE.isLocationSpecified() && !OAUTH_PROVIDER_CONF_FILE.getPath().toFile().exists()) {
                try {
                    OAUTH_PROVIDER_CONF_FILE.createByDefault();
                } catch (Exception e) {
                    play.Logger.error("Failed to initialize social-login.conf", e);
                }
            }
        } catch (URISyntaxException e) {
            play.Logger.error("Failed to check whether the social-login.conf file exists", e);
        }
    }

    private void start(Application app) {
        play.Play.setApplication(app);
        secretInvalid = equalsDefaultSecret(app);
        insertInitialData();

        Timestamp timestamp = new Timestamp("=== Yona server starting initialization ===");
        Config.onStart();
        timestamp.logElapsedTime("--- Config reading: ok!");
        Property.onStart();
        timestamp.logElapsedTime("--- Property reading: ok!");
        PullRequest.onStart();
        timestamp.logElapsedTime("--- Pull request checking: ok!");
        NotificationMail.onStart();
        timestamp.logElapsedTime("--- Notification mail scheduler: ok!");
        NotificationEvent.onStart();
        timestamp.logElapsedTime("--- Notification event cleanup scheduler: ok!");
        Attachment.onStart();
        timestamp.logElapsedTime("--- Temporary files cleanup scheduler: ok!");
        AccessControl.onStart();
        timestamp.logElapsedTime("--- Basic access controller config reading: ok!");

        if (!secretInvalid) {
            YobiUpdate.onStart();
            timestamp.logElapsedTime("--- Update checker run: ok! ");
            mailboxService.start();
            timestamp.logElapsedTime("--- MailboxService checker run: ok!");
        }
    }

    private boolean equalsDefaultSecret(Application app) {
        return DEFAULT_SECRET.equals(play.Configuration.root().getString("application.secret"));
    }

    private static void insertInitialData() {
        if (Ebean.find(User.class).findCount() == 0) {
            YamlUtil.insertDataFromYaml("initial-data.yml", INITIAL_ENTITY_NAME);
        }
    }

    private void stop() {
        NotificationMail.stopSchedule();
        mailboxService.stop();
    }

    boolean isSecretInvalid() {
        return secretInvalid;
    }

    boolean isRestartRequired() {
        return restartRequired;
    }

    boolean hasFailedToUpdateSecretKey() {
        return failedToUpdateSecretKey;
    }

    void requireRestart() {
        restartRequired = true;
    }

    void markFailedToUpdateSecretKey() {
        failedToUpdateSecretKey = true;
    }

    void updateSiteSecretKey(String seed) throws Exception {
        SecureRandom random = new SecureRandom(seed.getBytes(Config.getCharset()));
        String secret = new BigInteger(130, random).toString(32);

        if (CONFIG_FILE.isExternal()) {
            throw new Exception("Cowardly refusing to update an external file: " + CONFIG_FILE.getPath());
        }

        byte[] bytes = Files.readAllBytes(CONFIG_FILE.getPath());
        String config = new String(bytes, Config.getCharset());
        config = config.replace(DEFAULT_SECRET, secret);
        Files.write(CONFIG_FILE.getPath(), config.getBytes(Config.getCharset()));
    }

    private static class ConfigFile {
        private static final String CONFIG_DIRNAME = "conf";
        private final String fileName;
        private final String defaultFileName;
        private final String propertyGroup;

        ConfigFile(String propertyGroup, String fileName) {
            this.propertyGroup = propertyGroup;
            this.fileName = fileName;
            this.defaultFileName = fileName + ".default";
        }

        String getProperty(@Nonnull String key) {
            return System.getProperty(propertyGroup + "." + key);
        }

        String getProperty(@Nonnull String key, String defaultValue) {
            return System.getProperty(propertyGroup + "." + key, defaultValue);
        }

        boolean isLocationSpecified() {
            return (getProperty("resource") != null)
                    || (getProperty("file") != null)
                    || (getProperty("url") != null);
        }

        void createByDefault() throws IOException, URISyntaxException {
            InputStream stream = Config.class.getClassLoader().getResourceAsStream(defaultFileName);

            getPath().toFile().getParentFile().mkdirs();

            if (stream != null) {
                Files.copy(stream, getPath());
            } else {
                Files.copy(getDirectoryPath().resolve(defaultFileName), getPath());
            }
        }

        Path getPath() throws URISyntaxException {
            if (getProperty("url") != null) {
                return Paths.get(new URI(getProperty("url")));
            }

            if (getProperty("file") != null) {
                return Paths.get(getProperty("file"));
            }

            String filename = getProperty("resource", fileName);

            return getDirectoryPath().resolve(filename);
        }

        static Path getDirectoryPath() {
            return Paths.get(Config.getYonaDataDir(""), CONFIG_DIRNAME);
        }

        boolean isExternal() throws IOException, URISyntaxException {
            return !FileUtil.isSubpathOf(getPath(), getDirectoryPath()) &&
                    !FileUtil.isSubpathOf(getPath(), Paths.get(Config.getYonaDataDir()));
        }
    }
}
