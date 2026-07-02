package utils;

import play.mvc.Http;
import play.mvc.Result;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class LegacyRequestContext {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private LegacyRequestContext() {
    }

    public static void begin(Http.Request request) {
        CURRENT.set(new State(request));
    }

    public static void end() {
        CURRENT.remove();
    }

    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    public static CompletionStage<Result> withRequest(Http.Request request, Supplier<CompletionStage<Result>> body) {
        if (isBound()) {
            return body.get();
        }

        begin(request);
        try {
            CompletionStage<Result> promise = body.get();
            Snapshot snapshot = capture();
            return promise.thenApply(snapshot::apply);
        } finally {
            end();
        }
    }

    public static Http.Request request() {
        State state = state();
        return state.request;
    }

    public static Http.Request currentRequestOrNull() {
        State state = CURRENT.get();
        return state == null ? null : state.request;
    }

    public static LegacyResponse response() {
        return state().response;
    }

    public static LegacySession session() {
        return state().session;
    }

    public static LegacyFlash flash() {
        return state().flash;
    }

    public static Map<String, Object> args() {
        return state().args;
    }

    public static Snapshot capture() {
        return new Snapshot(state());
    }

    public static Result apply(Result result) {
        State state = CURRENT.get();
        if (state == null || result == null) {
            return result;
        }
        return apply(state, result);
    }

    private static Result apply(State state, Result result) {
        Result updated = state.response.applyTo(result);
        if (state.session.isCleared()) {
            updated = updated.withNewSession();
        } else if (state.session.isDirty()) {
            updated = updated.withSession(new Http.Session(state.session));
        }
        if (state.flash.isDirty()) {
            updated = updated.withFlash(new Http.Flash(state.flash));
        }
        return updated;
    }

    public static final class Snapshot {
        private final State state;

        private Snapshot(State state) {
            this.state = state;
        }

        public Result apply(Result result) {
            if (result == null) {
                return null;
            }
            return LegacyRequestContext.apply(state, result);
        }
    }

    private static State state() {
        State state = CURRENT.get();
        if (state == null) {
            throw new IllegalStateException("No Play request is bound to the current thread");
        }
        return state;
    }

    private static final class State {
        private final Http.Request request;
        private final LegacyResponse response = new LegacyResponse();
        private final LegacySession session;
        private final LegacyFlash flash;
        private final Map<String, Object> args = new LinkedHashMap<>();

        private State(Http.Request request) {
            this.request = request;
            this.session = new LegacySession(dataOf(request.session()));
            this.flash = new LegacyFlash(dataOf(request.flash()));
        }

        private static Map<String, String> dataOf(Object holder) {
            try {
                Object data = holder.getClass().getMethod("data").invoke(holder);
                if (data instanceof Map) {
                    return new LinkedHashMap<>((Map<String, String>) data);
                }
            } catch (Exception ignored) {
                // Older Play request maps are already copied through the fallback.
            }
            return new LinkedHashMap<>();
        }
    }

    public static class DirtyMap extends LinkedHashMap<String, String> {
        private boolean dirty;
        private boolean cleared;

        DirtyMap(Map<String, String> values) {
            super(values);
        }

        public boolean isDirty() {
            return dirty;
        }

        public boolean isCleared() {
            return cleared;
        }

        @Override
        public String put(String key, String value) {
            dirty = true;
            cleared = false;
            return super.put(key, value);
        }

        @Override
        public void putAll(Map<? extends String, ? extends String> values) {
            if (!values.isEmpty()) {
                dirty = true;
                cleared = false;
            }
            super.putAll(values);
        }

        @Override
        public String remove(Object key) {
            dirty = true;
            return super.remove(key);
        }

        @Override
        public void clear() {
            dirty = true;
            cleared = true;
            super.clear();
        }
    }

    public static class LegacySession extends DirtyMap {
        LegacySession(Map<String, String> values) {
            super(values);
        }
    }

    public static class LegacyFlash extends DirtyMap {
        LegacyFlash(Map<String, String> values) {
            super(values);
        }
    }
}
