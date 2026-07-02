package modules;

import com.feth.play.module.mail.MailerModule;
import com.typesafe.config.Config;
import play.ApplicationLoader;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;

public class YonaApplicationLoader extends GuiceApplicationLoader {

    @Override
    public GuiceApplicationBuilder builder(ApplicationLoader.Context context) {
        Config configuration = YonaRuntime.loadConfiguration(
                context.environment(), context.initialConfig());
        return super.builder(context.withConfig(configuration))
                .bindings(new YonaModule(), new MailerModule());
    }
}
