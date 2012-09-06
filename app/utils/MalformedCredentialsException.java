package utils;

public class MalformedCredentialsException extends Exception {
    
    public MalformedCredentialsException() {
        super();
    }
    
    public MalformedCredentialsException(String message) {
        super(message);
    }

    public MalformedCredentialsException(String message, Exception cause) {
        super(message, cause);
    }

    /**
     * 
     */
    private static final long serialVersionUID = 7129730952518199719L;
}
