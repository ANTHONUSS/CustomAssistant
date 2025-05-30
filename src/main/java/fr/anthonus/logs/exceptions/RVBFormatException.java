package fr.anthonus.logs.exceptions;

/**
 * Thrown to indicate that an invalid RGB format has been provided.
 */
public class RVBFormatException extends RuntimeException {
    /**
     * Constructs a RVBFormatException with the specified detail message.
     *
     * @param message the detail message
     */
    public RVBFormatException(String message) {
        super(message);
    }
}

