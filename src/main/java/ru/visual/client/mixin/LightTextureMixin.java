package ru.visual.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.visual.client.module.impl.Fullbright;

/** Fullbright: подменяем яркость (gamma) только для карты освещения. Настройка в options.txt не меняется. */
@Mixin(LightTexture.class)
public class LightTextureMixin {
    @WrapOperation(method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"),
            require = 0)
    private Object pepevisual$fullbright(OptionInstance<?> instance, Operation<Object> original) {
        Object value = original.call(instance);
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && instance == mc.options.gamma() && Fullbright.active()) {
            return Fullbright.gamma();
        }
        return value;
    }
}
