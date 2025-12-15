package com.sculkvision;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {
    
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SculkConfigScreen::new;
    }
    
    public static class SculkConfigScreen extends Screen {
        private final Screen parent;
        
        public SculkConfigScreen(Screen parent) {
            super(Text.translatable("sculkvision.config.title"));
            this.parent = parent;
        }
        
        @Override
        protected void init() {
            SculkConfig cfg = SculkConfig.get();
            int centerX = this.width / 2;
            int y = 40;
            int bw = 200;
            int bh = 20;
            int spacing = 24;
            
            // Screen Shake Toggle
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Screen Shake: " + (cfg.screenShake ? "§aON" : "§cOFF")),
                button -> {
                    cfg.screenShake = !cfg.screenShake;
                    button.setMessage(Text.literal("Screen Shake: " + (cfg.screenShake ? "§aON" : "§cOFF")));
                    SculkConfig.save();
                })
                .dimensions(centerX - bw / 2, y, bw, bh)
                .build());
            y += spacing;
            
            // Particles Toggle
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Particles: " + (cfg.particles ? "§aON" : "§cOFF")),
                button -> {
                    cfg.particles = !cfg.particles;
                    button.setMessage(Text.literal("Particles: " + (cfg.particles ? "§aON" : "§cOFF")));
                    SculkConfig.save();
                })
                .dimensions(centerX - bw / 2, y, bw, bh)
                .build());
            y += spacing;
            
            // Holograms Toggle
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Holograms: " + (cfg.holograms ? "§aON" : "§cOFF")),
                button -> {
                    cfg.holograms = !cfg.holograms;
                    button.setMessage(Text.literal("Holograms: " + (cfg.holograms ? "§aON" : "§cOFF")));
                    SculkConfig.save();
                })
                .dimensions(centerX - bw / 2, y, bw, bh)
                .build());
            y += spacing;
            
            // Overlay Height Slider
            this.addDrawableChild(new SliderWidget(
                centerX - bw / 2, y, bw, bh,
                Text.literal("Height Offset: " + String.format("%.1f", cfg.overlayHeightOffset)),
                (cfg.overlayHeightOffset + 5f) / 15f) {
                @Override
                protected void updateMessage() {
                    float val = (float) (this.value * 15f - 5f);
                    this.setMessage(Text.literal("Height Offset: " + String.format("%.1f", val)));
                }
                @Override
                protected void applyValue() {
                    cfg.overlayHeightOffset = (float) (this.value * 15f - 5f);
                    SculkConfig.save();
                }
            });
            y += spacing;
            
            // Warning Threshold Slider
            this.addDrawableChild(new SliderWidget(
                centerX - bw / 2, y, bw, bh,
                Text.literal("Warning: " + cfg.warningThreshold + " entities"),
                cfg.warningThreshold / 200f) {
                @Override
                protected void updateMessage() {
                    int val = (int) (this.value * 200);
                    this.setMessage(Text.literal("Warning: " + val + " entities"));
                }
                @Override
                protected void applyValue() {
                    cfg.warningThreshold = (int) (this.value * 200);
                    SculkConfig.save();
                }
            });
            y += spacing;
            
            // Critical Threshold Slider
            this.addDrawableChild(new SliderWidget(
                centerX - bw / 2, y, bw, bh,
                Text.literal("Critical: " + cfg.criticalThreshold + " entities"),
                cfg.criticalThreshold / 500f) {
                @Override
                protected void updateMessage() {
                    int val = (int) (this.value * 500);
                    this.setMessage(Text.literal("Critical: " + val + " entities"));
                }
                @Override
                protected void applyValue() {
                    cfg.criticalThreshold = (int) (this.value * 500);
                    SculkConfig.save();
                }
            });
            y += spacing + 20;
            
            // Done button
            this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.done"),
                button -> close())
                .dimensions(centerX - 100, this.height - 28, 200, 20)
                .build());
        }
        
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }
        
        @Override
        public void close() {
            SculkConfig.save();
            this.client.setScreen(parent);
        }
    }
}