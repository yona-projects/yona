package com.feth.play.module.pa.user;

public class SessionAuthUser implements BasicIdentity {
    private final String id;
    private final String provider;
    private final String email;
    private final String name;

    public SessionAuthUser(String id, String provider, String email, String name) {
        this.id = id;
        this.provider = provider;
        this.email = email;
        this.name = name;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getProvider() {
        return provider;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getName() {
        return name;
    }
}
