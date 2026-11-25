package ru.visual.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.module.impl.BlockOutline;
import ru.visual.client.util.Projection;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    /** Запоминаем реальные матрицы камеры - по ним HUD-модули точно рисуют ESP поверх мира. */
    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void pepevisual$captureMatrices(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderOutline,
                                            Camera camera, Matrix4f modelView, Matrix4f projection, Matrix4f cullingProjection,
                                            GpuBufferSlice terrainFog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        Projection.capture(camera.position(), modelView, projection);
    }

    /** BlockOutline: цвет обводки блока под прицелом (второй вызов - обычная обводка). */
    @ModifyArg(method = "renderBlockOutline",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDLnet/minecraft/client/renderer/state/BlockOutlineRenderState;IF)V"),
            index = 6, require = 0)
    private int pepevisual$outlineColor(int color) {
        return BlockOutline.color(color);
    }

    @ModifyArg(method = "renderBlockOutline",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDLnet/minecraft/client/renderer/state/BlockOutlineRenderState;IF)V"),
            index = 7, require = 0)
    private float pepevisual$outlineWidth(float width) {
        return BlockOutline.width(width);
    }
}
