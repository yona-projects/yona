package models.support;

import java.util.*;

public class Options extends LinkedHashMap<String, String> {
    private static final long serialVersionUID = 1L;

    public Options(String... args) {
        for (int idx = 0; idx < args.length; idx++) {
            this.put(String.valueOf(idx + 1), args[idx]);
        }
    }
}
