package de.hage.verification.util;

public final class TimeFormatter {

    private TimeFormatter() {}

    public static String formatPlaytime(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0h 0m";
        }
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }
}