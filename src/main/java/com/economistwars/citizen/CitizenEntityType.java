package com.economistwars.citizen;

import com.economistwars.EconomistWars;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.Registry;

public final class CitizenEntityType {
    public static final ResourceKey<EntityType<?>> CITIZEN_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "citizen")
    );
    public static final EntityType<CitizenEntity> CITIZEN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            CITIZEN_KEY,
            EntityType.Builder.<CitizenEntity>of(CitizenEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .build(CITIZEN_KEY)
    );

    private CitizenEntityType() {}

    public static void registerAttributes() {
        FabricDefaultAttributeRegistry.register(CITIZEN, CitizenEntity.createAttributes());
    }

    public static void initialize() {
        EconomistWars.LOGGER.info("Registered citizen entity type");
    }
}
