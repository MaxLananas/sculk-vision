package com.sculkvision;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkAnalyzer {
    
    private final Map<ChunkPos, ChunkData> chunks = new ConcurrentHashMap<>();
    private int tickCounter = 0;
    private int totalEntities = 0;
    private int criticalCount = 0;
    private volatile boolean isUpdating = false;
    
    private static final Set<Block> SCULK = Set.of(
        Blocks.SCULK, Blocks.SCULK_CATALYST, Blocks.SCULK_SENSOR,
        Blocks.CALIBRATED_SCULK_SENSOR, Blocks.SCULK_SHRIEKER, Blocks.SCULK_VEIN
    );
    
    public void tick(MinecraftClient client) {
        for (ChunkData data : chunks.values()) {
            data.tick();
        }
        
        tickCounter++;
        if (tickCounter >= 8 && !isUpdating) {
            tickCounter = 0;
            asyncUpdate(client);
        }
    }
    
    public void forceUpdate(MinecraftClient client) {
        fullUpdate(client);
    }
    
    private void asyncUpdate(MinecraftClient client) {
        if (isUpdating) return;
        isUpdating = true;
        
        final ClientWorld world = client.world;
        final ChunkPos playerChunk = client.player != null ? client.player.getChunkPos() : null;
        final int viewDist = Math.min(client.options.getViewDistance().getValue(), 16);
        
        CompletableFuture.runAsync(() -> {
            try {
                if (world != null && playerChunk != null) {
                    doUpdate(world, playerChunk, viewDist);
                }
            } catch (Exception ignored) {
            } finally {
                isUpdating = false;
            }
        });
    }
    
    private void fullUpdate(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        doUpdate(client.world, client.player.getChunkPos(), 
                 Math.min(client.options.getViewDistance().getValue(), 16));
    }
    
    private void doUpdate(ClientWorld world, ChunkPos playerChunk, int viewDist) {
        Set<ChunkPos> active = new HashSet<>();
        int newTotalEntities = 0;
        int newCriticalCount = 0;
        
        for (int dx = -viewDist; dx <= viewDist; dx++) {
            for (int dz = -viewDist; dz <= viewDist; dz++) {
                if (dx * dx + dz * dz > viewDist * viewDist) continue;
                
                ChunkPos pos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                active.add(pos);
                
                try {
                    WorldChunk chunk = world.getChunk(pos.x, pos.z);
                    if (chunk != null) {
                        int[] result = analyzeChunk(world, chunk, pos);
                        newTotalEntities += result[0];
                        if (result[1] == 1) newCriticalCount++;
                    }
                } catch (Exception ignored) {}
            }
        }
        
        totalEntities = newTotalEntities;
        criticalCount = newCriticalCount;
        chunks.keySet().removeIf(p -> !active.contains(p));
    }
    
    private int[] analyzeChunk(ClientWorld world, WorldChunk chunk, ChunkPos pos) {
        ChunkData data = chunks.computeIfAbsent(pos, ChunkData::new);
        
        int x1 = pos.getStartX();
        int z1 = pos.getStartZ();
        
        // Sample 5x5 grid of heights for smooth terrain
        float[][] heights = new float[5][5];
        float offset = SculkConfig.get().overlayHeightOffset;
        
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                int sampleX = x1 + i * 4;
                int sampleZ = z1 + j * 4;
                try {
                    heights[i][j] = world.getTopY(Heightmap.Type.MOTION_BLOCKING, sampleX, sampleZ) + offset;
                } catch (Exception e) {
                    heights[i][j] = 64 + offset;
                }
            }
        }
        
        // Count entities
        Box box = new Box(x1, world.getBottomY(), z1, x1 + 16, world.getTopYInclusive(), z1 + 16);
        
        List<Entity> entities;
        try {
            entities = world.getOtherEntities(null, box, e -> true);
        } catch (Exception e) {
            entities = List.of();
        }
        
        int entityCount = entities.size();
        boolean hasSculk = entities.stream().anyMatch(e -> e instanceof WardenEntity);
        if (!hasSculk) hasSculk = checkSculk(world, pos);
        
        Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
        int beCount = blockEntities != null ? blockEntities.size() : 0;
        
        boolean loading = entityCount == 0 && beCount == 0 && !chunk.needsSaving();
        
        data.update(entityCount, beCount, hasSculk, loading, heights);
        
        return new int[] { entityCount, data.isCritical() ? 1 : 0 };
    }
    
    private boolean checkSculk(ClientWorld world, ChunkPos pos) {
        Random rand = new Random(pos.toLong());
        for (int i = 0; i < 3; i++) {
            int x = pos.getStartX() + rand.nextInt(16);
            int z = pos.getStartZ() + rand.nextInt(16);
            try {
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                for (int dy = -3; dy <= 0; dy++) {
                    Block block = world.getBlockState(new BlockPos(x, y + dy, z)).getBlock();
                    if (SCULK.contains(block)) return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }
    
    public Collection<ChunkData> getChunks() { return chunks.values(); }
    public int chunkCount() { return chunks.size(); }
    public int totalEntities() { return totalEntities; }
    public int criticalCount() { return criticalCount; }
}