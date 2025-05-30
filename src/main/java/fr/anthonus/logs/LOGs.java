package fr.anthonus.logs;

import fr.anthonus.logs.exceptions.RVBFormatException;
import fr.anthonus.logs.logTypes.LogType;

import java.awt.*;

/**
 * This class is used to send logs to the console.
 * Each logs can be colored and tagged with a custom type.
 *
 * @author ANTHONUS
 * @version 2.0
 */
public class LOGs {
    public static final String ANSI_REGEX = "^\\u001B\\[((?:[134];){0,3})?38;2;(\\d{1,3});(\\d{1,3});(\\d{1,3})(?:;48;2;(\\d{1,3});(\\d{1,3});(\\d{1,3}))?m$";

    /**
     * Sends a log to the console using the specified message and log type.
     *
     * @param message the content of the log
     * @param logType the type of the log, defining the color and style
     */
    public static void sendLog(String message, LogType logType) {
        String timeMessage = "[" + java.time.LocalTime.now().toString().substring(0, 11) + "] ";

        String logTypeName = "[" + logType.getName() + "]";
        String ansiCode = logType.getAnsiCode();

        String resetCode = "\u001B[0m";

        System.out.println(ansiCode + timeMessage + logTypeName + " ==> " + message + resetCode);
    }

    /**
     * Creates an ANSI color code string.
     *
     * @param r the red component (0-255)
     * @param g the green component (0-255)
     * @param b the blue component (0-255)
     * @param underline whether the text should be underlined
     * @param italic whether the text should be italic
     * @param bold whether the text should be bold
     * @return the ANSI color code as a String
     */
    public static String createAnsiCode(int r, int g, int b,
                                        boolean underline, boolean italic, boolean bold) {

        verifyRGB(r, g, b);

        StringBuilder ansiCode = new StringBuilder("\u001B[");

        if (underline)
            ansiCode.append("4;");
        if (bold)
            ansiCode.append("1;");
        if (italic)
            ansiCode.append("3;");

        ansiCode.append("38;2;").append(r).append(";").append(g).append(";").append(b).append("m");

        return ansiCode.toString();
    }

    /**
     * Creates an ANSI color code string using a Color object.
     *
     * @param color the Color object for the foreground
     * @param underline whether the text should be underlined
     * @param italic whether the text should be italic
     * @param bold whether the text should be bold
     * @return the ANSI color code as a String
     */
    public static String createAnsiCode(Color color,
                                        boolean underline, boolean italic, boolean bold) {

        return createAnsiCode(color.getRed(), color.getGreen(), color.getBlue(), underline, italic, bold);
    }

    /**
     * Creates an ANSI color code string with both foreground and background colors.
     *
     * @param r the red component for foreground (0-255)
     * @param g the green component for foreground (0-255)
     * @param b the blue component for foreground (0-255)
     * @param bgR the red component for background (0-255)
     * @param bgG the green component for background (0-255)
     * @param bgB the blue component for background (0-255)
     * @param underline whether the text should be underlined
     * @param italic whether the text should be italic
     * @param bold whether the text should be bold
     * @return the ANSI color code as a String
     */
    public static String createAnsiCode(int r, int g, int b,
                                        int bgR, int bgG, int bgB,
                                        boolean underline, boolean italic, boolean bold) {

        verifyRGB(r, g, b, bgR, bgG, bgB);

        StringBuilder ansiCode = new StringBuilder("\u001B[");

        if (underline)
            ansiCode.append("4;");
        if (bold)
            ansiCode.append("1;");
        if (italic)
            ansiCode.append("3;");

        ansiCode.append("38;2;").append(r).append(";").append(g).append(";").append(b)
                .append(";48;2;").append(bgR).append(";").append(bgG).append(";").append(bgB).append("m");

        return ansiCode.toString();
    }

    /**
     * Creates an ANSI color code string for both foreground and background using Color objects.
     *
     * @param color the Color object for the foreground
     * @param bgColor the Color object for the background
     * @param underline whether the text should be underlined
     * @param italic whether the text should be italic
     * @param bold whether the text should be bold
     * @return the ANSI color code as a String
     */
    public static String createAnsiCode(Color color,
                                        Color bgColor,
                                        boolean underline, boolean italic, boolean bold) {

        return createAnsiCode(color.getRed(), color.getGreen(), color.getBlue(),
                bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(),
                underline, italic, bold);
    }

    /**
     * Verifies if the specified RGB values are within the valid range (0-255).
     *
     * @param values the RGB values to verify
     * @throws RVBFormatException if a value is out of range
     */
    private static void verifyRGB(int... values) {
        for (int value : values) {
            if (value < 0 || value > 255) {
                throw new RVBFormatException("The RGB values must be between 0 and 255.\n" +
                        "Invalid value: " + value);
            }
        }
    }


}

