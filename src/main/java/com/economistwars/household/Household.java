package com.economistwars.household;

import com.economistwars.ownership.OwnershipSavedData;
import com.economistwars.citizen.SettlementMarketSavedData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.function.Predicate;

public final class Household {
    private final UUID id;
    private final LinkedHashSet<UUID> members;
    private String homeDimension;
    private BlockPos homePosition;
    private final List<ItemStack> householdStorage;
    private Runnable changed = () -> {};
    private int coins = 100, foodShortage;
    private long lastEconomyDay = -1, lastMarketDay = -1;
    void onChange(Runnable callback) { changed = callback; }
    public void markChanged() { changed.run(); }
    public int coins() { return coins; }
    public int foodShortage() { return foodShortage; }
    public boolean spendCoins(int amount) { if (amount < 0 || coins < amount) return false; coins -= amount; markChanged(); return true; }
    public void receiveCoins(int amount) { coins = (int)Math.min(9999L, coins + (long)Math.max(0, amount)); markChanged(); }
    public boolean beginMarketDay(long day) { if (day <= lastMarketDay) return false; lastMarketDay = day; markChanged(); return true; }
    public boolean canBuyFarm(int owned) { return owned < 3 && coins >= 60 && (foodCount() < memberCount() * 4 || coins >= 150); }
    private static boolean isFood(ItemStack stack) { return stack.is(Items.WHEAT) || stack.is(Items.BREAD) || stack.has(net.minecraft.core.component.DataComponents.FOOD); }
    public boolean processEconomyDay(long day) {
        if (day <= lastEconomyDay) return false;
        boolean initial = lastEconomyDay < 0; lastEconomyDay = day;
        if (initial) { markChanged(); return false; }
        int fed = 0;
        for (int i = 0; i < memberCount(); i++) if (!takeFromStorage(Household::isFood, 1).isEmpty()) fed++;
        foodShortage = memberCount() - fed;
        int wheat = householdStorage.stream().filter(s -> s.is(Items.WHEAT)).mapToInt(ItemStack::getCount).sum();
        int sold = Math.min(4, Math.max(0, wheat - 32));
        for (int i = 0; i < sold; i++) takeFromStorage(s -> s.is(Items.WHEAT), 1);
        receiveCoins(sold); markChanged(); return true;
    }
    List<String> economyState() {
        return List.of(Integer.toString(coins), Integer.toString(foodShortage),
                Long.toString(lastEconomyDay), Long.toString(lastMarketDay));
    }

    void restoreEconomy(List<String> state) {
        if (state.size() != 4) return;
        try {
            coins = Math.clamp(Integer.parseInt(state.get(0)), 0, 9999);
            foodShortage = Math.max(0, Integer.parseInt(state.get(1)));
            lastEconomyDay = Long.parseLong(state.get(2));
            lastMarketDay = Long.parseLong(state.get(3));
        } catch (IllegalArgumentException ignored) {
            // Keep defaults for malformed household economy metadata.
        }
    }
    public int storageSize() { return 9; }
    public ItemStack getItem(int slot) { return slot >= 0 && slot < 9 ? householdStorage.get(slot) : ItemStack.EMPTY; }
    public void setItem(int slot, ItemStack stack) { if (slot >= 0 && slot < 9) { householdStorage.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(Math.min(stack.getCount(), Math.min(64, stack.getMaxStackSize())))); markChanged(); } }
    public ItemStack removeItem(int slot, int amount) { ItemStack result = getItem(slot).split(Math.max(0, amount)); markChanged(); return result; }
    public void clearStorage() { householdStorage.replaceAll(s -> ItemStack.EMPTY); markChanged(); }
    List<ItemStack> storageSlots() { return householdStorage.stream().map(ItemStack::copy).toList(); }
    void restoreStorage(List<ItemStack> stacks) { householdStorage.clear(); stacks.stream().limit(9).forEach(s -> householdStorage.add(s.copy())); while (householdStorage.size() < 9) householdStorage.add(ItemStack.EMPTY); }
    Household(UUID id) {
        this(id, new LinkedHashSet<>());
    }

    Household(UUID id, Set<UUID> members) {
        this.id = id;
        this.members = new LinkedHashSet<>(members);
        this.householdStorage = new ArrayList<>(); while (householdStorage.size() < 9) householdStorage.add(ItemStack.EMPTY);
    }
    public UUID id() {
        return id;
    }

    public Set<UUID> members() {
        return Set.copyOf(members);
    }

    public int memberCount() {
        return members.size();
    }

    public int foodCount() {
        return householdStorage.stream()
                .filter(stack -> !stack.isEmpty() && isFood(stack))
                .mapToInt(ItemStack::getCount).sum();
    }

    /** Farm work is limited to registered farm parcels and claimed crops. */
    public boolean ownsFarmPosition(ServerLevel level, BlockPos position) {
        return OwnershipSavedData.get(level).ownsFarmPosition(level.dimension(), id, position);
    }

    public Optional<BlockPos> nearestFarmPosition(ServerLevel level, Vec3 position) {
        return OwnershipSavedData.get(level).nearestFarmPosition(level.dimension(), id, position);
    }

    public int farmParcelCount(ServerLevel level) {
        return OwnershipSavedData.get(level).farmParcelCount(level.dimension(), id);
    }

    public Optional<BlockPos> home(ResourceKey<Level> dimension) {
        return dimension.identifier().toString().equals(homeDimension)
                ? Optional.ofNullable(homePosition) : Optional.empty();
    }

    void setHome(ResourceKey<Level> dimension, BlockPos position) {
        homeDimension = dimension.identifier().toString();
        homePosition = position.immutable();
    }

    public Optional<BlockPos> nearestMarket(ServerLevel level) {
        return home(level.dimension()).flatMap(home -> SettlementMarketSavedData.get(level)
                .nearestPosition(level.dimension(), home, 128));
    }

    Optional<String> serializedHome() {
        return homePosition == null ? Optional.empty()
                : Optional.of("home|" + homeDimension + "|" + homePosition.asLong());
    }

    void restoreHome(String serialized) {
        String[] fields = serialized.split("\\|", -1);
        if (fields.length != 3 || !fields[0].equals("home")) return;
        Identifier dimension = Identifier.tryParse(fields[1]);
        if (dimension == null) return;
        try {
            BlockPos position = BlockPos.of(Long.parseLong(fields[2]));
            homeDimension = dimension.toString();
            homePosition = position;
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed home metadata while preserving household membership.
        }
    }

    boolean add(UUID citizenId) {
        return members.add(citizenId);
    }

    boolean remove(UUID citizenId) {
        return members.remove(citizenId);
    }

    boolean isEmpty() {
        return members.isEmpty();
    }
    public ItemStack requestFromStorage(Item item, int amount) {
        return takeFromStorage(stack -> stack.is(item), amount);
    }
    public ItemStack requestFromStorage(DataComponentType<?> dataComponentType, int amount) {
        return takeFromStorage(stack -> stack.has(dataComponentType), amount);
    }
    public ItemStack AddToStorage(ItemStack item, int amount) {
        return item != null && !item.isEmpty() && amount > 0
                ? deposit(item.copyWithCount(amount)) : ItemStack.EMPTY;
    }

    public List<ItemStack> storageContents() {
        return householdStorage.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    /** Item ownership follows the actual inventory; there is no second persisted item ledger. */
    public List<com.economistwars.ownership.Ownership> itemOwnerships() {
        return householdStorage.stream().filter(stack -> !stack.isEmpty())
                .map(stack -> new com.economistwars.ownership.Ownership(id,
                        new com.economistwars.ownership.assetsReferences.ItemAssetsReference(stack))).toList();
    }

    public List<Item> storedItems() {
        return householdStorage.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::getItem).distinct().toList();
    }

    public boolean containsStoredItem(ItemStack target) {
        return !target.isEmpty() && householdStorage.stream().anyMatch(stack -> !stack.isEmpty()
                && ItemStack.isSameItemSameComponents(stack, target));
    }

    /** Deposits into nine slots, returning any items that do not fit. */
    public ItemStack deposit(ItemStack offered) {
        if (offered == null || offered.isEmpty()) return ItemStack.EMPTY;
        ItemStack remaining = offered.copy();
        for (int i = 0; i < 9 && !remaining.isEmpty(); i++) {
            ItemStack stored = householdStorage.get(i);
            if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, remaining)) continue;
            int moved = Math.min(remaining.getCount(), Math.max(0, Math.min(64, stored.getMaxStackSize()) - stored.getCount()));
            stored.grow(moved); remaining.shrink(moved);
        }
        for (int i = 0; i < 9 && !remaining.isEmpty(); i++) if (householdStorage.get(i).isEmpty()) {
            int moved = Math.min(remaining.getCount(), Math.min(64, remaining.getMaxStackSize()));
            householdStorage.set(i, remaining.copyWithCount(moved)); remaining.shrink(moved);
        }
        if (remaining.getCount() != offered.getCount()) markChanged();
        return remaining;
    }
    private ItemStack findStoredItem(Predicate<ItemStack> matches) {
        return householdStorage.stream().filter(stack -> !stack.isEmpty() && matches.test(stack))
                .findFirst().orElse(ItemStack.EMPTY);
    }

    public ItemStack takeFromStorage(Predicate<ItemStack> matches, int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack stored = findStoredItem(matches);
        ItemStack result = stored.isEmpty() ? ItemStack.EMPTY : stored.split(Math.min(amount, stored.getCount())); if (!result.isEmpty()) markChanged(); return result;
    }

    public boolean exchangeOne(Household other, Predicate<ItemStack> ownItem, Predicate<ItemStack> otherItem) {
        if (other == null || other == this) return false;
        ItemStack offered = findStoredItem(ownItem);
        ItemStack requested = other.findStoredItem(otherItem);
        if (offered.isEmpty() || requested.isEmpty()) return false;
        List<ItemStack> before = storageSlots(), otherBefore = other.storageSlots();
        ItemStack ownTransfer = offered.split(1);
        ItemStack otherTransfer = requested.split(1);
        if (!deposit(otherTransfer).isEmpty() || !other.deposit(ownTransfer).isEmpty()) { restoreStorage(before); other.restoreStorage(otherBefore); return false; }
        markChanged(); other.markChanged();
        return true;
    }
}
