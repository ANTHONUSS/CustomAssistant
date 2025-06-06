package fr.anthonus.logs.logTypes;

import fr.anthonus.logs.LOGs;
import fr.anthonus.logs.exceptions.LogTypeNameException;
import fr.anthonus.logs.exceptions.RVBFormatException;

import java.awt.*;

/**
 * The DefaultLogType enum provides predefined log types
 * with associated ANSI color codes for console logs.
 * You HAVE to use this template to create your own log types, just copy this in a new enum and type your custom log types.
 */
public enum DefaultLogType implements LogType {
    DEFAULT("DEFAULT", LOGs.createAnsiCode(Color.WHITE, false, false, false)),
    ERROR("ERROR", LOGs.createAnsiCode(Color.RED, false, false, false)),
    WARNING("WARNING", LOGs.createAnsiCode(Color.YELLOW, false, false, false)),
    DEBUG("DEBUG", LOGs.createAnsiCode(Color.PINK, false, false, true)),
    LOADING("LOADING", LOGs.createAnsiCode(53, 74, 255, false, false, false)),
    FILE_LOADING("FILE_LOADING", LOGs.createAnsiCode(0, 0, 0, 130, 0, 255, false, false, false)),;

    private final String name;
    private final String ansiCode;

    /**
     * Constructs a DefaultLogType with a name and an ANSI color code.
     *
     * @param name     the name of the log type
     * @param ansiCode the ANSI color code for this log type
     * @throws LogTypeNameException if the log type name is null or empty
     * @throws RVBFormatException   if the ANSI code is null, empty, or invalid
     */
    DefaultLogType(String name, String ansiCode) {
        if (name == null || name.isEmpty())
            throw new LogTypeNameException("The log type name cannot be null or empty.");
        this.name = name;

        if (ansiCode == null || ansiCode.isEmpty()) {
            throw new RVBFormatException("The ANSI code cannot be null or empty.");
        }

        if (!ansiCode.matches(LOGs.ANSI_REGEX)) {
            throw new RVBFormatException("The ANSI code is not valid for " + name + ".");
        }

        this.ansiCode = ansiCode;
    }

    /**
     * Returns the log type name.
     *
     * @return the log type name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the ANSI color code associated with this log type.
     *
     * @return the ANSI color code
     */
    @Override
    public String getAnsiCode() {
        return ansiCode;
    }
}
