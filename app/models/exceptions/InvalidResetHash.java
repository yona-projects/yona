package models.exceptions;

/**
 * User: doortts
 * Date: 4/5/13
 * Time: 10:37 AM
 */
public class InvalidResetHash extends RuntimeException {
    public static final String NOT_EXISTS = "Not Existed ResetHash";
    private static final long serialVersionUID = -1L;
    public static final String EXIRED = "Reset Password hash is expired";

    public InvalidResetHash(String message) {
        super(message);
    }
}
