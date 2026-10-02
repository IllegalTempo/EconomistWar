package com.economistwars.ownership;

import com.economistwars.ownership.assetsReferences.ItemAssetsReference;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemAssetsReferenceTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var components = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build();
        Items.WHEAT.builtInRegistryHolder().bindComponents(components);
        Items.BREAD.builtInRegistryHolder().bindComponents(components);
    }

    @Test
    void referenceRecordsAWholeStackAndDefensivelyCopiesIt() {
        ItemStack source = new ItemStack(Items.WHEAT, 12);
        ItemAssetsReference asset = new ItemAssetsReference(source);
        source.shrink(5);
        asset.stack().shrink(9);
        assertEquals(Items.WHEAT, asset.item());
        assertEquals(12, asset.amount());
        assertTrue(asset.matches(new ItemStack(Items.WHEAT, 12)));
        assertFalse(asset.matches(new ItemStack(Items.WHEAT, 15)));
        assertFalse(asset.matches(new ItemStack(Items.WHEAT, 11)));
        assertFalse(asset.matches(new ItemStack(Items.BREAD, 15)));
    }

    @Test
    void ownedQuantityAndComponentVariantsSurviveSerialization() {
        ItemStack source = new ItemStack(Items.BREAD, 3);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Household | bread"));
        ItemAssetsReference original = new ItemAssetsReference(source);
        ItemAssetsReference restored = ItemAssetsReference.parsePayload(original.serializePayload());
        assertEquals(3, restored.amount());
        assertEquals(source.get(DataComponents.CUSTOM_NAME), restored.stack().get(DataComponents.CUSTOM_NAME));
        assertTrue(restored.matches(source));
        assertFalse(restored.matches(new ItemStack(Items.BREAD, 3)));
    }

    @Test
    void emptyAndInvalidItemAssetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ItemAssetsReference(ItemStack.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new ItemAssetsReference(new ItemStack(Items.WHEAT, 100)));
        assertThrows(IllegalArgumentException.class, () -> ItemAssetsReference.parsePayload("not-json"));
        assertThrows(IllegalArgumentException.class, () -> ItemAssetsReference.parsePayload("{\"id\":\"missing:item\",\"count\":1}"));
    }
}
