package com.weatheraggregation.utils;

/* CLASS TO MANAGE LAMPORT TIME */
public class LamportClock {
    private int time;

    // initialise with time = 0
    public LamportClock() {
        this.time = 0;
    }

    // increment clock
    public synchronized void increment() {
        this.time++;
    }

    // update clock with largest time. Returns TRUE if receivedTime >= current time
    public synchronized boolean update(int receivedTime) {
        boolean isGreaterTime = receivedTime >= this.time;  // Check before updating
        this.time = Math.max(this.time, receivedTime) + 1;  // Update the clock
        return isGreaterTime;
    }

    // get clock time
    public synchronized int getTime() {
        return this.time;
    }
}
