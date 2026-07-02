package com.feth.play.module.mail;

import com.google.inject.AbstractModule;

public class MailerModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(Mailer.class).asEagerSingleton();
    }
}
