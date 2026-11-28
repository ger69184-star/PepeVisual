package ru.visual.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.module.impl.Zoom;
import ru.visual.client.util.Aim;
import ru.visual.client.util.Cps;

/** Zoom: колесо мыши меняет силу приближения. CPS: подсчёт кликов. Aim: плавный поворот камеры помощников каждый кадр. */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void pepevisual$zoomScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (handle != mc.getWindow().handle() || mc.screen != null) return;
        if (Zoom.onScroll(yoffset)) ci.cancel();
    }

    @Inject(method = "onButton", at = @At("HEAD"), require = 0)
    private void pepevisual$cps(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (action == 1 && mc.screen == null && handle == mc.getWindow().handle()) Cps.click(info.button());
    }

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), require = 0)
    private void pepevisual$userMouse(CallbackInfo ci) {
        if (Minecraft.getInstance().screen == null) Aim.onMouse(accumulatedDX, accumulatedDY);
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("TAIL"), require = 0)
    private void pepevisual$aimFrame(CallbackInfo ci) {
        Aim.frame(Minecraft.getInstance());
    }
}
