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

    // update clock with greatest time. Returns TRUE if receivedTime > current time
    public synchronized boolean update(int receivedTime) {
        boolean updated = false;
        if (receivedTime > this.time) {
            this.time = receivedTime;
            updated = true;
        }
        this.increment();
        return updated;
    }

    // get clock time
    public synchronized int getTime() {
        return this.time;
    }
}
