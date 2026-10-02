package com.economistwars.network;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CitizenProfilePayloadTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void customNeedsAndFollowingFieldsSurviveProfilePacketRoundTrip() {
        var needs = List.of(
                new CitizenProfilePayload.NeedProgress("eat", 15),
                new CitizenProfilePayload.NeedProgress("thirst", 81),
                new CitizenProfilePayload.NeedProgress("social_contact", 42));
        var profile = profile(needs);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CitizenProfilePayload.CODEC.encode(buffer, profile);
            var restored = CitizenProfilePayload.CODEC.decode(buffer);
            assertEquals(needs, restored.needs());
            assertEquals(1.25, restored.workSpeedMultiplier());
            assertEquals(240, restored.estimatedWorkTicks());
            assertEquals(List.of(new CitizenProfilePayload.SkillProgress("mining", 123)), restored.skills());
            assertFalse(buffer.isReadable(), "The profile decoder must consume the complete packet");
        } finally {
            buffer.release();
        }
    }

    @Test
    void emptyNeedsDoNotShiftFollowingProfileFields() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CitizenProfilePayload.CODEC.encode(buffer, profile(List.of()));
            var restored = CitizenProfilePayload.CODEC.decode(buffer);
            assertTrue(restored.needs().isEmpty());
            assertEquals(240, restored.estimatedWorkTicks());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    private static CitizenProfilePayload profile(List<CitizenProfilePayload.NeedProgress> needs) {
        return new CitizenProfilePayload("Alex", "male", "citizen", "skin", "household",
                2, 5, 0, 3, 1, "idle", 0.5, List.of(), 10, 20,
                15, 20, 30, needs, 1.25, 240, ItemStack.EMPTY, ItemStack.EMPTY,
                false, 0, 0, 0, 0.0,
                List.of(new CitizenProfilePayload.SkillProgress("mining", 123)), List.of(), ItemStack.EMPTY);
    }
}
