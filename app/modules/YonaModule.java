package modules;

import com.feth.play.module.pa.Resolver;
import com.feth.play.module.pa.service.UserService;
import com.google.inject.AbstractModule;
import service.YonaPlayAuthResolver;
import service.YonaUserServicePlugin;

public class YonaModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(Resolver.class).to(YonaPlayAuthResolver.class);
        bind(UserService.class).to(YonaUserServicePlugin.class);
        bind(YonaRuntime.class).asEagerSingleton();
    }
}
