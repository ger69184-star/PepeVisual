package ru.visual.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.visual.client.module.impl.AutoSprint;

/**
 * AutoSprint: сразу после чтения клавиш (input.tick) в этом же тике помечаем "спринт зажат".
 * Дальше ванильная логика aiStep сама проверяет голод, еду в руке, присед, стену и т.д. и включает бег
 * в том же тике, когда нажата W. Раньше клавиша спринта зажималась в конце тика - бег включался
 * только со следующего тика, а при включённом "Переключении бега" клавиша щёлкала каждый тик.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow public ClientInput input;

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER), require = 0)
    private void pepevisual$autoSprint(CallbackInfo ci) {
        if (!AutoSprint.active()) return;
        Input k = input.keyPresses;
        if (k.sprint() || !AutoSprint.wants(k)) return;
        input.keyPresses = new Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), k.shift(), true);
    }
}
