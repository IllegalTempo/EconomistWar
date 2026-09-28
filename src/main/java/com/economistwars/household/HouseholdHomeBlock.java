package com.economistwars.household;

import com.economistwars.EconomistWars;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

public final class HouseholdHomeBlock extends Block implements EntityBlock {
    private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "household_home")
    );

    public static final Block BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            BLOCK_KEY,
            new HouseholdHomeBlock(BlockBehaviour.Properties.of().setId(BLOCK_KEY).noCollision().noLootTable())
    );
    public static final BlockEntityType<HouseholdHomeBlockEntity> BLOCK_ENTITY_TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "household_home"),
            new BlockEntityType<>(HouseholdHomeBlockEntity::new, Set.of(BLOCK))
    );

    private HouseholdHomeBlock(Properties properties) {
        super(properties);
    }

    public static void initialize() {
        EconomistWars.LOGGER.info("Registered household home anchor");
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new HouseholdHomeBlockEntity(position, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != BLOCK_ENTITY_TYPE) {
            return null;
        }
        return (tickLevel, position, blockState, blockEntity) -> {
            if (tickLevel instanceof ServerLevel serverLevel) {
                ((HouseholdHomeBlockEntity) blockEntity).serverTick(serverLevel);
            }
        };
    }
}
