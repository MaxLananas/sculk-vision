package com.sculkvision;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SculkConfig {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("sculkvision.json");
    
    private static SculkConfig INSTANCE;
    
    public boolean screenShake = true;
    public boolean particles = true;
    public boolean holograms = true;
    public float overlayHeightOffset = 0.1f;
    public int warningThreshold = 50;
    public int dangerousThreshold = 150;
    public int criticalThreshold = 300;
    public int warningBlockEntities = 10;
    public int criticalBlockEntities = 50;
    
    public static SculkConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }
    
    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                INSTANCE = GSON.fromJson(json, SculkConfig.class);
                if (INSTANCE == null) INSTANCE = new SculkConfig();
            } else {
                INSTANCE = new SculkConfig();
                save();
            }
        } catch (IOException e) {
            INSTANCE = new SculkConfig();
        }
    }
    
    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
        } catch (IOException ignored) {}
    }
}