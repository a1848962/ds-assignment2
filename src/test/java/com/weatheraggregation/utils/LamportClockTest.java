package com.weatheraggregation.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LamportClockTest {
    @Test
    public void testInitialTime() {
        LamportClock clock = new LamportClock();
        assertEquals(0, clock.getTime(), "Initial time should be 0");
    }

    @Test
    public void testIncrement() {
        LamportClock clock = new LamportClock();
        clock.increment();
        assertEquals(1, clock.getTime(), "Time after one increment should be 1");
        clock.increment();
        assertEquals(2, clock.getTime(), "Time after two increments should be 2");
    }

    @Test
    public void testUpdateWithGreaterTime() {
        LamportClock clock = new LamportClock();
        boolean updated = clock.update(5);
        assertTrue(updated, "Clock should have been updated with greater time");
        assertEquals(6, clock.getTime(), "Time should be received time + 1");
    }

    @Test
    public void testUpdateWithLowerOrEqualTime() {
        LamportClock clock = new LamportClock();
        clock.update(5);  // set time to 6
        boolean updated = clock.update(3);
        assertFalse(updated, "Clock should not be updated with lesser or equal time");
        assertEquals(7, clock.getTime(), "Time should be incremented by 1");
    }
}