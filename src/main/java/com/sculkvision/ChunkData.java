package com.sculkvision;

import net.minecraft.util.math.ChunkPos;

public class ChunkData {
    
    public enum Status {
        PERFECT(0.2f, 1.0f, 0.4f),
        WARNING(1.0f, 0.85f, 0.1f),
        DANGEROUS(1.0f, 0.4f, 0.1f),
        CRITICAL(1.0f, 0.1f, 0.15f),
        LOADING(0.5f, 0.2f, 0.8f),
        SCULK(0.1f, 0.85f, 0.9f);
        
        public final float r, g, b;
        Status(float r, float g, float b) {
            this.r = r; this.g = g; this.b = b;
        }
    }
    
    private final ChunkPos pos;
    private int entities;
    private int blockEntities;
    private boolean sculkActive;
    private boolean loading;
    private Status status = Status.PERFECT;
    private float pulsePhase;
    
    // Heights grid 5x5 for smooth terrain following
    private final float[][] heights = new float[5][5];
    private float minHeight, maxHeight, avgHeight;
    
    public ChunkData(ChunkPos pos) {
        this.pos = pos;
        this.pulsePhase = (float) (Math.random() * Math.PI * 2);
        this.avgHeight = 64;
    }
    
    public void update(int entities, int blockEntities, boolean sculk, boolean loading, float[][] heights) {
        this.entities = entities;
        this.blockEntities = blockEntities;
        this.sculkActive = sculk;
        this.loading = loading;
        
        // Copy heights
        float sum = 0;
        minHeight = Float.MAX_VALUE;
        maxHeight = Float.MIN_VALUE;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                this.heights[i][j] = heights[i][j];
                sum += heights[i][j];
                minHeight = Math.min(minHeight, heights[i][j]);
                maxHeight = Math.max(maxHeight, heights[i][j]);
            }
        }
        avgHeight = sum / 25f;
        
        SculkConfig cfg = SculkConfig.get();
        
        if (loading) {
            status = Status.LOADING;
        } else if (sculk) {
            status = Status.SCULK;
        } else if (entities > cfg.criticalThreshold || blockEntities > cfg.criticalBlockEntities) {
            status = Status.CRITICAL;
        } else if (entities > cfg.dangerousThreshold || blockEntities > 30) {
            status = Status.DANGEROUS;
        } else if (entities > cfg.warningThreshold || blockEntities > cfg.warningBlockEntities) {
            status = Status.WARNING;
        } else {
            status = Status.PERFECT;
        }
    }
    
    public void tick() {
        pulsePhase += 0.1f;
        if (pulsePhase > Math.PI * 2) pulsePhase -= (float) (Math.PI * 2);
    }
    
    public ChunkPos pos() { return pos; }
    public int entities() { return entities; }
    public int blockEntities() { return blockEntities; }
    public Status status() { return status; }
    public boolean isCritical() { return status == Status.CRITICAL; }
    public boolean isSculk() { return status == Status.SCULK; }
    
    public float pulse() {
        return 0.5f + 0.5f * (float) Math.sin(pulsePhase);
    }
    
    public float mspt() {
        return entities * 0.05f + blockEntities * 0.15f + (sculkActive ? 0.5f : 0);
    }
    
    public int centerX() { return pos.getStartX() + 8; }
    public int centerZ() { return pos.getStartZ() + 8; }
    
    public float getHeight(int gridX, int gridZ) {
        return heights[Math.min(4, Math.max(0, gridX))][Math.min(4, Math.max(0, gridZ))];
    }
    
    public float avgHeight() { return avgHeight; }
    public float minHeight() { return minHeight; }
    public float maxHeight() { return maxHeight; }
}