package de.hage.verification.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TimeFormatterTest {

    @Test
    void zeroSeconds() {
        assertEquals("0h 0m", TimeFormatter.formatPlaytime(0));
    }

    @Test
    void negativeSeconds() {
        assertEquals("0h 0m", TimeFormatter.formatPlaytime(-10));
    }

    @Test
    void onlyMinutes() {
        assertEquals("0h 5m", TimeFormatter.formatPlaytime(300));
    }

    @Test
    void hoursAndMinutes() {
        assertEquals("2h 30m", TimeFormatter.formatPlaytime(9000));
    }

    @Test
    void exactlyOneHour() {
        assertEquals("1h 0m", TimeFormatter.formatPlaytime(3600));
    }
}