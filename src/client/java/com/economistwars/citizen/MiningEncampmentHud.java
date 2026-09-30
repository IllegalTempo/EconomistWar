package com.economistwars.citizen;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;

/** Shows resource stock while the player is near a loaded mining encampment. */
public final class MiningEncampmentHud implements HudElement {
    private static final int RESCAN_INTERVAL_TICKS = 10;

    private ClientLevel cachedLevel;
    private int cachedPlayerTick = Integer.MIN_VALUE;
    private MineWorksiteBlockEntity nearestSite;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            cachedLevel = null;
            nearestSite = null;
            return;
        }

        if (cachedLevel != client.level
                || client.player.tickCount - cachedPlayerTick >= RESCAN_INTERVAL_TICKS) {
            cachedLevel = client.level;
            cachedPlayerTick = client.player.tickCount;
            nearestSite = findNearestSite(client.level, client.player.blockPosition());
        }

        if (nearestSite == null || nearestSite.isRemoved()) return;

        Component status = Component.translatable("hud.economistwars.mining_encampment",
                nearestSite.resourceRemaining(), nearestSite.resourceCapacity());
        int centerX = client.getWindow().getGuiScaledWidth() / 2;
        int top = 8;
        int halfWidth = client.font.width(status) / 2 + 7;
        graphics.fill(centerX - halfWidth, top - 3, centerX + halfWidth,
                top + client.font.lineHeight + 3, 0xA0000000);
        graphics.centeredText(client.font, status, centerX, top, 0xFFFFFFFF);
    }

    private static MineWorksiteBlockEntity findNearestSite(ClientLevel level, BlockPos playerPos) {
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        int chunkRadius = 3;
        MineWorksiteBlockEntity nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        ClientChunkCache chunks = level.getChunkSource();

        for (int chunkX = playerChunkX - chunkRadius; chunkX <= playerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = playerChunkZ - chunkRadius; chunkZ <= playerChunkZ + chunkRadius; chunkZ++) {
                LevelChunk chunk = chunks.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof MineWorksiteBlockEntity site)) continue;
                    BlockPos sitePos = site.getBlockPos();
                    double dx = sitePos.getX() + 0.5 - (playerPos.getX() + 0.5);
                    double dy = sitePos.getY() + 0.5 - (playerPos.getY() + 0.5);
                    double dz = sitePos.getZ() + 0.5 - (playerPos.getZ() + 0.5);
                    double distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (site.contains(playerPos) && distanceSquared < nearestDistanceSquared) {
                        nearest = site;
                        nearestDistanceSquared = distanceSquared;
                    }
                }
            }
        }
        return nearest;
    }
}
