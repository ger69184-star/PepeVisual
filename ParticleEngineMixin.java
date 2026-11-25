package ru.visual.client.mixin;

import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.module.ModuleManager;
import ru.visual.client.module.impl.NoRender;

/** NoRender: не добавляем частицы в мир. */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true, require = 0)
    private void pepevisual$noParticles(CallbackInfo ci) {
        if (ModuleManager.noRender != null && NoRender.hides(ModuleManager.noRender.particles)) ci.cancel();
    }
}
