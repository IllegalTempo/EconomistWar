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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;

/** A sealed household store: it exposes no menu or automation inventory. */
public final class HouseholdStorageBlock extends Block implements EntityBlock {
    private static final ResourceKey<Block> KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "household_storage")
    );
    public static final Block BLOCK = Registry.register(
            BuiltInRegistries.BLOCK, KEY,
            new HouseholdStorageBlock(BlockBehaviour.Properties.of().setId(KEY)
                    .destroyTime(-1.0f).explosionResistance(3600000.0f)
                    .pushReaction(PushReaction.IMMOVEABLE).noLootTable())
    );
    public static final BlockEntityType<HouseholdStorageBlockEntity> BLOCK_ENTITY_TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "household_storage"),
            new BlockEntityType<>(HouseholdStorageBlockEntity::new, Set.of(BLOCK))
    );

    private HouseholdStorageBlock(Properties properties) {
        super(properties);
    }

    public static void initialize() {
        EconomistWars.LOGGER.info("Registered sealed household storage");
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new HouseholdStorageBlockEntity(position, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position,
                                                Player player, BlockHitResult hit) {
        return openForCreative(state, level, position, player);
    }

    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
                                         BlockPos position, Player player, net.minecraft.world.InteractionHand hand,
                                         BlockHitResult hit) {
        return openForCreative(state, level, position, player);
    }

    private InteractionResult openForCreative(BlockState state, Level level, BlockPos position, Player player) {
        if (!player.isCreative()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(position) instanceof HouseholdStorageBlockEntity storage)) {
            return InteractionResult.PASS;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory,
                        new CreativeContainer(storage), 1),
                Component.literal("Household Storage")
        ));
        return InteractionResult.SUCCESS;
    }

    private static final class CreativeContainer implements net.minecraft.world.Container {
        private final HouseholdStorageBlockEntity storage;

        private CreativeContainer(HouseholdStorageBlockEntity storage) {
            this.storage = storage;
        }

        @Override public int getContainerSize() { return storage.getContainerSize(); }
        @Override public boolean isEmpty() { return storage.isEmpty(); }
        @Override public net.minecraft.world.item.ItemStack getItem(int slot) { return storage.getItem(slot); }
        @Override public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) {
            return storage.removeItem(slot, amount);
        }
        @Override public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) {
            return storage.removeItemNoUpdate(slot);
        }
        @Override public void setItem(int slot, net.minecraft.world.item.ItemStack stack) {
            storage.setItem(slot, stack);
        }
        @Override public boolean stillValid(Player player) { return storage.stillValid(player); }
        @Override public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack stack) {
            return storage.canPlaceItem(slot, stack);
        }
        @Override public void setChanged() { storage.setChanged(); }
        @Override public void clearContent() { storage.clearContent(); }
    }
}
