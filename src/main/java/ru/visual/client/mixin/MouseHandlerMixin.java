package ru.visual.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.module.impl.Zoom;

/** Zoom: колесо мыши меняет силу приближения, пока зажата клавиша зума. */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void pepevisual$zoomScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (handle != mc.getWindow().handle() || mc.screen != null) return;
        if (Zoom.onScroll(yoffset)) ci.cancel();
    }
}
