package com.economistwars.citizen;

import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;

final class MinecraftTestBootstrap {
    private static boolean bootstrapped;

    private MinecraftTestBootstrap() {}

    static synchronized void ensureBootstrapped() {
        if (!bootstrapped) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            bootstrapped = true;
        }
    }
}
