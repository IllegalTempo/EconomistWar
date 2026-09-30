package com.economistwars.household;

import com.economistwars.EconomistWars;
import java.util.Set;
import com.economistwars.network.CitizenNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;

/** Visible, non-colliding settlement-center anchor which owns that settlement's barter book. */
public final class SettlementMarketBlock extends Block implements EntityBlock {
    private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "settlement_market"));
    public static final Block BLOCK = Registry.register(BuiltInRegistries.BLOCK, BLOCK_KEY,
            new SettlementMarketBlock(BlockBehaviour.Properties.of().setId(BLOCK_KEY).noCollision().noLootTable()));
    public static final BlockEntityType<SettlementMarketBlockEntity> BLOCK_ENTITY_TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "settlement_market"),
            new BlockEntityType<>(SettlementMarketBlockEntity::new, Set.of(BLOCK)));

    private SettlementMarketBlock(Properties properties) { super(properties); }

    public static void initialize() { EconomistWars.LOGGER.info("Registered settlement market anchor"); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                Player player, BlockHitResult hit) {
        return openMarket(level, position, player);
    }

    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
                                         BlockPos position, Player player, net.minecraft.world.InteractionHand hand,
                                         BlockHitResult hit) {
        return openMarket(level, position, player);
    }

    private InteractionResult openMarket(Level level, BlockPos position, Player player) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(position) instanceof SettlementMarketBlockEntity market) {
            CitizenNetworking.sendMarket(serverPlayer, market.snapshot());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new SettlementMarketBlockEntity(position, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != BLOCK_ENTITY_TYPE) return null;
        return (tickLevel, position, blockState, blockEntity) -> {
            if (tickLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                ((SettlementMarketBlockEntity) blockEntity).serverTick(serverLevel);
            }
        };
    }
}
