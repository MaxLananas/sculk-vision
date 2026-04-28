package com.sculkvision;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class SculkVisionClient implements ClientModInitializer {

    private static boolean enabled = false;
    private static int tempTicks = 0;
    private static long activationTime = 0;

    private static KeyBinding toggleKey;
    private static KeyBinding tempKey;

    private static ChunkAnalyzer analyzer;
    private static SculkRenderer renderer;

    private static float shakeIntensity = 0f;
    private static float shakeTime = 0f;

    @Override
    public void onInitializeClient() {
        SculkVisionMod.LOGGER.info("Sculk Vision initializing...");

        // Load config first
        SculkConfig.load();

        analyzer = new ChunkAnalyzer();
        renderer = new SculkRenderer();

        // ✅ Correction : nouvelle signature KeyBinding pour 1.21.4
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "sculkvision.key.toggle",
            GLFW.GLFW_KEY_F6,
            "sculkvision.key.category"
        ));

        tempKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "sculkvision.key.temp",
            GLFW.GLFW_KEY_F7,
            "sculkvision.key.category"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (isActive()) {
                try {
                    renderer.renderWorld(context, analyzer);
                } catch (Exception ignored) {}
            }
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (isActive()) {
                renderer.renderHud(context, analyzer, tempTicks);
            }
        });

        SculkVisionMod.LOGGER.info("Sculk Vision ready!");
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            tempTicks = 0;
            if (enabled) {
                activationTime = System.currentTimeMillis();
                analyzer.forceUpdate(client);
            }
            client.player.sendMessage(
                Text.literal(enabled ? "§b⚡ Sculk Vision §aON" : "§b⚡ Sculk Vision §cOFF"),
                true
            );
        }

        while (tempKey.wasPressed()) {
            tempTicks = 200;
            enabled = false;
            activationTime = System.currentTimeMillis();
            analyzer.forceUpdate(client);
            client.player.sendMessage(Text.literal("§b⚡ Sculk Vision §e10s"), true);
        }

        if (tempTicks > 0) tempTicks--;

        if (isActive()) {
            analyzer.tick(client);
            if (SculkConfig.get().screenShake) {
                updateShake(client);
            } else {
                shakeIntensity = 0;
            }
        }
    }

    private void updateShake(MinecraftClient client) {
        float targetShake = 0f;
        if (client.player != null) {
            for (ChunkData data : analyzer.getChunks()) {
                if (data.isCritical()) {
                    double dist = client.player.getPos().distanceTo(
                        new Vec3d(data.centerX(), client.player.getY(), data.centerZ())
                    );
                    if (dist < 48) {
                        float contribution = (float) (1.0 - dist / 48.0) * data.pulse() * 0.4f;
                        targetShake = Math.max(targetShake, contribution);
                    }
                }
            }
        }
        shakeIntensity = shakeIntensity * 0.85f + targetShake * 0.15f;
        shakeTime += 0.5f;
    }

    public static boolean isActive() {
        return enabled || tempTicks > 0;
    }

    public static float getTime() {
        return (System.currentTimeMillis() - activationTime) / 1000f;
    }

    public static float getShakeX() {
        return shakeIntensity > 0.01f ? (float) Math.sin(shakeTime * 1.7) * shakeIntensity : 0;
    }

    public static float getShakeY() {
        return shakeIntensity > 0.01f ? (float) Math.cos(shakeTime * 2.3) * shakeIntensity * 0.7f : 0;
    }

    public static ChunkAnalyzer getAnalyzer() {
        return analyzer;
    }
}
