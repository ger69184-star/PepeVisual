package ru.visual.client.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.visual.client.module.impl.FreeLook;

/** FreeLook: подмена углов камеры. Заодно отмечает, что камера-хук работает. */
@Mixin(Camera.class)
public class CameraMixin {
    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float pepevisual$yaw(float yRot) {
        return FreeLook.cameraYaw(yRot);
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private float pepevisual$pitch(float xRot) {
        return FreeLook.cameraPitch(xRot);
    }
}
