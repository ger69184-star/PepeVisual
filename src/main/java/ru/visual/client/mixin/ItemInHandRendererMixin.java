package ru.visual.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.module.impl.HitAnimations;
import ru.visual.client.module.impl.ViewModel;

/** Подмена ванильной анимации взмаха в руке от первого лица. */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true, require = 0)
    private void pepevisual$swing(PoseStack poseStack, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
        if (HitAnimations.apply(poseStack, arm, swingProgress)) ci.cancel();
    }

    /** ViewModel: сдвиг/масштаб рук. Вызывающий код (GameRenderer) сам делает push/pop стека. */
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 0)
    private void pepevisual$viewModel(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
        ViewModel.apply(poseStack);
    }
}
