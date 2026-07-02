package com.feth.play.module.pa.exceptions;

public class AccessDeniedException extends AuthException {
    private final String providerKey;

    public AccessDeniedException(String providerKey) {
        super("Access denied for provider: " + providerKey);
        this.providerKey = providerKey;
    }

    public String getProviderKey() {
        return providerKey;
    }
}
