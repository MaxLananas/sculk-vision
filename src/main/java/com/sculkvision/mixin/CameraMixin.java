package com.sculkvision.mixin;

import com.sculkvision.SculkVisionClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private float pitch;
    @Shadow private float yaw;
    
    @Inject(method = "update", at = @At("TAIL"))
    private void sculkvision$shake(BlockView area, Entity entity, boolean thirdPerson, 
                                    boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!SculkVisionClient.isActive()) return;
        
        float sx = SculkVisionClient.getShakeX();
        float sy = SculkVisionClient.getShakeY();
        
        if (Math.abs(sx) > 0.001f || Math.abs(sy) > 0.001f) {
            setRotation(yaw + sx, pitch + sy);
        }
    }
}