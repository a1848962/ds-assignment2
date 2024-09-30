package com.weatheraggregation.utils;

/* BASIC CLASS TO MANAGE LAMPORT TIME */
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

    // update clock with largest time
    public synchronized void update(int receivedTime) {
        this.time = Math.max(this.time, receivedTime) + 1;
    }

    // get clock time
    public synchronized int getTime() {
        return this.time;
    }
}
