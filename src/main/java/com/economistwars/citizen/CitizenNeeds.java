package com.economistwars.citizen;

/** The citizen's urgency values. Larger values mean a more urgent need. */
public final class CitizenNeeds {
    public static final int MAX = 100;
    private int eat;
    private int entertainment;
    private int safety;
    private long lastUpdatedDay;

    public CitizenNeeds() {
        this(0, 0, 0, 0);
    }

    public CitizenNeeds(int eat, int entertainment, int safety, long lastUpdatedDay) {
        this.eat = clamp(eat);
        this.entertainment = clamp(entertainment);
        this.safety = clamp(safety);
        this.lastUpdatedDay = Math.max(0, lastUpdatedDay);
    }

    public int eat() { return eat; }
    public int entertainment() { return entertainment; }
    public int safety() { return safety; }
    public long lastUpdatedDay() { return lastUpdatedDay; }

    public void advanceToDay(long day) {
        if (day <= lastUpdatedDay) return;
        long elapsedDays = day - lastUpdatedDay;
        eat = grow(eat, elapsedDays, 25);
        entertainment = grow(entertainment, elapsedDays, 12);
        safety = grow(safety, elapsedDays, 6);
        lastUpdatedDay = day;
    }

    public void satisfy(int eatAmount, int entertainmentAmount, int safetyAmount) {
        eat = Math.max(0, eat - Math.max(0, eatAmount));
        entertainment = Math.max(0, entertainment - Math.max(0, entertainmentAmount));
        safety = Math.max(0, safety - Math.max(0, safetyAmount));
    }

    private static int grow(int current, long days, int rate) {
        int neededDays = (MAX - current + rate - 1) / rate;
        if (days >= neededDays) return MAX;
        return (int) Math.min(MAX, (long) current + days * rate);
    }

    private static int clamp(int value) {
        return Math.clamp(value, 0, MAX);
    }
}
