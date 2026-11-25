package ru.visual.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.visual.client.module.impl.Glow;

/** Glow: контур вокруг существ только на нашем экране (сервер о нём не знает и не может его сбросить). */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void pepevisual$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Glow.shouldGlow(entity) || ru.visual.client.module.impl.TargetEsp.shouldGlow(entity)) cir.setReturnValue(true);
    }
}
