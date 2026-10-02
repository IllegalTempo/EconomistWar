package com.economistwars.citizen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class MineSiteState {
    public static final Codec<MineSiteState> CODEC = RecordCodecBuilder.create(i -> i.group(
            CitizenAssetKey.CODEC.fieldOf("key").forGetter(s -> s.key),
            Codec.intRange(0,10000).fieldOf("stock").forGetter(s -> s.stock),
            Codec.BOOL.fieldOf("valid").forGetter(s -> s.valid),
            Codec.INT.listOf().optionalFieldOf("bounds", List.of()).forGetter(MineSiteState::savedBounds),
            Codec.BOOL.optionalFieldOf("imported", true).forGetter(s -> s.imported)
    ).apply(i, MineSiteState::load));
    public final CitizenAssetKey key;
    int stock;
    boolean valid = true, imported = true;
    private BoundingBox bounds;
    private Runnable changed = () -> {};
    public MineSiteState(CitizenAssetKey key, int stock, BoundingBox bounds) {
        this.key = key; this.stock = Math.clamp(stock,0,10000); this.bounds = bounds;
    }
    void onChange(Runnable callback) { changed = callback; }
    public int remaining() { return stock; }
    public BoundingBox bounds() { return bounds; }
    public boolean consume(int amount) {
        if (!valid || amount <= 0 || amount > stock) return false;
        stock -= amount; changed.run(); return true;
    }
    void importOnce(int legacyStock, BoundingBox legacyBounds) {
        if (!imported && valid) { stock = Math.clamp(legacyStock,0,10000); imported = true; changed.run(); }
        if (bounds == null && legacyBounds != null && valid) { bounds = legacyBounds; changed.run(); }
    }
    private List<Integer> savedBounds() {
        return bounds == null ? List.of() : List.of(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ());
    }
    private static MineSiteState load(CitizenAssetKey key,int stock,boolean valid,List<Integer> bounds,boolean imported) {
        MineSiteState s = new MineSiteState(key,stock,bounds.size() == 6 ? new BoundingBox(
                bounds.get(0),bounds.get(1),bounds.get(2),bounds.get(3),bounds.get(4),bounds.get(5)) : null);
        s.valid = valid; s.imported = imported; return s;
    }
}
