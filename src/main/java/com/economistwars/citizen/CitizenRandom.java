package com.economistwars.citizen;

/** SplitMix64: its complete state can be saved between mining outcomes. */
public final class CitizenRandom {
    private long state;
    public CitizenRandom(long state) { this.state = state; }
    public long state() { return state; }
    public long nextLong() {
        long value = (state += 0x9E3779B97F4A7C15L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
        long value, result;
        do { value = nextLong() >>> 1; result = value % bound; }
        while (value - result + (bound - 1) < 0);
        return (int) result;
    }
}
