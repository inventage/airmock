package com.inventage.airmock.kernel.proxy.internal;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static io.netty.handler.codec.DateFormatter.parseHttpDate;

public final class ParseUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseUtils.class);

    private ParseUtils() {
    }

    /**
     * Parse the dateHeader into a Date.
     *
     * @param value dateHeader
     * @return Date
     */
    public static Date parseDateHeaderDate(String value) {
        try {
            return parseHttpDate(value);
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse the warningHeader into a Date.
     *
     * @param value warningHeader
     * @return date
     */
    public static Date parseWarningHeaderDate(String value) {
        // warn-code
        int index = value.indexOf(' ');
        if (index > 0) {
            // warn-agent
            index = value.indexOf(' ', index + 1);
            if (index > 0) {
                // warn-text
                index = value.indexOf(' ', index + 1);
                if (index > 0) {
                    // warn-date
                    final int len = value.length();
                    if (index + 2 < len && value.charAt(index + 1) == '"' && value.charAt(len - 1) == '"') {
                        // Space for 2 double quotes
                        final String date = value.substring(index + 2, len - 1);
                        try {
                            return parseHttpDate(date);
                        }
                        catch (Exception e) {
                            LOGGER.debug(e);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static SimpleDateFormat rfc1123DateTime() {
        final SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format;
    }

    /**
     * Formate a Date object into a String with format ("EEE, dd MMM yyyy HH:mm:ss z").
     *
     * @param date date
     * @return string
     */
    public static String formatHttpDate(Date date) {
        return rfc1123DateTime().format(date);
    }
}
