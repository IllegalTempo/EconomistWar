package com.economistwars.citizen;

/** Tracks elapsed ticks against the selected action's current duration estimate. */
final class CitizenDecisionProgress {
    private int durationTicks;
    private int elapsedTicks;

    void start(int durationTicks) {
        this.durationTicks = Math.max(0, durationTicks);
        elapsedTicks = 0;
    }

    void advance() {
        if (durationTicks > 0) elapsedTicks = Math.min(durationTicks, elapsedTicks + 1);
    }

    int durationTicks() { return durationTicks; }
    int elapsedTicks() { return elapsedTicks; }

    int percent() {
        return durationTicks == 0 ? 0 : (int) ((long) elapsedTicks * 100 / durationTicks);
    }
}
