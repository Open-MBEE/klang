package k.frontend;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing Time and Duration literals to milliseconds.
 *
 * Supported Time formats (ISO 8601):
 * - 2025-12-07 (date only, midnight)
 * - 2025-12-07T10:30:00 (date and time)
 * - 2025-12-07T10:30:00.123 (with milliseconds)
 * - 2025-12-07T10:30:00Z (with timezone)
 * - 2025-12-07T10:30:00+05:00 (with timezone offset)
 *
 * Supported Duration formats:
 * - ISO 8601: P1Y, P2M, P3D, PT5H, PT30M, PT45S, P1Y2M3DT4H5M6S
 * - Time format: 1:05:02 (H:MM:SS), 1:05:02.123 (with millis)
 *
 * All values are converted to milliseconds for SMT solving.
 */
public class TimeParser {

    // ISO 8601 Date/Time pattern
    private static final Pattern DATE_TIME_PATTERN = Pattern.compile(
        "(\\d{2,4})-(\\d{1,3}|\\d{1,2}-\\d{1,2})" +  // Year and day-of-year or month-day
        "(?:T(\\d{1,2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?)?" +  // Optional time
        "([Zz]|[+-]\\d{1,2}:\\d{2}|[A-Za-z]{3})?"  // Optional timezone
    );

    // ISO 8601 Duration pattern
    private static final Pattern DURATION_ISO_PATTERN = Pattern.compile(
        "P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?" +
        "(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?"
    );

    // HH:MM:SS.mmm format
    private static final Pattern DURATION_HMS_PATTERN = Pattern.compile(
        "(\\d+):(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?"
    );

    // Constants for time conversions (in milliseconds)
    private static final long MS_PER_SECOND = 1000L;
    private static final long MS_PER_MINUTE = 60 * MS_PER_SECOND;
    private static final long MS_PER_HOUR = 60 * MS_PER_MINUTE;
    private static final long MS_PER_DAY = 24 * MS_PER_HOUR;
    private static final long MS_PER_WEEK = 7 * MS_PER_DAY;
    // Approximations for months and years (use average)
    private static final long MS_PER_MONTH = 30 * MS_PER_DAY;  // ~30 days
    private static final long MS_PER_YEAR = 365 * MS_PER_DAY;  // ~365 days

    /**
     * Parse a date/time literal to milliseconds since Unix epoch.
     * @param literal The date/time string (e.g., "2025-12-07T10:30:00")
     * @return Milliseconds since 1970-01-01 00:00:00 UTC
     */
    public static long parseDateTime(String literal) {
        // Remove surrounding quotes if present
        String s = stripQuotes(literal);

        Matcher m = DATE_TIME_PATTERN.matcher(s);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid date/time literal: " + literal);
        }

        int year = Integer.parseInt(m.group(1));
        String dayPart = m.group(2);

        int month = 1, dayOfMonth = 1;
        if (dayPart.contains("-")) {
            // Month-day format: MM-DD
            String[] parts = dayPart.split("-");
            month = Integer.parseInt(parts[0]);
            dayOfMonth = Integer.parseInt(parts[1]);
        } else {
            // Day of year format: DDD
            int dayOfYear = Integer.parseInt(dayPart);
            // Convert day-of-year to month-day (simplified)
            int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
            if (isLeapYear(year)) daysInMonth[1] = 29;
            int remaining = dayOfYear;
            for (int i = 0; i < 12 && remaining > daysInMonth[i]; i++) {
                remaining -= daysInMonth[i];
                month++;
            }
            dayOfMonth = remaining;
        }

        int hour = 0, minute = 0, second = 0, millis = 0;
        if (m.group(3) != null) {
            hour = Integer.parseInt(m.group(3));
            minute = Integer.parseInt(m.group(4));
            second = Integer.parseInt(m.group(5));
            if (m.group(6) != null) {
                String ms = m.group(6);
                // Pad to 3 digits
                while (ms.length() < 3) ms = ms + "0";
                millis = Integer.parseInt(ms.substring(0, 3));
            }
        }

        // Calculate milliseconds since epoch (simplified, ignoring timezone for now)
        long result = 0;

        // Years since 1970
        for (int y = 1970; y < year; y++) {
            result += isLeapYear(y) ? 366 * MS_PER_DAY : 365 * MS_PER_DAY;
        }

        // Days in months before current month
        int[] daysInMonth = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        if (isLeapYear(year)) daysInMonth[1] = 29;
        for (int m2 = 1; m2 < month; m2++) {
            result += daysInMonth[m2 - 1] * MS_PER_DAY;
        }

        // Days in current month
        result += (dayOfMonth - 1) * MS_PER_DAY;

        // Time
        result += hour * MS_PER_HOUR;
        result += minute * MS_PER_MINUTE;
        result += second * MS_PER_SECOND;
        result += millis;

        return result;
    }

    /**
     * Parse a duration literal to milliseconds.
     * @param literal The duration string (e.g., "P1DT2H30M" or "1:30:00")
     * @return Duration in milliseconds
     */
    public static long parseDuration(String literal) {
        String s = stripQuotes(literal);

        // Try ISO 8601 format first
        if (s.startsWith("P")) {
            return parseDurationISO(s);
        }

        // Try HH:MM:SS format
        Matcher m = DURATION_HMS_PATTERN.matcher(s);
        if (m.matches()) {
            long hours = Long.parseLong(m.group(1));
            long minutes = Long.parseLong(m.group(2));
            long seconds = Long.parseLong(m.group(3));
            long millis = 0;
            if (m.group(4) != null) {
                String ms = m.group(4);
                while (ms.length() < 3) ms = ms + "0";
                millis = Long.parseLong(ms.substring(0, 3));
            }
            return hours * MS_PER_HOUR + minutes * MS_PER_MINUTE +
                   seconds * MS_PER_SECOND + millis;
        }

        throw new IllegalArgumentException("Invalid duration literal: " + literal);
    }

    /**
     * Parse ISO 8601 duration format.
     */
    private static long parseDurationISO(String s) {
        Matcher m = DURATION_ISO_PATTERN.matcher(s);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid ISO 8601 duration: " + s);
        }

        long result = 0;

        if (m.group(1) != null) result += Long.parseLong(m.group(1)) * MS_PER_YEAR;
        if (m.group(2) != null) result += Long.parseLong(m.group(2)) * MS_PER_MONTH;
        if (m.group(3) != null) result += Long.parseLong(m.group(3)) * MS_PER_WEEK;
        if (m.group(4) != null) result += Long.parseLong(m.group(4)) * MS_PER_DAY;
        if (m.group(5) != null) result += Long.parseLong(m.group(5)) * MS_PER_HOUR;
        if (m.group(6) != null) result += Long.parseLong(m.group(6)) * MS_PER_MINUTE;
        if (m.group(7) != null) {
            double secs = Double.parseDouble(m.group(7));
            result += (long)(secs * MS_PER_SECOND);
        }

        return result;
    }

    /**
     * Convert milliseconds to SMT integer literal.
     */
    public static String toSMT(long millis) {
        return String.valueOf(millis);
    }

    /**
     * Format milliseconds as ISO 8601 duration for display.
     */
    public static String formatDuration(long millis) {
        if (millis == 0) return "PT0S";

        StringBuilder sb = new StringBuilder("P");

        long days = millis / MS_PER_DAY;
        millis %= MS_PER_DAY;
        long hours = millis / MS_PER_HOUR;
        millis %= MS_PER_HOUR;
        long minutes = millis / MS_PER_MINUTE;
        millis %= MS_PER_MINUTE;
        long seconds = millis / MS_PER_SECOND;
        millis %= MS_PER_SECOND;

        if (days > 0) sb.append(days).append("D");

        if (hours > 0 || minutes > 0 || seconds > 0 || millis > 0) {
            sb.append("T");
            if (hours > 0) sb.append(hours).append("H");
            if (minutes > 0) sb.append(minutes).append("M");
            if (seconds > 0 || millis > 0) {
                if (millis > 0) {
                    sb.append(seconds).append(".").append(String.format("%03d", millis)).append("S");
                } else {
                    sb.append(seconds).append("S");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Format milliseconds as HH:MM:SS.mmm for display.
     */
    public static String formatHMS(long millis) {
        long hours = millis / MS_PER_HOUR;
        millis %= MS_PER_HOUR;
        long minutes = millis / MS_PER_MINUTE;
        millis %= MS_PER_MINUTE;
        long seconds = millis / MS_PER_SECOND;
        millis %= MS_PER_SECOND;

        if (millis > 0) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        } else {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
    }

    private static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    private static String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    // ========== Test Main ==========

    public static void main(String[] args) {
        System.out.println("=== Date/Time Parsing ===");
        String[] dates = {
            "2025-12-07",
            "2025-12-07T10:30:00",
            "2025-12-07T10:30:00.123",
            "2025-341",  // Day of year
            "2025-341T14:30:00"
        };
        for (String d : dates) {
            try {
                long ms = parseDateTime(d);
                System.out.printf("%s -> %d ms%n", d, ms);
            } catch (Exception e) {
                System.out.printf("%s -> ERROR: %s%n", d, e.getMessage());
            }
        }

        System.out.println("\n=== Duration Parsing ===");
        String[] durations = {
            "P1D",
            "PT1H",
            "PT30M",
            "PT45S",
            "P1DT2H30M",
            "PT1H30M45S",
            "PT1H30M45.123S",
            "1:30:00",
            "1:05:02",
            "1:05:02.123",
            "0:00:30",
            "100:00:00"
        };
        for (String d : durations) {
            try {
                long ms = parseDuration(d);
                System.out.printf("%s -> %d ms (%s, %s)%n", d, ms, formatDuration(ms), formatHMS(ms));
            } catch (Exception e) {
                System.out.printf("%s -> ERROR: %s%n", d, e.getMessage());
            }
        }
    }
}

