package com.economistwars.ownership;

import com.economistwars.ownership.assetsReferences.ItemAssetsReference;
import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.JukeboxSong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemAssetRegistryContextTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Items.MUSIC_DISC_13.builtInRegistryHolder().bindComponents(
                net.minecraft.core.component.DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).build());
    }

    @Test
    void dynamicComponentHolderSurvivesJsonPayloadUsingNbtRegistryContext() throws Exception {
        var songs = new MappedRegistry<JukeboxSong>(Registries.JUKEBOX_SONG, Lifecycle.stable());
        var songKey = ResourceKey.create(Registries.JUKEBOX_SONG, Identifier.parse("economistwars:test_song"));
        var song = songs.register(songKey, new JukeboxSong(
                Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.parse("economistwars:test_sound"))),
                Component.literal("Test song"), 10.0F, 1), RegistrationInfo.BUILT_IN);
        songs.freeze();

        var registries = new ArrayList<Registry<?>>();
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registries()
                .forEach(entry -> registries.add(entry.value()));
        registries.add(songs);
        var ops = RegistryOps.create(NbtOps.INSTANCE, new RegistryAccess.ImmutableRegistryAccess(registries));
        ItemStack source = new ItemStack(Items.MUSIC_DISC_13);
        source.set(DataComponents.JUKEBOX_PLAYABLE, new JukeboxPlayable(song));

        var original = new ItemAssetsReference(source);
        String payload = original.serializePayload(ops);
        assertTrue(payload.contains("economistwars:test_song"));
        var restored = ItemAssetsReference.parsePayload(payload, ops);

        assertTrue(ItemStack.isSameItemSameComponents(source, restored.stack()));
        assertEquals(1, restored.amount());
        assertSame(song, restored.stack().get(DataComponents.JUKEBOX_PLAYABLE).song());
        assertThrows(IllegalArgumentException.class, () -> ItemAssetsReference.parsePayload(payload));

        var owner = java.util.UUID.randomUUID();
        var group = new HouseholdOwnerships();
        group.addOwnership(new Ownership(owner, original));
        var data = new OwnershipSavedData();
        var ownershipsField = OwnershipSavedData.class.getDeclaredField("ownerships");
        ownershipsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var ownerships = (java.util.Map<java.util.UUID, HouseholdOwnerships>) ownershipsField.get(data);
        ownerships.put(owner, group);
        var codecField = OwnershipSavedData.class.getDeclaredField("CODEC");
        codecField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var codec = (com.mojang.serialization.Codec<OwnershipSavedData>) codecField.get(null);
        var loaded = codec.parse(ops, codec.encodeStart(ops, data).getOrThrow()).getOrThrow();
        @SuppressWarnings("unchecked")
        var loadedOwnerships = (java.util.Map<java.util.UUID, HouseholdOwnerships>) ownershipsField.get(loaded);
        var loadedAsset = (ItemAssetsReference) loadedOwnerships.get(owner).getAllOwnerships().getFirst().getAssetReference();
        assertTrue(loadedAsset.matches(source), "SavedData must pass world registry context to each item asset");
    }
}
