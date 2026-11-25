package ru.visual.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.visual.client.module.impl.WorldTime;

/** WorldTime: своё время суток (только на твоём экране). */
@Mixin(ClientLevel.ClientLevelData.class)
public class ClientLevelDataMixin {
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true, require = 0)
    private void pepevisual$dayTime(CallbackInfoReturnable<Long> cir) {
        if (WorldTime.active()) cir.setReturnValue(WorldTime.dayTime());
    }
}
