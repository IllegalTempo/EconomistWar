package com.economistwars.citizen;

import com.economistwars.EconomistWars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class MineWorksiteBlockEntity extends BlockEntity {
    public static final int RESOURCE_CAPACITY = 10_000;
    private static final TagKey<Structure> MINING_ENCAMPMENTS = TagKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "mining_encampment")
    );
    private int resourceRemaining = RESOURCE_CAPACITY;
    private BoundingBox triggerBounds;
    private boolean registered;

    public MineWorksiteBlockEntity(BlockPos position, BlockState state) {
        super(MineWorksiteBlock.BLOCK_ENTITY_TYPE, position, state);
    }

    public void serverTick(ServerLevel level) {
        if (triggerBounds == null) {
            StructureStart start = level.structureManager().getStructureWithPieceAt(worldPosition, MINING_ENCAMPMENTS);
            if (start != null && start.isValid()) {
                triggerBounds = start.getBoundingBox();
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        if (!registered) {
            MineSiteSavedData.get(level).register(level, worldPosition);
            registered = true;
        }
    }

    public int resourceRemaining() {
        return resourceRemaining;
    }

    public int resourceCapacity() {
        return RESOURCE_CAPACITY;
    }

    public boolean hasTriggerBounds() {
        return triggerBounds != null;
    }

    public BoundingBox triggerBounds() {
        return triggerBounds;
    }

    public boolean contains(BlockPos position) {
        return triggerBounds != null && triggerBounds.isInside(position);
    }

    public boolean consume(int amount) {
        if (amount <= 0 || amount > resourceRemaining) return false;
        resourceRemaining -= amount;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        resourceRemaining = Math.clamp(input.getInt("ResourceRemaining").orElse(RESOURCE_CAPACITY), 0, RESOURCE_CAPACITY);
        if (input.getInt("TriggerMinX").isPresent()) {
            triggerBounds = new BoundingBox(
                    input.getInt("TriggerMinX").orElse(0), input.getInt("TriggerMinY").orElse(0),
                    input.getInt("TriggerMinZ").orElse(0), input.getInt("TriggerMaxX").orElse(0),
                    input.getInt("TriggerMaxY").orElse(0), input.getInt("TriggerMaxZ").orElse(0));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ResourceRemaining", resourceRemaining);
        if (triggerBounds != null) {
            output.putInt("TriggerMinX", triggerBounds.minX());
            output.putInt("TriggerMinY", triggerBounds.minY());
            output.putInt("TriggerMinZ", triggerBounds.minZ());
            output.putInt("TriggerMaxX", triggerBounds.maxX());
            output.putInt("TriggerMaxY", triggerBounds.maxY());
            output.putInt("TriggerMaxZ", triggerBounds.maxZ());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
