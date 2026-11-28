package ru.visual.client.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    /** TargetESP: свой цвет контура для текущей цели. */
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void pepevisual$targetEspColor(CallbackInfoReturnable<Integer> cir) {
        Integer c = ru.visual.client.module.impl.TargetEsp.glowColor((Entity) (Object) this);
        if (c != null) cir.setReturnValue(c);
    }
}
