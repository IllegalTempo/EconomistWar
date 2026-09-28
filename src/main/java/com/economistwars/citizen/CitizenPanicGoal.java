package com.economistwars.citizen;

import net.minecraft.world.entity.ai.goal.PanicGoal;

/** Vanilla panic navigation with a visible sprint state. */
final class CitizenPanicGoal extends PanicGoal {
    private final CitizenEntity citizen;

    CitizenPanicGoal(CitizenEntity citizen) {
        super(citizen, 1.5);
        this.citizen = citizen;
    }

    @Override
    public void start() {
        super.start();
        citizen.setSprinting(true);
    }

    @Override
    public void tick() {
        citizen.setSprinting(true);
        super.tick();
    }

    @Override
    public void stop() {
        super.stop();
        citizen.setSprinting(false);
    }
}
