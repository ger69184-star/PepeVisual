package ru.visual.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.visual.client.module.impl.FreeLook;
import ru.visual.client.module.impl.Glow;

@Mixin(Entity.class)
public class EntityMixin {
    /** FreeLook: поворот мышью идёт в камеру, а не в персонажа. */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 0)
    private void pepevisual$freeLookTurn(double yRot, double xRot, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && FreeLook.onTurn(yRot, xRot)) ci.cancel();
    }

    /** Glow: свой цвет контура. */
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void pepevisual$glowColor(CallbackInfoReturnable<Integer> cir) {
        Integer c = Glow.colorFor((Entity) (Object) this);
        if (c != null) cir.setReturnValue(c);
    }
}
