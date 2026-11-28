package ru.visual.client.mixin;

import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.module.impl.CustomFog;

import java.nio.ByteBuffer;

/** CustomFog: дальность и цвет тумана. Параметры попадают в шейдер через updateBuffer. */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "updateBuffer", at = @At("HEAD"), require = 0)
    private void pepevisual$fogColor(ByteBuffer buffer, int offset, Vector4f fogColor, float envStart, float envEnd,
                                     float rdStart, float rdEnd, float skyEnd, float cloudEnd, CallbackInfo ci) {
        CustomFog.color(fogColor);
    }

    @ModifyVariable(method = "updateBuffer", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float pepevisual$envStart(float v) {
        return CustomFog.start(v);
    }

    @ModifyVariable(method = "updateBuffer", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private float pepevisual$envEnd(float v) {
        return CustomFog.end(v);
    }

    @ModifyVariable(method = "updateBuffer", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private float pepevisual$rdStart(float v) {
        return CustomFog.start(v);
    }

    @ModifyVariable(method = "updateBuffer", at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private float pepevisual$rdEnd(float v) {
        return CustomFog.end(v);
    }
}
