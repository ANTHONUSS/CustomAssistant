package fr.anthonus.logs.exceptions;

/**
 * Thrown to indicate that an invalid log type name has been provided.
 */
public class LogTypeNameException extends RuntimeException {
    /**
     * Constructs a LogTypeNameException with the specified detail message.
     *
     * @param message the detail message
     */
    public LogTypeNameException(String message) {
        super(message);
    }
}

