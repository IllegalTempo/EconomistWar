package com.economistwars.citizen;

import net.minecraft.server.level.ServerLevel;

/** Shared five-day citizen work rotation. */
public final class CitizenWorkSchedule {
    private CitizenWorkSchedule() {}

    public static Work workFor(ServerLevel level) {
        long day = level.getOverworldClockTime() / 24_000L;
        return Math.floorMod(day, 5) == 0 ? Work.FARM : Work.MINE;
    }

    public static boolean isNight(ServerLevel level) {
        long timeOfDay = Math.floorMod(level.getOverworldClockTime(), 24_000L);
        return timeOfDay >= 13_000L && timeOfDay < 23_000L;
    }

    public enum Work {
        FARM,
        MINE
    }
}
