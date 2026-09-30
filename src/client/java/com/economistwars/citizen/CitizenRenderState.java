package com.economistwars.citizen;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public final class CitizenRenderState extends HumanoidRenderState {
    public Identifier skin;
    public boolean teleporting;
    public int teleportProgress;
    public boolean actionProgressVisible;
    public int actionProgress;
}
