package com.feth.play.module.pa;

import com.feth.play.module.pa.exceptions.AuthException;
import play.mvc.Call;

public abstract class Resolver {
    public abstract Call login();

    public abstract Call afterAuth();

    public abstract Call afterLogout();

    public abstract Call auth(String provider);

    public Call onException(AuthException e) {
        return login();
    }

    public abstract Call askLink();

    public abstract Call askMerge();
}
