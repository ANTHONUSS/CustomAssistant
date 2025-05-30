package fr.anthonus.logs.logTypes;

/**
 * The LogType interface defines methods for retrieving
 * the name and associated ANSI color code of a log type.
 */
public interface LogType {
    /**
     * Returns the log type name.
     *
     * @return the name of the log type
     */
    String getName();

    /**
     * Returns the ANSI color code for this log type.
     *
     * @return the ANSI color code
     */
    String getAnsiCode();
}
