package com.economistwars.command;

import com.economistwars.EconomistWars;
import com.economistwars.citizen.CitizenEntity;
import com.economistwars.citizen.CitizenEntityType;
import com.economistwars.household.HouseholdSavedData;
import com.economistwars.household.LandSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class CitizenCommands {
    private CitizenCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ew")
                .then(Commands.literal("land").executes(context -> inspectLand(context.getSource())))
                .then(Commands.literal("household")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                                .executes(context -> createHousehold(context.getSource(), IntegerArgumentType.getInteger(context, "count"))))));
    }

    private static int inspectLand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        LandSavedData.get(player.level()).parcelAt(player.level(), player.blockPosition())
                .ifPresentOrElse(parcel -> source.sendSuccess(() -> parcel.owner() == null
                        ? Component.translatable("commands.economistwars.land.unowned")
                        : Component.translatable("commands.economistwars.land.owned", parcel.owner().toString()), false),
                        () -> source.sendFailure(Component.translatable("commands.economistwars.land.none")));
        return 1;
    }

    private static int createHousehold(CommandSourceStack source, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();
        HouseholdSavedData data = HouseholdSavedData.get(level);
        UUID householdId = data.createHousehold();
        List<CitizenEntity> created = new ArrayList<>();
        BlockPos origin = player.blockPosition();

        try {
            for (int index = 0; index < count; index++) {
                CitizenEntity citizen = new CitizenEntity(CitizenEntityType.CITIZEN, level);
                citizen.setHouseholdId(householdId);
                if (!data.addMember(householdId, citizen.citizenId())) {
                    throw new IllegalStateException("Could not register citizen household membership");
                }
                citizen.setPos(origin.getX() + (index % 4) - 1.5, origin.getY(), origin.getZ() + (index / 4) - 1.5);
                created.add(citizen);
                if (!level.addFreshEntity(citizen)) {
                    throw new IllegalStateException("Minecraft rejected a citizen entity spawn");
                }
            }
        } catch (RuntimeException exception) {
            for (CitizenEntity citizen : created) {
                citizen.discard();
                data.removeCitizen(citizen.citizenId());
            }
            data.removeHousehold(householdId);
            EconomistWars.LOGGER.error("Failed to create household {}; rolled back {} citizens", householdId, created.size(), exception);
            source.sendFailure(Component.translatable("commands.economistwars.household.failed"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.economistwars.household.created", count, householdId.toString()), true);
        return created.size();
    }
}
