package utils;

import javax.script.Invocable;

/**
 * Copied from https://gist.github.com/UnquietCode/5614860
 *
 * Simple utility class for executing an object's method with any arguments
 *
 * @author Keesun Baik
 * @see http://momentjs.com/docs/
 * @see https://gist.github.com/UnquietCode/5614860
 */
public class JSInvocable {

    private final Invocable invocable;
    private final Object object;

    public JSInvocable(Invocable invocable, Object object) {
        this.invocable = invocable;
        this.object = object;
    }

    public String invoke(String method, Object... args) {
        if(args == null) {
            args = new Object[0];
        }

        try {
            return invocable.invokeMethod(object, method, args).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}