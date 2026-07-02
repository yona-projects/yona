package play;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;

public final class Play {
    private static volatile Application currentApplication;

    private Play() {
    }

    public static void setApplication(Application application) {
        currentApplication = application;
    }

    public static LegacyApplication application() {
        return new LegacyApplication(currentApplication);
    }

    static Configuration configuration() {
        Application application = currentApplication;
        if (application == null) {
            return new Configuration(ConfigFactory.load());
        }
        return new LegacyApplication(application).configuration();
    }

    public static final class LegacyApplication {
        private final Application delegate;

        private LegacyApplication(Application delegate) {
            this.delegate = delegate;
        }

        public Application asPlayApplication() {
            if (delegate == null) {
                throw new IllegalStateException("Play application has not been initialized yet");
            }
            return delegate;
        }

        public play.inject.Injector injector() {
            return asPlayApplication().injector();
        }

        public Configuration configuration() {
            Application app = delegate;
            if (app == null) {
                return new Configuration(ConfigFactory.load());
            }

            Object value = invoke(app, "configuration");
            if (value instanceof Config) {
                return new Configuration((Config) value);
            }

            value = invoke(app, "config");
            if (value instanceof Config) {
                return new Configuration((Config) value);
            }

            Object scalaApplication = invoke(app, "asScala");
            Object scalaConfiguration = invoke(scalaApplication, "configuration");
            Object underlying = invoke(scalaConfiguration, "underlying");
            if (underlying instanceof Config) {
                return new Configuration((Config) underlying);
            }

            return new Configuration(ConfigFactory.load());
        }

        public File getFile(String path) {
            Application app = asPlayApplication();
            Object root = invoke(app, "path");
            if (root instanceof Path) {
                return ((Path) root).resolve(path).toFile();
            }
            if (root instanceof File) {
                return new File((File) root, path);
            }
            return new File(path);
        }

        private static Object invoke(Object target, String methodName) {
            if (target == null) {
                return null;
            }
            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
