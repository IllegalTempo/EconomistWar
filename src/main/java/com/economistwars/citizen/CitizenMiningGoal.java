package com.economistwars.citizen;

import com.economistwars.EconomistWars;
import com.economistwars.household.HouseholdSavedData;
import com.economistwars.network.CitizenProfilePayload;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/** Finds generated world encampments and mines their finite resource stock. */
final class CitizenMiningGoal extends CitizenProductionGoal {
    private static final int ACTION_TICKS = 40;
    private static final int STRUCTURE_SEARCH_RADIUS_CHUNKS = 512;
    private static final int DISCOVERY_SITE_RADIUS = 160;
    private static final TagKey<Structure> MINING_ENCAMPMENTS = TagKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "mining_encampment")
    );
    private static final net.minecraft.resources.ResourceKey<LootTable> MINING_LOOT_TABLE = net.minecraft.resources.ResourceKey.create(
            Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "citizen_mining")
    );

    private final Set<BlockPos> failedSites = new HashSet<>();
    private final Map<BlockPos, BlockPos> lastSiteTeleportPoints = new HashMap<>();
    private BlockPos target;
    private BlockPos discoveryTarget;
    private BlockPos workPosition;
    private UUID householdId;
    private List<ItemStack> pendingDrops = List.of();
    private int searchCooldown;
    private int discoveryWaitTicks;
    private int failedSiteRetryCooldown;
    private boolean dropsRolled;

    CitizenMiningGoal(CitizenEntity citizen) {
        super(citizen);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public CitizenDecisionPlanner.Action decisionAction() { return CitizenDecisionPlanner.Action.MINE; }
    @Override protected List<CitizenDecisionPlanner.OutputOutcome> productionOutcomes() {
        int stock = availableStock();
        int coalCost = MiningResourceValues.cost(new ItemStack(Items.COAL));
        int ironCost = MiningResourceValues.cost(new ItemStack(Items.RAW_IRON));
        int goldCost = MiningResourceValues.cost(new ItemStack(Items.RAW_GOLD));
        return List.of(
                new CitizenDecisionPlanner.OutputOutcome(Items.COAL, 1, 0.7, stock >= coalCost),
                new CitizenDecisionPlanner.OutputOutcome(Items.RAW_IRON, 1, 0.2, stock >= ironCost),
                new CitizenDecisionPlanner.OutputOutcome(Items.RAW_GOLD, 1, 0.1, stock >= goldCost));
    }
    @Override protected CitizenSkill productionSkill() { return CitizenSkill.MINING; }
    @Override protected int baseWorkTicks() { return ACTION_TICKS; }
    @Override protected int scoreTravelTicks() {
        BlockPos destination = workPosition != null ? workPosition : target != null ? target : discoveryTarget;
        return destination == null ? 0 : CitizenDecisionPlanner.travelTicks(citizen.distanceToSqr(
                destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5));
    }
    @Override protected int durationTravelTicks() {
        BlockPos destination = workPosition != null ? workPosition : target != null ? target : discoveryTarget;
        boolean alreadyAtWorksite = target != null && citizen.level() instanceof ServerLevel level
                && validWorksite(level, target) != null && validWorksite(level, target).contains(citizen.blockPosition());
        int travel = destination == null || alreadyAtWorksite ? 0 : CitizenDecisionPlanner.travelTicks(citizen.distanceToSqr(
                destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5));
        return travel + (discoveryTarget == null ? 0 : 100);
    }
    @Override protected boolean scheduledWork() {
        return citizen.level() instanceof ServerLevel level
                && CitizenWorkSchedule.workFor(level) == CitizenWorkSchedule.Work.MINE;
    }
    @Override protected List<CitizenProfilePayload.DecisionDetail> outcomeDetails(Evaluation evaluation) {
        int stock = Integer.MAX_VALUE;
        if (target != null && citizen.level() instanceof ServerLevel level && level.isLoaded(target)) {
            MineWorksiteBlockEntity worksite = validWorksite(level, target);
            if (worksite != null) stock = worksite.resourceRemaining();
        }
        int coalCost = MiningResourceValues.cost(new ItemStack(Items.COAL));
        int ironCost = MiningResourceValues.cost(new ItemStack(Items.RAW_IRON));
        int goldCost = MiningResourceValues.cost(new ItemStack(Items.RAW_GOLD));
        CitizenMarketMemory memory = citizen.marketMemory();
        CitizenNeeds needs = citizen.needs();
        double coalUtility = stock >= coalCost ? CitizenDecisionPlanner.adjustedOutputUtility(Items.COAL, 1,
                memory == null ? null : memory.received(), memory == null ? null : memory.requested(), needs) : 0.0;
        double ironUtility = stock >= ironCost ? CitizenDecisionPlanner.adjustedOutputUtility(Items.RAW_IRON, 1,
                memory == null ? null : memory.received(), memory == null ? null : memory.requested(), needs) : 0.0;
        double goldUtility = stock >= goldCost ? CitizenDecisionPlanner.adjustedOutputUtility(Items.RAW_GOLD, 1,
                memory == null ? null : memory.received(), memory == null ? null : memory.requested(), needs) : 0.0;
        return List.of(
                new CitizenProfilePayload.DecisionDetail("Available site stock", stock == Integer.MAX_VALUE ? "Unknown site; assumed sufficient" : stock + " resource points"),
                new CitizenProfilePayload.DecisionDetail("Coal outcome", String.format(java.util.Locale.ROOT, "70%%; cost %d; %s; utility %.4f; weighted contribution %.4f", coalCost, stock >= coalCost ? "affordable" : "unaffordable", coalUtility, coalUtility * 0.7)),
                new CitizenProfilePayload.DecisionDetail("Raw iron outcome", String.format(java.util.Locale.ROOT, "20%%; cost %d; %s; utility %.4f; weighted contribution %.4f", ironCost, stock >= ironCost ? "affordable" : "unaffordable", ironUtility, ironUtility * 0.2)),
                new CitizenProfilePayload.DecisionDetail("Raw gold outcome", String.format(java.util.Locale.ROOT, "10%%; cost %d; %s; utility %.4f; weighted contribution %.4f", goldCost, stock >= goldCost ? "affordable" : "unaffordable", goldUtility, goldUtility * 0.1))
        );
    }

    private int availableStock() {
        if (target != null && citizen.level() instanceof ServerLevel level && level.isLoaded(target)) {
            MineWorksiteBlockEntity worksite = validWorksite(level, target);
            if (worksite != null) return worksite.resourceRemaining();
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canUse() {
        if (!(citizen.level() instanceof ServerLevel level) || citizen.level().isClientSide()
                || citizen.isTeleporting()
                || CitizenWorkSchedule.isNight(level)) {
            return false;
        }
        if (searchCooldown > 0) {
            searchCooldown = Math.max(0, searchCooldown - CitizenDecisionPlanner.RESCORE_TICKS);
            return false;
        }
        searchCooldown = 100 + citizen.getRandom().nextInt(40);
        failedSiteRetryCooldown -= CitizenDecisionPlanner.RESCORE_TICKS;
        if (failedSiteRetryCooldown <= 0) {
            failedSites.clear();
            failedSiteRetryCooldown = 40;
        }
        target = null;
        discoveryTarget = null;
        workPosition = null;
        pendingDrops = List.of();
        dropsRolled = false;
        householdId = citizen.householdId().orElse(null);
        if (householdId == null || !HouseholdSavedData.get(level).hasMember(householdId, citizen.citizenId())) {
            return false;
        }
        if (citizen.inventoryFull()) {
            citizen.requestWorkProductDelivery();
            return false;
        }

        target = nearestKnownSite(level);
        BlockPos located = level.findNearestMapStructure(MINING_ENCAMPMENTS,
                citizen.blockPosition(), STRUCTURE_SEARCH_RADIUS_CHUNKS, true);
        if (located != null && (target == null
                || citizen.distanceToSqr(located.getX() + 0.5, located.getY(), located.getZ() + 0.5)
                < citizen.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5))) {
            target = null;
            discoveryTarget = located;
        }
        if (target == null && discoveryTarget == null) return false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(citizen.level() instanceof ServerLevel level)
                || CitizenWorkSchedule.isNight(level)
                || householdId == null
                || !HouseholdSavedData.get(level).hasMember(householdId, citizen.citizenId())
                || citizen.workProductDeliveryRequested()) {
            return false;
        }

        if (discoveryTarget != null) {
            BlockPos found = nearestLoadedSite(level, discoveryTarget, DISCOVERY_SITE_RADIUS);
            if (found != null) {
                discoveryTarget = null;
                target = found;
                workPosition = findWorkPosition(level, target);
                return workPosition != null;
            }
            return citizen.distanceToSqr(discoveryTarget.getX() + 0.5, discoveryTarget.getY(),
                    discoveryTarget.getZ() + 0.5) > 48 * 48 || discoveryWaitTicks < 100;
        }

        if (target == null) return false;
        if (!level.isLoaded(target)) return true;
        MineWorksiteBlockEntity worksite = validWorksite(level, target);
        if (worksite == null || !hasAffordableDrop(worksite.resourceRemaining())) {
            skipTarget(level);
            return false;
        }
        if (!worksite.hasTriggerBounds()) return true;
        if (workPosition == null) workPosition = findWorkPosition(level, target);
        return workPosition != null;
    }

    @Override
    public void start() {
        startProductionWork();
        discoveryWaitTicks = 0;
        citizen.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        if (citizen.level() instanceof ServerLevel level) beginTravel(level);
    }

    @Override
    public void tick() {
        if (!(citizen.level() instanceof ServerLevel level)) return;
        if (citizen.isTeleporting()) return;
        if (discoveryTarget != null) {
            BlockPos found = nearestLoadedSite(level, discoveryTarget, DISCOVERY_SITE_RADIUS);
            if (found != null) {
                target = found;
                discoveryTarget = null;
                workPosition = findWorkPosition(level, target);
                if (workPosition == null) {
                    skipTarget(level);
                    return;
                }
            } else {
                double distance = citizen.distanceToSqr(discoveryTarget.getX() + 0.5, discoveryTarget.getY(), discoveryTarget.getZ() + 0.5);
                if (distance <= 48 * 48) {
                    discoveryWaitTicks++;
                    return;
                }
                beginTravel(level);
                return;
            }
        }

        if (target == null) return;
        if (!level.isLoaded(target)) {
            beginTravel(level);
            return;
        }
        MineWorksiteBlockEntity worksite = validWorksite(level, target);
            if (worksite == null || !hasAffordableDrop(worksite.resourceRemaining())) {
            skipTarget(level);
            return;
        }
        if (!worksite.hasTriggerBounds()) return;
        if (workPosition == null) workPosition = findWorkPosition(level, target);
        if (workPosition == null) {
            skipTarget(level);
            return;
        }
        if (!worksite.contains(citizen.blockPosition())) {
            beginTravel(level);
            return;
        }

        if (!dropsRolled) {
            pendingDrops = rollMiningDrops(level);
            dropsRolled = true;
            MiningResourceValues.MiningYield payable = MiningResourceValues.affordableDrops(
                    pendingDrops, worksite.resourceRemaining());
            if (!citizen.canCarryItems(payable.drops())) {
                citizen.requestWorkProductDelivery();
                return;
            }
        }
        if (tickProductionWork(InteractionHand.MAIN_HAND)) {
            finishMiningAction(level, worksite);
        }
    }

    @Override
    public void stop() {
        citizen.cancelTeleport();
        if (!citizen.hasCarriedItems()) citizen.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        target = null;
        discoveryTarget = null;
        workPosition = null;
        householdId = null;
        pendingDrops = List.of();
        dropsRolled = false;
        resetProductionWork();
    }

    private BlockPos nearestKnownSite(ServerLevel level) {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos site : MineSiteSavedData.get(level).positions(level)) {
            if (failedSites.contains(site)) continue;
            if (level.isLoaded(site)) {
                MineWorksiteBlockEntity worksite = validWorksite(level, site);
                if (worksite == null || !hasAffordableDrop(worksite.resourceRemaining())) {
                    failedSites.add(site);
                    MineSiteSavedData.get(level).unregister(level, site);
                    continue;
                }
                if (!worksite.hasTriggerBounds()) continue;
            }
            double distance = citizen.distanceToSqr(site.getX() + 0.5, site.getY(), site.getZ() + 0.5);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = site;
            }
        }
        return nearest;
    }

    private BlockPos nearestLoadedSite(ServerLevel level, BlockPos center, int radius) {
        BlockPos nearest = null;
        double nearestDistance = (double) radius * radius;
        for (BlockPos site : MineSiteSavedData.get(level).positions(level)) {
            if (!level.isLoaded(site) || failedSites.contains(site)) continue;
            double distance = site.distToCenterSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
            if (distance > nearestDistance) continue;
            MineWorksiteBlockEntity worksite = validWorksite(level, site);
            if (worksite == null || !hasAffordableDrop(worksite.resourceRemaining()) || !worksite.hasTriggerBounds()) continue;
            nearest = site;
            nearestDistance = distance;
        }
        return nearest;
    }

    private MineWorksiteBlockEntity validWorksite(ServerLevel level, BlockPos site) {
        if (!level.getBlockState(site).is(MineWorksiteBlock.BLOCK)) return null;
        return level.getBlockEntity(site) instanceof MineWorksiteBlockEntity worksite ? worksite : null;
    }

    private static boolean hasAffordableDrop(int stock) {
        return stock >= Math.min(MiningResourceValues.cost(new ItemStack(Items.COAL)),
                Math.min(MiningResourceValues.cost(new ItemStack(Items.RAW_IRON)),
                        MiningResourceValues.cost(new ItemStack(Items.RAW_GOLD))));
    }

    private BlockPos findWorkPosition(ServerLevel level, BlockPos site) {
        MineWorksiteBlockEntity worksite = validWorksite(level, site);
        if (worksite == null || !worksite.hasTriggerBounds()) return null;
        net.minecraft.world.level.levelgen.structure.BoundingBox bounds = worksite.triggerBounds();
        BlockPos selected = null;
        BlockPos fallback = null;
        BlockPos previous = lastSiteTeleportPoints.get(site);
        int safeCount = 0;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    BlockPos stand = new BlockPos(x, y, z);
                    if (!CitizenTeleportPoints.isSafe(level, citizen, stand)) continue;
                    if (fallback == null) fallback = stand;
                    if (stand.equals(previous)) continue;
                    if (citizen.getRandom().nextInt(++safeCount) == 0) selected = stand;
                }
            }
        }
        BlockPos result = selected != null ? selected : fallback;
        if (result != null) lastSiteTeleportPoints.put(site.immutable(), result.immutable());
        return result;
    }

    private void beginTravel(ServerLevel level) {
        if (citizen.isTeleporting()) return;
        if (target != null && discoveryTarget == null && workPosition == null) {
            if (!level.isLoaded(target)) {
                level.getChunkAt(target);
                return;
            }
            workPosition = findWorkPosition(level, target);
            if (workPosition == null) return;
            MineWorksiteBlockEntity worksite = validWorksite(level, target);
            if (worksite != null && worksite.contains(citizen.blockPosition())) return;
        }
        BlockPos destination = discoveryTarget != null ? discoveryDestination(level)
                : workPosition != null ? workPosition : target;
        if (destination != null) citizen.beginTeleport(level, destination, "mining_encampment");
    }

    private BlockPos discoveryDestination(ServerLevel level) {
        int x = discoveryTarget.getX();
        int z = discoveryTarget.getZ();
        level.getChunkAt(discoveryTarget);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return CitizenTeleportPoints.randomSafePosition(level, citizen, new BlockPos(x, y, z),
                8, 5, 144, null, candidate -> true);
    }

    private void finishMiningAction(ServerLevel level, MineWorksiteBlockEntity worksite) {
        if (!level.getBlockState(target).is(MineWorksiteBlock.BLOCK)
                || CitizenWorkSchedule.isNight(level)
                || householdId == null || !HouseholdSavedData.get(level).hasMember(householdId, citizen.citizenId())) {
            return;
        }
        MiningResourceValues.MiningYield payable = MiningResourceValues.affordableDrops(
                pendingDrops, worksite.resourceRemaining());
        if (!citizen.canCarryItems(payable.drops())) {
            citizen.requestWorkProductDelivery();
            return;
        }
        if (payable.resourceCost() > 0 && !worksite.consume(payable.resourceCost())) {
            skipTarget(level);
            return;
        }
        if (!payable.drops().isEmpty()) {
            citizen.carryItems(payable.drops());
            citizen.setInventoryWork(CitizenWorkSchedule.Work.MINE);
            citizen.addSkillExperience(CitizenSkill.MINING, 5);
        }
        citizen.restartDecisionProgress(estimatedDurationTicks());
        resetProductionWork();
        dropsRolled = false;
        pendingDrops = List.of();
    }

    private void skipTarget(ServerLevel level) {
        if (target != null) {
            failedSites.add(target.immutable());
            MineSiteSavedData.get(level).unregister(level, target);
        }
        target = null;
        workPosition = null;
        pendingDrops = List.of();
        dropsRolled = false;
    }

    private List<ItemStack> rollMiningDrops(ServerLevel level) {
        LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
        List<ItemStack> drops = level.getServer().reloadableRegistries().getLootTable(MINING_LOOT_TABLE)
                .getRandomItems(params);
        return drops.stream().filter(drop -> !drop.isEmpty()).map(ItemStack::copy).toList();
    }

}

