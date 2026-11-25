package ru.visual.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.module.impl.NoRender;

/** NoRender: тряска камеры при уроне и анимация тотема. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private static void pepevisual$noHurtCam(CallbackInfo ci) {
        if (ModuleManager.noRender != null && NoRender.hides(ModuleManager.noRender.hurtCam)) ci.cancel();
    }

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true, require = 0)
    private static void pepevisual$noTotem(CallbackInfo ci) {
        if (ModuleManager.noRender != null && NoRender.hides(ModuleManager.noRender.totem)) ci.cancel();
    }
}
