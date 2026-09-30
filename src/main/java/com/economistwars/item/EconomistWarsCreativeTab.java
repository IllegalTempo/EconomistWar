package com.economistwars.item;

import com.economistwars.EconomistWars;
import com.economistwars.citizen.MineWorksiteBlock;
import com.economistwars.household.FarmPlotBlock;
import com.economistwars.household.HouseholdHomeBlock;
import com.economistwars.household.HouseholdStorageBlock;
import com.economistwars.household.SettlementMarketBlock;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Registers the mod's placeable content and exposes it in a single creative tab. */
public final class EconomistWarsCreativeTab {
    private static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "main")
    );

    private static final Item FARM_PLOT = registerBlockItem("farm_plot", FarmPlotBlock.BLOCK);
    private static final Item HOUSEHOLD_HOME = registerBlockItem("household_home", HouseholdHomeBlock.BLOCK);
    private static final Item HOUSEHOLD_STORAGE = registerBlockItem("household_storage", HouseholdStorageBlock.BLOCK);
    private static final Item SETTLEMENT_MARKET = registerBlockItem("settlement_market", SettlementMarketBlock.BLOCK);
    private static final Item MINE_WORKSITE = registerBlockItem("mine_worksite", MineWorksiteBlock.BLOCK);

    public static final CreativeModeTab TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            TAB_KEY,
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.economistwars.main"))
                    .icon(() -> new ItemStack(SETTLEMENT_MARKET))
                    .displayItems((parameters, output) -> List.of(
                            FARM_PLOT,
                            HOUSEHOLD_HOME,
                            HOUSEHOLD_STORAGE,
                            SETTLEMENT_MARKET,
                            MINE_WORKSITE
                    ).forEach(output::accept))
                    .build()
    );

    private EconomistWarsCreativeTab() {}

    private static Item registerBlockItem(String path, net.minecraft.world.level.block.Block block) {
        ResourceKey<Item> key = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, path)
        );
        return Registry.register(BuiltInRegistries.ITEM, key,
                new BlockItem(block, new Item.Properties().setId(key)));
    }

    public static void initialize() {
        EconomistWars.LOGGER.info("Registered Economist Wars blocks and creative tab");
    }
}
