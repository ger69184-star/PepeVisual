package ru.visual.client.mixin;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.module.impl.NoRender;

/** NoRender: огонь, вода и "блок в лицо" на экране. */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true, require = 0)
    private static void pepevisual$noFire(CallbackInfo ci) {
        if (ModuleManager.noRender != null && NoRender.hides(ModuleManager.noRender.fire)) ci.cancel();
    }

    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true, require = 0)
    private static void pepevisual$noWater(CallbackInfo ci) {
        if (ModuleManager.noRender != null && NoRender.hides(ModuleManager.noRender.water)) ci.cancel();
    }

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true, require = 0)
    private static void pepevisual$noInWall(CallbackInfo ci) {
        if (ModuleManager.noRender != null && NoRender.hides(ModuleManager.noRender.inWall)) ci.cancel();
    }
}
