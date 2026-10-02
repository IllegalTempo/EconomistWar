package com.economistwars.citizen;

import com.economistwars.EconomistWars;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Visible mineral work face with finite, persistent resource stock. */
public final class MineWorksiteBlock extends Block implements EntityBlock {
    private static final ResourceKey<Block> KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "mine_worksite")
    );

    public static final Block BLOCK = Registry.register(
            BuiltInRegistries.BLOCK, KEY,
            new MineWorksiteBlock(BlockBehaviour.Properties.of().setId(KEY).noLootTable().strength(4.0F))
    );
    public static final BlockEntityType<MineWorksiteBlockEntity> BLOCK_ENTITY_TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "mine_worksite"),
            new BlockEntityType<>(MineWorksiteBlockEntity::new, Set.of(BLOCK))
    );

    private MineWorksiteBlock(Properties properties) {
        super(properties);
    }

    public static void initialize() {
        EconomistWars.LOGGER.info("Registered public mine work site");
    }

    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved) {
        super.onPlace(state,level,pos,old,moved);
        if (!old.is(this) && level instanceof ServerLevel server)
            MineSiteSavedData.get(server).placed(new CitizenAssetKey(server.dimension().identifier().toString(),pos));
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved) {
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
        if (!level.getBlockState(pos).is(this)) MineSiteSavedData.get(level)
                .invalidate(new CitizenAssetKey(level.dimension().identifier().toString(),pos));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new MineWorksiteBlockEntity(position, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != BLOCK_ENTITY_TYPE) {
            return null;
        }
        return (tickLevel, position, blockState, blockEntity) -> {
            if (tickLevel instanceof ServerLevel serverLevel) {
                ((MineWorksiteBlockEntity) blockEntity).serverTick(serverLevel);
            }
        };
    }
}
