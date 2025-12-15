package com.sculkvision;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class SculkRenderer {
    
    private final Random random = Random.create();
    private int particleTick = 0;
    
    public void renderWorld(WorldRenderContext ctx, ChunkAnalyzer analyzer) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        
        Camera camera = ctx.camera();
        Vec3d camPos = camera.getPos();
        MatrixStack matrices = ctx.matrixStack();
        
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        
        List<ChunkData> visible = new ArrayList<>();
        List<ChunkData> critical = new ArrayList<>();
        
        for (ChunkData data : analyzer.getChunks()) {
            double distSq = camPos.squaredDistanceTo(data.centerX(), camPos.y, data.centerZ());
            if (distSq < 180 * 180) {
                visible.add(data);
                if (data.isCritical()) critical.add(data);
            }
        }
        
        if (!visible.isEmpty()) {
            renderChunkSurfaces(matrices, visible);
        }
        
        if (!critical.isEmpty()) {
            renderWaves(matrices, critical);
        }
        
        matrices.pop();
        
        SculkConfig cfg = SculkConfig.get();
        if (cfg.holograms) {
            renderHolograms(ctx, visible, camera, camPos);
        }
        
        if (cfg.particles) {
            spawnParticles(client, critical, analyzer);
        }
    }
    
    private void renderChunkSurfaces(MatrixStack matrices, List<ChunkData> chunks) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        
        int count = 0;
        
        for (ChunkData data : chunks) {
            int baseX = data.pos().getStartX();
            int baseZ = data.pos().getStartZ();
            
            ChunkData.Status s = data.status();
            float r = s.r, g = s.g, b = s.b;
            
            // Pulse effect for critical only (color intensity, not position)
            float alpha = 0.35f;
            if (data.isCritical()) {
                float pulse = data.pulse();
                alpha = 0.25f + 0.25f * pulse;
                r = Math.min(1f, r * (0.7f + 0.3f * pulse));
            }
            
            // Render 4x4 grid of quads following terrain
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    float x1 = baseX + i * 4;
                    float z1 = baseZ + j * 4;
                    float x2 = x1 + 4;
                    float z2 = z1 + 4;
                    
                    // Get heights at 4 corners of this quad
                    float h00 = data.getHeight(i, j) + 0.05f;
                    float h10 = data.getHeight(i + 1, j) + 0.05f;
                    float h01 = data.getHeight(i, j + 1) + 0.05f;
                    float h11 = data.getHeight(i + 1, j + 1) + 0.05f;
                    
                    // Top surface quad
                    buf.vertex(mat, x1, h00, z1).color(r, g, b, alpha);
                    buf.vertex(mat, x2, h10, z1).color(r, g, b, alpha);
                    buf.vertex(mat, x2, h11, z2).color(r, g, b, alpha);
                    buf.vertex(mat, x1, h01, z2).color(r, g, b, alpha);
                    
                    count++;
                }
            }
            
            // Render border edges for clarity
            renderChunkBorder(buf, mat, data, r, g, b, alpha * 1.5f);
            count++;
        }
        
        if (count > 0) {
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
        
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
    
    private void renderChunkBorder(BufferBuilder buf, Matrix4f mat, ChunkData data, 
                                    float r, float g, float b, float alpha) {
        int x1 = data.pos().getStartX();
        int z1 = data.pos().getStartZ();
        int x2 = x1 + 16;
        int z2 = z1 + 16;
        
        float thick = 0.3f;
        float h = 0.5f; // Border height
        
        // North edge (z1)
        for (int i = 0; i < 4; i++) {
            float sx = x1 + i * 4;
            float ex = sx + 4;
            float y1 = data.getHeight(i, 0) + 0.05f;
            float y2 = data.getHeight(i + 1, 0) + 0.05f;
            
            buf.vertex(mat, sx, y1, z1).color(r, g, b, alpha);
            buf.vertex(mat, ex, y2, z1).color(r, g, b, alpha);
            buf.vertex(mat, ex, y2 + h, z1).color(r, g, b, alpha * 0.3f);
            buf.vertex(mat, sx, y1 + h, z1).color(r, g, b, alpha * 0.3f);
        }
        
        // South edge (z2)
        for (int i = 0; i < 4; i++) {
            float sx = x1 + i * 4;
            float ex = sx + 4;
            float y1 = data.getHeight(i, 4) + 0.05f;
            float y2 = data.getHeight(i + 1, 4) + 0.05f;
            
            buf.vertex(mat, ex, y2, z2).color(r, g, b, alpha);
            buf.vertex(mat, sx, y1, z2).color(r, g, b, alpha);
            buf.vertex(mat, sx, y1 + h, z2).color(r, g, b, alpha * 0.3f);
            buf.vertex(mat, ex, y2 + h, z2).color(r, g, b, alpha * 0.3f);
        }
        
        // West edge (x1)
        for (int j = 0; j < 4; j++) {
            float sz = z1 + j * 4;
            float ez = sz + 4;
            float y1 = data.getHeight(0, j) + 0.05f;
            float y2 = data.getHeight(0, j + 1) + 0.05f;
            
            buf.vertex(mat, x1, y2, ez).color(r, g, b, alpha);
            buf.vertex(mat, x1, y1, sz).color(r, g, b, alpha);
            buf.vertex(mat, x1, y1 + h, sz).color(r, g, b, alpha * 0.3f);
            buf.vertex(mat, x1, y2 + h, ez).color(r, g, b, alpha * 0.3f);
        }
        
        // East edge (x2)
        for (int j = 0; j < 4; j++) {
            float sz = z1 + j * 4;
            float ez = sz + 4;
            float y1 = data.getHeight(4, j) + 0.05f;
            float y2 = data.getHeight(4, j + 1) + 0.05f;
            
            buf.vertex(mat, x2, y1, sz).color(r, g, b, alpha);
            buf.vertex(mat, x2, y2, ez).color(r, g, b, alpha);
            buf.vertex(mat, x2, y2 + h, ez).color(r, g, b, alpha * 0.3f);
            buf.vertex(mat, x2, y1 + h, sz).color(r, g, b, alpha * 0.3f);
        }
    }
    
    private void renderWaves(MatrixStack matrices, List<ChunkData> critical) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        
        float time = SculkVisionClient.getTime();
        int count = 0;
        
        for (ChunkData data : critical) {
            float cx = data.centerX();
            float cz = data.centerZ();
            float y = data.avgHeight() + 0.1f;
            
            for (int i = 0; i < 3; i++) {
                float radius = (time * 10f + i * 16f) % 48f;
                float alpha = (1f - radius / 48f);
                alpha = alpha * alpha * 0.4f;
                
                if (alpha < 0.02f) continue;
                
                drawRing(buf, mat, cx, y, cz, radius, 0.8f, 0.15f, 0.85f, 0.95f, alpha, 20);
                count++;
            }
        }
        
        if (count > 0) {
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
        
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
    
    private void drawRing(BufferBuilder buf, Matrix4f mat,
                          float cx, float y, float cz,
                          float radius, float thick,
                          float r, float g, float b, float a, int segs) {
        float inner = radius - thick / 2;
        float outer = radius + thick / 2;
        
        for (int i = 0; i < segs; i++) {
            float a1 = (float) (2 * Math.PI * i / segs);
            float a2 = (float) (2 * Math.PI * (i + 1) / segs);
            
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            
            buf.vertex(mat, cx + c1 * inner, y, cz + s1 * inner).color(r, g, b, a * 0.2f);
            buf.vertex(mat, cx + c1 * outer, y, cz + s1 * outer).color(r, g, b, a);
            buf.vertex(mat, cx + c2 * outer, y, cz + s2 * outer).color(r, g, b, a);
            buf.vertex(mat, cx + c2 * inner, y, cz + s2 * inner).color(r, g, b, a * 0.2f);
        }
    }
    
    private void renderHolograms(WorldRenderContext ctx, List<ChunkData> chunks, Camera camera, Vec3d camPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer text = client.textRenderer;
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        
        for (ChunkData data : chunks) {
            double distSq = camPos.squaredDistanceTo(data.centerX(), camPos.y, data.centerZ());
            if (distSq > 70 * 70) continue;
            
            MatrixStack ms = new MatrixStack();
            
            float x = data.centerX();
            float y = data.maxHeight() + 4f;
            float z = data.centerZ();
            
            ms.translate(x - camPos.x, y - camPos.y, z - camPos.z);
            ms.multiply(camera.getRotation());
            ms.scale(-0.03f, -0.03f, 0.03f);
            
            Matrix4f mat = ms.peek().getPositionMatrix();
            
            ChunkData.Status s = data.status();
            int color = ((int)(s.r * 255) << 16) | ((int)(s.g * 255) << 8) | (int)(s.b * 255) | 0xFF000000;
            
            String l1 = "§l" + data.entities() + " entities";
            String l2 = data.blockEntities() + " tile entities";
            String l3 = String.format("~%.1f mspt", data.mspt());
            String l4 = getStatusLabel(s);
            
            float w1 = text.getWidth(l1) / 2f;
            float w2 = text.getWidth(l2) / 2f;
            float w3 = text.getWidth(l3) / 2f;
            float w4 = text.getWidth(l4) / 2f;
            
            text.draw(l1, -w1, -24, color, false, mat, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            text.draw(l2, -w2, -12, color, false, mat, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            text.draw(l3, -w3, 0, color, false, mat, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            text.draw(l4, -w4, 14, color, false, mat, immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        }
        
        immediate.draw();
    }
    
    private String getStatusLabel(ChunkData.Status s) {
        return switch (s) {
            case PERFECT -> "§a✓ PERFECT";
            case WARNING -> "§e⚠ WARNING";
            case DANGEROUS -> "§6⚠ DANGEROUS";
            case CRITICAL -> "§c§l☠ CRITICAL";
            case LOADING -> "§d◌ LOADING";
            case SCULK -> "§b✦ SCULK";
        };
    }
    
    private void spawnParticles(MinecraftClient client, List<ChunkData> critical, ChunkAnalyzer analyzer) {
        if (client.world == null) return;
        
        particleTick++;
        if (particleTick < 5) return;
        particleTick = 0;
        
        for (ChunkData data : critical) {
            double x = data.centerX() + random.nextDouble() * 14 - 7;
            double y = data.avgHeight() + 1 + random.nextDouble() * 4;
            double z = data.centerZ() + random.nextDouble() * 14 - 7;
            
            client.world.addParticle(ParticleTypes.SCULK_SOUL, x, y, z, 0, 0.06, 0);
            
            if (random.nextInt(4) == 0) {
                client.world.addParticle(ParticleTypes.SOUL, x, y, z,
                    (random.nextDouble() - 0.5) * 0.03, 0.08, (random.nextDouble() - 0.5) * 0.03);
            }
        }
        
        for (ChunkData data : analyzer.getChunks()) {
            if (data.isSculk() && random.nextInt(10) == 0) {
                double x = data.centerX() + random.nextDouble() * 14 - 7;
                double y = data.avgHeight() + 6;
                double z = data.centerZ() + random.nextDouble() * 14 - 7;
                client.world.addParticle(ParticleTypes.END_ROD, x, y, z, 0, -0.03, 0);
            }
        }
    }
    
    public void renderHud(DrawContext ctx, ChunkAnalyzer analyzer, int tempTicks) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer text = client.textRenderer;
        int w = client.getWindow().getScaledWidth();
        
        int y = 8;
        
        ctx.drawTextWithShadow(text, "§b§l⚡ SCULK VISION", 8, y, 0xFFFFFF);
        y += 14;
        
        ctx.drawTextWithShadow(text, "§7Chunks: §a" + analyzer.chunkCount(), 8, y, 0xAAAAAA);
        y += 11;
        ctx.drawTextWithShadow(text, "§7Entities: §e" + analyzer.totalEntities(), 8, y, 0xAAAAAA);
        y += 11;
        
        if (analyzer.criticalCount() > 0) {
            ctx.drawTextWithShadow(text, "§c⚠ Critical: " + analyzer.criticalCount(), 8, y, 0xFF5555);
            y += 11;
        }
        
        if (tempTicks > 0) {
            String timer = "§e⏱ " + (tempTicks / 20) + "s";
            ctx.drawTextWithShadow(text, timer, w - text.getWidth(timer) - 8, 8, 0xFFFF55);
        }
        
        y += 6;
        drawLegendItem(ctx, text, 8, y, 0xFF33FF66, "Perfect"); y += 10;
        drawLegendItem(ctx, text, 8, y, 0xFFFFDD22, "Warning"); y += 10;
        drawLegendItem(ctx, text, 8, y, 0xFFFF6622, "Dangerous"); y += 10;
        drawLegendItem(ctx, text, 8, y, 0xFFFF2233, "Critical"); y += 10;
        drawLegendItem(ctx, text, 8, y, 0xFF8833CC, "Loading"); y += 10;
        drawLegendItem(ctx, text, 8, y, 0xFF22DDEE, "Sculk");
    }
    
    private void drawLegendItem(DrawContext ctx, TextRenderer text, int x, int y, int color, String label) {
        ctx.fill(x, y, x + 8, y + 8, color);
        ctx.drawBorder(x, y, 8, 8, 0xFFFFFFFF);
        ctx.drawTextWithShadow(text, "§7" + label, x + 12, y, 0xAAAAAA);
    }
}