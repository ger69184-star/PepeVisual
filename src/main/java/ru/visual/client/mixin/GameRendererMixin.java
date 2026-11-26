package ru.visual.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.module.impl.NoRender;
import ru.visual.client.module.impl.Zoom;

/** NoRender: тряска камеры при уроне и анимация тотема. Zoom: подмена FOV (настройка игры не меняется). */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    // bobHurt / getFov / displayItemActivation - методы экземпляра, поэтому обработчики НЕ static
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void pepevisual$noHurtCam(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (ModuleManager.noRender != null && NoRender.hides(ModuleManager.noRender.hurtCam)) ci.cancel();
    }

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true, require = 0)
    private void pepevisual$noTotem(ItemStack stack, CallbackInfo ci) {
        if (ModuleManager.noRender != null && NoRender.hides(ModuleManager.noRender.totem)) ci.cancel();
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void pepevisual$zoomFov(Camera camera, float partialTicks, boolean applyEffects, CallbackInfoReturnable<Float> cir) {
        if (!applyEffects) return;
        float fov = cir.getReturnValueF();
        float z = Zoom.modifyFov(fov);
        if (z != fov) cir.setReturnValue(z);
    }
}
